package com.mysteryman.embeddeddev;

import com.mysteryman.embeddeddev.block.ModBlockEntities;
import com.mysteryman.embeddeddev.client.*;
import com.mysteryman.embeddeddev.item.ModItems;
import com.mysteryman.embeddeddev.menu.ModMenus;
import com.mysteryman.embeddeddev.menu.CrystalSocketMenu;
import com.mysteryman.embeddeddev.network.BoardNetwork;
import com.mysteryman.embeddeddev.network.WireNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.*;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = EmbeddedDev.MODID, dist = Dist.CLIENT)
public final class EmbeddedDevClient {
    public EmbeddedDevClient(IEventBus bus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        bus.addListener(EmbeddedDevClient::registerRenderers);
        bus.addListener(EmbeddedDevClient::registerCompass);
        bus.addListener(EmbeddedDevClient::registerMenus);
        NeoForge.EVENT_BUS.addListener(WireRenderer::render);
        NeoForge.EVENT_BUS.addListener(WireRenderer::hud);
        WireNetwork.clientReceiver = WireRenderer::receive;
        BoardNetwork.clientReceiver = packet -> {
            Minecraft mc = Minecraft.getInstance();
            if (packet.open()) mc.setScreen(switch (packet.data().getString("Screen")) {
                case "oled" -> new OledScreen(packet);
                case "port" -> new RedstonePortScreen(packet);
                case "buttons" -> new ButtonPanelScreen(packet);
                case "manual" -> new ManualScreen();
                default -> new BoardScreen(packet);
            });
            else if (mc.screen instanceof DeviceStateScreen screen && screen.matches(packet.pos())) screen.update(packet.data());
        };
    }
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.OLED.get(), OledRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PERIPHERAL.get(), ButtonPanelRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SEVEN_SEGMENT.get(), SevenSegmentRenderer::new);
    }

    private static void registerCompass(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModItems.COMPASS.get(),
                ResourceLocation.fromNamespaceAndPath(EmbeddedDev.MODID, "north"),
                (stack, level, entity, seed) -> entity == null ? 0.0F
                        : Mth.positiveModulo(0.5F - entity.getVisualRotationYInDegrees() / 360.0F, 1.0F)));
    }

    private static void registerMenus(RegisterMenuScreensEvent event) {
        event.<CrystalSocketMenu, CrystalSocketScreen>register(ModMenus.CRYSTAL_SOCKET.get(), (menu, inventory, title) ->
                new CrystalSocketScreen(menu, inventory, title,
                        Minecraft.getInstance().screen instanceof BoardScreen board ? board : null));
    }
}
