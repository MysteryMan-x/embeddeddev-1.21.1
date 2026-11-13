#include "seven_segment.h"
static const ed_u8 ED_CODE glyph[16]={0x3F,0x06,0x5B,0x4F,0x66,0x6D,0x7D,0x07,0x7F,0x6F,0x77,0x7C,0x39,0x5E,0x79,0x71};
void seven_segment_raw(ed_u8 mask,ed_u8 anode){P2=anode?~mask:mask;}
void seven_segment_hex(ed_u8 value,ed_u8 dot,ed_u8 anode){seven_segment_raw(glyph[value&15]|(dot?128:0),anode);}
