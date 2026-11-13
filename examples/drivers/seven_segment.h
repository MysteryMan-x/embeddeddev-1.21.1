#ifndef ED_SEVEN_SEGMENT_H
#define ED_SEVEN_SEGMENT_H
#include "ed_platform.h"
/* A..G,DP map to P2.0..P2.7. This owns the full port, including P2.0. */
void seven_segment_raw(ed_u8 segments,ed_u8 common_anode);
void seven_segment_hex(ed_u8 digit,ed_u8 decimal_point,ed_u8 common_anode);
#endif
