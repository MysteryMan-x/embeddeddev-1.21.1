#ifndef EMBEDDEDDEV_H
#define EMBEDDEDDEV_H

/* Keil C51 / SMALL memory model. All 64 KiB XDATA is ordinary RAM.
 * OLED and analog conversion use external I2C devices, with no MOVX windows.
 * These are seven-bit I2C bus addresses, not MCU memory addresses. */
#define ED_OLED_I2C_ADDRESS 0x3C
#define ED_PCF8591_I2C_ADDRESS 0x48

/* OLED: eight pages of 128 columns, bit 0 is the top pixel within each page. */
#define ED_OLED_INDEX(x, y) ((((unsigned int)(y) >> 3) * 128u) + (x))
#endif
