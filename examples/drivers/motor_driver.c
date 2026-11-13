#include "motor_driver.h"
ed_u8 motor_driver_present(ed_u8 a,ed_u8 *mask){ed_u8 p[5];if(!ed_i2c_read_regs(a,0,p,5)||p[0]!=0x4D)return 0;*mask=p[4];return 1;}
ed_u8 motor_driver_arm(ed_u8 a,ed_u8 enable){return ed_i2c_write_reg(a,1,enable?0xA5:0);}
ed_u8 motor_driver_set_all(ed_u8 a,const ed_i16 rpm[6]){
    ed_u8 i;ed_i16 v;ed_i2c_start();
    if(!ed_i2c_write(a<<1)||!ed_i2c_write(0x20)){ed_i2c_stop();return 0;}
    for(i=0;i<6;i++){
        v=rpm[i];if(v>256)v=256;if(v< -256)v=-256;
        if(!ed_i2c_write((ed_u8)v)||!ed_i2c_write((ed_u8)((ed_u16)v>>8))){ed_i2c_stop();return 0;}
    }
    i=ed_i2c_write(0xA5);ed_i2c_stop();return i;
}
