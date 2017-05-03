public class Test {

    public static void main(String[] args) {
        // Prints "Hello, World" to the terminal window.
        System.out.println("Hello, World");

        if (args.length >= 3) {
            for (int i = 0; i < 10000; i ++) {
                UbusPoller poller = UbusPoller.getInstance();
                String result = poller.ubusInvoke(args[0], args[1], args[2]);
                if (result != null) {
                    System.out.println(result);
                }
            }
        }

        return;
    }
};
