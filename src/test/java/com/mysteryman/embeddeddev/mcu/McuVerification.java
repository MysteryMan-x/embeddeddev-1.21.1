package com.mysteryman.embeddeddev.mcu;

import java.nio.file.*;
import java.util.*;

/** Executable checks with no game boot or third-party testing dependency. */
public final class McuVerification {
    private static int checks;
    private static void eq(long expected, long actual, String label) {
        checks++; if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
    private static void ok(boolean result, String label) { eq(1, result ? 1 : 0, label); }
    private static byte[] image(int... program) {
        byte[] result = new byte[IntelHex.FLASH_SIZE];
        for (int i = 0; i < program.length; i++) result[i] = (byte) program[i];
        return result;
    }
    private static Mcs51 cpu(int... program) { Mcs51 c = new Mcs51(new Mcs51.Bus() {}); c.install(image(program)); return c; }
    private static void steps(Mcs51 c, int count) { for (int i = 0; i < count; i++) c.step(); }
    private static String record(int address, int type, int... values) {
        int sum = values.length + (address >>> 8) + (address & 255) + type;
        StringBuilder r = new StringBuilder(String.format(":%02X%04X%02X", values.length, address, type));
        for (int v : values) { r.append(String.format("%02X", v)); sum += v; }
        return r.append(String.format("%02X\n", -sum & 255)).toString();
    }
    private static void badHex(String text) {
        try { IntelHex.parse(text); throw new AssertionError("Accepted invalid HEX"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void hex() {
        String data = record(0, 0, 0x75, 0x90, 0, 0x80, 0xFE), eof = record(0, 1);
        var parsed = IntelHex.parse("\uFEFF" + data + eof);
        eq(5, parsed.bytesWritten(), "HEX byte count"); eq(255, parsed.code()[100] & 255, "erased flash");
        badHex(data); badHex(eof); badHex(data + eof + data); badHex(data.replace("75", "76") + eof);
        badHex(record(8191, 0, 1, 2) + eof); badHex(record(0, 4, 0, 1) + data + eof);
        badHex(data + record(0, 0, 1) + eof); badHex(":" + "0".repeat(25000));
        badHex(":00000000GG\n" + eof); badHex(record(1, 1));
        eq(5, IntelHex.parse(record(0, 2, 0, 0) + data + record(0, 5, 0, 0, 0, 0) + eof).bytesWritten(), "extended records");
        eq(5, IntelHex.parse(data + data + eof).bytesWritten(), "identical overlapping records");
        byte[] full = new byte[IntelHex.FLASH_SIZE]; new Random(8052).nextBytes(full);
        String canonical = IntelHex.encode(full);
        ok(canonical.length() < IntelHex.MAX_PAYLOAD_LENGTH, "full flash fits network payload");
        ok(Arrays.equals(full, IntelHex.parse(canonical).code()), "canonical HEX round trip");
        badHex(" ".repeat(IntelHex.MAX_TEXT_LENGTH + 1));
    }
    private static void opcodes() {
        for (int op = 0; op < 256; op++) {
            Mcs51 c = cpu(op, 0, 0);
            if (op == 0xA5) {
                try { c.step(); throw new AssertionError("A5 must fault"); }
                catch (IllegalStateException expected) { checks++; }
            } else { int used = c.step(); ok(used >= 1 && used <= 4, "opcode " + op); }
        }
        Mcs51 c = cpu(0x75, 0xD0, 8, 0x78, 42, 0xE8, 0xF5, 0x20, 0x75, 0xD0, 0, 0x78, 99);
        steps(c, 6); eq(42, c.inspectRam(8), "register bank 1"); eq(99, c.inspectRam(0), "register bank 0");
        eq(42, c.inspectRam(0x20), "direct RAM");
        c = cpu(0x78, 0x90, 0x76, 0x55, 0x75, 0x90, 0xAA, 0xE6); steps(c, 4);
        eq(0x55, c.accumulator(), "upper indirect RAM is separate from SFR"); eq(0xAA, c.portLatch(1), "port SFR");
        c = cpu(0xD2, 0, 0xA2, 0, 0x92, 0x90, 0xB2, 0); steps(c, 4);
        eq(0, c.inspectRam(0x20), "bit RAM"); eq(255, c.portLatch(1), "MOV bit,C");
        c = cpu(0x12, 0, 6, 0x74, 0x11, 0, 0x74, 0x22, 0x22); steps(c, 4);
        eq(0x11, c.accumulator(), "LCALL RET"); eq(7, c.inspect(0x81), "balanced stack");
        c = cpu(0x80, 0xFE); steps(c, 3); eq(0, c.pc(), "signed relative jump");
        byte[] pageImage = image(0x02, 7, 0xFF); pageImage[0x7FF] = 1; pageImage[0x800] = 0x23;
        c.install(pageImage); steps(c, 2); eq(0x823, c.pc(), "AJMP at page boundary");
        c = cpu(0x90, 0x12, 0x34, 0x74, 77, 0xF0, 0xE4, 0xE0); steps(c, 5); eq(77, c.accumulator(), "MOVX DPTR");
        c = cpu(0x90, 0x12, 0x34, 0x75, 0xA2, 1, 0x90, 0x56, 0x78, 0x75, 0xA2, 0); steps(c, 4);
        eq(0x12, c.inspect(0x83), "dual DPTR high"); eq(0x34, c.inspect(0x82), "dual DPTR low");
        c = cpu(0x74, 0x81, 0x13, 0x33, 0xC4); steps(c, 4); eq(0x18, c.accumulator(), "rotates and swap");
        c = cpu(0x74, 0x99, 0x24, 1, 0xD4); steps(c, 3); eq(0, c.accumulator(), "decimal adjust"); eq(1, c.inspect(0xD0) >>> 7, "BCD carry");
    }
    private static void arithmetic() {
        // Exhaustively check byte arithmetic and both carry inputs against integer arithmetic.
        Mcs51 c = cpu(0);
        for (int op : new int[]{0x24, 0x34, 0x94}) for (int carry = 0; carry < 2; carry++)
            for (int a = 0; a < 256; a++) for (int b = 0; b < 256; b++) {
                c.install(image(0x75, 0xD0, carry << 7, 0x74, a, op, b)); steps(c, 3);
                int inputCarry = op == 0x24 ? 0 : carry;
                int result = op == 0x94 ? a - b - inputCarry : a + b + inputCarry;
                int signed = op == 0x94 ? (byte) a - (byte) b - inputCarry : (byte) a + (byte) b + inputCarry;
                int psw = c.inspect(0xD0);
                eq(result & 255, c.accumulator(), "arithmetic value");
                eq(result < 0 || result > 255 ? 1 : 0, psw >>> 7, "carry / borrow");
                eq(signed < -128 || signed > 127 ? 1 : 0, (psw >>> 2) & 1, "signed overflow");
                eq(Integer.bitCount(result & 255) & 1, psw & 1, "parity");
                boolean ac = op == 0x94 ? (a & 15) < (b & 15) + inputCarry : (a & 15) + (b & 15) + inputCarry > 15;
                eq(ac ? 1 : 0, (psw >>> 6) & 1, "auxiliary carry");
            }
    }
    private static void timers() {
        Mcs51 c = cpu(0x75, 0x89, 1, 0x75, 0x8C, 255, 0x75, 0x8A, 254, 0xD2, 0x8C, 0);
        steps(c, 5); eq(1, (c.inspect(0x88) >>> 5) & 1, "Timer 0 overflow"); eq(0, c.inspect(0x8A), "Timer 0 wrap");
        c = cpu(0x75, 0x89, 2, 0x75, 0x8C, 100, 0x75, 0x8A, 255, 0xD2, 0x8C); steps(c, 4);
        eq(100, c.inspect(0x8A), "Timer 0 auto reload");
        c = cpu(0x75, 0xCA, 0x12, 0x75, 0xCB, 0x34, 0x75, 0xCC, 255, 0x75, 0xCD, 255, 0x75, 0xC8, 4);
        steps(c, 5); eq(0x13, c.inspect(0xCC), "Timer 2 auto reload plus remaining cycle"); eq(0x34, c.inspect(0xCD), "Timer 2 high");
        byte[] program = image(0x02, 0, 0x30);
        int[] main = {0x75, 0x89, 1, 0x75, 0x8C, 255, 0x75, 0x8A, 250, 0x75, 0xA8, 0x82, 0xD2, 0x8C, 0x80, 0xFE};
        for (int i = 0; i < main.length; i++) program[0x30 + i] = (byte) main[i];
        program[0x0B] = 0x05; program[0x0C] = 0x20; program[0x0D] = 0x32;
        c.install(program); c.runCycles(80); eq(1, c.inspectRam(0x20), "timer ISR executes"); eq(7, c.inspect(0x81), "RETI restores stack");
        c = cpu(0x75, 0x89, 2, 0x75, 0x8C, 0, 0xD2, 0x8C, 0x75, 0x87, 1); steps(c, 4);
        int pc = c.pc(), before = c.inspect(0x8A); c.runCycles(10); eq(pc, c.pc(), "idle stops instructions"); ok(c.inspect(0x8A) > before, "idle keeps timer");
    }
    private static FaceBinding pins(FaceBinding.Kind kind, int... channels) {
        return new FaceBinding(Arrays.stream(channels)
                .mapToObj(n -> new FaceBinding.Pin(kind.channelName(n), kind, n)).toList());
    }
    private static FaceBinding modulePins(int sda, int scl) {
        return new FaceBinding(List.of(new FaceBinding.Pin("scl", FaceBinding.Kind.GPIO, scl),
                new FaceBinding.Pin("SDA", FaceBinding.Kind.GPIO, sda)));
    }
    private static void peripherals() {
        BoardHardware h = new BoardHardware(); Mcs51 c = new Mcs51(h);
        Ssd1306 oled = new Ssd1306();
        h.bind(5, modulePins(14, 15)); h.attach(5, oled);
        h.bind(0, pins(FaceBinding.Kind.DIGITAL_OUTPUT, 1, 2));
        c.install(image(0x90, 0xF0, 2, 0x74, 9, 0xF0, 0xE0, 0x90, 0xF0, 0x13, 0xF0, 0x90, 0xF1, 0, 0x74, 0x81, 0xF0));
        steps(c, 9); eq(0x81, oled.ram[0] & 255, "independent OLED framebuffer");
        eq(15, h.output(0, true), "selected GPIOs"); eq(0, h.output(0, false), "stopped outputs off");
        h.writeExternal(0xF020, 2); eq(0, oled.ram[0], "OLED clear"); ok(!oled.enabled, "OLED off");
        c.install(image(0x75, 0x99, 65, 0x75, 0x98, 16, 0xE5, 0x99)); steps(c, 2); c.receive(66); c.step();
        eq(66, c.accumulator(), "UART receive"); eq('A', h.serial().charAt(0), "UART transmit");
        Mcs51.Bus lowPins = new Mcs51.Bus() { public int readPort(int p, int latch) { return 0; } };
        c = new Mcs51(lowPins); c.install(image(0x43, 0x90, 1, 0xE5, 0x90)); c.step();
        eq(255, c.portLatch(1), "RMW reads latch"); c.step(); eq(0, c.accumulator(), "MOV reads pins");
    }
    private static void persistence() {
        Mcs51 a = cpu(0x04, 0x80, 0xFD); a.runCycles(1001);
        Mcs51 b = cpu(0x04, 0x80, 0xFD); b.restore(a.save());
        a.runCycles(1000); b.runCycles(1000);
        ok(Arrays.equals(a.save(), b.save()), "save/resume is deterministic including cycle credit");
        try { b.restore(new byte[10]); throw new AssertionError("accepted bad state"); } catch (IllegalArgumentException expected) { checks++; }
    }
    private static void examples() throws Exception {
        for (String name : List.of("redstone_blink", "pcf8591_mirror", "oled_pattern", "oled_hello", "i2c_oled_hello", "i2c_sensor")) {
            BoardHardware h = new BoardHardware(); Mcs51 c = new Mcs51(h);
            Ssd1306 oled = new Ssd1306(); I2cSensor sensor = new I2cSensor();
            h.bind(5, modulePins(14, 15));
            sensor.values[0] = 11;
            Pcf8591 converter = new Pcf8591(); converter.sampleRedstone(0, 7);
            h.attach(5, name.equals("i2c_sensor") ? sensor : name.equals("pcf8591_mirror") ? converter : oled);
            c.install(IntelHex.parse(Files.readString(Path.of("examples", name + ".hex"))).code());
            c.runCycles(46080 * 8);
            if (name.equals("pcf8591_mirror")) {
                eq(119, c.inspectRam(0x30), "PCF8591 firmware reads a fresh ADC sample after dummy byte");
                eq(7, converter.redstoneOutput(), "external PCF8591 mirrors input through its own DAC");
            }
            if (name.equals("oled_pattern")) eq(0x55, oled.ram[1023] & 255, "diagnostic bitmap");
            if (name.equals("oled_hello") || name.equals("i2c_oled_hello")) {
                ok(oled.enabled, name + " turns on display"); eq(127, oled.ram[128 + 7] & 255, name + " readable E glyph");
                eq(0xA5, c.inspectRam(0x30), name + " completes framebuffer upload");
            }
            if (name.equals("i2c_sensor")) {
                eq(11, c.inspectRam(0x30), "sensor firmware stores raw I2C sample in IDATA");
                ok((c.portLatch(1) & 4) != 0, "sensor firmware drives GPIO indicator for nonzero input");
            }
            if (name.equals("redstone_blink")) {
                int p = c.portLatch(1); c.runCycles(46080 * 11); ok(p != c.portLatch(1), "example blink toggles");
            }
        }
    }
    private static final class BitBang {
        private final BoardHardware h;
        private final int sda, scl;
        private final int[] latches = {255,255,255,255};
        BitBang(BoardHardware h, int sda, int scl) { this.h=h; this.sda=sda; this.scl=scl; }
        void pin(int pin, boolean high) {
            int bank=pin>>>3, mask=1<<(pin&7); latches[bank]=high?latches[bank]|mask:latches[bank]&~mask;
            h.writePort(bank,latches[bank]);
        }
        boolean sda() { return (h.readPort(sda>>>3,latches[sda>>>3]) & (1<<(sda&7)))!=0; }
        void start() { pin(sda,true);pin(scl,true);pin(sda,false);pin(scl,false); }
        void stop() { pin(sda,false);pin(scl,true);pin(sda,true); }
        boolean write(int v) {
            for(int bit=7;bit>=0;bit--){pin(sda,((v>>>bit)&1)!=0);pin(scl,true);pin(scl,false);}
            pin(sda,true);pin(scl,true);boolean ack=!sda();pin(scl,false);return ack;
        }
        int read(boolean ack) {
            int value=0;pin(sda,true);
            for(int i=0;i<8;i++){pin(scl,true);value=(value<<1)|(sda()?1:0);pin(scl,false);}
            pin(sda,!ack);pin(scl,true);pin(scl,false);pin(sda,true);return value;
        }
    }
    private static void connectorsAndI2c() {
        BoardHardware h = new BoardHardware();
        for(int f=0;f<6;f++) eq(0,h.output(f,true),"new connectors are unbound");
        h.bind(0, pins(FaceBinding.Kind.DIGITAL_INPUT, 0, 9, 31));
        h.sample(new int[]{0,0,0,0,12,0});
        eq(254,h.readPort(0,255),"input arbitrary P0.0");eq(253,h.readPort(1,255),"input arbitrary P1.1");eq(127,h.readPort(3,255),"input arbitrary P3.7");
        h.bind(4, pins(FaceBinding.Kind.GPIO, 0, 7));h.reset();eq(2,h.binding(4).pins().size(),"CPU reset preserves wiring");
        FaceBinding b = modulePins(2, 27);
        eq(2, b.gpio("sda"), "name matches independent of casing and list order");
        FaceBinding unicode = new FaceBinding(List.of(new FaceBinding.Pin("数字,输出", FaceBinding.Kind.GPIO, 3)));
        ok(unicode.equals(FaceBinding.parse(unicode.wireText())), "Unicode and punctuation in pin names survive serialization");
        ok(b.equals(FaceBinding.parse(b.wireText())), "binding serialization");
        for(int[] invalid:new int[][]{{5,1,1},{99},{3,8},{1},{0,1}}) {
            try{FaceBinding.fromLegacy(invalid);throw new AssertionError("Accepted invalid binding");}catch(IllegalArgumentException expected){checks++;}
        }
        FaceBinding migrated = FaceBinding.restore("ANALOG_INPUT,0,VIN\nGPIO,8,LED\nANALOG_OUTPUT,1,OUT\nGPIO,15,SCL");
        eq(2, migrated.pins().size(), "Only obsolete ADC/DAC pins are removed from saves");
        eq(8, migrated.pins().get(0).wireId(), "GPIO wire IDs survive migration");
        eq(15, migrated.pins().get(1).wireId(), "Remaining GPIO wire IDs survive migration");
        ok(FaceBinding.hasRemovedAnalog("ANALOG_OUTPUT,1,OUT"), "A DAC-only save requests migration");
        ok(FaceBinding.fromLegacy(new int[]{4, 0, 7}).pins().isEmpty(), "Original numeric DAC configuration is retired");
        ok(FaceBinding.fromLegacy(new int[]{3, 0, 7}).pins().isEmpty(), "Original numeric ADC configuration is retired");
        for (String invalid : List.of("ANALOG_INPUT,0,VIN", "ANALOG_OUTPUT,1,OUT", "GPIO,2,SDA\nGPIO,27,sda", "GPIO,2,SDA\nDIGITAL_OUTPUT,2,LED", "GPIO,32,SCL")) {
            try { FaceBinding.parse(invalid); throw new AssertionError("Accepted ambiguous or invalid pin"); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        Ssd1306 oled=new Ssd1306();h.bind(5,b);h.attach(5,oled);BitBang bus=new BitBang(h,2,27);
        bus.start();ok(!bus.write(0x7A),"unmatched I2C address NACK");bus.stop();
        bus.start();ok(bus.write(0x78),"OLED address ACK");ok(bus.write(0x00),"control byte ACK");
        for(int v:new int[]{0xAF,0x20,0,0x21,5,6,0x22,1,2})ok(bus.write(v),"command ACK");bus.stop();
        bus.start();ok(bus.write(0x78),"second address ACK");bus.write(0x40);bus.write(0x81);bus.write(0x42);bus.write(0x24);bus.stop();
        ok(oled.enabled,"I2C display ON");eq(0x81,oled.ram[133]&255,"I2C column/page position");eq(0x42,oled.ram[134]&255,"horizontal increment");eq(0x24,oled.ram[261]&255,"horizontal page wrap");
        Ssd1306 second = new Ssd1306();h.bind(3,b);h.attach(3,second);
        bus.start();bus.write(0x78);bus.write(0);bus.write(0xA7);bus.stop();ok(oled.inverse&&second.inverse,"same pins fan out to multiple module connectors");
        I2cSensor sensor=new I2cSensor();sensor.values[0]=13;sensor.values[1]=7;h.attach(5,sensor);h.attach(3,null);
        bus.start();ok(bus.write(0x90),"sensor write address");bus.write(0);bus.start();ok(bus.write(0x91),"repeated START read address");
        eq(13,bus.read(true),"sensor first byte and controller ACK");eq(7,bus.read(false),"sensor second byte and controller NACK");bus.stop();
        h.attach(5,null);bus.start();ok(!bus.write(0x90),"detached module no longer ACKs");bus.stop();
        eq(255,h.readExternal(0xF100),"no hidden OLED framebuffer on the motherboard");
    }
    private static void disassembly() {
        for (int opcode = 0; opcode <= 255; opcode++) {
            var instruction = Mcs51Disassembler.decode(new byte[]{(byte) opcode, 0, 0}, 0, 0);
            ok(instruction.length() >= 1 && instruction.length() <= 3, "valid decoded length");
        }
        ok(Mcs51Disassembler.decode(new byte[]{(byte) 0xd2, (byte) 0x97}, 0, 0).text().equals("SETB P1.7"),
                "port bit symbol");
        ok(Mcs51Disassembler.decode(new byte[]{(byte) 0x85, 0x20, (byte) 0x90}, 0, 0).text().equals("MOV P1,20h"),
                "MOV direct displays destination first");
        ok(Mcs51Disassembler.decode(new byte[]{(byte) 0x80, (byte) 0xfe}, 0, 0xffff).text().equals("SJMP 0FFFFh"),
                "relative jump wraps at 16 bits");
        ok(Mcs51Disassembler.decode(new byte[]{0x41, 0x34}, 0, 0x7ff).text().equals("AJMP 0A34h"),
                "AJMP uses page of the next instruction");
        ok(Mcs51Disassembler.decode(new byte[]{(byte) 0x90, (byte) 0xf0, 0x10}, 0, 0).text().equals("MOV DPTR,#0F010h"),
                "16-bit immediate uses correct byte order");
    }

    private static void pcf8591Bus() {
        BoardHardware hardware = new BoardHardware();
        Pcf8591 adc = new Pcf8591();
        hardware.bind(5, modulePins(2, 27));
        hardware.attach(5, adc);
        adc.sampleRedstone(0, 3);
        adc.sampleRedstone(1, 12);
        BitBang bus = new BitBang(hardware, 2, 27);
        bus.start();
        ok(bus.write(0x90), "PCF8591 write address");
        ok(bus.write(0x44) && bus.write(0x99), "PCF8591 control and DAC bytes");
        bus.start();
        ok(bus.write(0x91), "PCF8591 repeated START");
        eq(0x80, bus.read(true), "PCF8591 dummy conversion byte");
        eq(51, bus.read(true), "PCF8591 samples channel 0");
        eq(204, bus.read(false), "PCF8591 auto-increments to channel 1");
        bus.stop();
        eq(9, adc.redstoneOutput(), "PCF8591 DAC quantization");
        adc.nextAddress();
        bus.start();
        ok(!bus.write(0x90), "PCF8591 releases old address");
        bus.stop();
        bus.start();
        ok(bus.write(0x92) && bus.write(0x30), "PCF8591 responds at new address");
        bus.start();
        ok(bus.write(0x93), "PCF8591 differential read address");
        bus.read(true);
        eq(0x80, bus.read(false), "PCF8591 differential sample saturates as signed 8-bit data");
        bus.stop();
        eq(0, adc.redstoneOutput(), "PCF8591 AOUT disable");
    }

    private static final class ThreeWire {
        private final BoardHardware hardware;
        ThreeWire(BoardHardware hardware, Ds1302 clock) {
            this.hardware = hardware;
            hardware.bind(2, new FaceBinding(List.of(new FaceBinding.Pin("CE", FaceBinding.Kind.GPIO, 8),
                    new FaceBinding.Pin("SCLK", FaceBinding.Kind.GPIO, 9),
                    new FaceBinding.Pin("IO", FaceBinding.Kind.GPIO, 10))));
            hardware.writePort(1, 4);
            hardware.attachGpio(2, clock);
        }
        private void writeByte(int value) {
            for (int bit = 0; bit < 8; bit++) {
                int pins = 1 | (((value >>> bit) & 1) << 2);
                hardware.writePort(1, pins);
                hardware.writePort(1, pins | 2);
                hardware.writePort(1, pins);
            }
        }
        void write(int command, int... bytes) {
            hardware.writePort(1, 5);
            writeByte(command);
            for (int value : bytes) writeByte(value);
            hardware.writePort(1, 4);
        }
        int[] read(int command, int length) {
            hardware.writePort(1, 5);
            writeByte(command);
            hardware.writePort(1, 5);
            int[] data = new int[length];
            for (int i = 0; i < data.length; i++) {
                for (int bit = 0; bit < 8; bit++) {
                    data[i] |= ((hardware.readPort(1, 5) >>> 2) & 1) << bit;
                    hardware.writePort(1, 7);
                    hardware.writePort(1, 5);
                }
            }
            hardware.writePort(1, 4);
            return data;
        }
    }

    private static void ds1302Bus() {
        Ds1302 clock = new Ds1302();
        ThreeWire bus = new ThreeWire(new BoardHardware(), clock);
        bus.write(0xbe, 0x58, 0x59, 0x23, 0x28, 2, 3, 0x24, 0);
        clock.setTick(40);
        ok(Arrays.equals(new int[]{0, 0, 0, 0x29, 2, 4, 0x24, 0}, bus.read(0xbf, 8)),
                "DS1302 calendar rolls into leap day with a coherent burst");
        bus.write(0x8e, 0x80);
        bus.write(0xc0, 0x55);
        eq(0, bus.read(0xc1, 1)[0], "DS1302 write protection covers RAM");
        bus.write(0x8e, 0);
        bus.write(0xfe, 0x12, 0x34);
        ok(Arrays.equals(new int[]{0x12, 0x34}, bus.read(0xff, 2)), "DS1302 partial RAM burst persists");
        bus.write(0xbe, 0x55);
        eq(0, bus.read(0x81, 1)[0], "Incomplete calendar burst is ignored");
        bus.write(0x80, 0x95);
        clock.setTick(400);
        eq(0x95, bus.read(0x81, 1)[0], "DS1302 CH halts the clock");
        Ds1302 restored = new Ds1302();
        restored.restore(clock.save(), clock.saveRam());
        restored.setTick(800);
        ThreeWire restoredBus = new ThreeWire(new BoardHardware(), restored);
        eq(0x95, restoredBus.read(0x81, 1)[0], "DS1302 clock survives a save");
        eq(0x34, restoredBus.read(0xc3, 1)[0], "DS1302 RAM survives a save");
    }

    private static void formerAnalogIsRam() {
        // Exercise real MOVX instructions and saved XRAM, rather than only checking the bus fallback.
        var program = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 16; i++) program.addAll(List.of(0x90, 0xF0, i < 8 ? i : i + 8, 0x74, 0xA0 + i, 0xF0));
        for (int i = 0; i < 16; i++) program.addAll(List.of(0x90, 0xF0, i < 8 ? i : i + 8, 0xE0, 0xF5, 0x30 + i));
        byte[] code = image(program.stream().mapToInt(Integer::intValue).toArray());
        Mcs51 cpu = new Mcs51(new BoardHardware()); cpu.install(code); steps(cpu, 48);
        Mcs51 restored = new Mcs51(new BoardHardware()); restored.install(code); restored.restore(cpu.save());
        steps(cpu, 48); steps(restored, 48);
        for (int i = 0; i < 16; i++) {
            eq(0xA0 + i, cpu.inspectRam(0x30 + i), "Former ADC/DAC bytes read their own XRAM writes");
            eq(0xA0 + i, restored.inspectRam(0x30 + i), "Former ADC/DAC bytes persist as ordinary XDATA");
        }
    }

    public static void main(String[] args) throws Exception {
        Ds18b20Verification.run();
        hex(); opcodes(); arithmetic(); timers(); peripherals(); persistence(); connectorsAndI2c(); examples(); disassembly();
        formerAnalogIsRam(); pcf8591Bus();
        ds1302Bus();
        System.out.println("MCU verification passed: " + checks + " checks");
    }
}
