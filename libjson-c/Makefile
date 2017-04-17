TOP_DIR ?= $(shell hg root || git rev-parse --show-toplevel)
include $(TOP_DIR)/makeRule/make.common

HOST := mips-linux
SRC_DIR := $(shell pwd )/json-c
OBJ_DIR := $(shell pwd | sed 's@/*$$@/build@' )
TARGET_ROOT := $(TOP_DIR)/staging_dir/target/root

DESTDIR ?= $(TARGET_ROOT)/usr
CONFIGURE_ENVS = 
CONFIGURE_ARGS = --prefix=/ MISSING=/bin/true

install: prepare $(DESTDIR)
	DESTDIR=$(DESTDIR) make -C $(OBJ_DIR) install
	DESTDIR=$(INSTALL_DIR)/ make -C $(OBJ_DIR) install-exec
	#since use libtool to generate .a,.so, we remove .a
	rm -f $(INSTALL_USR_LIB_DIR)/libjson-c.a

build: prepare
	make -C $(OBJ_DIR)

prepare: $(OBJ_DIR)/Makefile
	echo $(OBJ_DIR) is prepared

$(OBJ_DIR)/Makefile: $(SRC_DIR)/configure
	mkdir -p $(OBJ_DIR)
	(cd $(OBJ_DIR) && $(CONFIGURE_ENVS) exec $(SRC_DIR)/configure --host=$(HOST) $(CONFIGURE_ARGS))

$(DESTDIR):
	mkdir -p $(DESTDIR)

clean:
	make -C $(OBJ_DIR) clean

.PHONY: build clean install prepare
