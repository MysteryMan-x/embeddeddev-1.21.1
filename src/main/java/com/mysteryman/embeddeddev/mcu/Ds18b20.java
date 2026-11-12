package com.mysteryman.embeddeddev.mcu;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Standard-speed, externally powered 1-Wire thermometer. All bus timing is in microseconds. */
public final class Ds18b20 implements GpioDevice {
    private enum Phase { ROM, MATCH, FUNCTION, WRITE, READ, SEARCH, BUSY, IGNORE }
    private static final List<String> TERMINALS = List.of("DQ");
    private final byte[] rom = new byte[8], scratch = new byte[9];
    private final int[] staged = new int[3];
    private Phase phase = Phase.IGNORE;
    private boolean high = true, readSlot, matched, converting;
    private long now, lowSince, presenceStart = -1, presenceEnd, driveUntil;
    private long conversionEnd, gameTick, conversionTick;
    private double sourceCelsius = 20;
    private int incoming, bits, matchedBytes, readBit, searchBit, searchStep, written;
    private byte[] outgoing = new byte[0];
    private int savedHigh = 75, savedLow = 70, savedConfig = 0x7f, pendingTemperature;

    public Ds18b20() { this(ThreadLocalRandom.current().nextLong()); }
    public Ds18b20(long serial) {
        rom[0] = 0x28;
        for (int i = 1; i < 7; i++) rom[i] = (byte) (serial >>> ((i - 1) * 8));
        rom[7] = (byte) crc(rom, 7);
        scratch[0] = 0x50; scratch[1] = 5;
        recall();
        scratch[5] = (byte) 0xff; scratch[6] = 0x0c; scratch[7] = 0x10;
        updateCrc();
    }

    @Override public String label() { return "DS18B20"; }
    @Override public List<String> terminals() { return TERMINALS; }
    public void temperature(double celsius) { if (Double.isFinite(celsius)) sourceCelsius = Math.clamp(celsius, -55, 125); }
    public double celsius() { return (short) ((scratch[0] & 255) | (scratch[1] << 8)) / 16.0; }
    public byte[] rom() { return rom.clone(); }

    @Override public void time(long nanoseconds) {
        long micros = nanoseconds / 1000;
        if (micros < now) {
            conversionEnd = micros + Math.max(0, conversionEnd - now);
            reset(high ? 1 : 0);
        }
        now = micros;
        finishConversion();
    }

    public void tick(long tick) { gameTick = tick; finishConversion(); }

    @Override public int pullLow() {
        finishConversion();
        return now < driveUntil || presenceStart >= 0 && now >= presenceStart && now < presenceEnd ? 1 : 0;
    }

    @Override public void reset(int levels) {
        high = (levels & 1) != 0;
        phase = Phase.IGNORE;
        lowSince = now;
        presenceStart = -1;
        driveUntil = 0;
        readSlot = false;
        bits = incoming = matchedBytes = written = 0;
    }

    @Override public void update(int levels) {
        boolean nextHigh = (levels & 1) != 0;
        if (nextHigh == high) return;
        high = nextHigh;
        if (!high) {
            lowSince = now;
            driveUntil = 0;
            readSlot = phase == Phase.READ || phase == Phase.SEARCH && searchStep < 2 || phase == Phase.BUSY;
            if (readSlot) {
                int bit = nextReadBit();
                if (bit == 0) driveUntil = now + 60;
            }
            return;
        }
        long duration = now - lowSince;
        if (duration >= 480) {
            phase = Phase.ROM;
            presenceStart = now + 30;
            presenceEnd = presenceStart + 120;
            driveUntil = 0;
            incoming = bits = matchedBytes = written = 0;
            matched = true;
        } else if (!readSlot) receiveBit(duration < 15 ? 1 : 0);
        readSlot = false;
    }

    private int nextReadBit() {
        if (phase == Phase.BUSY) return converting ? 0 : 1;
        if (phase == Phase.SEARCH) {
            int bit = (rom[searchBit >>> 3] >>> (searchBit & 7)) & 1;
            return searchStep++ == 0 ? bit : bit ^ 1;
        }
        int bit = (outgoing[readBit >>> 3] >>> (readBit & 7)) & 1;
        if (++readBit == outgoing.length * 8) phase = Phase.IGNORE;
        return bit;
    }

    private void receiveBit(int bit) {
        if (phase == Phase.SEARCH) {
            int expected = (rom[searchBit >>> 3] >>> (searchBit & 7)) & 1;
            if (bit != expected) phase = Phase.IGNORE;
            else if (++searchBit == 64) phase = Phase.FUNCTION;
            searchStep = 0;
            return;
        }
        if (phase != Phase.ROM && phase != Phase.MATCH && phase != Phase.FUNCTION && phase != Phase.WRITE) return;
        incoming |= bit << bits;
        if (++bits != 8) return;
        int value = incoming;
        incoming = bits = 0;
        switch (phase) {
            case ROM -> romCommand(value);
            case MATCH -> {
                matched &= value == (rom[matchedBytes++] & 255);
                if (matchedBytes == 8) phase = matched ? Phase.FUNCTION : Phase.IGNORE;
            }
            case FUNCTION -> function(value);
            case WRITE -> {
                staged[written++] = value;
                if (written == 3) {
                    scratch[2] = (byte) staged[0]; scratch[3] = (byte) staged[1];
                    scratch[4] = (byte) ((staged[2] & 0x60) | 0x1f);
                    updateCrc(); phase = Phase.IGNORE;
                }
            }
            default -> {}
        }
    }

    private void romCommand(int command) {
        switch (command) {
            case 0xcc -> phase = Phase.FUNCTION;
            case 0x55 -> phase = Phase.MATCH;
            case 0x33 -> transmit(rom);
            case 0xf0, 0xec -> {
                if (command == 0xec && !alarm()) { phase = Phase.IGNORE; return; }
                phase = Phase.SEARCH; searchBit = searchStep = 0;
            }
            default -> phase = Phase.IGNORE;
        }
    }

    private boolean alarm() {
        int temperature = (int) Math.floor(celsius());
        return temperature >= scratch[2] || temperature <= scratch[3];
    }

    private void function(int command) {
        switch (command) {
            case 0x44 -> {
                int resolution = 9 + ((scratch[4] >>> 5) & 3);
                long micros = 750_000L >> (12 - resolution);
                pendingTemperature = ((int) Math.round(sourceCelsius * 16)) & ~((1 << (12 - resolution)) - 1);
                converting = true;
                conversionEnd = now + micros;
                conversionTick = gameTick + (micros + 49_999) / 50_000;
                phase = Phase.BUSY;
            }
            case 0xbe -> { updateCrc(); transmit(scratch); }
            case 0x4e -> { phase = Phase.WRITE; written = 0; }
            case 0x48 -> {
                savedHigh = scratch[2]; savedLow = scratch[3]; savedConfig = scratch[4] & 255;
                phase = Phase.IGNORE;
            }
            case 0xb8 -> { recall(); updateCrc(); phase = Phase.BUSY; }
            case 0xb4 -> transmit(new byte[]{(byte) 0xff});
            default -> phase = Phase.IGNORE;
        }
    }

    private void finishConversion() {
        if (!converting || now < conversionEnd && gameTick < conversionTick) return;
        scratch[0] = (byte) pendingTemperature;
        scratch[1] = (byte) (pendingTemperature >> 8);
        updateCrc(); converting = false;
    }

    private void transmit(byte[] bytes) { outgoing = bytes.clone(); readBit = 0; phase = Phase.READ; }
    private void recall() { scratch[2] = (byte) savedHigh; scratch[3] = (byte) savedLow; scratch[4] = (byte) savedConfig; }
    private void updateCrc() { scratch[8] = (byte) crc(scratch, 8); }

    public static int crc(byte[] bytes, int length) {
        int crc = 0;
        for (int i = 0; i < length; i++) {
            int value = bytes[i] & 255;
            for (int bit = 0; bit < 8; bit++) {
                int mix = (crc ^ value) & 1;
                crc >>>= 1;
                if (mix != 0) crc ^= 0x8c;
                value >>>= 1;
            }
        }
        return crc;
    }

    public byte[] save() {
        var data = new byte[14];
        System.arraycopy(rom, 0, data, 0, 8);
        data[8] = (byte) savedHigh; data[9] = (byte) savedLow; data[10] = (byte) savedConfig;
        data[11] = scratch[0]; data[12] = scratch[1]; data[13] = scratch[4];
        return data;
    }

    public void restore(byte[] data) {
        if (data.length != 14 || data[0] != 0x28 || crc(data, 7) != (data[7] & 255)) return;
        System.arraycopy(data, 0, rom, 0, 8);
        savedHigh = data[8]; savedLow = data[9]; savedConfig = (data[10] & 0x60) | 0x1f;
        recall(); scratch[0] = data[11]; scratch[1] = data[12]; scratch[4] = (byte) ((data[13] & 0x60) | 0x1f);
        converting = false;
        updateCrc(); reset(1);
    }
}
