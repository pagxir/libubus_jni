import java.util.Queue;
import java.util.LinkedList;

class UbusInvokable {
    boolean mQueued = false;
    Runnable mRunnable = null;

    public UbusInvokable(Runnable _runnable) {
        mRunnable = _runnable;
    }

    public void run() {
        mRunnable.run();
    }
}

interface UbusInvokableState {
    public void waiting(long millseconds);
    public void waiting();
    public void start();
    public void cancel();
    public void run();
}

class UbusBaseState implements UbusInvokableState {
    protected UbusInvoker mInvoker = null;
    protected UbusBaseState(UbusInvoker invoker) {
        mInvoker = invoker;
    }

    public void run() { 
    }

    public void waiting() {
        mInvoker.invokeWait();
    }

    public void waiting(long millseconds) {
        mInvoker.invokeWait(millseconds);
    }

    public void start() {
    }

    public void cancel() {
    }
}

class UbusInitializeState extends UbusBaseState {

    public UbusInitializeState(UbusInvoker invoker) {
        super(invoker);
    }

    public void run() {
        throw new IllegalStateException("could not run on initialize state");
    }

    public void start() {
        mInvoker.invokeStart();
        return;
    }

    public void waiting() {
        throw new IllegalStateException("could not wait on initialize state");
    }

    public void waiting(long millseconds) {
        throw new IllegalStateException("could not wait on initialize state");
    }
}

class UbusQueuedState extends UbusBaseState {
    public UbusQueuedState(UbusInvoker invoker) {
        super(invoker);
    }

    public void run() {
        mInvoker.invokeNative();
        return;
    }

    public void cancel() {
        mInvoker.JavaCancel();
        return;
    }
}

class UbusPendingState extends UbusBaseState {
    public UbusPendingState(UbusInvoker invoker) {
        super(invoker);
    }

    public void cancel() {
        mInvoker.JNICancel();
        return;
    }
}

class UbusCanceledState extends UbusBaseState {
    public UbusCanceledState(UbusInvoker invoker) {
        super(invoker);
    }

    public void run() {
        mInvoker.JNIAbort();
        return;
    }
}

class UbusCompletedState extends UbusBaseState {
    public UbusCompletedState(UbusInvoker invoker) {
        super(invoker);
    }

    public void run() {
        mInvoker.javaAbort();
        return;
    }

    public void waiting(long millseconds) {
        return;
    }

    public void waiting() {
        return;
    }
}

class UbusInvoker extends UbusInvokable implements Runnable {
    String object;
    String method;
    String params;
    String result;

    int index;
    boolean completed;
    byte[] native_context = UbusJNI.createContext();

    public UbusInvoker() {
        super(null);
        mRunnable = this;
    }

    public void run() {
        synchronized(this) {
            invokable.run();
        }
    }

    private String getResult() {
        byte[] data = UbusJNI.getResult(native_context);

        try {
            if (data != null)
                return new String(data);
        } catch (Exception e) {
            System.out.println(e.toString());
        }

        return null;
    }

    void completeInvoke() {
        synchronized(this) {
            UbusPoller.getInstance().setInvokeSlot(index, null);
            result = getResult();

            UbusJNI.release(native_context);
            invokable = new UbusCompletedState(this);
            completed = true;
            notify();
        }
        return;
    }

    public void invokeStart() {
        UbusPoller.getInstance().add(this);
        invokable = new UbusQueuedState(this);
    }

    public void invokeWait() {
        try {
            if (!completed) wait();
        } catch (InterruptedException e) {
            System.out.println(e.toString());
        }
    }

    public void invokeWait(long millseconds) {
        try {
            if (!completed) wait(millseconds);
        } catch (InterruptedException e) {
            System.out.println(e.toString());
        }
    }

    public void JavaCancel() {
        UbusPoller.getInstance().add(this);
        invokable = new UbusCompletedState(this);
    }

    public void JNICancel() {
        UbusPoller.getInstance().add(this);
        invokable = new UbusCanceledState(this);
    }

    public void javaAbort() {
        completed = true;
        invokable = new UbusCompletedState(this);
        notify();
    }

    public void JNIAbort() {
        UbusJNI.release(native_context);
        invokable = new UbusCompletedState(this);
        UbusPoller.getInstance().setInvokeSlot(index, null);
        completed = true;
        notify();
    }

    public void invokeNative() {
        int _index = UbusPoller.getInstance().findEmptyInvokeSlot();

        if (_index != -1) {
            UbusJNI.invoke(_index, native_context, object, method, params);
            UbusPoller.getInstance().setInvokeSlot(_index, this);
            invokable = new UbusPendingState(this);
            this.index = _index;
        } else {
            invokable = new UbusCompletedState(this);
            completed = true;
            notify();
        }
    }

    UbusInvokableState invokable = new UbusInitializeState(this);

    void remoteInvoke() {

        synchronized(this) {
            invokable.start();
            invokable.waiting(4000);
            invokable.cancel();
            System.out.println("cancel and wait");
            invokable.waiting();
        }

        return;
    }

    protected void finalize() {
        synchronized(this) {
            invokable.cancel();
        }
    }
}

public class UbusPoller implements Runnable {
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

    public String ubusInvoke(String object, String method, String params) {
        UbusInvoker invokable = new UbusInvoker();
        invokable.object = object;
        invokable.method = method;
        invokable.params = params;
        invokable.completed = false;
        invokable.remoteInvoke();
        return invokable.result;
    }

    int mLastIndex = 0;
    final int MAX_INVOKE = 100;
    UbusInvoker[] mPendingInvoke = new UbusInvoker[MAX_INVOKE];

    public void setInvokeSlot(int index, UbusInvoker invoker) {
        mPendingInvoke[index] = invoker;
        return;
    }

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

    private Thread mThread = new Thread(this);
    private Queue<UbusRequest> mRequestQueue = new LinkedList<UbusRequest>();
    private Queue<UbusInvokable> mInvokableQueue = new LinkedList<UbusInvokable>();

    public void add(UbusInvokable invokable) {
        synchronized(mInvokableQueue) {
            if (!invokable.mQueued) {
                invokable.mQueued = true;
                mInvokableQueue.add(invokable);
                UbusJNI.wakeup();
            }
        }
    }

    public void run() {
        int index;
        int[] pendingReturns = new int[100];

        UbusJNI.init();
        for ( ; ; ) {
            synchronized (mInvokableQueue) {
                while (!mInvokableQueue.isEmpty()) {
                    UbusInvokable invokable = mInvokableQueue.remove();
                    invokable.mQueued = false;
                    invokable.run();
                }
            }

            int count = UbusJNI.fetchReturn(pendingReturns, pendingReturns.length);

            for (int i = 0; i < count; i++) {
                index = pendingReturns[i];
                UbusInvoker ret = mPendingInvoke[index];
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

    private boolean mIsStarted = false;

    private void start() {
        boolean oldStarted = true;

        synchronized(this) {
            oldStarted = mIsStarted;
            mIsStarted = true;
        }

        if (oldStarted == false) {
            mThread.setDaemon(true);
            mThread.start();
        }
    }

    private static final UbusPoller instance = new UbusPoller();

    public static UbusPoller getInstance() {
        instance.start();
        return instance;
    }
};

