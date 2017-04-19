import java.util.Queue;
import java.util.LinkedList;


public class UbusPoller implements Runnable {

    static class UbusInvoke {
        Object data;
        Object result;
        String method;
        byte[] native_array; // allocate memory for native use
        boolean completed = false;

        public void nativeInvoke(int index, byte[] native_context) {
        }

        public void completeInvoke() {
            synchronized(this) {
                completed = true;
                this.notify();
            }
        }
    }

    static class UbusRequest {
        byte[] native_array; // allocate memory for native use

    }

    final int MAX_INVOKE = 100;

    int mLastIndex = 0;
    UbusInvoke[] mPendingInvoke = new UbusInvoke[MAX_INVOKE];

    public int findEmptyInvokeSlot() {
        int lastIndex;

        for (lastIndex = mLastIndex; lastIndex < MAX_INVOKE; lastIndex++) {
            if (mPendingInvoke[lastIndex] == null) {
                mLastIndex = lastIndex;
                return lastIndex;
            }
        }

        for (lastIndex = 0; lastIndex < mLastIndex; lastIndex++) {
            if (mPendingInvoke[lastIndex] == null) {
                mLastIndex = lastIndex;
                return lastIndex;
            }
        }

        // throw new RuntimeException("Out of Ubus Request Slot");
        return -1;
    }

    public void run() {
        int index;
        int[] pendingReturns = new int[100];

        for ( ; ; ) {
            synchronized (mInvokeQueue) {
                while (!mInvokeQueue.isEmpty()) {
                    index = findEmptyInvokeSlot();
                    if (index == -1) {
                        break;
                    }

                    UbusInvoke invoke = mInvokeQueue.remove();
                    invoke.nativeInvoke(index, invoke.native_array);
                    mPendingInvoke[index] = invoke;
                }
            }

            int count = UbusJNI.fetchReturn(pendingReturns, pendingReturns.length);

            for (int i = 0; i < count; i++) {
                index = pendingReturns[i];
                UbusInvoke ret = mPendingInvoke[index];
                mPendingInvoke[index] = null;
                ret.completeInvoke();
            }

            while (UbusJNI.hasPendingRequest()) {
                UbusRequest req = new UbusRequest();
                UbusJNI.pullRequest(req.native_array);
                mRequestQueue.add(req);
                mRequestQueue.notify();
            }
        }
    }

    public void start() {
        mThread.start();
    }

    private Thread mThread = new Thread(this);
    private Queue<UbusInvoke> mInvokeQueue = new LinkedList<UbusInvoke>();
    private Queue<UbusRequest> mRequestQueue = new LinkedList<UbusRequest>();

    static void init() {
        instance.start();
    }

    private Object ubusInvoke(String method, Object data) {
        UbusInvoke invoke = new UbusInvoke();
        invoke.data = data;
        invoke.method = method;
        invoke.completed = false;

        try {
            synchronized (mInvokeQueue) {
                mInvokeQueue.add(invoke);
                UbusJNI.wakeupUloop();
            }

            synchronized (invoke) {
                while (!invoke.completed) {
                    invoke.wait();
                }
            }
        } catch (InterruptedException e) {

        }

        return invoke.result;
    }

    private static final UbusPoller instance = new UbusPoller();
};

