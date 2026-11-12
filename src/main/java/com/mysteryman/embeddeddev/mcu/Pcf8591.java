package com.mysteryman.embeddeddev.mcu;

/** PCF8591 digital interface with a pipelined ADC and a latched DAC. */
public final class Pcf8591 implements I2cDevice {
    private static final int[] CHANNELS = {4, 3, 3, 2};
    private final int[] inputs = new int[4];
    private int address = 0x48;
    private int control, channel, dac;
    private int conversion = 0x80;
    private boolean expectControl;

    @Override public int address() { return address; }
    public void nextAddress() { address = 0x48 + ((address - 0x48 + 1) & 7); }
    public void sampleRedstone(int input, int strength) { inputs[input] = Math.clamp(strength, 0, 15) * 17; }
    public int redstoneOutput() { return (control & 0x40) == 0 ? 0 : (dac * 15 + 127) / 255; }

    @Override public boolean begin(boolean read) {
        expectControl = !read;
        return true;
    }

    @Override public boolean write(int value) {
        if (expectControl) {
            control = value & 0x77;
            channel = Math.min(control & 3, CHANNELS[mode()] - 1);
            expectControl = false;
        } else {
            dac = value & 0xff;
        }
        return true;
    }

    @Override public int read() {
        int previous = conversion;
        conversion = switch (mode()) {
            case 1 -> differential(channel, 3);
            case 2 -> channel == 2 ? differential(2, 3) : inputs[channel];
            case 3 -> differential(channel * 2, channel * 2 + 1);
            default -> inputs[channel];
        };
        if ((control & 4) != 0) channel = (channel + 1) % CHANNELS[mode()];
        return previous;
    }

    private int mode() { return (control >>> 4) & 3; }
    private int differential(int positive, int negative) {
        int signed = Math.round((inputs[positive] - inputs[negative]) * 256.0F / 255);
        return Math.clamp(signed, -128, 127) & 0xff;
    }

    public int[] save() { return new int[]{address, control, channel, dac, conversion}; }

    public void restore(int[] state) {
        if (state.length != 5) return;
        address = Math.clamp(state[0], 0x48, 0x4f);
        control = state[1] & 0x77;
        channel = Math.clamp(state[2], 0, CHANNELS[mode()] - 1);
        dac = state[3] & 0xff;
        conversion = state[4] & 0xff;
    }
}
