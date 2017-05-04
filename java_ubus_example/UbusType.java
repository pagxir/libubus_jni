import java.util.*;

class UBusPolicy {
    final static int BLOBMSG_TYPE_UNSPEC = 0;
    final static int BLOBMSG_TYPE_ARRAY  = 1;
    final static int BLOBMSG_TYPE_TABLE  = 2;
    final static int BLOBMSG_TYPE_STRING = 3;
    final static int BLOBMSG_TYPE_INT64  = 4;
    final static int BLOBMSG_TYPE_INT32  = 5;
    final static int BLOBMSG_TYPE_INT16  = 6;
    final static int BLOBMSG_TYPE_INT8   = 7;
    final static int __BLOBMSG_TYPE_LAST = 8;
    final static int BLOBMSG_TYPE_BOOL = BLOBMSG_TYPE_INT8;
    final static int BLOBMSG_TYPE_LAST = __BLOBMSG_TYPE_LAST - 1;

    public int type;
    public String name;
}

class UbusMethodBuilder {
    static class UbusMethod {
        public String mName;
        public UBusPolicy[] mPolicies;

        UbusMethod(String _name, UBusPolicy[] policies) {
            mPolicies = policies;
            mName = _name;
        }
    }

    public UbusMethodBuilder addMethod(String name, UBusPolicy[] policies) {
        UbusMethod method = new UbusMethod(name, policies);
        mMethods.add(method);
        return this;
    }

    public UbusMethod[] build() {
        return mMethods.toArray(new UbusMethod[1]);
    }

    private ArrayList<UbusMethod> mMethods = new ArrayList<UbusMethod>();
}

public class UbusType {
    UbusMethodBuilder.UbusMethod[] mMethod = null;

    public UbusType(UbusMethodBuilder.UbusMethod[] method) {
        mMethod = method;
    }
}

class UbusObject {
    int objId;
    String name;
    UbusType mType = null;

    UbusObject(UbusType type) {
        mType = type;
    }

    void register() {
    }
}
