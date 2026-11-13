#ifndef ED_DELAY_H
#define ED_DELAY_H
#include "ed_platform.h"
/* Blocking delay; reserves Timer0, disables ET0, leaves T0 stopped.
 * Setup/call overhead is additional. Do not use this for the initial 1-Wire read edge.
 */
void ed_delay_us(ed_u16 us);
void ed_delay_ms(ed_u16 ms);
#endif
