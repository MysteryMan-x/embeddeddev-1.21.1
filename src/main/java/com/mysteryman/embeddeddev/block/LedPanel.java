package com.mysteryman.embeddeddev.block;

import com.mysteryman.embeddeddev.mcu.GpioDevice;
import net.minecraft.nbt.*;
import java.util.*;

/** Stable 3x3 positions; one named GPIO endpoint and RGB color per lamp. */
public final class LedPanel {
    public static final int MAX_LEDS = 9, DEFAULT_COLOR = 0x8AE04D;
    private List<String> names = List.of("LED");
    private int[] colors = {DEFAULT_COLOR};
    private final GpioDevice[] devices = new GpioDevice[MAX_LEDS];
    private int lit;
    public LedPanel() { configure(names, colors); }
    public static String defaultName(int index) { return index == 0 ? "LED" : "LED" + (index + 1); }
    public List<String> names() { return names; }
    public int count() { return names.size(); }
    public int color(int index) { return colors[index]; }
    public int litMask() { return lit; }
    public boolean lit(int index) { return (lit & (1 << index)) != 0; }
    public void clear(int index) { lit &= ~(1 << index); }
    public int at(double x, double z) {
        for (int i = 0; i < count(); i++) if (Math.abs(x-ButtonPanel.x(i)) <= ButtonPanel.KEY_HALF_WIDTH
                && Math.abs(z-ButtonPanel.z(i)) <= ButtonPanel.KEY_HALF_WIDTH) return i;
        return -1;
    }
    public GpioDevice device(int index) { return index >= 0 && index < count() ? devices[index] : null; }
    public void configure(List<String> requested, int[] rgb) {
        var valid = ButtonPanel.validate(requested);
        if (rgb.length != valid.size() || Arrays.stream(rgb).anyMatch(c -> c < 0 || c > 0xFFFFFF))
            throw new IllegalArgumentException("leds.embeddeddev.invalid_color");
        for (int i = 0; i < valid.size(); i++) {
            if (devices[i] != null && i < count() && names.get(i).equals(valid.get(i))) continue;
            final int index = i; final var terminals = List.of(valid.get(i));
            devices[i] = new GpioDevice() {
                public String label() { return "LED"; }
                public List<String> terminals() { return terminals; }
                public void update(int levels) { if ((levels & 1) != 0) lit |= 1 << index; else clear(index); }
            };
        }
        for (int i=valid.size();i<MAX_LEDS;i++) devices[i]=null;
        names=valid;colors=rgb.clone();lit &= (1 << count()) - 1;
    }
    public void save(CompoundTag tag) {
        var list=new ListTag();for(var name:names)list.add(StringTag.valueOf(name));
        tag.put("LedNames",list);tag.putIntArray("LedColors",colors);
    }
    public void load(CompoundTag tag) {
        var list=tag.getList("LedNames",Tag.TAG_STRING);var labels=new ArrayList<String>();
        for(int i=0;i<list.size();i++)labels.add(list.getString(i));
        try { configure(labels,tag.getIntArray("LedColors")); }
        catch(IllegalArgumentException ignored) { configure(List.of("LED"),new int[]{DEFAULT_COLOR}); }
        lit=0;
    }
    public void updateLit(CompoundTag tag) { tag.putInt("LedLit",lit); }
    public void readLit(CompoundTag tag) { lit=tag.getInt("LedLit") & ((1 << count())-1); }
}
