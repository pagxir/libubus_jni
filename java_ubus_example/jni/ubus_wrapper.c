#include <stdio.h>
#include <assert.h>
#define _GNU_SOURCE
#include <unistd.h>
#include <fcntl.h>

#include <libubox/list.h>
#include <libubox/uloop.h>
#include <libubox/ustream.h>
#include <libubox/blobmsg_json.h>

#include <libubus.h>

#include "ubus_wrapper.h"

struct ubus_wrap_context;
#define WRAP_CONTEXT(upp) wrap_context(upp)

struct ubus_jni_context {
    struct ubus_wrap_context *wrap;
};

#define FLAG_REQ_PENDING 0x8000
#define FLAG_RET_PENDING 0x0800

struct ubus_wrap_context {
    struct list_head entry;
    struct ubus_request req;

    int index;
    char *result;

    int retval;
    int is_return;

    int flags;
};

static inline struct ubus_wrap_context *wrap_context(struct ubus_jni_context *ctx)
{
    if (ctx->wrap == NULL) {
        struct ubus_wrap_context c;
        ctx->wrap = calloc(sizeof(c), 1);
        assert(ctx->wrap != NULL);
    }

    return ctx->wrap;
}


LIST_HEAD(_ubus_return_reqs);

int ubus_wrap_get_context_size()
{
    struct ubus_jni_context c;
    return sizeof(c);
}

const char *ubus_wrap_get_result(struct ubus_jni_context *_upp)
{
    struct ubus_wrap_context *upp = (_upp->wrap);
    return upp != NULL? upp->result: NULL;
}

struct ubus_wrap_main_context {
    struct ubus_context *bus_handle;
    struct ubus_event_handler listener;

    int wakeup_read_fd;
    int wakeup_write_fd;
    struct uloop_fd wakeup_handler;
} _ubus_wrap_main;

static void pull_all_data(struct uloop_fd *u, unsigned int events)
{
    int error;
    char buf[1024];

    error = read(u->fd, buf, sizeof(buf));
    assert (error != -1 || errno == EAGAIN);

    uloop_end();
    return;
}

static int add_wakeup_fd(struct ubus_wrap_main_context *upp)
{
    struct uloop_fd *handler = &upp->wakeup_handler;
    int pipefd[2];
    int error;

    error = pipe2(pipefd, O_NONBLOCK| O_CLOEXEC);
    assert(error == 0);
    if (error) goto failure;
    
    handler->fd = pipefd[0];
    handler->cb = pull_all_data;

    uloop_fd_add(handler, ULOOP_BLOCKING| ULOOP_READ);
    upp->wakeup_write_fd = pipefd[1];
    upp->wakeup_read_fd = pipefd[0];

failure:
    return 0;
}

int ubus_wrap_init()
{
    int error = 0;
    static int is_initialized = 0;
    struct ubus_wrap_main_context *upp = &_ubus_wrap_main;

    if (is_initialized == 0) {
        is_initialized = 1;
        uloop_init();
        add_wakeup_fd(upp);
    }

    if (upp->bus_handle != NULL) {
        goto just_return;
    }

    upp->bus_handle = ubus_connect(NULL);
    if (upp->bus_handle == NULL) {
        error = -1;
        goto just_return;
    }

    ubus_add_uloop(upp->bus_handle);

just_return:
    return error;
}

int ubus_wrap_wakeup()
{
    struct ubus_wrap_main_context *upp = &_ubus_wrap_main;
    char ignore = 0;

    write(upp->wakeup_write_fd, &ignore, 1);
    return 0;
}

static void recv_cb_json_out(struct ubus_request *req, int type, struct blob_attr *msg)
{
    struct ubus_wrap_context *upp = req->priv;

    upp->result = blobmsg_format_json(msg, true);
    return;
}

static void ubus_sync_req_cb(struct ubus_request *req, int ret)
{
    struct ubus_wrap_context *upp = req->priv;

    upp->is_return = 1;
    upp->retval = ret;
    list_add_tail(&upp->entry, &_ubus_return_reqs);
    upp->flags &= ~FLAG_REQ_PENDING;
    upp->flags |= FLAG_RET_PENDING;

    uloop_end();
}

int ubus_wrap_invoke(struct ubus_jni_context *_upp, int index, const char *object, const char *method, const char *params)
{
    int ret = -1;
    struct ubus_wrap_main_context *ubus = &_ubus_wrap_main;
    struct ubus_wrap_context *upp = WRAP_CONTEXT(_upp);

    uint32_t id = 0;
    struct blob_buf msg = {};

    UBUS_WRAP_LOG("index = %d, object %s, method: %s, params: %s\n", index, object, method, params);

    assert(upp->flags == 0);
    upp->index = index;
    ubus_wrap_init();
    if (ubus->bus_handle == NULL) {
        goto invoke_fail;
    }

    ret = ubus_lookup_id(ubus->bus_handle, object, &id);
    if (ret != 0) {
        goto invoke_fail;
    }

    blob_buf_init(&msg, 0);
    blobmsg_add_json_from_string(&msg, params);
    ret = ubus_invoke_async(ubus->bus_handle, id, method, msg.head, &upp->req);
    blob_buf_free(&msg);
    if (ret != 0) {
        goto invoke_fail;
    }

    upp->req.complete_cb = ubus_sync_req_cb;
    upp->req.data_cb = recv_cb_json_out;
    upp->req.priv    = upp;

    upp->flags |= FLAG_REQ_PENDING;
    ubus_complete_request_async(ubus->bus_handle, &upp->req);
    return ret;

invoke_fail:
    upp->is_return = 1;
    upp->retval = -1;
    list_add_tail(&upp->entry, &_ubus_return_reqs);
    upp->flags |= FLAG_RET_PENDING;

    uloop_end();
    return -1;
}

int ubus_wrap_fetch_return(int returns[], int size)
{
    int count = 0;
    struct list_head *curr, *next;
    struct ubus_wrap_context *upp;

    if (list_empty(&_ubus_return_reqs)) {
        uloop_run();
    }

    list_for_each_safe(curr, next, &_ubus_return_reqs) {
        if (count >= size) {
            break;
        }

        upp = list_entry(curr, struct ubus_wrap_context, entry);
        returns[count++] = upp->index;
        upp->flags &= ~FLAG_RET_PENDING;
        list_del(curr);
    }

    return count;
}

int ubus_wrap_release(struct ubus_jni_context *_upp)
{
    struct ubus_wrap_context *upp = (_upp->wrap);
    struct ubus_wrap_main_context *ubus = &_ubus_wrap_main;

    if (upp != NULL) {
        UBUS_WRAP_LOG("_upp %p, upp %p\n", _upp, upp);

        if (upp->flags & FLAG_REQ_PENDING) {
            assert (ubus->bus_handle != NULL);
            ubus_abort_request(ubus->bus_handle, &upp->req);
            upp->flags &= ~FLAG_REQ_PENDING;
        }

        if (upp->result != NULL) {
            free(upp->result);
            upp->result = NULL;
        }

        if (upp->flags & FLAG_RET_PENDING) {
            upp->flags &= ~FLAG_RET_PENDING;
            list_del(&upp->entry);
        }

        _upp->wrap = NULL;
        free(upp);
    }

    return 0;
}

int ubus_wrap_accept_request(struct ubus_jni_context *upp)
{
    return -1;
}

int ubus_wrap_reply(struct ubus_jni_context *upp)
{
    return 0;
}

