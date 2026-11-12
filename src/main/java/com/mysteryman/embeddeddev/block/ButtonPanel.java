package com.mysteryman.embeddeddev.block;

import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.mcu.GpioDevice;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Stable physical positions and independent, active-low outputs for a 3 × 3 panel. */
public final class ButtonPanel {
    public static final int MAX_KEYS = 9;
    public static final double KEY_HALF_WIDTH = 1.65 / 16;
    private static final int[] COLUMNS = {1, 0, 2, 0, 2, 1, 0, 2, 1};
    private static final int[] ROWS =    {1, 0, 0, 2, 2, 0, 1, 1, 2};
    private static final VoxelShape[] SHAPES = shapes();

    private final long[] releaseAt = new long[MAX_KEYS];
    private final GpioDevice[] devices = new GpioDevice[MAX_KEYS];
    private List<String> names = List.of();
    private int pressed;

    public ButtonPanel() { configure(List.of(defaultName(0))); }

    public static String defaultName(int key) { return key == 0 ? "KEY" : "KEY" + (key + 1); }
    public static int column(int key) { return COLUMNS[key]; }
    public static int row(int key) { return ROWS[key]; }
    public static double x(int key) { return (4 + column(key) * 4) / 16.0; }
    public static double z(int key) { return (4 + row(key) * 4) / 16.0; }
    public List<String> names() { return names; }
    public int count() { return names.size(); }
    public boolean pressed(int key) { return (pressed & (1 << key)) != 0; }
    public boolean anyPressed() { return pressed != 0; }
    public GpioDevice device(int key) { return key >= 0 && key < count() ? devices[key] : null; }
    public VoxelShape shape() { return SHAPES[count() - 1]; }

    public int keyAt(double x, double z) {
        for (int key = 0; key < count(); key++) {
            if (Math.abs(x - x(key)) <= KEY_HALF_WIDTH && Math.abs(z - z(key)) <= KEY_HALF_WIDTH) return key;
        }
        return -1;
    }

    public static List<String> validate(List<String> requested) {
        if (requested.isEmpty() || requested.size() > MAX_KEYS) throw new IllegalArgumentException("buttons.embeddeddev.invalid_count");
        var distinct = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        var labels = new ArrayList<String>(requested.size());
        for (String requestedName : requested) {
            String name = requestedName.strip();
            if (name.isEmpty() || name.length() > FaceBinding.MAX_NAME_LENGTH || name.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("gui.embeddeddev.invalid_pin_name");
            }
            if (!distinct.add(name)) throw new IllegalArgumentException("gui.embeddeddev.duplicate_pin_name");
            labels.add(name);
        }
        return List.copyOf(labels);
    }

    public void configure(List<String> requested) {
        var next = validate(requested);
        for (int key = 0; key < next.size(); key++) {
            if (key >= count() || !next.get(key).equals(names.get(key))) devices[key] = new Key(key, next.get(key));
        }
        for (int key = next.size(); key < count(); key++) {
            devices[key] = null;
            releaseAt[key] = 0;
        }
        pressed &= (1 << next.size()) - 1;
        names = next;
    }

    public boolean press(int key, long time) {
        if (device(key) == null) return false;
        releaseAt[key] = time + 10;
        pressed |= 1 << key;
        return true;
    }

    public boolean tick(long time) {
        if (pressed == 0) return false;
        int previous = pressed;
        for (int key = 0; key < count(); key++) if (time >= releaseAt[key]) pressed &= ~(1 << key);
        return previous != pressed;
    }

    public void save(CompoundTag tag) {
        var values = new ListTag();
        for (String name : names) values.add(StringTag.valueOf(name));
        tag.put("ButtonNames", values);
    }

    public void load(CompoundTag tag) {
        var values = tag.getList("ButtonNames", Tag.TAG_STRING);
        List<String> labels = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) labels.add(values.getString(i));
        try { configure(labels); }
        catch (IllegalArgumentException ignored) { configure(List.of(defaultName(0))); }
    }

    public void updatePressed(CompoundTag tag) { tag.putInt("ButtonPressed", pressed); }
    public void readPressed(CompoundTag tag) { pressed = tag.getInt("ButtonPressed") & ((1 << count()) - 1); }

    private final class Key implements GpioDevice {
        private final int index;
        private final List<String> terminals;
        private Key(int index, String name) { this.index = index; terminals = List.of(name); }
        @Override public String label() { return "KEY"; }
        @Override public List<String> terminals() { return terminals; }
        @Override public int pullLow() { return pressed(index) ? 1 : 0; }
    }

    private static VoxelShape[] shapes() {
        var shapes = new VoxelShape[MAX_KEYS];
        VoxelShape shape = Block.box(1, 0, 1, 15, 1.4, 15);
        for (int key = 0; key < MAX_KEYS; key++) {
            double x = x(key) * 16, z = z(key) * 16;
            shape = Shapes.or(shape, Block.box(x - 1.65, 1.4, z - 1.65, x + 1.65, 5.35, z + 1.65));
            shapes[key] = shape;
        }
        return shapes;
    }
}
