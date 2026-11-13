package com.mysteryman.embeddeddev.mcu;

import java.util.Arrays;

/** Six signed RPM channels, one per adjacent face. Commands become live only on commit. */
public final class MotorDriver extends RegisterI2cDevice {
    private final int[] staged=new int[6], output=new int[6];
    private int address=0x60;
    private boolean armed;
    private long commits, activity;
    public MotorDriver() { publish(0,false); }
    @Override public int address() { return address; }
    public void address(int value) { if(value<0x60||value>0x67) throw new IllegalArgumentException("Motor address must be 60H–67H"); address=value; }
    public boolean armed() { return armed; }
    public long commits() { return commits; }
    public long activity() { return activity; }
    public int output(int face) { return output[face]; }
    public void disarm() { armed=false; Arrays.fill(staged,0); Arrays.fill(output,0); }
    @Override protected void writeRegister(int reg,int value) {
        if(reg==1) { if(value==0xa5) { armed=true; activity++; } else disarm(); }
        else if(reg>=0x20 && reg<0x2c) {
            int i=(reg-0x20)/2;
            staged[i]=(reg&1)==0 ? (staged[i]&0xff00)|value : (staged[i]&255)|(value<<8);
        } else if(reg==0x2c && value==0xa5) {
            for(int i=0;i<6;i++) output[i]=Math.clamp((short)staged[i],-256,256);
            commits++; activity++;
        }
    }
    public void publish(int mask,boolean controllerRunning) {
        registers[0]=0x4d; registers[1]=(byte)(armed?1:0); registers[2]++; registers[3]=1;
        registers[4]=(byte)mask; registers[5]=(byte)(controllerRunning?1:0);
        for(int i=0;i<6;i++) le16(0x20+i*2,output[i]);
    }
}
