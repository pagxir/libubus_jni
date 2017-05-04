#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <jni.h>

#include "ubus_wrapper.h"

#define JNI_LOG(fmt, args...) fprintf(stderr, fmt, ##args)

static const char *className = "UbusJNI";

#if 0
/*
    static public native void wakeup();
    static public native void reply(byte[] native_context);
    static public native boolean acceptRequest(byte[] native_context);

    static public native void release(byte[] native_context);
    static public native void invoke(int index, byte[] native_context);
    static public native int fetchReturn(int[] pendingInvokes, int count);

    static native int getNativeContextSize();
*/
#endif

#define HOLD_CONTEXT(ctx)   (struct ubus_jni_context *)(*env)->GetByteArrayElements(env, ctx, 0)
#define DROP_CONTEXT(ctx, upp) (*env)->ReleaseByteArrayElements(env, ctx, (void *)upp, JNI_ABORT)
#define FREE_CONTEXT(ctx, upp) (*env)->ReleaseByteArrayElements(env, ctx, (void *)upp, JNI_COMMIT)

static void jni_wakeup(JNIEnv *env, jclass clazz)
{
    ubus_wrap_wakeup();
    return;
}

static void jni_init(JNIEnv *env, jclass clazz)
{
    ubus_wrap_init();
    return;
}

static void jni_reply(JNIEnv *env, jclass clazz, jbyteArray context)
{
    struct ubus_jni_context *upp = HOLD_CONTEXT(context);
    assert(upp != NULL);

    ubus_wrap_reply(upp);
    FREE_CONTEXT(context, upp);
    return;
}

static jboolean jni_accept_request(JNIEnv *env, jclass clazz, jbyteArray context)
{
    int stat;
    struct ubus_jni_context *upp = HOLD_CONTEXT(context);
    assert(upp != NULL);

    stat = ubus_wrap_accept_request(upp);
    FREE_CONTEXT(context, upp);
    return (stat == 0)? JNI_TRUE: JNI_FALSE;
}

static void jni_release(JNIEnv *env, jclass clazz, jbyteArray context)
{
    struct ubus_jni_context *upp = HOLD_CONTEXT(context);
    assert(upp != NULL);

    ubus_wrap_release(upp);
    FREE_CONTEXT(context, upp);

    return;
}

static void jni_invoke(JNIEnv *env, jclass clazz, int index, jbyteArray context, jstring object, jstring method, jstring params)
{
    const char *buf_object, *buf_method, *buf_params;
    struct ubus_jni_context *upp = HOLD_CONTEXT(context);
    assert(upp != NULL);

    buf_object = (*env)->GetStringUTFChars(env, object, 0);
    buf_method = (*env)->GetStringUTFChars(env, method, 0);
    buf_params = (*env)->GetStringUTFChars(env, params, 0);
    assert(buf_object != NULL);
    assert(buf_method != NULL);
    assert(buf_params != NULL);

    ubus_wrap_invoke(upp, index, buf_object, buf_method, buf_params);
    (*env)->ReleaseStringUTFChars(env, params, buf_params);
    (*env)->ReleaseStringUTFChars(env, method, buf_method);
    (*env)->ReleaseStringUTFChars(env, object, buf_object);

    FREE_CONTEXT(context, upp);
    return;
}

static jint jni_fetch_return(JNIEnv *env, jclass clazz, jintArray results, jint size)
{
    int count;

    jint *returns = (*env)->GetIntArrayElements(env, results, 0);
    assert(returns != NULL);

    count = ubus_wrap_fetch_return(returns, size);
    (*env)->ReleaseIntArrayElements(env, results, returns, JNI_COMMIT);

    return count;
}

static jbyteArray jni_get_result(JNIEnv *env, jclass clazz, jbyteArray context)
{
    size_t len;
    jbyteArray result = NULL;
    struct ubus_jni_context *upp = HOLD_CONTEXT(context);
    const char * retval = ubus_wrap_get_result(upp);

    if (retval != NULL) {
        len = strlen(retval);
        result = (*env)->NewByteArray(env, len);
        if (result == NULL) {
            goto failure; 
        }

        (*env)->SetByteArrayRegion(env, result, 0, len, retval);
    }

failure:
    FREE_CONTEXT(context, upp);
    return result;
}

static jint jni_get_context_size(JNIEnv *env, jclass clazz)
{
    return ubus_wrap_get_context_size();
}

static void jni_abort(JNIEnv *env, jclass clazz)
{
    abort();
    return;
}

static jint jni_add_object(JNIEnv *env, jclass clazz, jstring name, jbyteArray context)
{
    int object_id;
    const char *buf_name;
    size_t data_len = (*env)->GetArrayLength(env, context);
    jbyte *data = (*env)->GetByteArrayElements(env, context, 0);
    assert(data != NULL);

    buf_name = (*env)->GetStringUTFChars(env, name, 0);
    assert(buf_name != NULL);

    object_id = ubus_wrap_add_object(buf_name, data, data_len);
    (*env)->ReleaseStringUTFChars(env, name, buf_name);
    (*env)->ReleaseByteArrayElements(env, context, data, JNI_ABORT);

    return object_id;
}

static void jni_remove_object(JNIEnv *env, jclass clazz, jint object_id)
{
    ubus_wrap_remove_object(object_id);
    return;
}

static JNINativeMethod methods[] = {
    {"init", "()V", (void *)jni_init},
    {"abort", "()V", (void *)jni_abort},
    {"wakeup", "()V", (void *)jni_wakeup},
    {"reply", "([B)V", (void *)jni_reply},
    {"acceptRequest", "([B)Z", (void *)jni_accept_request},

    {"release", "([B)V", (void *)jni_release},
    {"invoke", "(I[BLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", (void *)jni_invoke},
    {"fetchReturn", "([II)I", (void *)jni_fetch_return},

    {"getResult", "([B)[B", jni_get_result},
    {"getNativeContextSize", "()I", (void *)jni_get_context_size},

    {"addObject", "(Ljava/lang/String;[B)I", (void *)jni_add_object},
    {"removeObject", "(I)V", (void *)jni_remove_object},
};

jint JNI_OnLoad(JavaVM * vm, void * reserved)
{
    jclass clazz;
    JNIEnv* env = NULL;
    jint result = JNI_ERR;
    int methodsLength = 0;

    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_4) != JNI_OK) {
        JNI_LOG("ERROR: GetEnv failed\n");
        return JNI_ERR;
    }

    assert(env != NULL);

    clazz = (*env)->FindClass(env, className);
    if (clazz == NULL) {
        JNI_LOG("Native registration unable to find class '%s'", className);
        return JNI_ERR;
    }

    methodsLength = sizeof(methods) / sizeof(methods[0]);
    if ((*env)->RegisterNatives(env, clazz, methods, methodsLength) < 0) {
        JNI_LOG("RegisterNatives failed for '%s'", className);
        return JNI_ERR;
    }

    result = JNI_VERSION_1_4;
    return result;
}

void JNI_OnUnload(JavaVM * vm, void * reserved)
{
    JNI_LOG("call JNI_OnUnload ~~!!");
}
