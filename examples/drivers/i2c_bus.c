#include "i2c_bus.h"
static void pause(void) { ED_NOP();ED_NOP();ED_NOP(); }
void ed_i2c_start(void) { ED_SDA=1;ED_SCL=1;pause();ED_SDA=0;pause();ED_SCL=0; }
void ed_i2c_stop(void) { ED_SDA=0;ED_SCL=1;pause();ED_SDA=1;pause(); }
ed_u8 ed_i2c_write(ed_u8 value) {
    ed_u8 i,ack;
    for(i=0;i<8;i++){ED_SDA=(value&128)!=0;pause();ED_SCL=1;pause();ED_SCL=0;value<<=1;}
    ED_SDA=1;ED_SCL=1;pause();ack=!ED_SDA;ED_SCL=0;return ack;
}
ed_u8 ed_i2c_read(ed_u8 ack) {
    ed_u8 i,value=0;ED_SDA=1;
    for(i=0;i<8;i++){ED_SCL=1;pause();value=(value<<1)|ED_SDA;ED_SCL=0;pause();}
    ED_SDA=!ack;ED_SCL=1;pause();ED_SCL=0;ED_SDA=1;return value;
}
ed_u8 ed_i2c_recover(void) { ed_u8 i;ED_SDA=1;for(i=0;i<9;i++){ED_SCL=0;pause();ED_SCL=1;pause();}ed_i2c_stop();return ED_SDA&&ED_SCL; }
ed_u8 ed_i2c_write_regs(ed_u8 a,ed_u8 reg,const ed_u8 *data,ed_u8 length) {
    ed_u8 i;ed_i2c_start();
    if(!ed_i2c_write(a<<1)||!ed_i2c_write(reg)){ed_i2c_stop();return 0;}
    for(i=0;i<length;i++)if(!ed_i2c_write(data[i])){ed_i2c_stop();return 0;}
    ed_i2c_stop();return 1;
}
ed_u8 ed_i2c_write_reg(ed_u8 a,ed_u8 reg,ed_u8 value){return ed_i2c_write_regs(a,reg,&value,1);}
ed_u8 ed_i2c_read_regs(ed_u8 a,ed_u8 reg,ed_u8 *data,ed_u8 length) {
    ed_u8 i;ed_i2c_start();
    if(!length||!ed_i2c_write(a<<1)||!ed_i2c_write(reg)){ed_i2c_stop();return 0;}
    ed_i2c_start();if(!ed_i2c_write((a<<1)|1)){ed_i2c_stop();return 0;}
    for(i=0;i<length;i++)data[i]=ed_i2c_read(i+1<length);
    ed_i2c_stop();return 1;
}
