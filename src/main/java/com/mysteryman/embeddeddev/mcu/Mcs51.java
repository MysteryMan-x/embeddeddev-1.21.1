package com.mysteryman.embeddeddev.mcu;

import java.io.*;
import java.util.Arrays;

/** Minecraft-independent 8052 interpreter with all 255 defined base opcodes. */
public final class Mcs51 {
    public interface Bus {
        default int readPort(int port, int latch) { return latch; }
        default void writePort(int port, int latch) {}
        default void resetPorts(int[] latches) { for (int p = 0; p < 4; p++) writePort(p, latches[p]); }
        default int readExternal(int address) { return -1; }
        default void writeExternal(int address, int value) {}
        default void transmit(int value) {}
        default void advanceCycles(int cycles) {}
    }
    private final Bus bus;
    private byte[] code = new byte[IntelHex.FLASH_SIZE];
    private final int[] ram = new int[256], sfr = new int[128];
    private final byte[] xram = new byte[65536];
    private int pc, alternateDptr, previousP3 = 255, previousP1 = 255;
    private int interruptDepth, interruptLevel = -1, cycleCredit;
    private final int[] interruptLevels = new int[4];
    private long cycles;

    public Mcs51(Bus bus) { this.bus = bus; reset(); }
    public void install(byte[] image) {
        if (image.length != IntelHex.FLASH_SIZE) throw new IllegalArgumentException("Invalid flash size");
        code = image.clone(); reset();
    }
    public void reset() {
        Arrays.fill(ram, 0); Arrays.fill(sfr, 0); Arrays.fill(xram, (byte) 0);
        sfr[1] = 7;
        for (int p = 0; p < 4; p++) sfr[p * 16] = 255;
        pc = alternateDptr = interruptDepth = cycleCredit = 0;
        interruptLevel = -1; previousP3 = previousP1 = 255; cycles = 0;
        bus.resetPorts(new int[]{255, 255, 255, 255});
    }
    public int pc() { return pc; }
    public long cycles() { return cycles; }
    public int accumulator() { return raw(0xE0); }
    public int portLatch(int p) { return raw(0x80 + p * 16); }
    public int inspect(int a) { return read(a); }
    public int inspectRam(int a) { return ram[a & 255]; }
    public void runCycles(int budget) {
        cycleCredit += budget;
        try { while (cycleCredit > 0) cycleCredit -= step(); }
        catch (IllegalStateException e) { cycleCredit = 0; throw e; }
    }
    public void receive(int v) { if ((raw(0x98) & 16) != 0) { sfr[0x19] = v & 255; flag(0x98, 0, true); } }

    public int step() {
        samplePins();
        if ((raw(0x87) & 2) != 0) { cycles++; return 1; }
        int irq = serviceInterrupt();
        if (irq != 0) { tick(irq); return irq; }
        if ((raw(0x87) & 1) != 0) { tick(1); return 1; }
        int opAddress = pc, op = fetch(), n = op & 7, used = 1;
        if ((op & 31) == 1) { int low = fetch(); pc = (pc & 0xF800) | ((op & 0xE0) << 3) | low; used = 2; }
        else if ((op & 31) == 17) { int low = fetch(); call((pc & 0xF800) | ((op & 0xE0) << 3) | low); used = 2; }
        else if ((op & 0xF8) == 0x08) reg(n, reg(n) + 1);
        else if ((op & 0xF8) == 0x18) reg(n, reg(n) - 1);
        else if (op >= 0x24 && op <= 0x2F) add(operand(op), false);
        else if (op >= 0x34 && op <= 0x3F) add(operand(op), true);
        else if (op >= 0x44 && op <= 0x4F) acc(accumulator() | operand(op));
        else if (op >= 0x54 && op <= 0x5F) acc(accumulator() & operand(op));
        else if (op >= 0x64 && op <= 0x6F) acc(accumulator() ^ operand(op));
        else if ((op & 0xF8) == 0x78) reg(n, fetch());
        else if ((op & 0xF8) == 0x88) { write(fetch(), reg(n)); used = 2; }
        else if (op >= 0x94 && op <= 0x9F) subtract(operand(op));
        else if ((op & 0xF8) == 0xA8) { reg(n, read(fetch())); used = 2; }
        else if ((op & 0xF8) == 0xB8) { compare(reg(n), fetch()); used = 2; }
        else if ((op & 0xF8) == 0xC8) { int v = reg(n); reg(n, accumulator()); acc(v); }
        else if ((op & 0xF8) == 0xD8) { reg(n, reg(n) - 1); branch(reg(n) != 0); used = 2; }
        else if ((op & 0xF8) == 0xE8) acc(reg(n));
        else if ((op & 0xF8) == 0xF8) reg(n, accumulator());
        else switch (op) {
            case 0x00 -> {}
            case 0x02 -> { pc = fetch16(); used = 2; }
            case 0x03 -> acc((accumulator() >>> 1) | (accumulator() << 7));
            case 0x04 -> acc(accumulator() + 1);
            case 0x05 -> { int d = fetch(); write(d, raw(d) + 1); }
            case 0x06, 0x07 -> indirect(op, indirect(op) + 1);
            case 0x10 -> { int b = fetch(); boolean set = readBit(b, true); if (set) writeBit(b, false); branch(set); used = 2; }
            case 0x12 -> { int target = fetch16(); call(target); used = 2; }
            case 0x13 -> { int a = accumulator(); acc((a >>> 1) | (carry() << 7)); carry(a & 1); }
            case 0x14 -> acc(accumulator() - 1);
            case 0x15 -> { int d = fetch(); write(d, raw(d) - 1); }
            case 0x16, 0x17 -> indirect(op, indirect(op) - 1);
            case 0x20 -> { int b = fetch(); branch(readBit(b, false)); used = 2; }
            case 0x22 -> { ret(); used = 2; }
            case 0x23 -> acc((accumulator() << 1) | (accumulator() >>> 7));
            case 0x30 -> { int b = fetch(); branch(!readBit(b, false)); used = 2; }
            case 0x32 -> { ret(); if (interruptDepth > 0) interruptLevel = interruptLevels[--interruptDepth]; used = 2; }
            case 0x33 -> { int a = accumulator(); acc((a << 1) | carry()); carry(a >>> 7); }
            case 0x40 -> { branch(carry() != 0); used = 2; }
            case 0x42, 0x52, 0x62 -> { int d = fetch(); write(d, logic(op, raw(d), accumulator())); }
            case 0x43, 0x53, 0x63 -> { int d = fetch(); write(d, logic(op, raw(d), fetch())); used = 2; }
            case 0x50 -> { branch(carry() == 0); used = 2; }
            case 0x60 -> { branch(accumulator() == 0); used = 2; }
            case 0x70 -> { branch(accumulator() != 0); used = 2; }
            case 0x72 -> { int b = fetch(); carry(carry() | (readBit(b, false) ? 1 : 0)); used = 2; }
            case 0x73 -> { pc = (dptr() + accumulator()) & 65535; used = 2; }
            case 0x74 -> acc(fetch());
            case 0x75 -> { int d = fetch(); write(d, fetch()); used = 2; }
            case 0x76, 0x77 -> indirect(op, fetch());
            case 0x80 -> { branch(true); used = 2; }
            case 0x82 -> { int b = fetch(); carry(carry() & (readBit(b, false) ? 1 : 0)); used = 2; }
            case 0x83 -> { acc(codeAt(pc + accumulator())); used = 2; }
            case 0x84 -> { int a = accumulator(), b = raw(0xF0); carry(0); flag(0xD0, 2, b == 0);
                if (b != 0) { acc(a / b); write(0xF0, a % b); } used = 4; }
            case 0x85 -> { int src = fetch(), dst = fetch(); write(dst, read(src)); used = 2; }
            case 0x86, 0x87 -> { write(fetch(), indirect(op)); used = 2; }
            case 0x90 -> { dptr(fetch16()); used = 2; }
            case 0x92 -> { writeBit(fetch(), carry() != 0); used = 2; }
            case 0x93 -> { acc(codeAt(dptr() + accumulator())); used = 2; }
            case 0xA0 -> { int b = fetch(); carry(carry() | (readBit(b, false) ? 0 : 1)); used = 2; }
            case 0xA2 -> carry(readBit(fetch(), false) ? 1 : 0);
            case 0xA3 -> { dptr(dptr() + 1); used = 2; }
            case 0xA4 -> { int v = accumulator() * raw(0xF0); acc(v); write(0xF0, v >>> 8);
                carry(0); flag(0xD0, 2, v > 255); used = 4; }
            case 0xA5 -> throw new IllegalStateException(String.format("Reserved opcode A5 at %04X", opAddress));
            case 0xA6, 0xA7 -> { indirect(op, read(fetch())); used = 2; }
            case 0xB0 -> { int b = fetch(); carry(carry() & (readBit(b, false) ? 0 : 1)); used = 2; }
            case 0xB2 -> { int b = fetch(); writeBit(b, !readBit(b, true)); }
            case 0xB3 -> carry(carry() ^ 1);
            case 0xB4 -> { compare(accumulator(), fetch()); used = 2; }
            case 0xB5 -> { compare(accumulator(), read(fetch())); used = 2; }
            case 0xB6, 0xB7 -> { compare(indirect(op), fetch()); used = 2; }
            case 0xC0 -> { push(read(fetch())); used = 2; }
            case 0xC2 -> writeBit(fetch(), false);
            case 0xC3 -> carry(0);
            case 0xC4 -> acc((accumulator() << 4) | (accumulator() >>> 4));
            case 0xC5 -> { int d = fetch(), v = read(d); write(d, accumulator()); acc(v); }
            case 0xC6, 0xC7 -> { int v = indirect(op); indirect(op, accumulator()); acc(v); }
            case 0xD0 -> { int d = fetch(), v = pop(); write(d, v); used = 2; }
            case 0xD2 -> writeBit(fetch(), true);
            case 0xD3 -> carry(1);
            case 0xD4 -> { int a = accumulator(), c = carry();
                if ((a & 15) > 9 || (raw(0xD0) & 64) != 0) a += 6;
                if (a > 0x9F || c != 0) a += 0x60; carry(c != 0 || a > 255 ? 1 : 0); acc(a); }
            case 0xD5 -> { int d = fetch(), v = (raw(d) - 1) & 255; write(d, v); branch(v != 0); used = 2; }
            case 0xD6, 0xD7 -> { int a = accumulator(), v = indirect(op);
                acc((a & 0xF0) | (v & 15)); indirect(op, (v & 0xF0) | (a & 15)); }
            case 0xE0 -> { acc(readX(dptr())); used = 2; }
            case 0xE2, 0xE3 -> { acc(readX((portLatch(2) << 8) | reg(op & 1))); used = 2; }
            case 0xE4 -> acc(0);
            case 0xE5 -> acc(read(fetch()));
            case 0xE6, 0xE7 -> acc(indirect(op));
            case 0xF0 -> { writeX(dptr(), accumulator()); used = 2; }
            case 0xF2, 0xF3 -> { writeX((portLatch(2) << 8) | reg(op & 1), accumulator()); used = 2; }
            case 0xF4 -> acc(~accumulator());
            case 0xF5 -> write(fetch(), accumulator());
            case 0xF6, 0xF7 -> indirect(op, accumulator());
            default -> throw new IllegalStateException("Undecoded opcode " + op);
        }
        flag(0xD0, 0, (Integer.bitCount(accumulator()) & 1) != 0); tick(used); return used;
    }
    private int fetch() { int v = codeAt(pc); pc = (pc + 1) & 65535; return v; }
    private int fetch16() { int high = fetch(); return high * 256 + fetch(); }
    public int codeAt(int a) { a &= 65535; return a < code.length ? code[a] & 255 : 255; }
    private int raw(int a) { return a < 128 ? ram[a] : sfr[a - 128]; }
    private int read(int a) { return a >= 0x80 && a <= 0xB0 && (a & 15) == 0 ? bus.readPort((a - 0x80) / 16, raw(a)) & 255 : raw(a); }
    private void write(int a, int v) {
        v &= 255;
        if (a < 128) ram[a] = v;
        else {
            if (a == 0xA2 && ((raw(a) ^ v) & 1) != 0) { int old = dptr(); dptr(alternateDptr); alternateDptr = old; }
            if (a == 0x99) { bus.transmit(v); flag(0x98, 1, true); return; }
            sfr[a - 128] = v;
            if (a >= 0x80 && a <= 0xB0 && (a & 15) == 0) bus.writePort((a - 0x80) / 16, v);
        }
    }
    private int reg(int n) { return ram[(raw(0xD0) & 24) + n]; }
    private void reg(int n, int v) { ram[(raw(0xD0) & 24) + n] = v & 255; }
    private int indirect(int op) { return ram[reg(op & 1)]; }
    private void indirect(int op, int v) { ram[reg(op & 1)] = v & 255; }
    private int operand(int op) { return switch (op & 15) { case 4 -> fetch(); case 5 -> read(fetch()); case 6, 7 -> indirect(op); default -> reg(op & 7); }; }
    private void acc(int v) { sfr[0x60] = v & 255; }
    private int dptr() { return raw(0x83) * 256 + raw(0x82); }
    private void dptr(int v) { sfr[2] = v & 255; sfr[3] = (v >>> 8) & 255; }
    private int carry() { return raw(0xD0) >>> 7; }
    private void carry(int v) { flag(0xD0, 7, v != 0); }
    private void flag(int a, int bit, boolean v) { sfr[a - 128] = v ? raw(a) | (1 << bit) : raw(a) & ~(1 << bit); }
    private int bitAddress(int b) { return b < 128 ? 0x20 + (b >>> 3) : b & 0xF8; }
    private boolean readBit(int b, boolean latch) { int a = bitAddress(b); return (((latch ? raw(a) : read(a)) >>> (b & 7)) & 1) != 0; }
    private void writeBit(int b, boolean v) { int a = bitAddress(b), mask = 1 << (b & 7); write(a, v ? raw(a) | mask : raw(a) & ~mask); }
    private void branch(boolean taken) { int offset = (byte) fetch(); if (taken) pc = (pc + offset) & 65535; }
    private void push(int v) { int sp = (raw(0x81) + 1) & 255; sfr[1] = sp; ram[sp] = v & 255; }
    private int pop() { int sp = raw(0x81), v = ram[sp]; sfr[1] = (sp - 1) & 255; return v; }
    private void call(int target) { push(pc); push(pc >>> 8); pc = target; }
    private void ret() { int high = pop(); pc = (high << 8) | pop(); }
    private int logic(int op, int a, int b) { return switch (op & 0xF0) { case 0x40 -> a | b; case 0x50 -> a & b; default -> a ^ b; }; }
    private void compare(int a, int b) { carry(a < b ? 1 : 0); branch(a != b); }
    private void add(int b, boolean withCarry) {
        int a = accumulator(), c = withCarry ? carry() : 0, sum = a + b + c;
        flag(0xD0, 6, (a & 15) + (b & 15) + c > 15);
        flag(0xD0, 2, ((~(a ^ b) & (a ^ sum)) & 128) != 0); carry(sum > 255 ? 1 : 0); acc(sum);
    }
    private void subtract(int b) {
        int a = accumulator(), c = carry(), result = a - b - c;
        flag(0xD0, 6, (a & 15) < (b & 15) + c);
        flag(0xD0, 2, (((a ^ b) & (a ^ result)) & 128) != 0); carry(result < 0 ? 1 : 0); acc(result);
    }
    private int readX(int a) { int v = bus.readExternal(a); return v < 0 ? xram[a] & 255 : v & 255; }
    private void writeX(int a, int v) { xram[a] = (byte) v; bus.writeExternal(a, v); }
    private void samplePins() {
        int p3 = read(0xB0), p1 = read(0x90), tcon = raw(0x88);
        for (int i = 0; i < 2; i++) {
            boolean low = (p3 & (4 << i)) == 0, falling = (previousP3 & (4 << i)) != 0 && low;
            if ((tcon & (1 << (i * 2))) == 0) flag(0x88, i * 2 + 1, low);
            else if (falling) flag(0x88, i * 2 + 1, true);
            if ((previousP3 & (16 << i)) != 0 && (p3 & (16 << i)) == 0) timer01(i, true);
        }
        if ((previousP1 & 1) != 0 && (p1 & 1) == 0 && (raw(0xC8) & 2) != 0) timer2();
        if ((previousP1 & 2) != 0 && (p1 & 2) == 0 && (raw(0xC8) & 12) == 12) {
            if ((raw(0xC8) & 1) == 0) { sfr[0x4C] = raw(0xCA); sfr[0x4D] = raw(0xCB); }
            else { sfr[0x4A] = raw(0xCC); sfr[0x4B] = raw(0xCD); }
            flag(0xC8, 6, true);
        }
        previousP3 = p3; previousP1 = p1;
    }
    private void tick(int amount) {
        bus.advanceCycles(amount);
        cycles += amount;
        for (int c = 0; c < amount; c++) {
            timer01(0, false); timer01(1, false);
            if ((raw(0x89) & 3) == 3 && (raw(0x88) & 0x40) != 0) {
                int v = (raw(0x8C) + 1) & 255; sfr[0x0C] = v; if (v == 0) flag(0x88, 7, true);
            }
            if ((raw(0xC8) & 2) == 0) timer2();
        }
    }
    private void timer01(int timer, boolean pulse) {
        int modeReg = raw(0x89) >>> (timer * 4), mode = modeReg & 3;
        if (timer == 1 && ((raw(0x89) & 3) == 3 || mode == 3)) return;
        if (((modeReg & 4) != 0) != pulse || (raw(0x88) & (16 << (timer * 2))) == 0) return;
        if ((modeReg & 8) != 0 && (previousP3 & (4 << timer)) == 0) return;
        int tl = 0x8A + timer, th = 0x8C + timer, value; boolean overflow;
        switch (mode) {
            case 0 -> { value = ((raw(th) << 5) | (raw(tl) & 31)) + 1; overflow = value > 8191;
                sfr[th - 128] = (value >>> 5) & 255; sfr[tl - 128] = (raw(tl) & 0xE0) | (value & 31); }
            case 1 -> { value = ((raw(th) << 8) | raw(tl)) + 1; overflow = value > 65535;
                sfr[th - 128] = (value >>> 8) & 255; sfr[tl - 128] = value & 255; }
            default -> { value = raw(tl) + 1; overflow = value > 255; sfr[tl - 128] = overflow && mode == 2 ? raw(th) : value & 255; }
        }
        if (overflow) flag(0x88, 5 + timer * 2, true);
    }
    private void timer2() {
        if ((raw(0xC8) & 4) == 0) return;
        int value = raw(0xCD) * 256 + raw(0xCC) + 1;
        if (value > 65535) {
            if ((raw(0xC8) & 0x30) == 0) flag(0xC8, 7, true);
            if ((raw(0xC8) & 1) == 0) value = raw(0xCB) * 256 + raw(0xCA);
        }
        sfr[0x4C] = value & 255; sfr[0x4D] = (value >>> 8) & 255;
    }
    private int serviceInterrupt() {
        int ie = raw(0xA8); if ((ie & 128) == 0) return 0;
        int t = raw(0x88);
        int pending = ((t & 2) != 0 ? 1 : 0) | ((t & 32) != 0 ? 2 : 0) | ((t & 8) != 0 ? 4 : 0)
                | ((t & 128) != 0 ? 8 : 0) | ((raw(0x98) & 3) != 0 ? 16 : 0) | ((raw(0xC8) & 0xC0) != 0 ? 32 : 0);
        for (int priority = 1; priority >= 0; priority--) {
            if (priority <= interruptLevel) continue;
            for (int i = 0; i < 6; i++) {
                if ((pending & ie & (1 << i)) == 0 || ((raw(0xB8) >>> i) & 1) != priority) continue;
                interruptLevels[interruptDepth++] = interruptLevel; interruptLevel = priority;
                if (i == 0 && (t & 1) != 0) flag(0x88, 1, false);
                if (i == 2 && (t & 4) != 0) flag(0x88, 3, false);
                if (i == 1 || i == 3) flag(0x88, i == 1 ? 5 : 7, false);
                flag(0x87, 0, false); call(3 + i * 8); return 2;
            }
        }
        return 0;
    }
    public byte[] save() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(1); out.writeInt(pc); out.writeInt(alternateDptr); out.writeLong(cycles);
            out.writeInt(previousP3); out.writeInt(previousP1); out.writeInt(interruptDepth); out.writeInt(interruptLevel); out.writeInt(cycleCredit);
            for (int v : interruptLevels) out.writeInt(v);
            for (int v : ram) out.writeByte(v); for (int v : sfr) out.writeByte(v);
            out.write(xram); return bytes.toByteArray();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public void restore(byte[] state) {
        if (state.length == 0) return;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(state));
            if (in.readInt() != 1) throw new IOException("Unknown state version");
            pc = in.readInt() & 65535; alternateDptr = in.readInt() & 65535; cycles = Math.max(0, in.readLong());
            previousP3 = in.readInt() & 255; previousP1 = in.readInt() & 255;
            interruptDepth = in.readInt(); interruptLevel = in.readInt(); cycleCredit = in.readInt();
            if (interruptDepth < 0 || interruptDepth > 2 || interruptLevel < -1 || interruptLevel > 1 || cycleCredit < -8 || cycleCredit > 0)
                throw new IOException("Invalid state");
            for (int i = 0; i < interruptLevels.length; i++) interruptLevels[i] = in.readInt();
            for (int i = 0; i < ram.length; i++) ram[i] = in.readUnsignedByte();
            for (int i = 0; i < sfr.length; i++) sfr[i] = in.readUnsignedByte();
            in.readFully(xram); if (in.available() != 0) throw new IOException("Trailing state");
            bus.resetPorts(new int[]{portLatch(0), portLatch(1), portLatch(2), portLatch(3)});
        } catch (IOException e) { reset(); throw new IllegalArgumentException("Invalid saved CPU state", e); }
    }
}
