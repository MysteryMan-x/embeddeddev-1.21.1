package com.mysteryman.embeddeddev.block;

import com.mysteryman.embeddeddev.EmbeddedDev;
import com.mysteryman.embeddeddev.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EmbeddedDev.MODID);
    public static final DeferredBlock<WireRelayBlock> WIRE_RELAY = register("wire_relay", () -> new WireRelayBlock(properties()));
    public static final DeferredBlock<Block> SILICON_ORE = register("silicon_ore", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F, 3.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> DEEPSLATE_SILICON_ORE = register("deepslate_silicon_ore", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.5F, 3.0F)
                    .sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<SystemBoardBlock> SystemBoard51 = register("51systemboard", () -> new SystemBoardBlock(properties()));
    public static final DeferredBlock<RedstonePortBlock> REDSTONE_PORT = register("redstone_port", () -> new RedstonePortBlock(properties()));
    public static final DeferredBlock<OledBlock> OLED = register("oled", () -> new OledBlock(properties()));
    public static final DeferredBlock<SensorBlock> SENSOR = register("i2c_sensor", () -> new SensorBlock(properties()));
    public static final DeferredBlock<BusModuleBlock> MPU6050 = register("mpu6050", () -> new BusModuleBlock(properties(),BusModuleBlock.Kind.MPU6050));
    public static final DeferredBlock<BusModuleBlock> ALTIMETER = register("altimeter", () -> new BusModuleBlock(properties(),BusModuleBlock.Kind.ALTIMETER));
    public static final DeferredBlock<BusModuleBlock> MOTOR_INTERFACE = register("motor_interface", () -> new BusModuleBlock(properties(),BusModuleBlock.Kind.MOTOR));
    public static final DeferredBlock<PeripheralBlock> BUTTON = register("button_module",
            () -> new PeripheralBlock(properties(), PeripheralBlock.Kind.BUTTON));
    public static final DeferredBlock<PeripheralBlock> LED = register("led_module",
            () -> new PeripheralBlock(properties().lightLevel(s -> s.getValue(PeripheralBlock.ACTIVE) ? 10 : 0),
                    PeripheralBlock.Kind.LED));
    public static final DeferredBlock<PeripheralBlock> DS1302 = register("ds1302",
            () -> new PeripheralBlock(properties(), PeripheralBlock.Kind.DS1302));
    public static final DeferredBlock<PeripheralBlock> PCF8591 = register("pcf8591",
            () -> new PeripheralBlock(properties(), PeripheralBlock.Kind.PCF8591));
    public static final DeferredBlock<Ds18b20Block> DS18B20 = register("ds18b20", () -> new Ds18b20Block(properties()));
    public static final DeferredBlock<SevenSegmentBlock> SEVEN_SEGMENT = register("seven_segment", () -> new SevenSegmentBlock(properties()));
    private static BlockBehaviour.Properties properties() { return BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion(); }
    private static <T extends Block> DeferredBlock<T> register(String name, Supplier<T> factory) {
        DeferredBlock<T> block = BLOCKS.register(name, factory);
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus); }
    private ModBlocks() {}
}
