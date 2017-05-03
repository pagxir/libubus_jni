import java.util.Queue;
import java.util.LinkedList;


public class UbusPoller implements Runnable {

    static class UbusInvoke {
        String object;
        String method;
        String params;
        String result;
        byte[] native_context = UbusJNI.createContext(); // allocate memory for native use

        int reqIndex = 0;
        boolean isPending = false;
        boolean isAborted = false;
        boolean canAbort  = false;

        boolean queued = false;
        boolean completed = false;

        boolean nativeInvoke(int index) {
            boolean doAbort = false;
            boolean doInvoke = false;
            System.out.println("call nativeInvoke");

            synchronized(this) {
                this.queued = false;

                if (!isAborted) {
                    isPending = true;
                    doInvoke = true;
                    canAbort = true;
                    reqIndex = index;
                } else if (isPending) {
                    instance.mPendingInvoke[reqIndex] = null;
                    doAbort = canAbort;
                    canAbort = false;
                } else {
                    this.completed = true;
                    this.notify();
                }
            }

            if (doInvoke) {
                UbusJNI.invoke(this.reqIndex, native_context, object, method, params);
            }

            if (doAbort) {
                UbusJNI.release(native_context);
                synchronized(this) {
                    this.isPending = false;
                    this.completed = true;
                    this.notify();
                }
            }

            return doInvoke;
        }

        private boolean enqueue() {
            if (queued) return true;

            queued = true;
            synchronized(instance.mInvokeQueue) {
                instance.mInvokeQueue.add(this);
                UbusJNI.wakeup();
            }
            return false;
        }

        boolean start() {
            synchronized(this) {
                if (isPending) return false;
                if (isAborted) return false;
                if (completed) return false;
                enqueue();
            }
            return true;
        }

        boolean cancel() {
            synchronized(this) {
                if (completed) return false;
                isAborted = true;
                enqueue();
            }
            return true;
        }

        void completeInvoke() {
            System.out.println("call completeInvoke");
            byte[] retval = UbusJNI.getResult(native_context);
            UbusJNI.release(native_context);

            try {
                if (retval != null) 
                    result = new String(retval);
            } catch (Exception e) {
                System.out.println(e.toString());
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
                    if (invoke.nativeInvoke(index)) {
                        mPendingInvoke[index] = invoke;
                    }
                }
            }

            int count = UbusJNI.fetchReturn(pendingReturns, pendingReturns.length);

            for (int i = 0; i < count; i++) {
                index = pendingReturns[i];
                UbusInvoke ret = mPendingInvoke[index];
                if (ret != null) {
                    mPendingInvoke[index] = null;
                    ret.completeInvoke();
                }
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

            invoke.start();
            synchronized (invoke) {
                if (!invoke.completed) {
                    invoke.wait(10000);
                }
            }

            invoke.cancel();
            synchronized (invoke) {
                while (!invoke.completed || invoke.queued) {
                    invoke.wait();
                }
            }
        } catch (InterruptedException e) {
            System.out.println(e.toString());
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

