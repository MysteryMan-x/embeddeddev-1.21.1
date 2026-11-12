#ifndef EMBEDDEDDEV_I2C_SOFT_H
#define EMBEDDEDDEV_I2C_SOFT_H
#include <REG52.H>
/* Add GPIO labels SDA=P1.6 and SCL=P1.7 on the side touching the module. */
sbit i2c_sda = P1^6;
sbit i2c_scl = P1^7;
void i2c_start(void) { i2c_sda=1; i2c_scl=1; i2c_sda=0; i2c_scl=0; }
void i2c_stop(void) { i2c_sda=0; i2c_scl=1; i2c_sda=1; }
bit i2c_write(unsigned char value) {
    unsigned char i; bit nack;
    for (i=0;i<8;i++) { i2c_sda=(value & 0x80)!=0; value<<=1; i2c_scl=1; i2c_scl=0; }
    i2c_sda=1; i2c_scl=1; nack=i2c_sda; i2c_scl=0;
    return !nack;
}
unsigned char i2c_read(bit ack) {
    unsigned char i, value=0;
    i2c_sda=1;
    for (i=0;i<8;i++) { i2c_scl=1; value=(value<<1) | i2c_sda; i2c_scl=0; }
    i2c_sda=!ack; i2c_scl=1; i2c_scl=0; i2c_sda=1;
    return value;
}
#endif
