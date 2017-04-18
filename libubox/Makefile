TOP_DIR ?= $(shell git rev-parse --show-toplevel)

LIB  = libubox.so
OBJS = avl.o avl-cmp.o blob.o blobmsg.o uloop.o usock.o ustream.o ustream-fd.o \
       vlist.o utils.o safe_list.o runqueue.o md5.o kvlist.o ulog.o base64.o \
       blobmsg_json.o

HEADERFILES := avl-cmp.h blob.h blobmsg_json.h kvlist.h uloop.h ustream.h \
	 vlist.h avl.h blobmsg.h json_script.h list.h runqueue.h ulog.h usock.h utils.h

all: $(LIB) 
	install -c -m 755 $(LIB) $(DESTDIR)/lib
	install -c -m 660 $(HEADERFILES) $(DESTDIR)/include/libubox

clean:
	rm -f *.o $(LIB)

ALLOWED_INCLUDE_PATHS := -I.\
                         -I$(TOP_DIR)/staging_dir/target/root/usr/include \
                         -I$(TOP_DIR)/staging_dir/target/root/usr/include/json-c

CFLAGS += -Wall -g  -fPIC $(ALLOWED_INCLUDE_PATHS)
CFLAGS += -DJSONC 

$(LIB): $(OBJS)
	$(CC) -shared -Wl,--whole-archive,-soname,$@ -o $@ $(OBJS) -Wl,--no-whole-archive

install: all
	install -c -m 755 $(LIB) $(DESTDIR)/lib

