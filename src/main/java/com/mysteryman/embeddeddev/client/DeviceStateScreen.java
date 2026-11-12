package com.mysteryman.embeddeddev.client;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public interface DeviceStateScreen {
    boolean matches(BlockPos pos);
    void update(CompoundTag data);
}
