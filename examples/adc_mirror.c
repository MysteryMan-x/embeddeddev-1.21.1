#include <REG52.H>
#include "embeddeddev.h"

void main(void) {
    P1 = 0; /* Other faces remain off. */
    /* Bind any input direction to ADC0 and any output direction to DAC1 in the GUI. */
    while (1) ED_DAC[1] = ED_ADC[0];
}
