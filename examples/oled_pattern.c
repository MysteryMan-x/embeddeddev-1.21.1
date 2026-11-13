#include "i2c_soft.h"

/* Raw diagnostic stripes sent over software I2C, SDA=P1.6 / SCL=P1.7. */
const unsigned char code init_commands[] = {0xAE,0xA1,0xC8,0x20,0,0x21,0,127,0x22,0,7,0xAF};

void main(void) {
    unsigned int i;
    P1 = 0xC0;
    i2c_start(); i2c_write(0x78); i2c_write(0x00);
    for (i=0;i<sizeof(init_commands);i++) i2c_write(init_commands[i]);
    i2c_stop();
    i2c_start(); i2c_write(0x78); i2c_write(0x40);
    for (i=0;i<1024;i++) i2c_write((unsigned char)i ^ 0xAA);
    i2c_stop();
    while (1) {}
}
