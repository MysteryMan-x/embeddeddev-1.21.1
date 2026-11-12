#include "i2c_soft.h"
#include "embeddeddev.h"
/* Name the sensor-side GPIOs SDA=P1.6 and SCL=P1.7. Bind DAC0 on another side. */
void main(void) {
    unsigned char value;
    P1=0xC0;
    while(1) {
        i2c_start(); i2c_write(0x90); i2c_write(0); /* Select redstone register. */
        i2c_start(); i2c_write(0x91); value=i2c_read(0); i2c_stop();
        ED_DAC[0]=value;
    }
}
