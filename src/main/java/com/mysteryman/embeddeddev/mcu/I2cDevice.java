package com.mysteryman.embeddeddev.mcu;

/** Extension point for modules attached to a configured connector. Address is seven bits. */
public interface I2cDevice {
    String SDA = "SDA";
    String SCL = "SCL";

    int address();
    boolean begin(boolean read);
    boolean write(int value);
    int read();
    default void end() {}
}
