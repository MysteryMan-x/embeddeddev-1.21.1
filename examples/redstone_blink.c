#include <REG52.H>

/* 11.0592 MHz / 12T, 20 TPS. Toggle the north face every ~0.5 seconds. */
sbit north_output = P1^2;
unsigned char ticks;

void timer0_isr(void) interrupt 1 {
    TR0 = 0;
    TH0 = 0x4C;
    TL0 = 0x00;
    TR0 = 1;
    if (++ticks == 10) { ticks = 0; north_output = !north_output; }
}

void main(void) {
    P1 = 0;
    ticks = 0;
    TMOD = 0x01;
    TH0 = 0x4C;
    TL0 = 0x00;
    ET0 = 1;
    EA = 1;
    TR0 = 1;
    while (1) {}
}
