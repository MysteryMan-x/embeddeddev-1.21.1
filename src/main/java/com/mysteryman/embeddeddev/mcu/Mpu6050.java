package com.mysteryman.embeddeddev.mcu;

import java.util.Arrays;

/** MPU6050 raw-register subset. Sensor axes: X=east, Y=north, Z=up. No attitude solver or DMP. */
public final class Mpu6050 extends RegisterI2cDevice {
    private boolean ad0;
    public Mpu6050() { reset(); }
    public void ad0(boolean value) { ad0=value; }
    @Override public int address() { return ad0 ? 0x69 : 0x68; }
    private void reset() { Arrays.fill(registers,(byte)0); registers[0x75]=0x68; registers[0x6b]=0x40; }
    @Override protected void writeRegister(int reg,int value) {
        if(reg==0x6b && (value&0x80)!=0) { reset(); return; }
        if(reg==0x19 || reg==0x1a || reg==0x1b || reg==0x1c || reg==0x38 || reg==0x6b || reg==0x6c)
            registers[reg]=(byte)value;
    }
    @Override protected void afterRead(int register) { if(register==0x3a) registers[0x3a]=0; }
    public boolean sleeping() { return (peek(0x6b)&0x40)!=0; }
    public int[] saveConfiguration() { return new int[]{peek(0x19),peek(0x1a),peek(0x1b),peek(0x1c),peek(0x38),peek(0x6b),peek(0x6c)}; }
    public void restoreConfiguration(int[] values) {
        if(values.length!=7)return;
        int[] addresses={0x19,0x1a,0x1b,0x1c,0x38,0x6b,0x6c};
        for(int i=0;i<7;i++)writeRegister(addresses[i],values[i]&255);
    }
    public void sample(double axG,double ayG,double azG,double gx,double gy,double gz,double temperature) {
        if(sleeping()) return;
        double accel=16384.0/(1 << ((peek(0x1c)>>3)&3));
        double gyro=new double[]{131,65.5,32.8,16.4}[(peek(0x1b)>>3)&3];
        int disabled=peek(0x6c);
        be16(0x3b,(disabled&0x20)!=0?0:axG*accel); be16(0x3d,(disabled&0x10)!=0?0:ayG*accel); be16(0x3f,(disabled&8)!=0?0:azG*accel);
        be16(0x41,(peek(0x6b)&8)!=0?0:(temperature-36.53)*340);
        be16(0x43,(disabled&4)!=0?0:gx*gyro); be16(0x45,(disabled&2)!=0?0:gy*gyro); be16(0x47,(disabled&1)!=0?0:gz*gyro);
        registers[0x3a]=1;
    }
}
