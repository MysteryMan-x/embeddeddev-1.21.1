package com.mysteryman.embeddeddev;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue CYCLES_PER_TICK = BUILDER
            .comment("Machine cycles per tick for an 11.0592 MHz crystal. 46080 matches 12T at 20 TPS.",
                    "Other crystals scale this budget proportionally. Unloaded chunks do not execute firmware.")
            .defineInRange("cyclesPerTick", 46080, 256, 100000);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private Config() {}
}
