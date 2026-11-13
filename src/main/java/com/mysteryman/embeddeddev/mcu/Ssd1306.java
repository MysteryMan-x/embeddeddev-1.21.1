package com.mysteryman.embeddeddev.mcu;

/** SSD1306 command/data subset, 7-bit address 3Ch. Display RAM belongs to the OLED module. */
public final class Ssd1306 implements I2cDevice {
    public final byte[] ram = new byte[1024];
    public boolean enabled, inverse, allOn, remap = true, scanReverse = true;
    private int column, page, mode = 2, columnStart, columnEnd = 127, pageStart, pageEnd = 7;
    private int command, needed, argumentIndex, startLine, offset;
    private boolean controlExpected = true, dataStream, continuation;
    private final int[] arguments = new int[6];
    private long revision;
    @Override public int address() { return 0x3C; }
    @Override public boolean begin(boolean read) { controlExpected = true; return !read; }
    @Override public boolean write(int value) {
        value &= 255;
        if (controlExpected) { dataStream = (value & 64) != 0; continuation = (value & 128) != 0; controlExpected = false; }
        else { if (dataStream) putData(value); else putCommand(value); if (continuation) controlExpected = true; }
        return true;
    }
    @Override public int read() { return 255; }
    public long revision() { return revision; }
    public void putData(int value) {
        int index = page * 128 + column;
        if (ram[index] != (byte) value) { ram[index] = (byte) value; revision++; }
        if (mode == 1) {
            if (++page > pageEnd) { page = pageStart; if (++column > columnEnd) column = columnStart; }
        } else if (++column > (mode == 2 ? 127 : columnEnd)) {
            column = mode == 2 ? 0 : columnStart;
            if (mode == 0 && ++page > pageEnd) page = pageStart;
        }
    }
    public void putCommand(int v) {
        if (needed > 0) {
            arguments[argumentIndex++] = v;
            if (--needed == 0) applyArguments();
            return;
        }
        command = v; argumentIndex = 0;
        if (v <= 15) column = (column & 0x70) | v;
        else if (v <= 31) column = (column & 15) | ((v & 7) << 4);
        else if (v >= 0xB0 && v <= 0xB7) page = v & 7;
        else if (v >= 0x40 && v <= 0x7F) { startLine = v & 63; revision++; }
        else switch (v) {
            case 0xAE -> { enabled = false; revision++; }
            case 0xAF -> { enabled = true; revision++; }
            case 0xA4, 0xA5 -> { allOn = v == 0xA5; revision++; }
            case 0xA6, 0xA7 -> { inverse = v == 0xA7; revision++; }
            case 0xA0, 0xA1 -> { remap = v == 0xA1; revision++; }
            case 0xC0, 0xC8 -> { scanReverse = v == 0xC8; revision++; }
            case 0x21, 0x22 -> needed = 2;
            case 0x20, 0x81, 0x8D, 0xA8, 0xD3, 0xD5, 0xD9, 0xDA, 0xDB -> needed = 1;
            case 0x26, 0x27 -> needed = 6; // Accept scrolling setup; no scrolling animation yet.
            case 0x29, 0x2A -> needed = 5;
            case 0xA3 -> needed = 2;
            default -> {} // Analog panel timing/charge-pump settings have no in-game timing effect.
        }
    }
    private void applyArguments() {
        switch (command) {
            case 0x20 -> mode = Math.min(2, arguments[0] & 3);
            case 0x21 -> { columnStart = arguments[0] & 127; columnEnd = Math.max(columnStart, arguments[1] & 127); column = columnStart; }
            case 0x22 -> { pageStart = arguments[0] & 7; pageEnd = Math.max(pageStart, arguments[1] & 7); page = pageStart; }
            case 0xD3 -> { offset = arguments[0] & 63; revision++; }
            default -> {}
        }
    }
    public byte[] frame() {
        byte[] frame = new byte[1024];
        for (int y = 0; y < 64; y++) for (int x = 0; x < 128; x++) {
            int sx = remap ? x : 127 - x, sy = ((scanReverse ? y : 63 - y) + startLine - offset) & 63;
            boolean lit = allOn || (ram[(sy >>> 3) * 128 + sx] & (1 << (sy & 7))) != 0;
            if (lit ^ inverse) frame[(y >>> 3) * 128 + x] |= (byte) (1 << (y & 7));
        }
        return frame;
    }
    public int[] saveRegisters() { return new int[]{column, page, mode, columnStart, columnEnd, pageStart, pageEnd, startLine, offset,
            enabled ? 1 : 0, inverse ? 1 : 0, allOn ? 1 : 0, remap ? 1 : 0, scanReverse ? 1 : 0}; }
    public void restoreRegisters(int[] values) {
        if (values.length != 14) return;
        column = values[0] & 127; page = values[1] & 7; mode = Math.clamp(values[2], 0, 2);
        columnStart = values[3] & 127; columnEnd = Math.max(columnStart, values[4] & 127);
        pageStart = values[5] & 7; pageEnd = Math.max(pageStart, values[6] & 7);
        startLine = values[7] & 63; offset = values[8] & 63;
        enabled = values[9] != 0; inverse = values[10] != 0; allOn = values[11] != 0; remap = values[12] != 0; scanReverse = values[13] != 0;
        revision++;
    }
}
