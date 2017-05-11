package com.sbell.ubus;

import java.lang.reflect.*;
import java.lang.annotation.*;

interface osgimgt {
    void install(String Plugin_Name, String Version, String PluginDir, String JarName);
    void installUpdate(String Plugin_Name, String Version, String PluginDir, String JarName, String RunMode);
    void installQuery(String Plugin_Name, String Version);
    void installCancel(String Plugin_Name, String Version);
    void uninstall(String Plugin_Name, String Version);
    void stop(String Plugin_Name, String Version);
    void run(String Plugin_Name, String Version);
    void restoreFactory(String Plugin_Name, String Version);
    void GetPluginList();
    void setPlugParam(String Plugin_Name, String Version, String Parameter);
    void getStatus(String Plugin_Name, String Version);
    void getPluginAttr(String Name);
    void getBundleAttr(String Name);
    void setAutoStart(String EUName, String NeedAutoStart);
    void setRequestedState(String EUName, String RequestedState);
    void setAPICap(String Name, String Cap);
    void APICapChange(String Name);
}

public class Test {

    static String[] mParams = null;

    static Runnable mRunner = new Runnable() {
        public void run() {
            for (int i = 0; i < 10000; i ++) {
                UbusPoller poller = UbusPoller.getInstance();
                String result = poller.ubusInvoke(mParams[0], mParams[1], mParams[2]);
                if (result != null) {
                    System.out.println(result);
                }
            }
        }
    };

    static void dumpTypeInfo(Class klass) {
        Method[] methods = klass.getDeclaredMethods();

        System.out.println("Class Name is " + klass.getName());
        for (int index = 0; index < methods.length; index++) {
            System.out.println("Name is " + methods[index].getName());
            Annotation[][] pas = methods[index].getParameterAnnotations();
        }
    }

    static final String json_str = "{\"name\": \"blog\"," + 
        "\"method\": [" +
            "{\"name\": \"gc\", \"policy\": []}," +
            "{\"name\": \"post\", \"policy\": [{\"name\": \"data\", \"type\": 3}]}," +
            "{\"name\": \"read\", \"policy\": [{\"name\": \"index\", \"type\": 3}]}" +
        "] }";

    public static void main(String[] args) {
        // Prints "Hello, World" to the terminal window.
        final String jsonStr = "{ \"Result\": 0, \"Plugin\": [ { \"Plugin_Name\": \"com.chinatelecom.all.smartgatew\", \"Version\": \"\", \"Run\": 1 }, { \"Plugin_Name\": \"com.huawei.smarthome.kernel\", \"Version\": \"\", \"Run\": 1 }, { \"Plugin_Name\": \"com.chinatelecom.all.smartgatew\", \"Version\": \"\", \"Run\": 1 } ] }";

        for (String str: args) 
            System.out.println("Hello, World " + WrapUbusResult(str));

        mParams = args;
        int objectId = UbusPoller.getInstance().ubusAddObject("osgimgr", json_str);
        System.out.println("object id is " + objectId);

        if (args.length >= 3) {
            for (int i = 0; i < 10; i ++) {
                UbusPoller poller = UbusPoller.getInstance();
                String result = poller.ubusInvoke(args[0], args[1], args[2]);
                if (result != null) {
                    System.out.println(result);
                }
            }
        }

        do {
            UbusPoller.UbusRequest req = UbusPoller.getInstance().acceptRequest();
            System.out.println(req.method + " " + req.params);
            if (req.method.equals("gc")) { 
                System.gc();
                System.out.println("JVM MAX MEMORY: " + Runtime.getRuntime().maxMemory()/1024/1024+"M");
                System.out.println("JVM IS USING MEMORY:" + Runtime.getRuntime().totalMemory()/1024/1024+"M");
                System.out.println("JVM IS FREE MEMORY:" + Runtime.getRuntime().freeMemory()/1024/1024+"M");
            }
            req.replyRequest(WrapUbusResult(jsonStr));
        } while (true);

        // dumpTypeInfo(osgimgt.class);
        // return;
    }

    static String WrapUbusResult(String origin) {
        String newRetval = origin;

        if (origin == null)
            newRetval = "{\"XXXBUSRET\": -1}";
        else if (origin.matches(".*\"Result\":.*"))
            newRetval = origin.replaceFirst("\"Result\":", "\"XXXBUSRET\":");
        else if (origin.matches(".*\\{[^\"]*\\}.*"))
            newRetval = origin.replaceFirst("\\{", "{\"XXXBUSRET\": 0");
        else
            newRetval = origin.replaceFirst("\\{", "{\"XXXBUSRET\": 0,");

        System.out.println("return " + newRetval);
        return newRetval;
    }
};
