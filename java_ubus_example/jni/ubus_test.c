#include <stdio.h>

#include "ubus_wrapper.h"

int main(int argc, char *argv[])
{
    static const char obj_type_str[] = {
        "{\"method\": ["
            "{"
                "\"name\": \"hello\","
                "\"policy\": ["
                    "{\"name\": \"one\", \"type\": 5},"
                    "{\"name\": \"two\", \"type\": 4}"
                "]"
            "},"
            "{"
                "\"name\": \"byebye\","
                "\"policy\": ["
                    "{\"name\": \"one\", \"type\": 5},"
                    "{\"name\": \"two\", \"type\": 4}"
                "]"
            "}"
        "]}"
    };

    ubus_parse_object_type(obj_type_str);
    return 0;
}
