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
            "{\"name\": \"post\", \"policy\": [{\"name\": \"data\", \"type\": 3}]}," +
            "{\"name\": \"read\", \"policy\": [{\"name\": \"index\", \"type\": 3}]}" +
        "] }";

    public static void main(String[] args) {
        // Prints "Hello, World" to the terminal window.
        System.out.println("Hello, World");

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

        try {
            Thread.sleep(10000);
        } catch (Exception e) {
        }

        dumpTypeInfo(osgimgt.class);
        return;
    }
};
