#include "gpio_panel.h"
volatile ed_u8 demo_ok,demo_done;
volatile ed_i16 demo_value;
void main(void){gpio_panel_init();while(1){demo_value=gpio_panel_pressed();gpio_led(demo_value!=0);demo_ok=1;demo_done=0xA5;}}
