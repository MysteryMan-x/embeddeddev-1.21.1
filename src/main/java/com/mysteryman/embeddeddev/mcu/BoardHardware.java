package com.mysteryman.embeddeddev.mcu;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Configurable connectors. No direction has an intrinsic ADC, DAC or GPIO assignment. */
public final class BoardHardware implements Mcs51.Bus {
    public final int[] adc = new int[8], dac = new int[8], inputs = new int[4];
    private final int[] latches = {255, 255, 255, 255};
    private final FaceBinding[] bindings = new FaceBinding[6];
    private final boolean[][] reserved = new boolean[6][FaceBinding.Kind.values().length * 32];
    private final I2cLink[] links = new I2cLink[6];
    private final int[] dataPins = new int[6], clockPins = new int[6];
    private final GpioDevice[] gpioDevices = new GpioDevice[6];
    private final int[][] gpioChannels = new int[6][];
    private final StringBuilder serial = new StringBuilder();
    private final Map<WiredI2c, I2cLink> wiredLinks = new LinkedHashMap<>();
    private Map.Entry<WiredI2c, I2cLink>[] activeLinks = emptyLinks();
    private List<WiredGpio> wiredGpio = List.of();
    private long nanoseconds, clockRemainder;
    private int clockHz = 11_059_200;

    public void clockHz(int hz) {
        if (hz > 0 && clockHz != hz) { clockHz = hz; clockRemainder = 0; }
    }
    @Override public void advanceCycles(int cycles) {
        long duration = cycles * 12_000_000_000L + clockRemainder;
        nanoseconds += duration / clockHz;
        clockRemainder = duration % clockHz;
    }

    public record WiredI2c(I2cDevice device, int sda, int scl, int address) {
        public WiredI2c(I2cDevice device, int sda, int scl) { this(device, sda, scl, device.address()); }
    }
    public record WiredGpio(GpioDevice device, List<Integer> pins) {
        public WiredGpio { pins = List.copyOf(pins); }
    }

    @SuppressWarnings("unchecked")
    private static Map.Entry<WiredI2c, I2cLink>[] emptyLinks() { return (Map.Entry<WiredI2c, I2cLink>[]) new Map.Entry<?, ?>[0]; }

    public void wireDevices(List<WiredI2c> i2c, List<WiredGpio> gpio) {
        if (!List.copyOf(wiredLinks.keySet()).equals(i2c)) {
            var nextLinks = new LinkedHashMap<WiredI2c, I2cLink>();
            for (WiredI2c route : i2c) {
                I2cLink link = wiredLinks.remove(route);
                if (link == null) {
                    link = new I2cLink();
                    link.attach(route.device(), high(route.sda()), high(route.scl()));
                }
                nextLinks.put(route, link);
            }
            wiredLinks.values().forEach(link -> link.reset(true, true));
            wiredLinks.clear();
            wiredLinks.putAll(nextLinks);
            activeLinks = wiredLinks.entrySet().toArray(emptyLinks());
        }
        if (!wiredGpio.equals(gpio)) {
            for (WiredGpio route : wiredGpio) {
                if (!gpio.contains(route) && Arrays.stream(gpioDevices).noneMatch(device -> device == route.device())) {
                    route.device().reset(0);
                }
            }
            for (WiredGpio route : gpio) if (!wiredGpio.contains(route)) {
                route.device().time(nanoseconds);
                route.device().reset(gpioLevels(route));
            }
            wiredGpio = List.copyOf(gpio);
        }
    }

    public BoardHardware() {
        Arrays.fill(bindings, FaceBinding.EMPTY);
        Arrays.fill(dataPins, -1);
        Arrays.fill(clockPins, -1);
        Arrays.setAll(links, i -> new I2cLink());
        reset();
    }
    public void reset() {
        Arrays.fill(dac, 0); Arrays.fill(inputs, 255); Arrays.fill(adc, 0); Arrays.fill(latches, 255);
        for (I2cLink link : links) link.reset(true, true);
        for (var link : activeLinks) link.getValue().reset(true, true);
        for (WiredGpio route : wiredGpio) route.device().reset(gpioLevels(route));
        for (int f = 0; f < 6; f++) {
            if (gpioDevices[f] != null) gpioDevices[f].reset(gpioLevels(f));
        }
        serial.setLength(0);
    }
    public FaceBinding binding(int face) { return bindings[face]; }
    public void reserve(int face, Set<Integer> pins) {
        Arrays.fill(reserved[face], false);
        for (int pin : pins) reserved[face][pin] = true;
    }
    public void bind(int face, FaceBinding value) {
        if (face < 0 || face >= 6) throw new IllegalArgumentException("Invalid direction");
        int dataPin = value.gpio(I2cDevice.SDA);
        int clockPin = value.gpio(I2cDevice.SCL);
        if (dataPins[face] != dataPin || clockPins[face] != clockPin) {
            links[face].attach(null, true, true);
        }
        if (!bindings[face].equals(value)) attachGpio(face, null);
        bindings[face] = value;
        dataPins[face] = dataPin;
        clockPins[face] = clockPin;
    }
    public boolean hasModulePins(int face) { return dataPins[face] >= 0 && clockPins[face] >= 0; }
    public void synchronizePorts(int[] values) {
        for (int p = 0; p < 4; p++) latches[p] = values[p] & 255;
        for (int f = 0; f < 6; f++) {
            if (hasModulePins(f)) links[f].reset(high(dataPins[f]), high(clockPins[f]));
            if (gpioDevices[f] != null) gpioDevices[f].reset(gpioLevels(f));
        }
        for (var link : activeLinks) {
            var route = link.getKey();
            link.getValue().reset(high(route.sda()), high(route.scl()));
        }
        for (WiredGpio route : wiredGpio) route.device().reset(gpioLevels(route));
    }
    @Override public void resetPorts(int[] values) { synchronizePorts(values); }
    public void attach(int face, I2cDevice device) {
        if (hasModulePins(face)) {
            links[face].attach(device, high(dataPins[face]), high(clockPins[face]));
        } else {
            links[face].attach(null, true, true);
        }
    }
    public I2cDevice device(int face) { return links[face].device(); }
    public boolean hasLocalAddress(WiredI2c route) {
        for (int f = 0; f < 6; f++) {
            I2cDevice device = links[f].device();
            if (device != null && device.address() == route.address() && dataPins[f] == route.sda() && clockPins[f] == route.scl()) return true;
        }
        return false;
    }
    public GpioDevice gpioDevice(int face) { return gpioDevices[face]; }

    public boolean hasGpioPins(int face, GpioDevice device) {
        for (String name : device.terminals()) {
            if (bindings[face].gpio(name) < 0) return false;
        }
        return true;
    }

    public void attachGpio(int face, GpioDevice device) {
        if (gpioDevices[face] == device) return;
        if (gpioDevices[face] != null) gpioDevices[face].reset(0);
        gpioDevices[face] = null;
        gpioChannels[face] = null;
        if (device == null || !hasGpioPins(face, device)) return;
        gpioChannels[face] = device.terminals().stream().mapToInt(bindings[face]::gpio).toArray();
        gpioDevices[face] = device;
        device.time(nanoseconds);
        device.reset(gpioLevels(face));
    }

    private int gpioLevels(int face) {
        int levels = 0;
        int[] pins = gpioChannels[face];
        for (int i = 0; i < pins.length; i++) {
            if (high(pins[i])) levels |= 1 << i;
        }
        return levels;
    }

    private int gpioLevels(WiredGpio route) {
        int levels = 0;
        for (int i = 0; i < route.pins().size(); i++) if (high(route.pins().get(i))) levels |= 1 << i;
        return levels;
    }

    private boolean high(int pin) { return ((latches[pin >>> 3] >>> (pin & 7)) & 1) != 0; }
    @Override public void writePort(int p, int latch) {
        latches[p] = latch & 255;
        for (int f = 0; f < 6; f++) {
            if (links[f].device() != null && (dataPins[f] >>> 3 == p || clockPins[f] >>> 3 == p)) {
                links[f].update(high(dataPins[f]), high(clockPins[f]));
            }
            if (gpioDevices[f] != null) {
                gpioDevices[f].time(nanoseconds);
                gpioDevices[f].update(gpioLevels(f));
            }
        }
        for (var link : activeLinks) {
            var route = link.getKey();
            if (route.sda() >>> 3 == p || route.scl() >>> 3 == p) {
                link.getValue().update(high(route.sda()), high(route.scl()));
            }
        }
        for (int i = 0; i < wiredGpio.size(); i++) {
            var route = wiredGpio.get(i);
            route.device().time(nanoseconds);
            route.device().update(gpioLevels(route));
        }
    }
    @Override public int readPort(int p, int latch) {
        int value = latch & inputs[p];
        for (int f = 0; f < 6; f++) {
            int dataPin = dataPins[f];
            if (dataPin >>> 3 == p && links[f].pullLow()) value &= ~(1 << (dataPin & 7));
            if (gpioDevices[f] != null) {
                gpioDevices[f].time(nanoseconds);
                int low = gpioDevices[f].pullLow();
                while (low != 0) {
                    int pin = gpioChannels[f][Integer.numberOfTrailingZeros(low)];
                    if (pin >>> 3 == p) value &= ~(1 << (pin & 7));
                    low &= low - 1;
                }
            }
        }
        for (var link : activeLinks) {
            int pin = link.getKey().sda();
            if (pin >>> 3 == p && link.getValue().pullLow()) value &= ~(1 << (pin & 7));
        }
        for (int i = 0; i < wiredGpio.size(); i++) {
            var route = wiredGpio.get(i);
            route.device().time(nanoseconds);
            int low = route.device().pullLow();
            while (low != 0) {
                int pin = route.pins().get(Integer.numberOfTrailingZeros(low));
                if (pin >>> 3 == p) value &= ~(1 << (pin & 7));
                low &= low - 1;
            }
        }
        return value;
    }
    public void sample(int[] signals) {
        Arrays.fill(inputs, 255); Arrays.fill(adc, 0);
        for (int f = 0; f < 6; f++) {
            int signal = Math.clamp(signals[f], 0, 15);
            for (FaceBinding.Pin pin : bindings[f].pins()) {
                if (!reserved[f][pin.wireId()] && (pin.kind() == FaceBinding.Kind.DIGITAL_INPUT
                        || pin.kind() == FaceBinding.Kind.ANALOG_INPUT)) samplePin(pin, signal);
            }
        }
    }
    public void samplePin(FaceBinding.Pin pin, int strength) {
        int channel = pin.channel();
        switch (pin.kind().panelKind()) {
            case ANALOG_INPUT -> adc[channel] = Math.max(adc[channel], strength);
            case GPIO -> {
                if (strength == 0) inputs[channel >>> 3] &= ~(1 << (channel & 7));
            }
            default -> {}
        }
    }
    public Ssd1306 display() {
        for (I2cLink link : links) if (link.device() instanceof Ssd1306 oled) return oled;
        for (var link : activeLinks) if (link.getKey().device() instanceof Ssd1306 oled) return oled;
        return null;
    }
    @Override public int readExternal(int a) {
        if (a >= 0xF000 && a < 0xF008) return adc[a - 0xF000];
        if (a >= 0xF010 && a < 0xF018) return dac[a - 0xF010];
        if (a == 0xF020) { Ssd1306 d = display(); return d == null ? 255 : d.enabled ? 1 : 0; }
        if (a >= 0xF100 && a < 0xF500) { Ssd1306 d = display(); return d == null ? 255 : d.ram[a - 0xF100] & 255; }
        return -1;
    }
    @Override public void writeExternal(int a, int v) {
        if (a >= 0xF010 && a < 0xF018) dac[a - 0xF010] = Math.clamp(v, 0, 15);
        if (a == 0xF020) { Ssd1306 d = display(); if (d != null) d.legacyControl(v); }
        if (a >= 0xF100 && a < 0xF500) {
            Ssd1306 d = display(); if (d != null && d.ram[a - 0xF100] != (byte) v) { d.ram[a - 0xF100] = (byte) v; d.changed(); }
        }
    }
    @Override public void transmit(int v) {
        if (v == 10 || v == 13 || v >= 32 && v < 127) serial.append((char) v);
        else serial.append('.');
        if (serial.length() > 512) serial.delete(0, serial.length() - 512);
    }
    public String serial() { return serial.toString(); }
    public void restoreSerial(String text) { serial.setLength(0); serial.append(text.substring(Math.max(0, text.length() - 512))); }
    public int output(int face, boolean running) {
        if (!running) return 0;
        int strength = 0;
        for (FaceBinding.Pin pin : bindings[face].pins()) {
            if (reserved[face][pin.wireId()]) continue;
            if (pin.kind() == FaceBinding.Kind.DIGITAL_OUTPUT && high(pin.channel())) return 15;
            if (pin.kind() == FaceBinding.Kind.ANALOG_OUTPUT) strength = Math.max(strength, dac[pin.channel()]);
        }
        return strength;
    }
    public int outputPin(FaceBinding.Pin pin) {
        return switch (pin.kind().panelKind()) {
            case GPIO -> high(pin.channel()) ? 15 : 0;
            case ANALOG_OUTPUT -> dac[pin.channel()];
            default -> 0;
        };
    }
}
