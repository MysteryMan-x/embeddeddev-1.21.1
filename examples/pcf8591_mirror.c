#include "sensor_display.h"
#include "drivers/pcf8591.h"
/* 12 MHz, PCF8591=0x48, OLED=0x3C on SDA=P1.6/SCL=P1.7.
 * AIN0=NORTH AIN1=EAST AIN2=SOUTH AIN3=WEST, AOUT=UP.
 * Mirrors AIN0 through DAC and displays all four 8-bit ADC samples.
 */
#ifdef __SDCC
__data __at(0x30) unsigned char sample;
#else
unsigned char data sample _at_ 0x30;
#endif
void main(void){
    ed_u8 v[4],ok;
    while(1){ok=pcf8591_read_channels_control(0x48,0x44,v);if(ok){sample=v[0];ok=pcf8591_dac(0x48,0x44,sample);}
        if(display_begin("PCF8591 0X48")){
            if(!ok)display_error("SENSOR OFFLINE");else{
                display_number(2,"AIN0 ",v[0],0," /255");display_number(3,"AIN1 ",v[1],0," /255");
                display_number(4,"AIN2 ",v[2],0," /255");display_number(5,"AIN3 ",v[3],0," /255");
                display_number(6,"DAC ",sample,0," /255");display_text(7,"AIN0 TO AOUT");
            }
        }ed_delay_ms(200);
    }
}
