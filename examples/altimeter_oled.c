#include "sensor_display.h"
#include "drivers/altimeter.h"
/* 12 MHz. ALT-01=0x76 and OLED=0x3C, SDA=P1.6/SCL=P1.7.
 * Zero is the sensor's saved zero; this example never silently resets it.
 */
void main(void){
    altimeter_sample s;ed_u8 ok;
    while(1){ok=altimeter_read(&s)&&(s.status&1);
        if(display_begin("ALT-01 0X76")){
            if(!ok)display_error("SENSOR OFFLINE");else{
                display_number(2,"ABS ",s.absolute_cm,0," CM");
                display_number(3,"REL ",s.relative_cm,0," CM");
                display_number(4,"ZERO ",s.zero_cm,0," CM");
                display_number(5,"V ",s.velocity_cm_s,0," CM/S");
                display_text(6,(s.status&2)?"MOVING STRUCTURE":"WORLD POSITION");display_text(7,"LIVE HEIGHT");
            }
        }ed_delay_ms(200);
    }
}
