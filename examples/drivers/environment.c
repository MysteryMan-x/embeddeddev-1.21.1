#include "environment.h"
ed_u8 environment_read(environment_sample *s){
    ed_u8 p[4];if(!ed_i2c_read_regs(0x48,0,p,4))return 0;
    s->redstone=p[0];s->sky_light=p[1];s->block_light=p[2];s->day_percent=p[3];return 1;
}
