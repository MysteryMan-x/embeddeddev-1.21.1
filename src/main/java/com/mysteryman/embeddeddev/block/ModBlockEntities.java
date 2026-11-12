package com.mysteryman.embeddeddev.block;

import com.mysteryman.embeddeddev.EmbeddedDev;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EmbeddedDev.MODID);
    public static final Supplier<BlockEntityType<SystemBoardEntity>> BOARD = TYPES.register("system_board", () ->
            BlockEntityType.Builder.of(SystemBoardEntity::new, ModBlocks.SystemBoard51.get()).build(null));
    public static final Supplier<BlockEntityType<RedstonePortEntity>> PORT = TYPES.register("redstone_port", () ->
            BlockEntityType.Builder.of(RedstonePortEntity::new, ModBlocks.REDSTONE_PORT.get()).build(null));
    public static final Supplier<BlockEntityType<OledEntity>> OLED = TYPES.register("oled", () ->
            BlockEntityType.Builder.of(OledEntity::new, ModBlocks.OLED.get()).build(null));
    public static final Supplier<BlockEntityType<SensorEntity>> SENSOR = TYPES.register("i2c_sensor", () ->
            BlockEntityType.Builder.of(SensorEntity::new, ModBlocks.SENSOR.get()).build(null));
    public static final Supplier<BlockEntityType<PeripheralEntity>> PERIPHERAL = TYPES.register("peripheral", () ->
            BlockEntityType.Builder.of(PeripheralEntity::new, ModBlocks.BUTTON.get(), ModBlocks.LED.get(),
                    ModBlocks.DS1302.get(), ModBlocks.PCF8591.get()).build(null));
    public static final Supplier<BlockEntityType<Ds18b20Entity>> DS18B20 = TYPES.register("ds18b20", () ->
            BlockEntityType.Builder.of(Ds18b20Entity::new, ModBlocks.DS18B20.get()).build(null));
    public static final Supplier<BlockEntityType<SevenSegmentEntity>> SEVEN_SEGMENT = TYPES.register("seven_segment", () ->
            BlockEntityType.Builder.of(SevenSegmentEntity::new, ModBlocks.SEVEN_SEGMENT.get()).build(null));
    private ModBlockEntities() {}
}
