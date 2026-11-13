package com.mysteryman.embeddeddev.wire;

import com.mysteryman.embeddeddev.mcu.FaceBinding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public record PinGroup(String name, FaceBinding binding) {
    public static final int COUNT = 4;

    public PinGroup {
        name = name.strip();
        if (name.isEmpty() || name.length() > FaceBinding.MAX_NAME_LENGTH || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("gui.embeddeddev.invalid_pin_name");
        }
    }

    public static PinGroup empty(int group) { return new PinGroup("G" + (group + 1), FaceBinding.EMPTY); }

    public static ListTag save(List<PinGroup> groups) {
        var entries = new ListTag();
        for (var group : groups) {
            var tag = new CompoundTag();
            tag.putString("Name", group.name());
            tag.putString("Pins", group.binding().wireText());
            entries.add(tag);
        }
        return entries;
    }

    public static List<PinGroup> load(ListTag entries) {
        return load(entries, false);
    }

    public static List<PinGroup> restore(ListTag entries) { return load(entries, true); }

    private static List<PinGroup> load(ListTag entries, boolean restoring) {
        if (entries.size() != COUNT) throw new IllegalArgumentException("gui.embeddeddev.invalid_wiring");
        var groups = new ArrayList<PinGroup>(COUNT);
        for (Tag entry : entries) {
            var tag = (CompoundTag) entry;
            String pins = tag.getString("Pins");
            groups.add(new PinGroup(tag.getString("Name"), restoring ? FaceBinding.restore(pins) : FaceBinding.parse(pins)));
        }
        return List.copyOf(groups);
    }
}
