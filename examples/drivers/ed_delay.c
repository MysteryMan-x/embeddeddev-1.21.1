#include "ed_delay.h"
void ed_delay_us(ed_u16 us) {
    ed_u16 ticks,start,part;
    while(us){part=us>10000?10000:us;us-=part;
#if ED_FOSC_HZ == 12000000UL
    ticks=part;
#else
    ticks=(ed_u16)(((ed_u32)part*(ED_FOSC_HZ/12000UL)+999UL)/1000UL);
#endif
    start=0u-ticks; TR0=0; ET0=0; TMOD=(TMOD&0xF0)|1;
    TH0=(ed_u8)(start>>8);TL0=(ed_u8)start;TF0=0;TR0=1;
    while(!TF0) {} TR0=0;
    }
}
void ed_delay_ms(ed_u16 ms) { while(ms--)ed_delay_us(1000); }
