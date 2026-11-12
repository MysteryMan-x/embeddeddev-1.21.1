#include <REG52.H>
#include "embeddeddev.h"
#include "oled_hello_bitmap.h"
/* Optional MOVX bridge to the first attached OLED; the display is a separate module. */
void main(void) {
    unsigned int i;
    P1=0xC0;
    ED_OLED_CONTROL=3;
    for(i=0;i<1024;i++) ED_OLED[i]=hello_bitmap[i];
    while(1) {}
}
