#include "mpu6050.h"
static ed_i16 signed_be(const ed_u8 *p){return (ed_i16)(((ed_u16)p[0]<<8)|p[1]);}
ed_u8 mpu6050_set_ranges(ed_u8 a,ed_u8 gyro,ed_u8 accel) {
    if(gyro>3||accel>3)return 0;
    return ed_i2c_write_reg(a,0x1B,gyro<<3)&&ed_i2c_write_reg(a,0x1C,accel<<3);
}
ed_u8 mpu6050_init(ed_u8 a) {
    ed_u8 id;
    if(!ed_i2c_read_regs(a,0x75,&id,1)||id!=0x68)return 0;
    return ed_i2c_write_reg(a,0x6B,0)&&ed_i2c_write_reg(a,0x1A,3)&&mpu6050_set_ranges(a,0,0);
}
ed_u8 mpu6050_read_raw(ed_u8 a,mpu6050_raw *s) {
    ed_u8 bytes[14];if(!ed_i2c_read_regs(a,0x3B,bytes,14))return 0;
    s->ax=signed_be(bytes);s->ay=signed_be(bytes+2);s->az=signed_be(bytes+4);
    s->temperature=signed_be(bytes+6);s->gx=signed_be(bytes+8);s->gy=signed_be(bytes+10);s->gz=signed_be(bytes+12);return 1;
}
