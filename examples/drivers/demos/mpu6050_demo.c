#include "mpu6050.h"
volatile ed_u8 demo_ok,demo_done;
volatile ed_i16 demo_value;
mpu6050_raw sample;
void main(void){
    ED_SDA=ED_SCL=1;
    demo_ok=mpu6050_init(MPU6050_ADDRESS);
    while(1){if(demo_ok&&mpu6050_read_raw(MPU6050_ADDRESS,&sample))demo_value=sample.gx;demo_done=0xA5;}
}
