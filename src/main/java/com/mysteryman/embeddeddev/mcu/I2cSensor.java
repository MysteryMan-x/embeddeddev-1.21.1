package com.mysteryman.embeddeddev.mcu;

/** Simple 48h sensor: register 0 redstone, 1 sky light, 2 block light, 3 day percentage. */
public final class I2cSensor implements I2cDevice {
    public final int[] values = new int[4];
    private int pointer;
    private boolean expectRegister;
    @Override public int address() { return 0x48; }
    @Override public boolean begin(boolean read) { expectRegister = !read; return true; }
    @Override public boolean write(int value) { if (expectRegister) { pointer = value & 255; expectRegister = false; } return true; }
    @Override public int read() { return pointer < values.length ? values[pointer++] & 255 : 255; }
}
