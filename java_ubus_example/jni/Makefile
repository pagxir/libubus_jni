CFLAGS += -I$(TOP_DIR)/staging_dir/target/root/usr/include
CFLAGS += -I/usr/lib/jvm/java-1.6.0-openjdk-1.6.0.0/include/
CFLAGS += -I/usr/lib/jvm/java-1.6.0-openjdk-1.6.0.0/include/linux
LDFLAGS += -L$(TOP_DIR)/staging_dir/target/root/usr/lib

LDLIBS += -ljson-c -lubox  -lubus -lrt

all: libubus_jni.so

libubus_jni.so: ubus_wrapper.o ubus_jni.o
	$(CXX) $(LDFLAGS) -fPIC -shared -o $@ $^ $(LDLIBS)
