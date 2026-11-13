#include "seven_segment.h"
volatile ed_u8 demo_ok,demo_done;
volatile ed_i16 demo_value;
void main(void){seven_segment_hex(8,1,0);demo_value=P2;demo_ok=1;demo_done=0xA5;while(1){}}
