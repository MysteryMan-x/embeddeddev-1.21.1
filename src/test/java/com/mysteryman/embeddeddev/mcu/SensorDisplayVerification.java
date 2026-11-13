package com.mysteryman.embeddeddev.mcu;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.function.BooleanSupplier;

/** Execute real bundled sensor HEX and decode the OLED pixels into ASCII. */
final class SensorDisplayVerification {
    private static final int[][] FONT=new int[64][5];
    private static void require(boolean v,String s){if(!v)throw new AssertionError(s);}
    static String line(Ssd1306 oled,int page){
        var out=new StringBuilder();
        for(int col=0;col<21;col++){
            int found='?';
            for(int c=0;c<64;c++){
                boolean matches=true;for(int i=0;i<5;i++)if((oled.ram[page*128+col*6+i]&255)!=FONT[c][i])matches=false;
                if(matches){found=c+32;break;}
            }out.append((char)found);
        }return out.toString().stripTrailing();
    }
    private static void runUntil(Mcs51 cpu,BooleanSupplier condition,String message){
        for(int i=0;i<250;i++){cpu.runCycles(20000);if(condition.getAsBoolean())return;}
        throw new AssertionError(message+" PC="+cpu.pc());
    }
    static void run()throws Exception{
        var match=Pattern.compile("\\{([0-9,]+)\\}").matcher(Files.readString(Path.of("examples/drivers/ed_font5x7.c")));
        for(int c=0;c<64;c++){require(match.find(),"Font glyph");String[] v=match.group(1).split(",");for(int i=0;i<5;i++)FONT[c][i]=Integer.parseInt(v[i]);}
        for(String name:List.of("i2c_sensor","pcf8591_mirror","ds18b20_read","mpu6050_oled","altimeter_oled")){
            var hw=new BoardHardware();hw.clockHz(12_000_000);var oled=new Ssd1306();var env=new I2cSensor();var pcf=new Pcf8591();var temp=new Ds18b20(87);var imu=new Mpu6050();var alt=new Altimeter();
            env.values[0]=11;env.values[1]=15;env.values[2]=3;env.values[3]=42;pcf.sampleRedstone(0,7);pcf.sampleRedstone(1,12);temp.temperature(-5.25);alt.homeCm(125);alt.sample(1,-2.5,-.75,false);
            var links=new ArrayList<BoardHardware.WiredI2c>();links.add(new BoardHardware.WiredI2c(oled,14,15));
            I2cDevice device=switch(name){case "i2c_sensor"->env;case "pcf8591_mirror"->pcf;case "mpu6050_oled"->imu;case "altimeter_oled"->alt;default->null;};
            if(device!=null)links.add(new BoardHardware.WiredI2c(device,14,15));
            var gpio=name.equals("ds18b20_read")?List.of(new BoardHardware.WiredGpio(temp,List.of(10))):List.<BoardHardware.WiredGpio>of();
            hw.wireDevices(links,gpio);var cpu=new Mcs51(hw);cpu.install(IntelHex.parse(Files.readString(Path.of("examples",name+".hex"))).code());
            if(name.equals("mpu6050_oled")){
                runUntil(cpu,()->!imu.sleeping(),"MPU wakes");imu.sample(-.5,.25,1,-12.3,20,0,25);
            }
            String expected=switch(name){case "i2c_sensor"->"REDSTONE 11 /15";case "pcf8591_mirror"->"AIN0 119 /255";case "ds18b20_read"->"TEMP -5.25 C";case "mpu6050_oled"->"AZ 1000 MG";default->"ABS -250 CM";};
            int page=switch(name){case "ds18b20_read","mpu6050_oled"->3;default->2;};
            runUntil(cpu,()->line(oled,page).equals(expected),name+" displays actual data, got "+line(oled,page));
            require(oled.enabled,"OLED enabled for "+name);
            if(name.equals("pcf8591_mirror")){
                for(int i=0;i<40000;i++){cpu.runCycles(32);require(pcf.redstoneOutput()==7,"Four-channel ADC scanning must keep DAC enabled without output flicker");}
            }
            if(name.equals("ds18b20_read"))require(cpu.inspectRam(0x3f)==0xA5,"CRC verified before display");
            if(name.equals("i2c_sensor")){
                env.values[0]=0;runUntil(cpu,()->line(oled,2).equals("REDSTONE 0 /15"),"Changed environment updates and pads old digits");
                require((cpu.portLatch(1)&4)==0,"LED follows zero input");
            }
            if(device!=null){links.removeIf(l->l.device()==device);hw.wireDevices(links,gpio);}
            else hw.wireDevices(links,List.of());
            runUntil(cpu,()->line(oled,2).contains("OFFLINE"),name+" reports disconnect instead of stale readings");
            if(device!=null)links.add(new BoardHardware.WiredI2c(device,14,15));
            hw.wireDevices(links,gpio);
            String recovered=name.equals("i2c_sensor")?"REDSTONE 0 /15":expected;
            runUntil(cpu,()->line(oled,page).equals(recovered),name+" retries reconnect with live data");
            // Missing display must not stop sensor work; reinserting the same screen reinitializes it.
            links.removeIf(l->l.device()==oled);hw.wireDevices(links,gpio);cpu.runCycles(800000);
            oled.enabled=false;links.add(new BoardHardware.WiredI2c(oled,14,15));hw.wireDevices(links,gpio);
            runUntil(cpu,()->oled.enabled,"OLED reconnect "+name);
            System.out.println("Sensor HEX + OLED verified: "+name);
        }
    }
}
