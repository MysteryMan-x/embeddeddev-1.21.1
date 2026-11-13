#include "pcf8591.h"
volatile ed_u8 demo_ok,demo_done;
volatile ed_i16 demo_value;
void main(void){
    ed_u8 value;ED_SDA=ED_SCL=1;
    while(1){demo_ok=pcf8591_read(0x48,0x40,&value);if(demo_ok){demo_value=value;demo_ok=pcf8591_dac(0x48,0x40,value);}demo_done=0xA5;}
}
