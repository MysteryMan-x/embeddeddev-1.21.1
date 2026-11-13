#ifndef ED_ALTIMETER_H
#define ED_ALTIMETER_H
#include "i2c_bus.h"
#define ALTIMETER_ADDRESS 0x76
typedef struct { ed_u8 status,sequence;ed_i32 absolute_cm,zero_cm,relative_cm;ed_i16 velocity_cm_s; } altimeter_sample;
ed_u8 altimeter_present(void);
ed_u8 altimeter_zero(void);
ed_u8 altimeter_read(altimeter_sample *sample);
#endif
