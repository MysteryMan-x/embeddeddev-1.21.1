#include "altimeter.h"
volatile ed_u8 demo_ok,demo_done;
volatile ed_i16 demo_value;
altimeter_sample sample;
void main(void){
    ED_SDA=ED_SCL=1;demo_ok=altimeter_present();
    while(1){if(demo_ok&&altimeter_read(&sample))demo_value=(ed_i16)sample.absolute_cm;demo_done=0xA5;}
}
