/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class org_jetlang_epoll_EPoll */

#ifndef _Included_org_jetlang_epoll_EPoll
#define _Included_org_jetlang_epoll_EPoll
#ifdef __cplusplus
extern "C" {
#endif
#undef org_jetlang_epoll_EPoll_EVENT_SIZE
#define org_jetlang_epoll_EPoll_EVENT_SIZE 24L
/*
 * Class:     org_jetlang_epoll_EPoll
 * Method:    select
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_jetlang_epoll_EPoll_select
  (JNIEnv *, jclass, jlong, jint);

/*
 * Class:     org_jetlang_epoll_EPoll
 * Method:    getEventArrayAddress
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_org_jetlang_epoll_EPoll_getEventArrayAddress
  (JNIEnv *, jclass, jlong);

/*
 * Class:     org_jetlang_epoll_EPoll
 * Method:    getReadBufferAddress
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_org_jetlang_epoll_EPoll_getReadBufferAddress
  (JNIEnv *, jclass, jlong);

/*
 * Class:     org_jetlang_epoll_EPoll
 * Method:    init
 * Signature: (III)J
 */
JNIEXPORT jlong JNICALL Java_org_jetlang_epoll_EPoll_init
  (JNIEnv *, jclass, jint, jint, jint);

/*
 * Class:     org_jetlang_epoll_EPoll
 * Method:    freeNativeMemory
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_org_jetlang_epoll_EPoll_freeNativeMemory
  (JNIEnv *, jclass, jlong);

/*
 * Class:     org_jetlang_epoll_EPoll
 * Method:    interrupt
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_org_jetlang_epoll_EPoll_interrupt
  (JNIEnv *, jclass, jlong);

JNIEXPORT void JNICALL Java_org_jetlang_epoll_EPoll_clearInterrupt
  (JNIEnv *, jclass, jlong);

/*
 * Class:     org_jetlang_epoll_EPoll
 * Method:    ctl
 * Signature: (JIIII)I
 */
JNIEXPORT jint JNICALL Java_org_jetlang_epoll_EPoll_ctl
  (JNIEnv *, jclass, jlong, jint, jint, jint, jint);

#ifdef __cplusplus
}
#endif
#endif
