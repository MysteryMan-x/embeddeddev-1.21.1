package com.mysteryman.embeddeddev.mcu;

/** Byte-addressed peripheral with an atomic snapshot for each read transaction. */
public abstract class RegisterI2cDevice implements I2cDevice {
    protected final byte[] registers = new byte[256];
    private final byte[] snapshot = new byte[256];
    private int pointer;
    private boolean registerExpected;
    @Override public boolean begin(boolean reading) {
        registerExpected = !reading;
        if (reading) System.arraycopy(registers, 0, snapshot, 0, 256);
        return true;
    }
    @Override public boolean write(int value) {
        if (registerExpected) { pointer = value & 255; registerExpected = false; }
        else writeRegister(pointer++ & 255, value & 255);
        return true;
    }
    protected abstract void writeRegister(int register, int value);
    @Override public int read() { int reg=pointer++ & 255; int value=snapshot[reg]&255; afterRead(reg); return value; }
    protected void afterRead(int register) {}
    public int peek(int register) { return registers[register & 255] & 255; }
    protected void le16(int register, int value) {
        int v = Math.clamp(value, Short.MIN_VALUE, Short.MAX_VALUE);
        registers[register] = (byte)v; registers[register+1] = (byte)(v >> 8);
    }
    protected void le32(int register, int value) { for(int i=0;i<4;i++) registers[register+i]=(byte)(value >> (8*i)); }
    protected void be16(int register, double value) {
        int v=(int)Math.round(Math.clamp(value, Short.MIN_VALUE, Short.MAX_VALUE));
        registers[register]=(byte)(v >> 8); registers[register+1]=(byte)v;
    }
}
