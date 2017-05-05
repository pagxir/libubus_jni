#include <stdio.h>

#include "ubus_wrapper.h"

int main(int argc, char *argv[])
{
    ubus_parse_object_type("test", "L", 1);
    return 0;
}
