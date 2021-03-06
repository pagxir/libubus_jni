#ifndef _UBUS_WRAPPER_H
#define _UBUS_WRAPPER_H

#ifdef __cplusplus
extern "C" {
#endif

struct ubus_jni_context;
int ubus_wrap_get_context_size();
const char *ubus_wrap_get_result(struct ubus_jni_context *upp);

int ubus_wrap_init();
int ubus_wrap_wakeup();

int ubus_wrap_accept_request(struct ubus_jni_context *upp);
int ubus_wrap_reply(struct ubus_jni_context *upp, const char *json);
const char * ubus_wrap_get_requst_method(struct ubus_jni_context *upp);
const char * ubus_wrap_get_requst_json(struct ubus_jni_context *upp, size_t *len);

int ubus_wrap_invoke(struct ubus_jni_context *upp, int index, const char *object, const char *method, const char *params);
int ubus_wrap_fetch_return(int returns[], int size);
int ubus_wrap_release(struct ubus_jni_context *upp);

int ubus_wrap_add_object(const char *name, const char *type_json);
int ubus_wrap_remove_object(int object_id);

int ubus_wrap_check_object_type(const char *type_json);

#ifdef __cplusplus
};
#endif

// #define UBUS_WRAP_LOG(fmt, args...) fprintf(stderr, fmt, ##args)
#define UBUS_WRAP_LOG(fmt, args...) 

#endif
