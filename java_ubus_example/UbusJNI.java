public class UbusJNI {
    static Object signal = new Object();

    static public void wakeupUloop() {
        signal.notify();
        return;
    }

    static public int fetchReturn(int[] pendingInvokes, int count) {

        try {
            signal.wait();
        } catch (InterruptedException e) {

        }

        return 0;
    }

    static public boolean hasPendingRequest() {
        return false;
    }

    static public void pullRequest(byte[] native_context) {
    }
}
