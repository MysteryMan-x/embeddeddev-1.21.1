#include "pcf8591.h"
static ed_u8 begin_read(ed_u8 a,ed_u8 control){
    ed_i2c_start();if(!ed_i2c_write(a<<1)||!ed_i2c_write(control)){ed_i2c_stop();return 0;}
    ed_i2c_start();if(!ed_i2c_write((a<<1)|1)){ed_i2c_stop();return 0;}return 1;
}
ed_u8 pcf8591_read(ed_u8 a,ed_u8 control,ed_u8 *sample){
    if(!begin_read(a,control))return 0;ed_i2c_read(1); /* discard previous conversion */
    *sample=ed_i2c_read(0);ed_i2c_stop();return 1;
}
ed_u8 pcf8591_read_channels(ed_u8 a,ed_u8 *p){
    ed_u8 i;if(!begin_read(a,0x04))return 0;ed_i2c_read(1);
    for(i=0;i<4;i++)p[i]=ed_i2c_read(i<3);ed_i2c_stop();return 1;
}
ed_u8 pcf8591_dac(ed_u8 a,ed_u8 control,ed_u8 value){
    ed_u8 ok;ed_i2c_start();ok=ed_i2c_write(a<<1)&&ed_i2c_write(control|0x40)&&ed_i2c_write(value);ed_i2c_stop();return ok;
}
