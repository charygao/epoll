#include <jni.h>
#include <sys/epoll.h>
#include "org_jetlang_epoll_EPoll.h"
#include <cstdlib>
#include <unistd.h>
#include <sys/eventfd.h>
#include <stdio.h>

struct epoll_state {
   int fd;
   int efd;
   struct epoll_event * events;
   int max_events;
   struct epoll_event efd_event;
   char * buffer;
};

JNIEXPORT jint JNICALL Java_org_jetlang_epoll_EPoll_select
  (JNIEnv *, jclass, jlong ptrAddress, jint timeout){
    struct epoll_state *state = (struct epoll_state *) ptrAddress;
    printf("epoll wait\n");
    fflush(stdout);
    int result = epoll_wait(state->fd, state->events, state->max_events, timeout);
    printf("epoll wait %d\n", result);
    printf("events_address %p\n", &state->events);
    printf("events_address.fd %p\n", &state->events[0].data.fd);
    fflush(stdout);
    return result;
 }


JNIEXPORT jlong JNICALL Java_org_jetlang_epoll_EPoll_getEventArrayAddress
  (JNIEnv *, jclass, jlong ptrAddress){
    struct epoll_state *state = (struct epoll_state *) ptrAddress;
    return (jlong) state->events;
}


JNIEXPORT jlong JNICALL Java_org_jetlang_epoll_EPoll_getReadBufferAddress
  (JNIEnv *, jclass, jlong ptrAddress){
    struct epoll_state *state = (struct epoll_state *) ptrAddress;
    return (jlong) state->buffer;
}

JNIEXPORT jlong JNICALL Java_org_jetlang_epoll_EPoll_init
  (JNIEnv *, jclass, jint maxSelectedEvents, jint, jint readBufferBytes){
    int epoll_fd = epoll_create1(0);
    printf("epoll_fd %d\n", epoll_fd);
    struct epoll_state *state = (struct epoll_state *) malloc(sizeof(struct epoll_state));
    state->fd = epoll_fd;
    state->events = (struct epoll_event *) malloc(maxSelectedEvents * (sizeof(struct epoll_event)));
    state->max_events = maxSelectedEvents;
    state->efd = eventfd(0, EFD_NONBLOCK);
    state->efd_event.events = EPOLLHUP | EPOLLERR | EPOLLIN;
    state->efd_event.data.u32 = 0;
    int result = epoll_ctl(epoll_fd, EPOLL_CTL_ADD, state->efd, &state->efd_event);
    printf("add event fd %d fd %d\n", result, state->efd);
    fflush(stdout);
    state->buffer = (char *) malloc(readBufferBytes);
    return (jlong) state;
  }

JNIEXPORT void JNICALL Java_org_jetlang_epoll_EPoll_freeNativeMemory
  (JNIEnv *, jclass, jlong ptrAddress){
    struct epoll_state *state = (struct epoll_state *) ptrAddress;
    close(state->fd);
    close(state->efd);
    free(state->events);
    free(state->buffer);
    free(state);
  }

JNIEXPORT void JNICALL Java_org_jetlang_epoll_EPoll_interrupt
  (JNIEnv *, jclass, jlong ptrAddress){
      struct epoll_state *state = (struct epoll_state *) ptrAddress;
      printf("found epoll_fd %d\n", state->fd);
      uint64_t d;
      int written = write(state->efd, &d, sizeof(uint64_t));
      printf("written %d to %d\n", written, state->efd_event.data.fd);
      fflush(stdout);
  }

JNIEXPORT void JNICALL Java_org_jetlang_epoll_EPoll_clearInterrupt
  (JNIEnv *, jclass, jlong ptrAddress){
      struct epoll_state *state = (struct epoll_state *) ptrAddress;
      uint64_t d;
      printf("start read %d\n", state->efd);
      fflush(stdout);
      int result = read(state->efd, &d, sizeof(uint64_t));
      printf("read result %d\n", result);
      fflush(stdout);
  }


JNIEXPORT jint JNICALL Java_org_jetlang_epoll_EPoll_ctl
  (JNIEnv *, jclass, jlong, jint, jint, jint, jint){
    return 0;
  }
