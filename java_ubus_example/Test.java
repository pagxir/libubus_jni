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

    public static void main(String[] args) {
        // Prints "Hello, World" to the terminal window.
        System.out.println("Hello, World");

        mParams = args;
        if (args.length >= 3) {
/*
            for (int i = 0; i < 20; i++) {
                Thread newThread = new Thread(mRunner);
                // newThread.setDaemon(true);
                newThread.start();
            }
*/

            for (int i = 0; i < 10; i ++) {
                UbusPoller poller = UbusPoller.getInstance();
                String result = poller.ubusInvoke(args[0], args[1], args[2]);
                if (result != null) {
                    System.out.println(result);
                }
            }
        }

        try {
            Thread.sleep(1000000);
        } catch (InterruptedException e) {
            System.out.println(e.toString());
        }

        return;
    }
};
