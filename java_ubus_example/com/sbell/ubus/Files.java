package com.sbell.ubus;

public class Files {
    public static native int createSymbolLink(String oldpath, String newpath);

    static {
        System.loadLibrary("ubus_jni");
    }
}
