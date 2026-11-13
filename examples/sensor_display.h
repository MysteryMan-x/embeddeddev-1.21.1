#ifndef SENSOR_DISPLAY_H
#define SENSOR_DISPLAY_H
#include "drivers/ssd1306.h"
#include "drivers/ed_delay.h"
/* Shared OLED: SDA=P1.6 SCL=P1.7, 0x3C. No framebuffer or XDATA.
 * Compile at 12 MHz / 12T. A missing OLED is retried on the next refresh.
 */
ed_u8 display_begin(const char *title);
void display_text(ed_u8 page,const char *text);
void display_number(ed_u8 page,const char *label,ed_i32 value,ed_u8 decimals,const char *unit);
void display_error(const char *reason);
#endif
