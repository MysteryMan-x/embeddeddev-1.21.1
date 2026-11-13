#include "environment.h"
volatile ed_u8 demo_ok,demo_done;
volatile ed_i16 demo_value;
environment_sample sample;
void main(void){ED_SDA=ED_SCL=1;while(1){demo_ok=environment_read(&sample);if(demo_ok)demo_value=sample.redstone;demo_done=0xA5;}}
