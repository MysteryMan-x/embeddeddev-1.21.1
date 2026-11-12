package com.mysteryman.embeddeddev.block;

import com.mysteryman.embeddeddev.compat.TemperatureSources;
import com.mysteryman.embeddeddev.mcu.Ds18b20;
import com.mysteryman.embeddeddev.mcu.GpioDevice;
import com.mysteryman.embeddeddev.mcu.I2cDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.Locale;

public final class Ds18b20Entity extends BlockEntity implements GpioModule, I2cModule {
    public final Ds18b20 sensor = new Ds18b20();
    private TemperatureSources.Reading reading = new TemperatureSources.Reading(20, "temperature.embeddeddev.ambient", true);
    private byte[] saved = new byte[0];
    private long nextSample;

    public Ds18b20Entity(BlockPos pos, BlockState state) { super(ModBlockEntities.DS18B20.get(), pos, state); }
    @Override public GpioDevice gpioDevice() {
        if (level != null && !level.isClientSide && level.getGameTime() >= nextSample) sample();
        return sensor;
    }
    @Override public I2cDevice i2cDevice() { return null; }
    public TemperatureSources.Reading reading() { return reading; }

    public void sample() {
        nextSample = level.getGameTime() + 5;
        var facing = getBlockState().getValue(Ds18b20Block.FACING);
        reading = TemperatureSources.read(level, worldPosition.relative(facing), facing.getOpposite());
        sensor.temperature(reading.celsius());
    }

    public void inspect(Player player) {
        sample();
        player.displayClientMessage(Component.translatable("message.embeddeddev.ds18b20",
                String.format(Locale.ROOT, "%.2f", Math.clamp(reading.celsius(), -55, 125)),
                Component.translatable(reading.source()),
                Component.translatable(reading.celsius() < -55 || reading.celsius() > 125
                        ? "temperature.embeddeddev.overrange" : "temperature.embeddeddev.inrange")), true);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, Ds18b20Entity entity) {
        if (level.getGameTime() >= entity.nextSample) entity.sample();
        entity.sensor.tick(level.getGameTime());
        byte[] data = entity.sensor.save();
        if (!Arrays.equals(data, entity.saved)) { entity.saved = data; entity.setChanged(); }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByteArray("Thermometer", sensor.save());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sensor.restore(tag.getByteArray("Thermometer"));
    }
}
