package org.jetlang.epoll;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.channels.DatagramChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class EPoll implements Executor {

    private static final Unsafe unsafe;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception failed) {
            throw new ExceptionInInitializerError(failed);
        }
        System.loadLibrary("jetlang-epoll");
    }

    private final Object lock = new Object();
    private final long ptrAddress;
    private final Packet[] udpReadBuffers;
    private final long[] eventIdxAddresses;
    private final long[] eventsAddresses;
    private final Thread thread;
    private boolean running = true;
    private ArrayList<Runnable> pending = new ArrayList<>();
    private final ArrayList<State> unused = new ArrayList<>();
    private final ArrayList<State> fds = new ArrayList<State>();
    private final Map<Integer, State> stateMap = new HashMap<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final Controls controls = new Controls();

    public class Controls {

        public int receive(int fd) {
            return recvmmsg(ptrAddress, fd);
        }
    }

    private static class State {

        public int fd;
        public final int idx;
        public EventConsumer handler;
        private boolean hasNativeStructure;
        private long eventAddress;

        public State(int idx) {
            this.idx = idx;
        }

        public void cleanupNativeResources(Unsafe unsafe) {
            if (hasNativeStructure) {
                hasNativeStructure = false;
                unsafe.freeMemory(eventAddress);
            }
        }

        public void setNativeStructureAddress(long ptr) {
            this.eventAddress = ptr;
            hasNativeStructure = true;
        }

        public void init(int fd, EventConsumer reader) {
            this.fd = fd;
            this.handler = reader;
        }
    }

    public static class Packet {
        public final Unsafe unsafe;
        public final long bufferAddress;
        private final long msgLengthAddress;

        public Packet(Unsafe u, long bufferAddress, long msgLengthAddress) {
            unsafe = u;
            this.bufferAddress = bufferAddress;
            this.msgLengthAddress = msgLengthAddress;
        }

        public int getLength() {
            return unsafe.getInt(msgLengthAddress);
        }
    }

    public EPoll(String threadName, int maxSelectedEvents, int maxDatagramsPerRead, int readBufferBytes,
                 PollStrategy poller, EventBatch eventBatcher) {
        maxSelectedEvents++; // + 1 for interrupt handler
        this.ptrAddress = init(maxSelectedEvents, maxDatagramsPerRead, readBufferBytes);
        this.udpReadBuffers = new Packet[maxDatagramsPerRead];
        for (int i = 0; i < maxDatagramsPerRead; i++) {
            long readBufferAddress = getReadBufferAddress(ptrAddress, i);
            Packet p = new Packet(unsafe, readBufferAddress, getMsgLengthAddress(ptrAddress, i));
            this.udpReadBuffers[i] = p;
        }
        this.eventIdxAddresses = new long[maxSelectedEvents];
        for (int i = 0; i < maxSelectedEvents; i++) {
            this.eventIdxAddresses[i] = getEpollEventIdxAddress(ptrAddress, i);
        }
        this.eventsAddresses = new long[maxSelectedEvents];
        for (int i = 0; i < maxSelectedEvents; i++) {
            this.eventsAddresses[i] = getEpollEventsAddress(ptrAddress, i);
        }
        Runnable eventLoop = () -> {
            while (running) {
                int events = poller.poll(ptrAddress);
                eventBatcher.start(events);
                for (int i = 0; i < events; i++) {
                    int idx = unsafe.getInt(eventIdxAddresses[i]);
                    State state = fds.get(idx);
                    int event = unsafe.getInt(eventsAddresses[i]);
                    EventResult result = state.handler.onEvent(event);
                    if (result == EventResult.Remove) {
                        remove(state.fd);
                    }
                }
                eventBatcher.end();
            }
            cleanUpNativeResources();
        };
        this.thread = createThread(eventLoop, threadName);
        State interrupt = claimState();
        interrupt.handler = new EventConsumer() {
            ArrayList<Runnable> swap = new ArrayList<>();

            @Override
            public EventResult onEvent(int events) {
                synchronized (lock) {
                    ArrayList<Runnable> tmp = pending;
                    pending = swap;
                    swap = tmp;
                    clearInterrupt(ptrAddress);
                }
                for (int i = 0, size = swap.size(); i < size; i++) {
                    runEvent(swap.get(i));
                }
                swap.clear();
                return EventResult.Continue;
            }

            @Override
            public void onRemove() {

            }
        };
    }

    protected Thread createThread(Runnable eventLoop, String threadName) {
        Thread t = new Thread(eventLoop, threadName);
        t.setDaemon(true);
        return t;
    }

    private void cleanUpNativeResources() {
        for (Integer fd : new ArrayList<>(stateMap.keySet())) {
            remove(fd);
        }
        freeNativeMemory(ptrAddress);
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            thread.start();
        }
    }

    public void close() {
        if (started.compareAndSet(false, true)) {
            cleanUpNativeResources();
        } else {
            execute(() -> {
                running = false;
            });
        }
    }

    public boolean awaitClose(int timeoutInMilis) {
        close();
        try {
            getThread().join(timeoutInMilis);
        } catch (InterruptedException e) {

        }
        return !getThread().isAlive();
    }


    protected void runEvent(Runnable runnable) {
        runnable.run();
    }

    static native int epollWait(long ptrAddress);

    static native int epollSpin(long ptrAddress);

    static native int epollSpinWait(long ptrAddress, long microsecondsToSpin);

    private static native long getEpollEventIdxAddress(long ptrAddress, int idx);

    private static native long getEpollEventsAddress(long ptrAddress, int idx);

    private static native long getReadBufferAddress(long ptrAddress, int idx);

    private static native long getMsgLengthAddress(long ptrAddress, int idx);

    private static native long init(int maxSelectedEvents, int maxDatagramsPerRead, int readBufferBytes);

    private static native void freeNativeMemory(long ptrAddress);

    private static native void interrupt(long ptrAddress);

    private static native void clearInterrupt(long ptrAddress);

    private static native long ctl(long ptrAddress, int op, int eventTypes, int fd, int idx);

    private static native int recvmmsg(long ptrAddress, int fd);

    public Runnable register(DatagramChannel channel, DatagramReader reader) {
        DatagramReader.Factory factory = new DatagramReader.Factory(reader);
        return register(channel, factory);
    }

    public Runnable register(DatagramChannel channel, EventConsumer.Factory factory) {
        final int fd = FdUtils.getFd(channel);
        final int eventTypes = EventTypes.EPOLLIN.value;
        return register(fd, eventTypes, factory);
    }

    public Runnable register(AbstractSelectableChannel serverSocketChannel, int eventTypes, EventConsumer.Factory factory) {
        final int fd = FdUtils.getFd(serverSocketChannel);
        return register(fd, eventTypes, factory);
    }

    public Runnable register(int fd, int eventTypes, EventConsumer.Factory factory) {
        execute(() -> {
            State e = claimState();
            e.init(fd, factory.create(fd, unsafe, controls, udpReadBuffers));
            addFd(eventTypes, fd, e);
            stateMap.put(fd, e);
        });
        return () -> {
            execute(() -> {
                remove(fd);
            });
        };
    }

    private void remove(int fd) {
        State st = stateMap.remove(fd);
        if (st != null) {
            ctl(ptrAddress, Ops.Del.value, 0, fd, st.idx);
            unused.add(st);
            st.cleanupNativeResources(unsafe);
            st.handler.onRemove();
        }
    }

    private void addFd(int eventTypes, int fd, State st) {
        st.setNativeStructureAddress(ctl(ptrAddress, Ops.Add.value, eventTypes, fd, st.idx));
    }

    private State claimState() {
        if (!unused.isEmpty()) {
            return unused.remove(unused.size() - 1);
        } else {
            State st = new State(fds.size());
            fds.add(st);
            return st;
        }
    }

    public Thread getThread() {
        return thread;
    }

    @Override
    public void execute(Runnable runnable) {
        synchronized (lock) {
            if (running) {
                pending.add(runnable);
                if (pending.size() == 1) {
                    interrupt(ptrAddress);
                }
            }
        }
    }

    enum Ops {
        Add(1), Del(2);

        private final int value;

        Ops(int value) {
            this.value = value;
        }
    }

    enum EventTypes {
        EPOLLIN(0x00000001),
        EPOLLPRI(0x00000002),
        EPOLLOUT(0x00000004),
        EPOLLERR(0x00000008),
        EPOLLHUP(0x00000010),
        EPOLLNVAL(0x00000020),
        EPOLLRDNORM(0x00000040),
        EPOLLRDBAND(0x00000080),
        EPOLLWRNORM(0x00000100),
        EPOLLWRBAND(0x00000200),
        EPOLLMSG(0x00000400),
        EPOLLRDHUP(0x00002000);

        public final int value;

        EventTypes(int value) {
            this.value = value;
        }

        public boolean isSet(int event) {
            return (event & value) != 0;
        }
    }
}
