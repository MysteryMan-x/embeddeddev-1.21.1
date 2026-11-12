package com.mysteryman.embeddeddev.mcu;

import java.util.List;

/** One digit, with A–G in bits 0–6 and the decimal point in bit 7. */
public final class SevenSegment implements GpioDevice {
    public static final List<String> TERMINALS = List.of("A", "B", "C", "D", "E", "F", "G", "DP");
    private int levels;
    private boolean commonAnode;

    @Override public String label() { return "7SEG"; }
    @Override public List<String> terminals() { return TERMINALS; }
    @Override public void update(int levels) { this.levels = levels & 255; }
    public int segments() { return levels ^ (commonAnode ? 255 : 0); }
    public boolean commonAnode() { return commonAnode; }
    public void commonAnode(boolean value) { commonAnode = value; }
}
