package com.mysteryman.embeddeddev.mcu;

import java.util.Arrays;

/** Strict and atomic Intel HEX parser for the STC89C52RC's 8 KiB flash. */
public final class IntelHex {
    public static final int FLASH_SIZE = 8192, MAX_TEXT_LENGTH = 131072, MAX_PAYLOAD_LENGTH = 24576;
    public record Image(byte[] code, int bytesWritten) {}

    public static Image parse(String text) {
        if (text.length() > MAX_TEXT_LENGTH) throw new IllegalArgumentException("HEX exceeds 128 KiB text limit");
        byte[] code = new byte[FLASH_SIZE]; Arrays.fill(code, (byte) 255);
        boolean[] written = new boolean[FLASH_SIZE];
        long base = 0; int count = 0, lineNumber = 0; boolean eof = false;
        for (String raw : text.replace("\uFEFF", "").split("\\R")) {
            lineNumber++; String line = raw.strip();
            if (line.isEmpty()) continue;
            String prefix = "HEX line " + lineNumber + ": ";
            if (eof) throw new IllegalArgumentException(prefix + "data after EOF");
            if (!line.startsWith(":") || line.length() < 11 || (line.length() & 1) == 0)
                throw new IllegalArgumentException(prefix + "invalid record");
            byte[] r = new byte[(line.length() - 1) / 2]; int sum = 0;
            for (int i = 0; i < r.length; i++) {
                int hi = Character.digit(line.charAt(1 + i * 2), 16), lo = Character.digit(line.charAt(2 + i * 2), 16);
                if (hi < 0 || lo < 0) throw new IllegalArgumentException(prefix + "invalid hexadecimal digit");
                r[i] = (byte) (hi * 16 + lo); sum += r[i] & 255;
            }
            int length = r[0] & 255, address = (r[1] & 255) * 256 + (r[2] & 255), type = r[3] & 255;
            if (r.length != length + 5) throw new IllegalArgumentException(prefix + "incorrect length");
            if ((sum & 255) != 0) throw new IllegalArgumentException(prefix + "checksum mismatch");
            switch (type) {
                case 0 -> {
                    if ((long) address + length > 65536 || base + address + length > FLASH_SIZE)
                        throw new IllegalArgumentException(prefix + "program exceeds 8 KiB flash");
                    for (int i = 0; i < length; i++) {
                        int target = (int) (base + address + i);
                        if (written[target] && code[target] != r[4 + i]) throw new IllegalArgumentException(prefix + "conflicting overlap");
                        if (!written[target]) count++;
                        written[target] = true; code[target] = r[4 + i];
                    }
                }
                case 1 -> { if (length != 0 || address != 0) throw new IllegalArgumentException(prefix + "invalid EOF"); eof = true; }
                case 2, 4 -> {
                    if (length != 2 || address != 0) throw new IllegalArgumentException(prefix + "invalid extended address");
                    base = ((r[4] & 255L) * 256 + (r[5] & 255L)) << (type == 2 ? 4 : 16);
                }
                case 3, 5 -> { // Optional debugger metadata; the CPU always resets at 0000h.
                    if (length != 4 || address != 0) throw new IllegalArgumentException(prefix + "invalid start address");
                }
                default -> throw new IllegalArgumentException(prefix + "unsupported record type " + type);
            }
        }
        if (!eof || count == 0) throw new IllegalArgumentException("HEX requires program data and EOF");
        return new Image(code, count);
    }
    /** Canonical 16-byte records keep every accepted image below the C2S packet limit. */
    public static String encode(byte[] code) {
        if (code.length != FLASH_SIZE) throw new IllegalArgumentException("Invalid flash size");
        StringBuilder result = new StringBuilder(23000);
        for (int address = 0; address < code.length; address += 16) {
            int sum = 16 + (address >>> 8) + (address & 255);
            result.append(String.format(java.util.Locale.ROOT, ":10%04X00", address));
            for (int i = 0; i < 16; i++) { int v = code[address + i] & 255; sum += v; result.append(String.format(java.util.Locale.ROOT, "%02X", v)); }
            result.append(String.format(java.util.Locale.ROOT, "%02X\n", -sum & 255));
        }
        return result.append(":00000001FF\n").toString();
    }
    private IntelHex() {}
}
