#include "ds18b20.h"
volatile ed_u8 demo_ok,demo_done;
volatile ed_u8 demo_aux;
volatile ed_i16 demo_value;
ed_u8 rom[8];ds18b20_search search;
void main(void){
    ed_i16 q4=0;ED_DQ=1;
    if(ds18b20_read_rom(rom))demo_aux|=1;
    ds18b20_search_init(&search);if(ds18b20_search_next(&search))demo_aux|=2;
    if(ds18b20_resolution(0,12))demo_aux|=4;
    while(1){demo_ok=ds18b20_start(0);ed_delay_ms(800);if(demo_ok)demo_ok=ds18b20_read_q4(0,&q4);if(demo_ok)demo_value=q4;demo_done=0xA5;}
}
