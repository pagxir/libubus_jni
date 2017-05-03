public class Test {

    public static void main(String[] args) {
        // Prints "Hello, World" to the terminal window.
        System.out.println("Hello, World");

        if (args.length >= 3) {
            UbusPoller poller = UbusPoller.getInstance();
            poller.ubusInvoke(args[0], args[1], args[2]);
        }

        return;
    }
};
