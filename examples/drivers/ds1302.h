#ifndef ED_DS1302_H
#define ED_DS1302_H
#include "ed_platform.h"
typedef struct { ed_u8 year,month,date,weekday,hour,minute,second,halted; } ds1302_time;
void ds1302_init(void);
void ds1302_write_protect(ed_u8 enabled);
ed_u8 ds1302_get_time(ds1302_time *time);
ed_u8 ds1302_set_time(const ds1302_time *time);
ed_u8 ds1302_ram_read(ed_u8 address,ed_u8 *value);
ed_u8 ds1302_ram_write(ed_u8 address,ed_u8 value);
/* Year 0..99 means 2000..2099. RAM addresses 0..30. IO is LSB-first, not I2C.
 * RAM writes require write protection off; get_time supports both 12h and 24h.
 */
#endif
