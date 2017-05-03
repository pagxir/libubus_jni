import java.util.Queue;
import java.util.LinkedList;


public class UbusPoller implements Runnable {

    static class UbusInvoke {
        String object;
        String method;
        String params;
        String result;
        boolean isPending = false;

        byte[] native_context = UbusJNI.createContext(); // allocate memory for native use
        boolean completed = false;

        void nativeInvoke(int index) {
            System.out.println("call nativeInvoke");
            UbusJNI.invoke(index, native_context, object, method, params);
            isPending = true;
            return;
        }

        void completeInvoke() {
            System.out.println("call completeInvoke");
            byte[] retval = UbusJNI.getResult(native_context);
            UbusJNI.release(native_context);
            if (retval != null) {
                result = new String(retval);
            }

            isPending = false;
            synchronized(this) {
                completed = true;
                this.notify();
            }
        }

        protected void finalize() {
            if (isPending) {
                UbusJNI.abort();
            }
        }
    }

    static class UbusRequest {
        byte[] native_context = UbusJNI.createContext(); // allocate memory for native use

        public boolean pullRequest() {
            return UbusJNI.acceptRequest(native_context);
        }

        public void relayRequest() {
            UbusJNI.reply(native_context);
            return;
        }
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

        UbusJNI.init();
        for ( ; ; ) {
            synchronized (mInvokeQueue) {
                while (!mInvokeQueue.isEmpty()) {
                    index = findEmptyInvokeSlot();
                    if (index == -1) {
                        break;
                    }

                    UbusInvoke invoke = mInvokeQueue.remove();
                    mPendingInvoke[index] = invoke;
                    invoke.nativeInvoke(index);
                }
            }

            int count = UbusJNI.fetchReturn(pendingReturns, pendingReturns.length);

            for (int i = 0; i < count; i++) {
                index = pendingReturns[i];
                UbusInvoke ret = mPendingInvoke[index];
                mPendingInvoke[index] = null;
                ret.completeInvoke();
            }

            UbusRequest req = new UbusRequest();
            while (req.pullRequest()) {
                mRequestQueue.add(req);
                mRequestQueue.notify();
                req = new UbusRequest();
            }
        }
    }

    private Thread mThread = new Thread(this);
    private Queue<UbusInvoke> mInvokeQueue = new LinkedList<UbusInvoke>();
    private Queue<UbusRequest> mRequestQueue = new LinkedList<UbusRequest>();

    public String ubusInvoke(String object, String method, String params) {
        UbusInvoke invoke = new UbusInvoke();
        invoke.object = object;
        invoke.method = method;
        invoke.params = params;
        invoke.completed = false;

        try {
            synchronized (mInvokeQueue) {
                mInvokeQueue.add(invoke);
                UbusJNI.wakeup();
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

    public static UbusPoller getInstance() {
        instance.start();
        return instance;
    }

    private boolean isStarted = false;
    public void start() {
        boolean oldStarted = true;

        synchronized(this) {
            oldStarted = isStarted;
            isStarted = true;
        }

        if (oldStarted == false) {
            mThread.setDaemon(true);
            mThread.start();
        }
    }
};

