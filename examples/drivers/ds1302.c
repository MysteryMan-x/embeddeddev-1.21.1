#include "ds1302.h"
static ed_u8 bcd(ed_u8 v){return (v/10)*16+v%10;}
static ed_u8 binary(ed_u8 v){return (v>>4)*10+(v&15);}
static void select(void){ED_RTC_CE=0;ED_RTC_CLK=0;ED_RTC_IO=1;ED_RTC_CE=1;}
static void put(ed_u8 v){ed_u8 i;for(i=0;i<8;i++){ED_RTC_IO=v&1;ED_RTC_CLK=1;ED_RTC_CLK=0;v>>=1;}}
static ed_u8 get(void){ed_u8 i,v=0;ED_RTC_IO=1;for(i=0;i<8;i++){if(ED_RTC_IO)v|=1<<i;ED_RTC_CLK=1;ED_RTC_CLK=0;}return v;}
static void reg_write(ed_u8 command,ed_u8 v){select();put(command&0xFE);put(v);ED_RTC_CE=0;ED_RTC_IO=1;}
void ds1302_init(void){ED_RTC_CE=ED_RTC_CLK=0;ED_RTC_IO=1;}
void ds1302_write_protect(ed_u8 on){reg_write(0x8E,on?128:0);}
ed_u8 ds1302_get_time(ds1302_time *t){
    ed_u8 p[8],i;select();put(0xBF);for(i=0;i<8;i++)p[i]=get();ED_RTC_CE=0;
    t->halted=(p[0]&128)!=0;t->second=binary(p[0]&127);t->minute=binary(p[1]&127);
    if(p[2]&128){t->hour=binary(p[2]&31)%12;if(p[2]&32)t->hour+=12;}
    else t->hour=binary(p[2]&63);
    t->date=binary(p[3]&63);t->month=binary(p[4]&31);t->weekday=binary(p[5]&7);t->year=binary(p[6]);
    return t->second<60&&t->minute<60&&t->hour<24&&t->month>=1&&t->month<=12&&t->date>=1&&t->date<=31;
}
ed_u8 ds1302_set_time(const ds1302_time *t){
    ed_u8 days;static const ed_u8 ED_CODE lengths[]={31,28,31,30,31,30,31,31,30,31,30,31};
    if(t->year>99||t->month<1||t->month>12||t->weekday<1||t->weekday>7||t->hour>23||t->minute>59||t->second>59)return 0;
    days=lengths[t->month-1];if(t->month==2&&t->year%4==0)days=29;if(t->date<1||t->date>days)return 0;
    ds1302_write_protect(0);select();put(0xBE);
    put(bcd(t->second)|(t->halted?128:0));put(bcd(t->minute));put(bcd(t->hour));put(bcd(t->date));
    put(bcd(t->month));put(bcd(t->weekday));put(bcd(t->year));put(0);ED_RTC_CE=0;ED_RTC_IO=1;return 1;
}
ed_u8 ds1302_ram_read(ed_u8 a,ed_u8 *v){if(a>30)return 0;select();put(0xC1+a*2);*v=get();ED_RTC_CE=0;return 1;}
ed_u8 ds1302_ram_write(ed_u8 a,ed_u8 v){if(a>30)return 0;reg_write(0xC0+a*2,v);return 1;}
