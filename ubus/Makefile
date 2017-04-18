TOP_DIR ?= $(shell git rev-parse --show-toplevel)
.PHONY: install all clean 

JSONC = $(TOP_DIR)/staging_dir/target/root/usr

UBUS_LIB  = libubus.so
UBUS_LIB_OBJS = libubus.o libubus-io.o libubus-obj.o libubus-sub.o libubus-req.o

UBUS_CLI = ubus
UBUS_CLI_OBJS = cli.o
    
UBUS_DEAMON = ubusd
UBUS_DEAMON_OBJS = ubusd.o ubusd_id.o ubusd_obj.o ubusd_proto.o ubusd_event.o

install: all
	install -c -m 755 $(UBUS_LIB) $(DESTDIR)/lib
	install -c -m 755 $(UBUS_DEAMON) $(DESTDIR)/sbin
	install -c -m 755 $(UBUS_CLI) $(DESTDIR)/sbin

all: $(UBUS_DEAMON) $(UBUS_CLI)
	install -c -m 755 $(UBUS_LIB) $(DESTDIR)/lib
	install -c -m 755 $(UBUS_DEAMON) $(DESTDIR)/sbin
	install -c -m 755 $(UBUS_CLI) $(DESTDIR)/sbin
    
clean:
	rm -f *.o $(UBUS_LIB) $(UBUS_DEAMON) $(UBUS_CLI) 

ALLOWED_INCLUDE_PATHS := -I.  \
             -I$(JSONC)/include/ \
             -I$(JSONC)/include/libubox \
             -I$(JSONC)/include/json-c

CFLAGS += -Wall -g  -fPIC $(ALLOWED_INCLUDE_PATHS)

LDFLAGS += -Wl,-allow-shlib-undefined 
LDFLAGS += -L$(DESTDIR)/lib -lubox
LDFLAGS_CLI = -L$(JSONC)/lib -ljson-c
LDFLAGS_CLI += -L . -lubus

$(UBUS_LIB): $(UBUS_LIB_OBJS)
	$(CC) -shared -Wl,--whole-archive,-soname,$@ -o $@ $(UBUS_LIB_OBJS) -Wl,--no-whole-archive

$(UBUS_DEAMON): $(UBUS_DEAMON_OBJS) 
	$(CC) -s $(LDFLAGS) -o $@ $(UBUS_DEAMON_OBJS)
	@echo Compile $(UBUS_DEAMON) successful
    
$(UBUS_CLI): $(UBUS_CLI_OBJS) $(UBUS_LIB)
	$(CC) -s $(LDFLAGS) $(LDFLAGS_CLI) -o $@ $(UBUS_CLI_OBJS)
	@echo Compile $(UBUS_CLI) successful
