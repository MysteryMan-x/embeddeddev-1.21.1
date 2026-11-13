#include "ssd1306.h"
volatile ed_u8 demo_ok,demo_done;
volatile ed_i16 demo_value;
void main(void){
    ED_SDA=ED_SCL=1;demo_ok=ssd1306_init();
    if(demo_ok)demo_ok=ssd1306_clear();if(demo_ok)demo_ok=ssd1306_text(0,0,"HELLO 51");
    demo_value=demo_ok;demo_done=0xA5;while(1){}
}
