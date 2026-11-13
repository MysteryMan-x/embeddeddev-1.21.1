#ifndef ED_SSD1306_H
#define ED_SSD1306_H
#include "i2c_bus.h"
#define SSD1306_ADDRESS 0x3C
ed_u8 ssd1306_init(void);
ed_u8 ssd1306_command(ed_u8 command);
ed_u8 ssd1306_position(ed_u8 page,ed_u8 column);
ed_u8 ssd1306_columns(const ed_u8 *data,ed_u8 count);
ed_u8 ssd1306_clear(void);
ed_u8 ssd1306_text(ed_u8 page,ed_u8 column,const char *text);
/* 128x64, 8 pages. Bit0 is top pixel. Text uses 5x7 uppercase ASCII glyphs.
 * Include ed_font5x7.c; writes go to OLED-owned RAM, with no MCU framebuffer.
 */
#endif
