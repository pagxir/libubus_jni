public class UbusJNI {
    static public native void init();
    static public native void abort();
    static public native void wakeup();
    static public native byte[] getResult(byte[] native_context);
    static public native void reply(byte[] native_context);
    static public native boolean acceptRequest(byte[] native_context);

    static public native void release(byte[] native_context);
    static public native void invoke(int index, byte[] native_context, String object, String method, String params);
    static public native int fetchReturn(int[] pendingInvokes, int count);
    static native int getNativeContextSize();

    static public byte[] createContext() {
        final int NATIVE_CONTEXT_LENGTH = getNativeContextSize();
        return new byte[NATIVE_CONTEXT_LENGTH];
    }

    static {
        System.loadLibrary("ubus_jni");
    }

}
