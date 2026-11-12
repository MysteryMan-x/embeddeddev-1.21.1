/* Keil C51, 12 MHz / 12T. DQ=P1.2. Scratchpad is copied to IDATA 30H..38H. */
#include <REG52.H>
#include <intrins.h>
sbit DQ = P1^2;
unsigned char idata scratch[9] _at_ 0x30;

static void delay_us(unsigned int us) {
    unsigned int start = 65536UL - us;
    TR0 = 0; TMOD = (TMOD & 0xF0) | 1;
    TH0 = start >> 8; TL0 = start; TF0 = 0; TR0 = 1;
    while (!TF0) {}
    TR0 = 0;
}
static void reset_bus(void) {
    DQ = 0; delay_us(500); DQ = 1; delay_us(480);
}
static void write_byte(unsigned char value) {
    unsigned char i;
    for (i = 0; i < 8; i++) {
        DQ = 0;
        if (value & 1) { _nop_(); _nop_(); DQ = 1; delay_us(65); }
        else { delay_us(60); DQ = 1; delay_us(10); }
        value >>= 1;
    }
}
static unsigned char read_byte(void) {
    unsigned char i, value = 0;
    for (i = 0; i < 8; i++) {
        DQ = 0; _nop_(); _nop_(); _nop_(); DQ = 1;
        _nop_(); _nop_(); _nop_(); _nop_();
        value >>= 1;
        if (DQ) value |= 0x80;
        delay_us(60);
    }
    return value;
}
void main(void) {
    unsigned char i;
    EA = 0; DQ = 1;
    reset_bus(); write_byte(0xCC); write_byte(0x44);
    for (i = 0; i < 80; i++) delay_us(10000);
    reset_bus(); write_byte(0xCC); write_byte(0xBE);
    for (i = 0; i < 9; i++) scratch[i] = read_byte();
    /* Signed temperature in degrees C is ((signed int)(scratch[1]<<8 | scratch[0])) / 16. */
    while (1) {}
}
