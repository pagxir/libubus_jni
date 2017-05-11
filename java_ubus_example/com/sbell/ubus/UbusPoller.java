package com.sbell.ubus;

import java.util.Queue;
import java.util.LinkedList;

class UbusInvokable {
    boolean mQueued = false;
    Runnable mRunnable = null;

    public void run() {
        mRunnable.run();
    }
}

interface UbusInvokableState {
    public boolean completed(UbusInvoker invoker);

    public void cancel(UbusInvoker invoker);
    public void start(UbusInvoker invoker);
    public void run(UbusInvoker invoker);
}

class UbusBaseState implements UbusInvokableState {
    public boolean completed(UbusInvoker invoker) {
        return false;
    }

    public void cancel(UbusInvoker invoker) {
    }

    public void start(UbusInvoker invoker) {
    }

    public void run(UbusInvoker invoker) {
    }
}

class UbusPendingRequestState extends UbusBaseState {
    public void start(UbusInvoker invoker) {
        invoker.invokeStart();
        return;
    }

    public void cancel(UbusInvoker invoker) {
        invoker.JNICancel();
        return;
    }
}

class UbusInitializeState extends UbusBaseState {
    public void run(UbusInvoker invoker) {
        throw new IllegalStateException("could not run on initialize state");
    }

    public void start(UbusInvoker invoker) {
        invoker.invokeStart();
        return;
    }
}

class UbusQueuedState extends UbusBaseState {
    public void run(UbusInvoker invoker) {
        invoker.invokeNative();
        return;
    }

    public void cancel(UbusInvoker invoker) {
        invoker.JavaCancel();
        return;
    }
}

class UbusPendingState extends UbusBaseState {
    public void cancel(UbusInvoker invoker) {
        invoker.JNICancel();
        return;
    }
}

class UbusCanceledState extends UbusBaseState {
    public void run(UbusInvoker invoker) {
        invoker.JNIAbort();
        return;
    }
}

class UbusCompletedState extends UbusBaseState {
    public void run(UbusInvoker invoker) {
        invoker.javaAbort();
        return;
    }

    public boolean completed(UbusInvoker invoker) {
        return true;
    }
}

class UbusInvoker extends UbusInvokable implements Runnable {
    String object;
    String method;
    String params;
    String result;

    int index;
    byte[] native_context = UbusJNI.createContext();

    public void run() {
        mRunnable = null;
        synchronized(this) {
            invokable.run(this);
        }
    }

    void completeInvoke() {
        synchronized(this) {
            UbusPoller.getInstance().setInvokeSlot(index, null);
            result = UbusJNI.getResult(native_context);

            UbusJNI.release(native_context);
            invokable = new UbusCompletedState();
            notify();
        }
        return;
    }

    public void invokeStart() {
        mRunnable = this;
        UbusPoller.getInstance().add(this);
        invokable = new UbusQueuedState();
    }

    public void JavaCancel() {
        // UbusPoller.getInstance().add(this);
        invokable = new UbusCompletedState();
    }

    public void JNICancel() {
        mRunnable = this;
        UbusPoller.getInstance().add(this);
        invokable = new UbusCanceledState();
    }

    public void javaAbort() {
        invokable = new UbusCompletedState();
        notify();
    }

    public void JNIAbort() {
        UbusJNI.release(native_context);
        invokable = new UbusCompletedState();
        UbusPoller.getInstance().setInvokeSlot(index, null);
        notify();
    }

    public void invokeNative() {
        int _index = UbusPoller.getInstance().findEmptyInvokeSlot();

        if (_index != -1) {
            UbusJNI.invoke(_index, native_context, object, method, params);
            UbusPoller.getInstance().setInvokeSlot(_index, this);
            invokable = new UbusPendingState();
            this.index = _index;
        } else {
            invokable = new UbusCompletedState();
            notify();
        }
    }

    UbusInvokableState invokable = new UbusInitializeState();

    void remoteInvoke() {

        synchronized(this) {
            invokable.start(this);
            try {
                if (!invokable.completed(this)) wait(4000);
                invokable.cancel(this);
                while (!invokable.completed(this)) wait();
            } catch (InterruptedException e) {
                System.out.println("remoteInvoke " + e.toString());
                UbusJNI.abort();
            }
        }

        return;
    }
}

class UbusAddObjectInvoker extends UbusInvoker {
    int objectIndex = -1;

    @Override
    public void invokeNative() {
        objectIndex = UbusJNI.addObject(this.object, this.params);
        invokable = new UbusCompletedState();
        notify();
    }
}

public class UbusPoller implements Runnable {
    public static class UbusRequest extends UbusInvoker {
        public String params = null;
        public String method = null;
        public String stat = "init";
        byte[] holdMem = new byte[819200];

        public boolean pullRequest() {
            if (UbusJNI.acceptRequest(native_context)) {
                params = UbusJNI.getRequestJson(native_context);
                method = UbusJNI.getRequestMethod(native_context);
                invokable = new UbusPendingRequestState();
                stat = "pending";
                return true;
            }
            stat = "pending failure";
            return false;
        }

        @Override
            public void invokeNative() {
                UbusJNI.reply(native_context, this.result);
                invokable = new UbusCompletedState();
                stat = "completed";
                notify();
                return;
            }

        @Override
            public void JNIAbort() {
                UbusJNI.reply(native_context, "{}");
                invokable = new UbusCompletedState();
                stat = "abortcompleted";
                return;
            }

        @Override
            public void invokeStart() {
                mRunnable = this;
                UbusPoller.getInstance().add(this);
                invokable = new UbusQueuedState();
                stat = "queued";
                return;
            }

        public void replyRequest(String jsonStr) {
            synchronized(this) {
                this.result = (jsonStr == null? "{}": jsonStr);
                invokable.start(this);
            }
        }
    }

    public String ubusInvoke(String object, String method, String params) {
        UbusInvoker invokable = new UbusInvoker();
        invokable.object = object;
        invokable.method = method;
        invokable.params = params;
        invokable.remoteInvoke();
        return invokable.result;
    }

    public int ubusAddObject(String name, String json_str) {
        UbusAddObjectInvoker invokable = new UbusAddObjectInvoker();
        invokable.object = name;
        invokable.params = json_str;
        invokable.remoteInvoke();
        return invokable.objectIndex;
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
        try {
            loop();
        } catch (OutOfMemoryError e) {
            System.out.println("memory " + e.toString());
            UbusJNI.abort();
        }
    }

    public void loop() {
        int index;
        int[] pendingReturns = new int[100];

        UbusJNI.init();

        UbusRequest req = new UbusRequest();
        for ( ; ; ) {
            for ( ; ; ) {
                UbusInvokable invokable = null;

                synchronized (mInvokableQueue) {
                    if (mInvokableQueue.isEmpty()) {
                        break;
                    }

                    invokable = mInvokableQueue.remove();
                    invokable.mQueued = false;
                }
                invokable.run();
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

            while (req.pullRequest()) {
                synchronized(mRequestQueue) {
                    mRequestQueue.add(req);
                    mRequestQueue.notify();
                }
                req = new UbusRequest();
            }
        }
    }

    public UbusRequest acceptRequest() {
        synchronized(mRequestQueue) {
            try {
                if (!mRequestQueue.isEmpty())
                    return mRequestQueue.remove();

                while (mRequestQueue.isEmpty()) {
                    mRequestQueue.wait();
                }

                return mRequestQueue.remove();
            } catch (InterruptedException e) {
                throw new RuntimeException("acceptRequest failure");
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

