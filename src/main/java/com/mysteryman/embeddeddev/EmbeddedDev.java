package com.mysteryman.embeddeddev;

import com.mojang.logging.LogUtils;
import com.mysteryman.embeddeddev.block.*;
import com.mysteryman.embeddeddev.item.ModItems;
import com.mysteryman.embeddeddev.menu.ModMenus;
import com.mysteryman.embeddeddev.network.BoardNetwork;
import com.mysteryman.embeddeddev.network.WireNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(EmbeddedDev.MODID)
public final class EmbeddedDev {
    public static final String MODID = "embeddeddev";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    static {
        TABS.register("example_tab", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.embeddeddev"))
                .icon(() -> ModItems.STC89C52RC.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    output.accept(ModItems.STC89C52RC.get());
                    output.accept(ModBlocks.SystemBoard51.get());
                    output.accept(ModBlocks.REDSTONE_PORT.get());
                    output.accept(ModBlocks.OLED.get());
                    output.accept(ModBlocks.SENSOR.get());
                    output.accept(ModBlocks.BUTTON.get());
                    output.accept(ModBlocks.LED.get());
                    output.accept(ModBlocks.DS1302.get());
                    output.accept(ModBlocks.PCF8591.get());
                    output.accept(ModBlocks.DS18B20.get());
                    output.accept(ModItems.WIRE_COIL.get());
                    output.accept(ModItems.WIRE_CUTTER.get());
                    output.accept(ModBlocks.WIRE_RELAY.get());
                    output.accept(ModItems.MANUAL.get());
                    output.accept(ModItems.CRYSTAL_6.get());
                    output.accept(ModItems.CRYSTAL_11.get());
                    output.accept(ModItems.CRYSTAL_12.get());
                    output.accept(ModItems.CRYSTAL_22.get());
                    output.accept(ModItems.CRYSTAL_24.get());
                    output.accept(ModItems.COMPASS.get());
                    output.accept(ModItems.CIRCUIT_BOARD.get());
                    output.accept(ModItems.SILICON_WAFER.get());
                    output.accept(ModItems.RAW_SILICON.get());
                    output.accept(ModBlocks.SILICON_ORE.get());
                    output.accept(ModBlocks.DEEPSLATE_SILICON_ORE.get());
                }).build());
    }
    public EmbeddedDev(IEventBus bus, ModContainer container) {
        ModBlocks.register(bus); ModItems.register(bus); ModBlockEntities.TYPES.register(bus); TABS.register(bus);
        ModMenus.MENUS.register(bus);
        bus.addListener(BoardNetwork::register);
        NeoForge.EVENT_BUS.addListener(WireNetwork::tickPlayer);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }
}
