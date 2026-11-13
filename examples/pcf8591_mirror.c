/* Keil C51: external PCF8591, A0/A1/A2=0, SDA=P1.6, SCL=P1.7.
 * AIN0 samples redstone on the module's NORTH face; AOUT emits UP.
 * The generated HEX implements the same software I2C sequence, not a Keil build.
 */
#include "i2c_soft.h"
unsigned char data sample _at_ 0x30;
void main(void) {
    P1 = 0xC0;
    while (1) {
        i2c_start(); i2c_write(0x90); i2c_write(0x40); /* Enable AOUT, select AIN0. */
        i2c_start(); i2c_write(0x91);
        i2c_read(1);              /* Discard previous conversion; ACK for next byte. */
        sample = i2c_read(0);     /* Fresh conversion; finish with NACK. */
        i2c_stop();
        i2c_start(); i2c_write(0x90); i2c_write(0x40); i2c_write(sample); i2c_stop();
    }
}
