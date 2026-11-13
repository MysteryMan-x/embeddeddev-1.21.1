#include "gpio_redstone.h"
void gpio_redstone_write(ed_u8 on){ED_REDSTONE=on!=0;}
ed_u8 gpio_redstone_read(void){ED_REDSTONE=1;return ED_REDSTONE;}
