package com.mysteryman.embeddeddev.compat;

import com.mysteryman.embeddeddev.EmbeddedDev;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;

import java.lang.reflect.Method;

public final class TemperatureSources {
    public static final BlockCapability<TemperatureProvider, Direction> TEMPERATURE = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath(EmbeddedDev.MODID, "temperature"), TemperatureProvider.class);
    public record Reading(double celsius, String source, boolean estimated) {
        public Reading {
            if (!Double.isFinite(celsius)) throw new IllegalArgumentException("Non-finite temperature");
        }
    }

    public static Reading read(Level level, BlockPos pos, Direction side) {
        if (!level.hasChunkAt(pos)) return new Reading(20, "temperature.embeddeddev.ambient", true);
        var state = level.getBlockState(pos);
        var entity = level.getBlockEntity(pos);
        var provider = level.getCapability(TEMPERATURE, pos, side);
        if (provider == null && entity instanceof TemperatureProvider direct) provider = direct;
        if (provider != null) {
            double temperature = provider.celsius();
            if (Double.isFinite(temperature)) return new Reading(temperature, "temperature.embeddeddev.device", false);
        }
        Reading mekanism = Mekanism.read(level, pos, side);
        if (mekanism != null) return mekanism;
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id.getNamespace().equals("create") && (id.getPath().equals("fluid_tank") || id.getPath().equals("creative_fluid_tank")) && entity != null) {
            var data = entity.saveCustomOnly(level.registryAccess());
            var controllerPos = NbtUtils.readBlockPos(data, "Controller").orElse(pos);
            var controller = level.hasChunkAt(controllerPos) ? level.getBlockEntity(controllerPos) : null;
            if (controller != null) {
                var controllerId = BuiltInRegistries.BLOCK.getKey(controller.getBlockState().getBlock());
                if (controllerId.getNamespace().equals("create") && controllerId.getPath().contains("fluid_tank")) {
                    return createBoiler(controller.saveCustomOnly(level.registryAccess()).getCompound("Boiler"));
                }
            }
        }
        if (state.is(Blocks.LAVA)) return new Reading(1000, "temperature.embeddeddev.block", true);
        if (state.is(Blocks.MAGMA_BLOCK)) return new Reading(110, "temperature.embeddeddev.block", true);
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
            if (state.getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock) return new Reading(90, "temperature.embeddeddev.block", true);
            if (state.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock) return new Reading(95, "temperature.embeddeddev.block", true);
        }
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.SNOW_BLOCK)) {
            return new Reading(0, "temperature.embeddeddev.block", true);
        }
        return ambient(level, pos);
    }

    public static Reading createBoiler(CompoundTag boiler) {
        int heat = Math.clamp(boiler.getInt("ActiveHeat"), 0, 18);
        double celsius = heat > 0 ? 20 + 5 * heat : boiler.getBoolean("PassiveHeat") ? 60 : 20;
        return new Reading(celsius, "temperature.embeddeddev.create", true);
    }

    private static Reading ambient(Level level, BlockPos pos) {
        return new Reading(20 + 20 * (level.getBiome(pos).value().getBaseTemperature() - .8), "temperature.embeddeddev.ambient", true);
    }

    private static final class Mekanism {
        private static final Access ACCESS = load();
        private record Access(BlockCapability<Object, Direction> heat, Method count, Method temperature) {}
        @SuppressWarnings("unchecked")
        private static Access load() {
            if (!ModList.get().isLoaded("mekanism")) return null;
            try {
                var type = Class.forName("mekanism.api.heat.IHeatHandler");
                var heat = (BlockCapability<Object, Direction>) Class.forName("mekanism.common.capabilities.Capabilities").getField("HEAT").get(null);
                return new Access(heat, type.getMethod("getHeatCapacitorCount"), type.getMethod("getTemperature", int.class));
            } catch (ReflectiveOperationException | LinkageError error) {
                EmbeddedDev.LOGGER.warn("Mekanism heat API is unavailable", error);
                return null;
            }
        }
        private static Reading read(Level level, BlockPos pos, Direction side) {
            if (ACCESS == null) return null;
            Object handler = level.getCapability(ACCESS.heat(), pos, side);
            if (handler == null) return null;
            try {
                int count = Math.clamp(((Number) ACCESS.count().invoke(handler)).intValue(), 0, 1024);
                double hottest = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < count; i++) {
                    double kelvin = ((Number) ACCESS.temperature().invoke(handler, i)).doubleValue();
                    if (Double.isFinite(kelvin)) hottest = Math.max(hottest, kelvin - 273.15);
                }
                return Double.isFinite(hottest) ? new Reading(hottest, "temperature.embeddeddev.mekanism", false) : null;
            } catch (ReflectiveOperationException error) { return null; }
        }
    }

    private TemperatureSources() {}
}
