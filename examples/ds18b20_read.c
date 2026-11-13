/* 12 MHz / 12T. DQ=P1.2, OLED SDA=P1.6 SCL=P1.7.
 * Set ED_DQ_BIT_ADDRESS=0x92 for EVERY source in this project.
 * CRC-checked scratchpad at DATA 30H..38H; 3FH=A5 success / E1 error.
 * Refreshes continuously; retries disconnected sensors; displays signed Celsius.
 */
#ifndef ED_DQ_BIT_ADDRESS
#define ED_DQ_BIT_ADDRESS 0x92
#endif
#include "sensor_display.h"
#include "drivers/ds18b20.h"
#ifdef __SDCC
__data __at(0x30) unsigned char scratch[9];
__data __at(0x3F) unsigned char done;
#else
unsigned char data scratch[9] _at_ 0x30;
unsigned char data done _at_ 0x3F;
#endif
void main(void){
    ed_u8 ok;ed_i16 q4;
    ED_DQ=1;
    while(1){
        ok=ds18b20_start(0);if(ok){ed_delay_ms(800);ok=ds18b20_read_scratchpad(0,(ed_u8 *)scratch);}
        done=ok?0xA5:0xE1;
        if(display_begin("DS18B20")){
            if(!ok)display_error("OFFLINE / CRC ERROR");else{
                display_text(2,"");display_text(4,"");
                q4=(ed_i16)((ed_u16)scratch[0]|((ed_u16)scratch[1]<<8));
                display_number(3,"TEMP ",(ed_i32)q4*100/16,2," C");
                display_text(5,"CRC OK");display_text(7,"DQ P1.2 / 12MHZ");
            }
        }ed_delay_ms(200);
    }
}
