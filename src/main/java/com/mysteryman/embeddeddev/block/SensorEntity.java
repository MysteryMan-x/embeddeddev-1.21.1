package com.mysteryman.embeddeddev.block;

import com.mysteryman.embeddeddev.mcu.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SensorEntity extends BlockEntity implements I2cModule {
    public final I2cSensor sensor = new I2cSensor();
    public SensorEntity(BlockPos p, BlockState s) { super(ModBlockEntities.SENSOR.get(), p, s); }
    @Override public I2cDevice i2cDevice() { return sensor; }
    public static void tick(Level l, BlockPos p, BlockState s, SensorEntity e) {
        int redstone = 0;
        for (Direction d : Direction.values()) {
            BlockPos neighbor = p.relative(d);
            if (l.hasChunkAt(neighbor) && !(l.getBlockEntity(neighbor) instanceof SystemBoardEntity)) redstone = Math.max(redstone, l.getSignal(neighbor, d));
        }
        e.sensor.values[0] = redstone;
        e.sensor.values[1] = l.getBrightness(LightLayer.SKY, p.above());
        e.sensor.values[2] = l.getBrightness(LightLayer.BLOCK, p.above());
        e.sensor.values[3] = (int) (Math.floorMod(l.getDayTime(), 24000) * 100 / 24000);
    }
}
