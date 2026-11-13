#include "sensor_display.h"
#include "drivers/environment.h"
/* 12 MHz; sensor=0x48 and OLED=0x3C share P1.6 SDA / P1.7 SCL.
 * Optional indicator LED=P1.2. Debug sample at DATA 30H.
 */
#ifdef __SDCC
__data __at(0x30) unsigned char value;
#else
unsigned char data value _at_ 0x30;
#endif
void main(void){
    environment_sample s;ed_u8 ok;
    while(1){ok=environment_read(&s);value=ok?s.redstone:0;ED_REDSTONE=value!=0;
        if(display_begin("ENVIRONMENT 0X48")){
            if(!ok)display_error("SENSOR OFFLINE");else{
                display_number(2,"REDSTONE ",s.redstone,0," /15");
                display_number(3,"SKY ",s.sky_light,0," /15");
                display_number(4,"BLOCK ",s.block_light,0," /15");
                display_number(5,"DAY ",s.day_percent,0," PCT");display_text(7,"LIVE SENSOR DATA");
            }
        }ed_delay_ms(200);
    }
}
