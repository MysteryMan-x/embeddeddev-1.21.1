package com.mysteryman.embeddeddev.mcu;

import java.util.List;

/** Pin masks use the order in terminals(). A peripheral can pull a released MCU pin low. */
public interface GpioDevice {
    String label();
    List<String> terminals();
    default void update(int levels) {}
    default int pullLow() { return 0; }
    default void reset(int levels) { update(levels); }
    default void time(long nanoseconds) {}
}
