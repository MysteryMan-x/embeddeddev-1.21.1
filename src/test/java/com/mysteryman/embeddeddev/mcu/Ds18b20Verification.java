package com.mysteryman.embeddeddev.mcu;

import java.util.Arrays;
import java.util.List;

final class Ds18b20Verification {
    private final BoardHardware hardware = new BoardHardware();
    private final Ds18b20 sensor = new Ds18b20(0x123456789abcL);

    private Ds18b20Verification() {
        hardware.clockHz(12_000_000);
        hardware.wireDevices(List.of(), List.of(new BoardHardware.WiredGpio(sensor, List.of(10))));
    }

    private void waitUs(int micros) { hardware.advanceCycles(micros); }
    private void line(boolean high) { hardware.writePort(1, high ? 255 : 251); }
    private int level() { return (hardware.readPort(1, 255) >>> 2) & 1; }
    private void reset() {
        line(false); waitUs(500); line(true); waitUs(70);
        require(level() == 0, "Presence pulse");
        waitUs(410); require(level() == 1, "Presence releases the bus");
    }
    private void write(int value) { for (int i = 0; i < 8; i++) writeBit((value >>> i) & 1); }
    private void writeBit(int value) {
        line(false); waitUs(value == 0 ? 60 : 6); line(true); waitUs(value == 0 ? 10 : 64);
    }
    private int readBit() {
        line(false); waitUs(6); line(true); waitUs(9);
        int result = level(); waitUs(55); return result;
    }
    private byte[] read(int count) {
        byte[] data = new byte[count];
        for (int i = 0; i < count; i++) for (int bit = 0; bit < 8; bit++) data[i] |= (byte) (readBit() << bit);
        return data;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    static void run() {
        var test = new Ds18b20Verification();
        test.reset(); test.write(0x33);
        byte[] rom = test.read(8);
        require(Arrays.equals(rom, test.sensor.rom()) && Ds18b20.crc(rom, 8) == 0, "ROM and Dallas CRC");
        test.reset(); test.write(0xf0);
        for (int bit = 0; bit < 64; bit++) {
            int expected = (rom[bit / 8] >>> (bit & 7)) & 1;
            require(test.readBit() == expected && test.readBit() == (expected ^ 1), "Search ROM bit and complement");
            test.writeBit(expected);
        }
        test.write(0xbe);
        require(Ds18b20.crc(test.read(9), 9) == 0, "Search-selected device accepts function commands");

        test.sensor.temperature(-10.125);
        test.reset(); test.write(0xcc); test.write(0x44);
        require(test.readBit() == 0, "12-bit conversion is initially busy");
        test.waitUs(750_000);
        require(test.readBit() == 1, "Conversion completes on MCU time");
        test.reset(); test.write(0xcc); test.write(0xbe);
        byte[] scratch = test.read(9);
        require((short) ((scratch[0] & 255) | (scratch[1] << 8)) == -162 && Ds18b20.crc(scratch, 9) == 0,
                "Signed 12-bit temperature and scratchpad CRC");
        test.reset(); test.write(0x55);
        for (byte value : rom) test.write(value & 255);
        test.write(0x4e); test.write(40); test.write(5); test.write(0x1f);
        test.reset(); test.write(0xcc); test.write(0x48);
        var restored = new Ds18b20(1);
        restored.restore(test.sensor.save());
        require(Arrays.equals(restored.rom(), rom), "ROM and EEPROM survive a save");
        test.sensor.temperature(200);
        test.reset(); test.write(0xcc); test.write(0x44); test.waitUs(93_750);
        test.reset(); test.write(0xcc); test.write(0xbe);
        scratch = test.read(9);
        require((short) ((scratch[0] & 255) | (scratch[1] << 8)) == 2000 && (scratch[4] & 255) == 0x1f,
                "Nine-bit conversion timing and 125 C sensor range");
        System.out.println("DS18B20 verification passed: timing, ROM search, CRC, negative temperatures, resolution and persistence");
    }
}
