TOP_DIR ?= $(shell git rev-parse --show-toplevel)

SRC_DIR := $(TOP_DIR)/json-c
OBJ_DIR := $(TOP_DIR)/build_dir/jscon-c
TARGET_ROOT := $(TOP_DIR)/staging_dir/target/root

DESTDIR ?= $(TARGET_ROOT)/usr
CONFIGURE_ENVS = 
CONFIGURE_ARGS = --prefix=/ MISSING=/bin/true

all: libubox install ubus

install: prepare $(DESTDIR)
	make -C $(OBJ_DIR) install DESTDIR=$(DESTDIR) 
	make -C $(OBJ_DIR) install-exec DESTDIR=$(DESTDIR)/ 

build: prepare
	make -C $(OBJ_DIR)

prepare: $(OBJ_DIR)/Makefile
	echo $(OBJ_DIR) is prepared

$(OBJ_DIR)/Makefile: $(SRC_DIR)/configure
	mkdir -p $(OBJ_DIR) $(DESTDIR)
	(cd $(OBJ_DIR) && $(CONFIGURE_ENVS) $(SRC_DIR)/configure $(CONFIGURE_ARGS))

$(DESTDIR):
	mkdir -p $(DESTDIR)

clean:
	make -C $(OBJ_DIR) clean

.PHONY: build clean install prepare libubox ubus

libubox: install
	mkdir -p $(TOP_DIR)/staging_dir/target/root/usr/include/libubox
	mkdir -p $(TOP_DIR)/staging_dir/target/root/usr/lib
	make -C $(TOP_DIR)/libubox DESTDIR=$(DESTDIR)

ubus: install libubox
	mkdir -p $(TOP_DIR)/staging_dir/target/root/usr/lib
	mkdir -p $(TOP_DIR)/staging_dir/target/root/usr/bin
	mkdir -p $(TOP_DIR)/staging_dir/target/root/usr/sbin
	make -C $(TOP_DIR)/ubus DESTDIR=$(DESTDIR)
