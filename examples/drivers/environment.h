#ifndef ED_ENVIRONMENT_H
#define ED_ENVIRONMENT_H
#include "i2c_bus.h"
/* Address 48H conflicts with a default-address PCF8591 on the same bus. */
typedef struct { ed_u8 redstone,sky_light,block_light,day_percent; } environment_sample;
ed_u8 environment_read(environment_sample *sample);
#endif
