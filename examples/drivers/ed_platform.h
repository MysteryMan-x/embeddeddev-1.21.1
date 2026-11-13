#ifndef ED_PLATFORM_H
#define ED_PLATFORM_H
/* Edit these pins to match the labels configured in the Minecraft board UI.
 * Drivers are independent projects: SEG=P2 conflicts with KEY9=P2.0.
 * DS1302 uses P3.0/P3.1/P3.2, so do not enable UART on those pins.
 * Timing examples use 12 MHz / 12T. Change ED_FOSC_HZ with the installed crystal.
 */
#ifndef ED_FOSC_HZ
#define ED_FOSC_HZ 12000000UL
#endif
#ifndef ED_DQ_BIT_ADDRESS
#define ED_DQ_BIT_ADDRESS 0xB7
#endif
#ifdef __SDCC
__sfr __at(0x80) P0; __sfr __at(0x90) P1;
__sfr __at(0xA0) P2; __sfr __at(0xB0) P3;
__sfr __at(0x88) TCON; __sfr __at(0x89) TMOD;
__sfr __at(0x8A) TL0; __sfr __at(0x8C) TH0;
__sbit __at(0x8C) TR0; __sbit __at(0x8D) TF0; __sbit __at(0xA9) ET0;
__sbit __at(0x96) ED_SDA; __sbit __at(0x97) ED_SCL;
__sbit __at(0x90) ED_LED; __sbit __at(0xA0) ED_KEY9;
__sbit __at(0x92) ED_REDSTONE;
__sbit __at(0xB0) ED_RTC_CE; __sbit __at(0xB1) ED_RTC_CLK; __sbit __at(0xB2) ED_RTC_IO;
__sbit __at(ED_DQ_BIT_ADDRESS) ED_DQ;
#define ED_CODE __code
#define ED_IDATA __idata
#define ED_NOP() __asm nop __endasm
#else
#include <REG52.H>
#include <intrins.h>
sbit ED_SDA=P1^6; sbit ED_SCL=P1^7;
sbit ED_LED=P1^0; sbit ED_KEY9=P2^0;
sbit ED_REDSTONE=P1^2;
sbit ED_RTC_CE=P3^0; sbit ED_RTC_CLK=P3^1; sbit ED_RTC_IO=P3^2;
sbit ED_DQ=ED_DQ_BIT_ADDRESS;
#define ED_CODE code
#define ED_IDATA idata
#define ED_NOP() _nop_()
#endif
typedef unsigned char ed_u8;
typedef signed char ed_i8;
typedef unsigned int ed_u16;
typedef signed int ed_i16;
typedef unsigned long ed_u32;
typedef signed long ed_i32;
#endif
