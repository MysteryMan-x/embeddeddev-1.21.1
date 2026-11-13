#ifndef ED_I2C_BUS_H
#define ED_I2C_BUS_H
#include "ed_platform.h"
/* Seven-bit addresses. Return 1=ACK/success, 0=NACK/failure.
 * Release SDA/SCL with 1; never drive an open-drain bus high via another output.
 */
void ed_i2c_start(void);
void ed_i2c_stop(void);
ed_u8 ed_i2c_write(ed_u8 value);
ed_u8 ed_i2c_read(ed_u8 ack);
ed_u8 ed_i2c_recover(void);
ed_u8 ed_i2c_write_regs(ed_u8 address,ed_u8 reg,const ed_u8 *data,ed_u8 length);
ed_u8 ed_i2c_write_reg(ed_u8 address,ed_u8 reg,ed_u8 value);
ed_u8 ed_i2c_read_regs(ed_u8 address,ed_u8 reg,ed_u8 *data,ed_u8 length);
#endif
