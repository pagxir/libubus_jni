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
    struct ubus_incoming_context *req;
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

struct ubus_incoming_context {
    struct list_head entry;
    struct ubus_request_data req;

    void *msg_data;
    size_t msg_len;
    char dummy[1];
};

static int _accept_incoming = 0;
LIST_HEAD(_ubus_incoming_reqs);

int ubus_wrap_accept_request(struct ubus_jni_context *upp)
{
    struct list_head *curr, *next;
    struct ubus_incoming_context *incoming;

    if (list_empty(&_ubus_incoming_reqs)) {
        return -1;
    }

    list_for_each_safe(curr, next, &_ubus_incoming_reqs) {
        incoming = list_entry(curr, struct ubus_incoming_context, entry);
        upp->req = incoming;
        _accept_incoming++;
        list_del(curr);
        break;
    }

    return 0;
}

int ubus_wrap_reply(struct ubus_jni_context *upp)
{
    struct ubus_incoming_context *incoming;
    struct ubus_wrap_main_context *ctx = &_ubus_wrap_main;

    incoming = upp->req;
    assert(incoming != NULL);
#if 0
    blob_buf_init(&b, 0);
    blobmsg_add_string(&b, "message", req->data);
    ubus_send_reply(ctx, &req->req, b.head);
#endif
    ubus_complete_deferred_request(ctx->bus_handle, &incoming->req, 0);
    _accept_incoming--;
    return 0;
}

static int dummy_defer_cb(struct ubus_context *ctx, struct ubus_object *obj,
        struct ubus_request_data *req, const char *method,
        struct blob_attr *msg)
{
    size_t len = blob_len(msg);
    void   *data = blob_data(msg);
    struct ubus_incoming_context *dreq = NULL;
    assert(_accept_incoming < 100);

    dreq = calloc(sizeof(*req) + len, 1);
    if (dreq == NULL) {
        return UBUS_STATUS_UNKNOWN_ERROR;
    }

    memcpy(dreq->dummy, data, len);
    dreq->msg_data = dreq->dummy;
    dreq->msg_len = len;
    list_add_tail(&dreq->entry, &_ubus_incoming_reqs);

    ubus_defer_request(ctx, req, &dreq->req);
    uloop_end();
    return 0;
}

#if 0
static const struct ubus_method test_methods[] = {
    UBUS_METHOD("hello", test_hello, NULL),
    UBUS_METHOD("watch", test_watch, watch_policy),
    UBUS_METHOD("count", test_count, count_policy),
};

static struct ubus_object_type test_object_type =
    UBUS_OBJECT_TYPE("test", test_methods);

static struct ubus_object test_object = {
    .name = "test",
    .type = &test_object_type,
    .methods = test_methods,
    .n_methods = ARRAY_SIZE(test_methods),
};
#endif

#define MAX_OBJECTS 100
static int _ubus_nobject = 0;
static char _ubus_use_bitmap[MAX_OBJECTS];
static struct ubus_object _ubus_objects[MAX_OBJECTS];

static const struct blobmsg_policy type_policy[] = {
    [0] = { .name = "method", .type = BLOBMSG_TYPE_ARRAY }
};

static const struct blobmsg_policy method_policy[] = {
    [0] = { .name = "name", .type = BLOBMSG_TYPE_STRING },
    [1] = { .name = "policy", .type = BLOBMSG_TYPE_ARRAY }
};

static const struct blobmsg_policy params_policy[] = {
    [0] = { .name = "name", .type = BLOBMSG_TYPE_STRING },
    [1] = { .name = "type", .type = BLOBMSG_TYPE_INT32 }
};

struct ubus_object_type_info {
    size_t n_methods;
    size_t n_policys;
    size_t n_strings;
    size_t n_strbyte;

    char *strings;
    struct ubus_object_type *type;
    struct ubus_method      *method;
    struct blobmsg_policy   *policy;
};

static ssize_t ubus_get_type_info(const char *type_json, struct ubus_object_type_info *info);
static struct ubus_object_type *ubus_parse_object_type(const char *type_json, struct ubus_object_type_info *type_info);

int ubus_wrap_check_object_type(const char *type_json)
{
    ssize_t retval;
    struct ubus_object_type_info info = {};

    retval = ubus_get_type_info(type_json, &info);
    return 0;
}

ssize_t ubus_get_type_info(const char *type_json, struct ubus_object_type_info *info)
{
    ssize_t retval = -1;
    struct blob_buf buf = {'\0'};
    struct blob_attr *object_type_attr = NULL;
    struct blob_attr *method_item_attr = NULL;
    struct blob_attr *param_item_attr = NULL;

    struct blob_attr *method_attr[ARRAY_SIZE(method_policy)];
    struct blob_attr *params_attr[ARRAY_SIZE(params_policy)];

    size_t type_buf_len = 0;
    char * type_buf_buf = 0;

    info->n_methods = 0;
    info->n_policys = 0;
    info->n_strings = 0;
    info->n_strbyte = 0;

    blob_buf_init(&buf, BLOBMSG_TYPE_ARRAY);
    if (! blobmsg_add_json_from_string(&buf, type_json)) {
        UBUS_WRAP_LOG("load json to blob buf failed\n");
        goto finalize;
    }

    if (blobmsg_parse(type_policy, ARRAY_SIZE(type_policy), &object_type_attr, blob_data(buf.head), blob_len(buf.head)) != 0) {
        UBUS_WRAP_LOG("base parse failed\n");
        goto finalize;
    }

    size_t type_data_len = blobmsg_len(object_type_attr);
    __blob_for_each_attr(method_item_attr, blobmsg_data(object_type_attr), type_data_len) {
        if (blobmsg_parse(method_policy, ARRAY_SIZE(method_policy), method_attr, blobmsg_data(method_item_attr), blobmsg_len(method_item_attr)) != 0) {
            UBUS_WRAP_LOG("parse failed\n");
            goto finalize;
        }

        const char *tmpstr = blobmsg_get_string(method_attr[0]);
        UBUS_WRAP_LOG("name is %s\n", tmpstr);
        info->n_strbyte += strlen(tmpstr);
        info->n_strings++;

        size_t method_data_len = blobmsg_len(method_attr[1]);
        __blob_for_each_attr(param_item_attr, blobmsg_data(method_attr[1]), method_data_len) {
            if (blobmsg_parse(params_policy, ARRAY_SIZE(params_policy), params_attr, blobmsg_data(param_item_attr), blobmsg_len(param_item_attr)) != 0) {
                UBUS_WRAP_LOG("parse failed\n");
                goto finalize;
            }

            tmpstr = blobmsg_get_string(params_attr[0]);
            UBUS_WRAP_LOG("arg_name is %s\n", tmpstr);
            info->n_strbyte += strlen(tmpstr);
            info->n_strings++;

            UBUS_WRAP_LOG("arg_type is %d\n", blobmsg_get_u32(params_attr[1]));
            info->n_policys++;
        }

        info->n_methods++;
    }

    UBUS_WRAP_LOG("parse success: %p %d method %d params %d string %d\n",
            object_type_attr, type_data_len, info->n_methods, info->n_policys, info->n_strbyte);
    retval = sizeof(struct ubus_object_type) + info->n_methods * sizeof(struct ubus_method) 
        + info->n_policys * sizeof(struct blobmsg_policy) + info->n_strings + info->n_strbyte;

finalize:
    blob_buf_free(&buf);
    return retval;
}


struct ubus_object_type *ubus_parse_object_type(const char *type_json, struct ubus_object_type_info *info)
{
    struct blob_buf buf = {'\0'};
    struct blob_attr *object_type_attr = NULL;
    struct blob_attr *method_item_attr = NULL;
    struct blob_attr *param_item_attr = NULL;

    struct blob_attr *method_attr[ARRAY_SIZE(method_policy)];
    struct blob_attr *params_attr[ARRAY_SIZE(params_policy)];

    size_t type_buf_len = 0;
    char * type_buf_buf = 0;
    size_t type_method_count = 0;
    size_t type_params_count = 0;
    size_t type_string_total = 0;

    blob_buf_init(&buf, BLOBMSG_TYPE_ARRAY);
    if (! blobmsg_add_json_from_string(&buf, type_json)) {
        UBUS_WRAP_LOG("load json to blob buf failed\n");
        goto finalize;
    }

    if (blobmsg_parse(type_policy, ARRAY_SIZE(type_policy), &object_type_attr, blob_data(buf.head), blob_len(buf.head)) != 0) {
        UBUS_WRAP_LOG("base parse failed\n");
        goto finalize;
    }

    size_t type_data_len = blobmsg_len(object_type_attr);
    __blob_for_each_attr(method_item_attr, blobmsg_data(object_type_attr), type_data_len) {
        if (blobmsg_parse(method_policy, ARRAY_SIZE(method_policy), method_attr, blobmsg_data(method_item_attr), blobmsg_len(method_item_attr)) != 0) {
            UBUS_WRAP_LOG("parse failed\n");
            goto finalize;
        }

        const char *tmpstr = blobmsg_get_string(method_attr[0]);
        UBUS_WRAP_LOG("name is %s\n", tmpstr);
        type_string_total += strlen(tmpstr);
        type_string_total++;

        size_t method_data_len = blobmsg_len(method_attr[1]);
        __blob_for_each_attr(param_item_attr, blobmsg_data(method_attr[1]), method_data_len) {
            if (blobmsg_parse(params_policy, ARRAY_SIZE(params_policy), params_attr, blobmsg_data(param_item_attr), blobmsg_len(param_item_attr)) != 0) {
                UBUS_WRAP_LOG("parse failed\n");
                goto finalize;
            }

            tmpstr = blobmsg_get_string(params_attr[0]);
            UBUS_WRAP_LOG("arg_name is %s\n", tmpstr);
            type_string_total += strlen(tmpstr);
            type_string_total++;

            UBUS_WRAP_LOG("arg_type is %d\n", blobmsg_get_u32(params_attr[1]));
            type_params_count++;
        }

        type_method_count++;
    }

    UBUS_WRAP_LOG("parse success: %p %d method %d params %d string %d\n",
            object_type_attr, type_data_len, type_method_count, type_params_count, type_string_total);
finalize:
    blob_buf_free(&buf);
    return NULL;
}

static const char * wrap_alloc_string(char *buf, size_t offset, const char *name)
{
    strcpy(buf + offset, name);
    return buf + offset;
}

int ubus_wrap_add_object(const char *name, const char *type_json)
{
    int ret;
    char *base_buf;
    ssize_t total_size;

    struct ubus_object *object;
    int index = _ubus_nobject;
    struct ubus_object_type_info info = {};
    struct ubus_wrap_main_context *main_ctx = &_ubus_wrap_main;

    ubus_wrap_init();
    assert(_ubus_nobject + 1 < MAX_OBJECTS);
    object = &_ubus_objects[index];

    total_size = ubus_get_type_info(type_json, &info);
    assert (total_size > 0);

    base_buf = calloc(total_size + strlen(name) + 1, 1);
    info.type = (struct ubus_object_type *)base_buf;
    info.method = (struct ubus_method *)(info.type + 1);
    info.policy = (struct blobmsg_policy *)(info.method + info.n_methods);
    info.strings = (char *)(info.policy + info.n_policys);

    object->type = ubus_parse_object_type(type_json, &info);
    assert(object->type != NULL);

    object->name = wrap_alloc_string(base_buf, total_size, name);
    object->methods = object->type->methods;
    object->n_methods = object->type->n_methods;

    ret = ubus_add_object(main_ctx->bus_handle, object);
    assert(ret == 0);

    _ubus_use_bitmap[index] = 0x1;
    _ubus_nobject++;
    return -1;
}

int ubus_wrap_remove_object(int object_id)
{
    struct ubus_object *object;
    assert(object_id < _ubus_nobject && object_id >= 0);
    assert(_ubus_use_bitmap[object_id] == 0x1);

    object = &_ubus_objects[object_id];
    object->name = NULL;
    free(object->type);

    memset(object, 0, sizeof(*object));
    _ubus_use_bitmap[object_id] = 0;
    return 0;
}

