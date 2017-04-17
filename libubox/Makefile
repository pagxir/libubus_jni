LIB  = libubox.so
OBJS = avl.o avl-cmp.o blob.o blobmsg.o uloop.o usock.o ustream.o ustream-fd.o \
       vlist.o utils.o safe_list.o runqueue.o md5.o kvlist.o ulog.o base64.o \
       blobmsg_json.o

all: $(LIB) 
	install -c -m 755 $(LIB) $(INSTALL_LIB_DIR)

clean:
	rm -f *.o $(LIB)
	rm -f $(INSTALL_LIB_DIR)/$(LIB)

ALLOWED_INCLUDE_PATHS := -I.\
                         -I../../include  \
                         -I../../include/linux \
                         -I$(TOP_DIR)/staging_dir/target/root/usr/include/json-c

CFLAGS += -Wall -g  -fPIC $(ALLOWED_INCLUDE_PATHS)
CFLAGS += -DJSONC 

$(LIB): $(OBJS)  
	$(CC) -shared -Wl,--whole-archive,-soname,$@ -o $@ $(OBJS) -Wl,--no-whole-archive

install: all
	install -c -m 755 $(LIB) $(INSTALL_LIB_DIR)
