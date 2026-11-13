#include "sensor_display.h"
#include "drivers/mpu6050.h"
/* 12 MHz. MPU6050=0x68 and OLED=0x3C share SDA=P1.6/SCL=P1.7.
 * Acceleration in milli-g; angular rate in degrees/s. Not an attitude solver.
 */
void main(void){
    mpu6050_raw s;ed_u8 initialized=0,ok;
    while(1){
        if(!initialized)initialized=mpu6050_init(0x68);
        ok=initialized&&mpu6050_read_raw(0x68,&s);
        if(display_begin("MPU6050 0X68")){
            if(!ok)display_error("SENSOR OFFLINE");else{
                display_number(1,"AX ",(ed_i32)s.ax*1000/16384,0," MG");
                display_number(2,"AY ",(ed_i32)s.ay*1000/16384,0," MG");
                display_number(3,"AZ ",(ed_i32)s.az*1000/16384,0," MG");
                display_number(4,"GX ",(ed_i32)s.gx*10/131,1," D/S");
                display_number(5,"GY ",(ed_i32)s.gy*10/131,1," D/S");
                display_number(6,"GZ ",(ed_i32)s.gz*10/131,1," D/S");display_text(7,"RANGE 2G / 250DPS");
            }
        }
        if(!ok)initialized=0;ed_delay_ms(200);
    }
}
