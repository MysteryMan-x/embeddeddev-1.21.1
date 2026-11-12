package com.mysteryman.embeddeddev.mcu;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** DS1302 clock/RAM with a game-time clock and an LSB-first three-wire interface. */
public final class Ds1302 implements GpioDevice {
    private static final int CE = 1, CLOCK = 2, DATA = 4;
    private static final long EPOCH = LocalDateTime.of(2000, 1, 1, 0, 0).toEpochSecond(ZoneOffset.UTC);
    private static final long CENTURY_SECONDS = LocalDateTime.of(2100, 1, 1, 0, 0).toEpochSecond(ZoneOffset.UTC) - EPOCH;
    private static final List<String> TERMINALS = List.of("CE", "SCLK", "IO");
    private final byte[] ram = new byte[31];
    private final int[] staged = new int[31];
    private long tick, baseTick, baseSeconds = EPOCH, revision;
    private int baseWeekday = 6, charger;
    private boolean halted, protectedWrites, twelveHour;
    private int levels, command, incoming, bits, written, outgoingBit;
    private boolean reading, driveLow, selected;
    private int[] snapshot = new int[9];

    @Override public String label() { return "DS1302"; }
    @Override public List<String> terminals() { return TERMINALS; }
    @Override public int pullLow() { return driveLow ? DATA : 0; }
    public void setTick(long gameTick) { tick = gameTick; }
    public long revision() { return revision; }
    public String timeLabel() { return time().toString().replace('T', ' '); }

    @Override public void reset(int newLevels) {
        levels = newLevels;
        selected = driveLow = reading = false;
        command = incoming = bits = written = outgoingBit = 0;
    }

    @Override public void update(int newLevels) {
        boolean wasSelected = (levels & CE) != 0;
        boolean wasHigh = (levels & CLOCK) != 0;
        levels = newLevels;
        if ((levels & CE) == 0) {
            if (selected) commit();
            reset(newLevels);
            return;
        }
        boolean high = (levels & CLOCK) != 0;
        if (!wasSelected) {
            if (high) return;
            selected = true;
            snapshot = registers();
        } else if (selected && !wasHigh && high) {
            driveLow = false;
            if (!reading) receiveBit();
        } else if (selected && wasHigh && !high && reading) {
            driveLow = ((readByte(outgoingBit / 8) >>> (outgoingBit & 7)) & 1) == 0;
            outgoingBit = (outgoingBit + 1) % (transferLength() * 8);
        }
    }

    private void receiveBit() {
        if ((levels & DATA) != 0) incoming |= 1 << bits;
        if (++bits != 8) return;
        if (command == 0) {
            // Use a nonzero sentinel for an invalid command until CE is released.
            command = (incoming & 0x80) != 0 ? incoming : 0x7f;
            reading = command != 0x7f && (command & 1) != 0;
        } else if (command != 0x7f && written < transferLength()) {
            staged[written++] = incoming;
        }
        bits = incoming = 0;
    }

    private boolean ramCommand() { return (command & 0x40) != 0; }
    private int register() { return (command >>> 1) & 31; }
    private boolean burst() { return register() == 31; }
    private int transferLength() { return burst() ? (ramCommand() ? 31 : 8) : 1; }

    private int readByte(int byteIndex) {
        int address = burst() ? byteIndex : register();
        if (ramCommand()) return address < ram.length ? ram[address] & 0xff : 0xff;
        return address < snapshot.length ? snapshot[address] : 0xff;
    }

    private void commit() {
        if (reading || written == 0 || command == 0x7f) return;
        int address = register();
        if (ramCommand()) {
            if (protectedWrites) return;
            int start = burst() ? 0 : address;
            for (int i = 0; i < written; i++) ram[start + i] = (byte) staged[i];
            revision++;
        } else if (address == 7) {
            protectedWrites = (staged[0] & 0x80) != 0;
            revision++;
        } else if (!protectedWrites && address == 8) {
            charger = staged[0];
            revision++;
        } else if (!protectedWrites && (address < 7 || burst() && written == 8)) {
            int[] next = registers();
            if (burst()) System.arraycopy(staged, 0, next, 0, 8);
            else next[address] = staged[0];
            setCalendar(next);
        }
    }

    private long elapsedSeconds() { return halted ? 0 : Math.max(0, tick - baseTick) / 20; }
    private LocalDateTime time() {
        long seconds = EPOCH + Math.floorMod(baseSeconds - EPOCH + elapsedSeconds(), CENTURY_SECONDS);
        return LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.UTC);
    }

    private int[] registers() {
        LocalDateTime now = time();
        long days = (baseSeconds % 86400 + elapsedSeconds()) / 86400;
        int weekday = 1 + (int) Math.floorMod(baseWeekday - 1L + days, 7);
        int hour = now.getHour();
        int hourRegister = twelveHour ? 0x80 | (hour >= 12 ? 0x20 : 0) | bcd((hour + 11) % 12 + 1) : bcd(hour);
        return new int[]{bcd(now.getSecond()) | (halted ? 0x80 : 0), bcd(now.getMinute()),
                hourRegister, bcd(now.getDayOfMonth()), bcd(now.getMonthValue()), weekday,
                bcd(now.getYear() % 100), protectedWrites ? 0x80 : 0, charger};
    }

    private void setCalendar(int[] next) {
        boolean mode12 = (next[2] & 0x80) != 0;
        int hour = decimal(next[2] & (mode12 ? 0x1f : 0x3f));
        if (mode12) {
            if (hour < 1 || hour > 12) return;
            hour = hour % 12 + ((next[2] & 0x20) != 0 ? 12 : 0);
        }
        int second = decimal(next[0] & 0x7f), minute = decimal(next[1] & 0x7f);
        int day = decimal(next[3] & 0x3f), month = decimal(next[4] & 0x1f), year = decimal(next[6]);
        int weekday = next[5] & 7;
        if (hour < 0 || hour > 23 || second < 0 || second > 59 || minute < 0 || minute > 59
                || day < 1 || day > 31 || month < 1 || month > 12 || year < 0 || weekday == 0) return;
        LocalDate first = LocalDate.of(2000 + year, month, 1);
        LocalDate date = first.withDayOfMonth(Math.min(day, first.lengthOfMonth()));
        baseSeconds = date.atTime(hour, minute, second).toEpochSecond(ZoneOffset.UTC);
        baseTick = tick;
        baseWeekday = weekday;
        halted = (next[0] & 0x80) != 0;
        protectedWrites = (next[7] & 0x80) != 0;
        twelveHour = mode12;
        revision++;
    }

    private static int bcd(int value) { return ((value / 10) << 4) | (value % 10); }
    private static int decimal(int value) {
        return (value & 15) > 9 || (value >>> 4) > 9 ? -1 : (value >>> 4) * 10 + (value & 15);
    }

    public long[] save() {
        int flags = (halted ? 1 : 0) | (protectedWrites ? 2 : 0) | (twelveHour ? 4 : 0);
        return new long[]{baseSeconds, baseTick, baseWeekday, flags, charger};
    }

    public byte[] saveRam() { return ram.clone(); }

    public void restore(long[] state, byte[] savedRam) {
        if (state.length == 5) {
            baseSeconds = Math.clamp(state[0], EPOCH, EPOCH + CENTURY_SECONDS - 1);
            baseTick = Math.max(0, state[1]);
            baseWeekday = (int) Math.clamp(state[2], 1, 7);
            halted = (state[3] & 1) != 0;
            protectedWrites = (state[3] & 2) != 0;
            twelveHour = (state[3] & 4) != 0;
            charger = (int) state[4] & 0xff;
        }
        if (savedRam.length == ram.length) System.arraycopy(savedRam, 0, ram, 0, ram.length);
    }
}
