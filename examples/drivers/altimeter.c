#include "altimeter.h"
static ed_i32 signed_le32(const ed_u8 *p){return (ed_i32)((ed_u32)p[0]|((ed_u32)p[1]<<8)|((ed_u32)p[2]<<16)|((ed_u32)p[3]<<24));}
ed_u8 altimeter_present(void){ed_u8 id;return ed_i2c_read_regs(0x76,0,&id,1)&&id==0x41;}
ed_u8 altimeter_zero(void){return ed_i2c_write_reg(0x76,0x12,0xA5);}
ed_u8 altimeter_read(altimeter_sample *s){
    ed_u8 p[18];if(!ed_i2c_read_regs(0x76,0,p,18)||p[0]!=0x41)return 0;
    s->status=p[1];s->sequence=p[2];s->absolute_cm=signed_le32(p+4);
    s->velocity_cm_s=(ed_i16)((ed_u16)p[8]|((ed_u16)p[9]<<8));s->zero_cm=signed_le32(p+10);s->relative_cm=signed_le32(p+14);return 1;
}
