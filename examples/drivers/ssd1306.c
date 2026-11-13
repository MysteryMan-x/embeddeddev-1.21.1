#include "ssd1306.h"
#include "ed_font5x7.h"
ed_u8 ssd1306_command(ed_u8 v){return ed_i2c_write_reg(0x3C,0,v);}
ed_u8 ssd1306_init(void){
    static const ed_u8 ED_CODE commands[]={0xAE,0xD5,0x80,0xA8,0x3F,0xD3,0,0x40,0x8D,0x14,0x20,2,0xA1,0xC8,0xDA,0x12,0x81,0x7F,0xA4,0xA6,0xAF};
    ed_u8 i;ed_i2c_start();if(!ed_i2c_write(0x78)||!ed_i2c_write(0)){ed_i2c_stop();return 0;}
    for(i=0;i<sizeof(commands);i++)if(!ed_i2c_write(commands[i])){ed_i2c_stop();return 0;}ed_i2c_stop();return 1;
}
ed_u8 ssd1306_position(ed_u8 page,ed_u8 column){if(page>7||column>127)return 0;return ssd1306_command(0xB0|page)&&ssd1306_command(column&15)&&ssd1306_command(0x10|(column>>4));}
ed_u8 ssd1306_columns(const ed_u8 *p,ed_u8 count){
    ed_u8 i;ed_i2c_start();if(!ed_i2c_write(0x78)||!ed_i2c_write(0x40)){ed_i2c_stop();return 0;}
    for(i=0;i<count;i++)if(!ed_i2c_write(p[i])){ed_i2c_stop();return 0;}ed_i2c_stop();return 1;
}
ed_u8 ssd1306_clear(void){
    ed_u8 page,i;for(page=0;page<8;page++){
        if(!ssd1306_position(page,0))return 0;ed_i2c_start();if(!ed_i2c_write(0x78)||!ed_i2c_write(0x40)){ed_i2c_stop();return 0;}
        for(i=0;i<128;i++)if(!ed_i2c_write(0)){ed_i2c_stop();return 0;}ed_i2c_stop();
    }return 1;
}
ed_u8 ssd1306_text(ed_u8 page,ed_u8 column,const char *text){
    ed_u8 c,i;if(!ssd1306_position(page,column))return 0;ed_i2c_start();
    if(!ed_i2c_write(0x78)||!ed_i2c_write(0x40)){ed_i2c_stop();return 0;}
    while(*text&&column<=122){c=*text++;if(c>='a'&&c<='z')c-=32;if(c<32||c>95)c=32;
        for(i=0;i<5;i++)if(!ed_i2c_write(ed_font5x7[c-32][i])){ed_i2c_stop();return 0;}
        if(!ed_i2c_write(0)){ed_i2c_stop();return 0;}column+=6;
    }ed_i2c_stop();return 1;
}
