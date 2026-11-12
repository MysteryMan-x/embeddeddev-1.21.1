#ifndef EMBEDDEDDEV_H
#define EMBEDDEDDEV_H

/* Keil C51 / SMALL memory model. The board supplies external expansion devices. */
/* ADC0..ADC7 and DAC0..DAC7 are selectable channels, independent of direction. */
#define ED_ANALOG_CHANNELS 8
#define ED_ADC  ((volatile unsigned char xdata *)0xF000)
#define ED_DAC  ((volatile unsigned char xdata *)0xF010)
#define ED_OLED_CONTROL (*((volatile unsigned char xdata *)0xF020))
#define ED_OLED ((volatile unsigned char xdata *)0xF100)

/* OLED: eight pages of 128 columns, bit 0 is the top pixel within each page. */
#define ED_OLED_INDEX(x, y) ((((unsigned int)(y) >> 3) * 128u) + (x))
#endif
