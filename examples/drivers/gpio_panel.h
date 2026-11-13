#ifndef ED_GPIO_PANEL_H
#define ED_GPIO_PANEL_H
#include "ed_platform.h"
/* Keys 1..8=P0.0..7, key9=P2.0. Named GPIO wires are required per key. */
void gpio_panel_init(void);
ed_u16 gpio_panel_pressed(void);
ed_u16 gpio_panel_edges(ed_u16 *previous);
void gpio_led(ed_u8 on); /* LED=P1.0, high active */
#endif
