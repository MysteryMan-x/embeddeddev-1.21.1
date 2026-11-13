#include "gpio_panel.h"
void gpio_panel_init(void){P0=0xFF;ED_KEY9=1;ED_LED=0;}
ed_u16 gpio_panel_pressed(void){return (ed_u8)~P0|(ED_KEY9?0:256);}
ed_u16 gpio_panel_edges(ed_u16 *previous){ed_u16 now=gpio_panel_pressed(),edge=now&~*previous;*previous=now;return edge;}
void gpio_led(ed_u8 on){ED_LED=on!=0;}
