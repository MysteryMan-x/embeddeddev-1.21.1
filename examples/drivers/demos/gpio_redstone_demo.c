#include "gpio_redstone.h"
volatile ed_u8 demo_ok,demo_done;
volatile ed_i16 demo_value;
void main(void){gpio_redstone_write(0);gpio_redstone_write(1);demo_value=gpio_redstone_read();demo_ok=1;demo_done=0xA5;while(1){}}
