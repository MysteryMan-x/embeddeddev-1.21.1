#include "i2c_soft.h"
/* Sensor: SDA=P1.6 and SCL=P1.7. LED module: LED=P1.2 on another group. */
unsigned char data value _at_ 0x30;
sbit indicator = P1^2;
void main(void) {
    P1=0xC0;
    while(1) {
        i2c_start(); i2c_write(0x90); i2c_write(0); /* Select redstone register. */
        i2c_start(); i2c_write(0x91); value=i2c_read(0); i2c_stop();
        indicator = value != 0;
    }
}
