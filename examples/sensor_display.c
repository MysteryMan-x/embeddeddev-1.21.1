#include "sensor_display.h"
static ed_u8 ready;
void display_text(ed_u8 page,const char *text){
    char line[22];ed_u8 i=0;while(*text&&i<21)line[i++]=*text++;
    while(i<21)line[i++]=' ';line[21]=0;
    if(!ssd1306_text(page,0,line))ready=0;
}
ed_u8 display_begin(const char *title){
    if(!ready){if(!ssd1306_init()||!ssd1306_clear())return 0;ready=1;}
    display_text(0,title);return ready;
}
void display_number(ed_u8 page,const char *label,ed_i32 value,ed_u8 decimals,const char *unit){
    char line[22],digits[11];ed_u8 i=0,n=0,j;ed_u32 magnitude;
    while(*label&&i<20)line[i++]=*label++;
    if(value<0&&i<20)line[i++]='-';
    magnitude=value<0?0UL-(ed_u32)value:(ed_u32)value;
    do{digits[n++]=(char)('0'+magnitude%10);magnitude/=10;}while(magnitude&&n<10);
    while(n<=decimals&&n<10)digits[n++]='0';
    for(j=n;j>0&&i<20;j--){line[i++]=digits[j-1];if(decimals&&j==decimals+1&&i<20)line[i++]='.';}
    while(*unit&&i<21)line[i++]=*unit++;line[i]=0;display_text(page,line);
}
void display_error(const char *reason){ed_u8 page;for(page=1;page<8;page++)display_text(page,"");display_text(2,reason);display_text(4,"CHECK WIRES / ADDRESS");}
