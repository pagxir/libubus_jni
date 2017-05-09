package com.sbell.ubus;

public class UbusJNI {
    static public native void init();
    static public native void abort();
    static public native void wakeup();
    static public native byte[] getResult(byte[] native_context);
    static public native String getRequestJson(byte[] native_context);
    static public native String getRequestMethod(byte[] native_context);

    static public native void reply(byte[] native_context, String result);
    static public native boolean acceptRequest(byte[] native_context);

    static public native void release(byte[] native_context);
    static public native void invoke(int index, byte[] native_context, String object, String method, String params);
    static public native int fetchReturn(int[] pendingInvokes, int count);
    static native int getNativeContextSize();

    static public byte[] createContext() {
        final int NATIVE_CONTEXT_LENGTH = getNativeContextSize();
        return new byte[NATIVE_CONTEXT_LENGTH];
    }

    static public native int addObject(String name, String type_json);
    static public native void removeObject(int objectId);

    static {
        System.loadLibrary("ubus_jni");
    }

}
