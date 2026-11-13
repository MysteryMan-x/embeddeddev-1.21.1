#ifndef ED_DS18B20_H
#define ED_DS18B20_H
#include "ed_delay.h"
typedef struct { ed_u8 rom[8],last_discrepancy,last_device; } ds18b20_search;
ed_u8 ds18b20_reset(void);
ed_u8 ds18b20_crc8(const ed_u8 *data,ed_u8 length);
ed_u8 ds18b20_read_rom(ed_u8 rom[8]);
void ds18b20_search_init(ds18b20_search *state);
ed_u8 ds18b20_search_next(ds18b20_search *state);
ed_u8 ds18b20_start(const ed_u8 *rom);
ed_u8 ds18b20_read_q4(const ed_u8 *rom,ed_i16 *temperature_q4);
ed_u8 ds18b20_resolution(const ed_u8 *rom,ed_u8 bits);
/* rom=0 selects SKIP ROM (single sensor only). Otherwise supply all 8 ROM bytes.
 * Start conversion, wait >=750ms at 12-bit resolution, then read_q4.
 * Signed Celsius = q4 / 16. Preserve the sign when displaying fractional values.
 */
#endif
