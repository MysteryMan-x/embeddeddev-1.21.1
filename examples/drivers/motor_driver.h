#ifndef ED_MOTOR_DRIVER_H
#define ED_MOTOR_DRIVER_H
#include "i2c_bus.h"
enum { MOTOR_DOWN=0,MOTOR_UP,MOTOR_NORTH,MOTOR_SOUTH,MOTOR_WEST,MOTOR_EAST };
ed_u8 motor_driver_present(ed_u8 address,ed_u8 *mask);
ed_u8 motor_driver_arm(ed_u8 address,ed_u8 enable);
ed_u8 motor_driver_set_all(ed_u8 address,const ed_i16 rpm[6]);
/* Address 60H..67H; keep sending all channels faster than the 4-tick watchdog.
 * Six little-endian signed words are committed atomically by the final A5H.
 */
#endif
