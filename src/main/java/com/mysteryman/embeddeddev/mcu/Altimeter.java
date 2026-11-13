package com.mysteryman.embeddeddev.mcu;

/** ALT-01: mod-defined height sensor, independent of any external mod or MCU memory map. */
public final class Altimeter extends RegisterI2cDevice {
    private int homeCm, currentCm;
    @Override public int address() { return 0x76; }
    @Override protected void writeRegister(int reg,int value) { if(reg==0x12 && value==0xa5) homeCm=currentCm; }
    public int homeCm() { return homeCm; }
    public void homeCm(int value) { homeCm=value; }
    public void sample(int sequence,double absoluteY,double verticalSpeed,boolean movingBody) {
        currentCm=(int)Math.round(absoluteY*100);
        registers[0]=0x41; registers[1]=(byte)(1|(movingBody?2:0)); registers[2]=(byte)sequence; registers[3]=1;
        le32(4,currentCm); le16(8,(int)Math.round(verticalSpeed*100)); le32(10,homeCm); le32(14,currentCm-homeCm);
    }
}
