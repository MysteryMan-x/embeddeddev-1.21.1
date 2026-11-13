#include "ds18b20.h"
static void write_bit(ed_u8 v){
    ED_DQ=0;
    if(v){ED_NOP();ED_NOP();ED_DQ=1;ed_delay_us(65);}
    else{ed_delay_us(55);ED_DQ=1;ed_delay_us(10);}
}
static ed_u8 read_bit(void){
    ed_u8 v;ED_DQ=0;ED_NOP();ED_NOP();ED_NOP();ED_DQ=1;
    ED_NOP();ED_NOP();ED_NOP();ED_NOP();
#if ED_FOSC_HZ >= 22000000UL
    ED_NOP();ED_NOP();ED_NOP();ED_NOP();ED_NOP();ED_NOP();
#endif
    v=ED_DQ;ed_delay_us(60);return v;
}
static void put(ed_u8 v){ed_u8 i;for(i=0;i<8;i++){write_bit(v&1);v>>=1;}}
static ed_u8 get(void){ed_u8 i,v=0;for(i=0;i<8;i++){v>>=1;if(read_bit())v|=128;}return v;}
ed_u8 ds18b20_reset(void){ed_u8 present;ED_DQ=0;ed_delay_us(500);ED_DQ=1;ed_delay_us(60);present=!ED_DQ;ed_delay_us(430);return present;}
ed_u8 ds18b20_crc8(const ed_u8 *p,ed_u8 n){ed_u8 c=0,i,v;while(n--){v=*p++;for(i=0;i<8;i++){ed_u8 mix=(c^v)&1;c>>=1;if(mix)c^=0x8C;v>>=1;}}return c;}
static ed_u8 select_rom(const ed_u8 *rom){ed_u8 i;if(!ds18b20_reset())return 0;if(!rom)put(0xCC);else{put(0x55);for(i=0;i<8;i++)put(rom[i]);}return 1;}
ed_u8 ds18b20_read_rom(ed_u8 *rom){ed_u8 i;if(!ds18b20_reset())return 0;put(0x33);for(i=0;i<8;i++)rom[i]=get();return ds18b20_crc8(rom,8)==0;}
void ds18b20_search_init(ds18b20_search *s){ed_u8 i;for(i=0;i<8;i++)s->rom[i]=0;s->last_discrepancy=s->last_device=0;}
ed_u8 ds18b20_search_next(ds18b20_search *s){
    ed_u8 bit_number=1,last_zero=0,byte=0,mask=1,a,b,choice;
    if(s->last_device||!ds18b20_reset())return 0;put(0xF0);
    while(byte<8){
        a=read_bit();b=read_bit();if(a&&b){ds18b20_search_init(s);return 0;}
        if(a!=b)choice=a;
        else{choice=bit_number<s->last_discrepancy?(s->rom[byte]&mask)!=0:bit_number==s->last_discrepancy;if(!choice)last_zero=bit_number;}
        if(choice)s->rom[byte]|=mask;else s->rom[byte]&=~mask;write_bit(choice);
        bit_number++;mask<<=1;if(!mask){byte++;mask=1;}
    }
    s->last_discrepancy=last_zero;s->last_device=last_zero==0;return ds18b20_crc8(s->rom,8)==0;
}
ed_u8 ds18b20_start(const ed_u8 *rom){if(!select_rom(rom))return 0;put(0x44);return 1;}
ed_u8 ds18b20_read_scratchpad(const ed_u8 *rom,ed_u8 p[9]){ed_u8 i;if(!select_rom(rom))return 0;put(0xBE);for(i=0;i<9;i++)p[i]=get();return ds18b20_crc8(p,9)==0;}
ed_u8 ds18b20_read_q4(const ed_u8 *rom,ed_i16 *q4){ed_u8 p[9];if(!ds18b20_read_scratchpad(rom,p))return 0;*q4=(ed_i16)((ed_u16)p[0]|((ed_u16)p[1]<<8));return 1;}
ed_u8 ds18b20_resolution(const ed_u8 *rom,ed_u8 bits){ed_u8 p[9];if(bits<9||bits>12||!ds18b20_read_scratchpad(rom,p)||!select_rom(rom))return 0;put(0x4E);put(p[2]);put(p[3]);put(0x1F|((bits-9)<<5));return 1;}
