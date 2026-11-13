package com.mysteryman.embeddeddev.mcu;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Checks complete appendix drivers by executing freshly compiled C demo binaries. */
public final class DriverSourceVerification {
    private static int symbol(String name,String symbol)throws Exception{
        String map=Files.readString(Path.of("build/driver-examples/demo_"+name+".map"));
        var match=Pattern.compile("(?m)^\\s+([0-9A-F]{8})\\s+_"+symbol+"\\s").matcher(map);
        if(!match.find())throw new AssertionError("Missing SDCC symbol "+symbol);return Integer.parseInt(match.group(1),16);
    }
    private static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        for(String name:List.of("mpu6050","altimeter","motor_driver","pcf8591","environment","gpio_panel","seven_segment","gpio_redstone","ds1302","ds18b20","ssd1306")){
            var hw=new BoardHardware();hw.clockHz(12_000_000);var links=new ArrayList<BoardHardware.WiredI2c>();var gpio=new ArrayList<BoardHardware.WiredGpio>();
            var imu=new Mpu6050();var alt=new Altimeter();var motor=new MotorDriver();var pcf=new Pcf8591();var env=new I2cSensor();var oled=new Ssd1306();
            var rtc=new Ds1302();var temperature=new Ds18b20(123);var digit=new SevenSegment();
            temperature.temperature(-10.125);pcf.sampleRedstone(0,5);env.values[0]=7;env.values[1]=15;
            I2cDevice i2c=switch(name){case "mpu6050"->imu;case "altimeter"->alt;case "motor_driver"->motor;case "pcf8591"->pcf;case "environment"->env;case "ssd1306"->oled;default->null;};
            if(i2c!=null)links.add(new BoardHardware.WiredI2c(i2c,14,15));
            if(name.equals("ds1302"))gpio.add(new BoardHardware.WiredGpio(rtc,List.of(24,25,26)));
            if(name.equals("ds18b20"))gpio.add(new BoardHardware.WiredGpio(temperature,List.of(31)));
            if(name.equals("seven_segment"))gpio.add(new BoardHardware.WiredGpio(digit,List.of(16,17,18,19,20,21,22,23)));
            if(name.equals("gpio_panel"))gpio.add(new BoardHardware.WiredGpio(new GpioDevice(){public String label(){return "keys";}public List<String> terminals(){return List.of("1","2","3","4","5","6","7","8","9");}public int pullLow(){return 0x101;}},List.of(0,1,2,3,4,5,6,7,16)));
            hw.wireDevices(links,gpio);var cpu=new Mcs51(hw);
            cpu.install(IntelHex.parse(Files.readString(Path.of("build/driver-examples/demo_"+name+".ihx"))).code());
            for(int i=0;i<130;i++){imu.sample(0,0,1,10,0,0,25);alt.sample(i,66,0,false);motor.publish(32,true);cpu.runCycles(20000);}
            int ok=cpu.inspectRam(symbol(name,"demo_ok")),done=cpu.inspectRam(symbol(name,"demo_done")),address=symbol(name,"demo_value");
            int value=(short)(cpu.inspectRam(address)|(cpu.inspectRam(address+1)<<8));
            require(ok==1&&done==0xA5,"Compiled appendix demo failed: "+name+" ok="+ok+" done="+done+" value="+value);
            int expected=switch(name){case "mpu6050"->1310;case "altimeter"->6600;case "motor_driver"->32;case "pcf8591"->85;case "environment"->7;case "gpio_panel"->257;case "seven_segment"->255;case "ds1302"->1234;case "ds18b20"->-162;default->1;};
            require(value==expected,"Driver result mismatch: "+name+" got="+value+" expected="+expected);
            if(name.equals("pcf8591"))require(pcf.redstoneOutput()==5,"Compiled ADC/DAC pipeline");
            if(name.equals("ds1302"))require(cpu.inspectRam(symbol(name,"demo_aux"))==0xA7,"Compiled DS1302 RAM transfer");
            if(name.equals("ds18b20"))require(cpu.inspectRam(symbol(name,"demo_aux"))==7,"Compiled 1-Wire ROM/read/search/CRC/resolution commands");
            if(name.equals("ssd1306"))require(oled.enabled&&(oled.ram[0]&255)==127&&(oled.ram[1]&255)==8,"Compiled OLED text and independent framebuffer");
            if(name.equals("seven_segment"))require(digit.segments()==255,"Compiled eight-segment GPIO mapping");
            System.out.println("Appendix C driver verified: "+name);
        }
    }
}
