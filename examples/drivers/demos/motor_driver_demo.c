#include "motor_driver.h"
volatile ed_u8 demo_ok,demo_done;
volatile ed_i16 demo_value;
/* Read-only demo works without Create. For motion, explicitly arm and keep
 * calling motor_driver_set_all(0x60,rpm) with all six signed channel values.
 */
void main(void){
    ed_u8 mask;ED_SDA=ED_SCL=1;demo_ok=motor_driver_present(0x60,&mask);
    demo_value=mask;demo_done=0xA5;while(1){}
}
