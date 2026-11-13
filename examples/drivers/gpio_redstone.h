#ifndef ED_GPIO_REDSTONE_H
#define ED_GPIO_REDSTONE_H
#include "ed_platform.h"
/* SIGNAL=P1.2. Configure the redstone connector's read/write mode in game.
 * GPIO reads only 0/1; output on=15, off=0. Use PCF8591 for analog strengths.
 */
void gpio_redstone_write(ed_u8 on);
ed_u8 gpio_redstone_read(void);
#endif
