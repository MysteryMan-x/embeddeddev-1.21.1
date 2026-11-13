#include "ds1302.h"
volatile ed_u8 demo_ok,demo_done,demo_aux;
volatile ed_i16 demo_value;
ds1302_time time={26,11,13,6,12,34,56,0};
void main(void){
    ed_u8 value;ds1302_init();demo_ok=ds1302_set_time(&time);
    if(demo_ok)demo_ok=ds1302_get_time(&time);
    demo_value=(ed_i16)time.hour*100+time.minute;
    ds1302_ram_write(0,0xA7);ds1302_ram_read(0,&value);demo_aux=value;demo_done=0xA5;while(1){}
}
