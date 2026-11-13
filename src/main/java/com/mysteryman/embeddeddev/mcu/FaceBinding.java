package com.mysteryman.embeddeddev.mcu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.StringJoiner;
import java.util.TreeSet;

/** Independent, named terminals on one world-facing side of the board. */
public record FaceBinding(List<Pin> pins) {
    public static final int MAX_NAME_LENGTH = 24;
    public static final int MAX_PINS = 48;
    public static final FaceBinding EMPTY = new FaceBinding(List.of());

    public enum Kind {
        // Analog kinds are save-format tombstones; never reuse their old wire IDs.
        GPIO, DIGITAL_INPUT, DIGITAL_OUTPUT, ANALOG_INPUT, ANALOG_OUTPUT;

        public boolean removedAnalog() { return this == ANALOG_INPUT || this == ANALOG_OUTPUT; }

        /** Keep legacy redstone modes and wire IDs in saves, with one digital choice in the editor. */
        public Kind panelKind() {
            return switch (this) {
                case DIGITAL_INPUT, DIGITAL_OUTPUT -> GPIO;
                default -> this;
            };
        }

        public int channels() {
            return this == ANALOG_INPUT || this == ANALOG_OUTPUT ? 8 : 32;
        }

        public String channelName(int channel) {
            return switch (this) {
                case ANALOG_INPUT -> "ADC" + channel;
                case ANALOG_OUTPUT -> "DAC" + channel;
                default -> "P" + (channel >>> 3) + "." + (channel & 7);
            };
        }
    }

    public record Pin(String name, Kind kind, int channel) {
        public Pin {
            name = name.strip();
            if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || name.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("gui.embeddeddev.invalid_pin_name");
            }
            if (channel < 0 || channel >= kind.channels()) {
                throw new IllegalArgumentException("gui.embeddeddev.invalid_pin_channel");
            }
        }

        public String channelName() {
            return kind.channelName(channel);
        }

        public int wireId() { return kind.ordinal() * 32 + channel; }
    }

    public FaceBinding {
        pins = List.copyOf(pins);
        if (pins.size() > MAX_PINS) throw new IllegalArgumentException("gui.embeddeddev.too_many_pins");
        var names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        var channels = new HashSet<String>();
        for (Pin pin : pins) {
            if (pin.kind().removedAnalog()) throw new IllegalArgumentException("gui.embeddeddev.analog_removed");
            if (!names.add(pin.name())) throw new IllegalArgumentException("gui.embeddeddev.duplicate_pin_name");
            if (!channels.add(pin.channelName())) throw new IllegalArgumentException("gui.embeddeddev.duplicate_pin_channel");
        }
    }

    public int gpio(String name) {
        for (Pin pin : pins) {
            if (pin.kind() == Kind.GPIO && pin.name().equalsIgnoreCase(name)) return pin.channel();
        }
        return -1;
    }

    public String wireText() {
        StringJoiner rows = new StringJoiner("\n");
        for (Pin pin : pins) rows.add(pin.kind().name() + "," + pin.channel() + "," + pin.name());
        return rows.toString();
    }

    public static FaceBinding parse(String text) {
        return parse(text, false);
    }

    /** Remove retired analog terminals while preserving all saved GPIO names and IDs. */
    public static FaceBinding restore(String text) { return parse(text, true); }

    public static boolean hasRemovedAnalog(String text) {
        return text.lines().anyMatch(row -> row.startsWith("ANALOG_INPUT,") || row.startsWith("ANALOG_OUTPUT,"));
    }

    private static FaceBinding parse(String text, boolean restoring) {
        if (text.isEmpty()) return EMPTY;
        if (text.length() > MAX_PINS * (MAX_NAME_LENGTH + 20)) {
            throw new IllegalArgumentException("gui.embeddeddev.too_many_pins");
        }
        String[] rows = text.split("\n", -1);
        if (rows.length > MAX_PINS) throw new IllegalArgumentException("gui.embeddeddev.too_many_pins");
        var pins = new ArrayList<Pin>(rows.length);
        for (String row : rows) {
            String[] fields = row.split(",", 3);
            if (fields.length != 3) throw new IllegalArgumentException("gui.embeddeddev.invalid_wiring");
            Kind kind;
            int channel;
            try {
                kind = Kind.valueOf(fields[0]);
                channel = Integer.parseInt(fields[1]);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("gui.embeddeddev.invalid_wiring");
            }
            Pin pin = new Pin(fields[2], kind, channel);
            if (!restoring || !kind.removedAnalog()) pins.add(pin);
        }
        return new FaceBinding(pins);
    }

    /** Version 1.1 stored a single mode followed by channel numbers. */
    public static FaceBinding fromLegacy(int[] data) {
        if (data.length == 0 || data.length == 1 && data[0] == 0) return EMPTY;
        if (data.length < 2 || data.length > 33) {
            throw new IllegalArgumentException("gui.embeddeddev.invalid_wiring");
        }
        if (data[0] == 5 && data.length == 3) {
            return new FaceBinding(List.of(new Pin(I2cDevice.SDA, Kind.GPIO, data[1]),
                    new Pin(I2cDevice.SCL, Kind.GPIO, data[2])));
        }
        Kind kind = switch (data[0]) {
            case 1 -> Kind.DIGITAL_INPUT;
            case 2 -> Kind.DIGITAL_OUTPUT;
            case 3 -> Kind.ANALOG_INPUT;
            case 4 -> Kind.ANALOG_OUTPUT;
            default -> throw new IllegalArgumentException("gui.embeddeddev.invalid_wiring");
        };
        var pins = new ArrayList<Pin>();
        for (int i = 1; i < data.length; i++) {
            int channel = data[i];
            Pin pin = new Pin(kind.channelName(channel), kind, channel);
            if (!kind.removedAnalog()) pins.add(pin);
        }
        return new FaceBinding(pins);
    }
}
