#include <REG52.H>
#include "embeddeddev.h"

void main(void) {
    unsigned int i;
    P1 = 0;
    ED_OLED_CONTROL = 1;
    for (i = 0; i < 1024; ++i) ED_OLED[i] = (unsigned char)i ^ 0xAA;
    while (1) {}
}
