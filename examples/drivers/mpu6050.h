#ifndef ED_MPU6050_H
#define ED_MPU6050_H
#include "i2c_bus.h"
#define MPU6050_ADDRESS 0x68
typedef struct { ed_i16 ax,ay,az,temperature,gx,gy,gz; } mpu6050_raw;
ed_u8 mpu6050_init(ed_u8 address);
ed_u8 mpu6050_set_ranges(ed_u8 address,ed_u8 gyro_range,ed_u8 accel_range);
ed_u8 mpu6050_read_raw(ed_u8 address,mpu6050_raw *sample);
/* Gyro full-scale code 0: raw / 131 degrees/s. Accel code 0: raw / 16384 g.
 * This is a raw driver, not a DMP/attitude filter. All sample words are big-endian.
 */
#endif
