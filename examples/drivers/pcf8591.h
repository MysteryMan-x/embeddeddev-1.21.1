#ifndef ED_PCF8591_H
#define ED_PCF8591_H
#include "i2c_bus.h"
/* Address 48H..4FH. mode=0 single-ended, 1/2/3 differential/mixed.
 * Control: bit6 DAC enable, bits5:4 mode, bit2 auto-increment, bits1:0 channel.
 */
ed_u8 pcf8591_read(ed_u8 address,ed_u8 control,ed_u8 *sample);
ed_u8 pcf8591_read_channels(ed_u8 address,ed_u8 *four_samples);
ed_u8 pcf8591_dac(ed_u8 address,ed_u8 control,ed_u8 value);
#endif
