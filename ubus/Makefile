TOP_DIR := ../../../..
include ../../../../makeRule/make.common

.PHONY: install all clean 

JSONC = $(TOP_DIR)/staging_dir/target/root/usr

UBUS_LIB  = libubus.so
UBUS_LIB_OBJS = libubus.o libubus-io.o libubus-obj.o libubus-sub.o libubus-req.o

UBUS_CLI = ubus
UBUS_CLI_OBJS = cli.o
    
UBUS_DEAMON = ubusd
UBUS_DEAMON_OBJS = ubusd.o ubusd_id.o ubusd_obj.o ubusd_proto.o ubusd_event.o

install: all
	install -c -m 755 $(UBUS_LIB) $(INSTALL_LIB_DIR)
	install -c -m 755 $(UBUS_DEAMON) $(INSTALL_SBIN_DIR) 
	install -c -m 755 $(UBUS_CLI) $(INSTALL_SBIN_DIR) 

all: $(UBUS_DEAMON) $(UBUS_CLI)
	install -c -m 755 $(UBUS_LIB) $(INSTALL_LIB_DIR)
	install -c -m 755 $(UBUS_DEAMON) $(INSTALL_SBIN_DIR) 
	install -c -m 755 $(UBUS_CLI) $(INSTALL_SBIN_DIR) 
    
clean:
	rm -f *.o $(UBUS_LIB) $(UBUS_DEAMON) $(UBUS_CLI) 
	rm -f $(INSTALL_LIB_DIR)/$(UBUS_LIB)
	rm -f $(INSTALL_SBIN_DIR)/$(UBUS_CLI)
	rm -f $(INSTALL_SBIN_DIR)/$(UBUS_DEAMON)

ALLOWED_INCLUDE_PATHS := -I.  \
             -I../../include  \
             -I../../include/linux \
             -I.. \
             -I$(JSONC)/include/json-c

CFLAGS += -Wall -g  -fPIC $(ALLOWED_INCLUDE_PATHS)

LDFLAGS += -Wl,-allow-shlib-undefined 
LDFLAGS += -L../libubox -lubox
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

    
    
    
    
