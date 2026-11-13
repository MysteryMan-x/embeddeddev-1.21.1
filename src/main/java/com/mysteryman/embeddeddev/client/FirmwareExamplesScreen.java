package com.mysteryman.embeddeddev.client;

import com.mysteryman.embeddeddev.firmware.BuiltinFirmware;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Select first, then explicitly flash from the programmer. */
final class FirmwareExamplesScreen extends Screen implements DeviceStateScreen {
    private final BoardScreen parent;
    private int x, y, w;

    FirmwareExamplesScreen(BoardScreen parent) {
        super(Component.translatable("gui.embeddeddev.examples_title"));
        this.parent = parent;
    }
    @Override public boolean matches(BlockPos pos) { return parent.matches(pos); }
    @Override public void update(CompoundTag data) { parent.update(data); }
    @Override public void tick() { parent.tick(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override protected void init() {
        w = Math.min(480, width - 12); x = (width - w) / 2; y = Math.max(4, (height - 226) / 2);
        addRenderableWidget(BoardScreen.closeButton(x + w - 28, y + 5, this::onClose));
        int row = 0;
        for (var example : BuiltinFirmware.values()) {
            Component label = Component.translatable(example.titleKey()).append("  ·  " + example.fileName());
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label.getString(), w - 32)), button -> {
                parent.selectExample(example); onClose();
            }).tooltip(Tooltip.create(Component.translatable(example.helpKey())))
                    .bounds(x + 10, y + 30 + row++ * 20, w - 20, 18).build());
        }
    }
    @Override public void onFilesDrop(java.util.List<java.nio.file.Path> files) { parent.onFilesDrop(files); onClose(); }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        super.renderBackground(g, mx, my, dt);
        g.fill(x, y, x + w, y + 226, 0xF018232D);
        g.fill(x, y, x + w, y + 2, 0xFF43D9BC);
        g.drawString(font, title, x + 10, y + 10, 0xFFE0F5F0, false);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float dt) {
        super.render(g, mx, my, dt);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("gui.embeddeddev.examples_hint").getString(), w - 20),
                x + 10, y + 206, 0xFFA4B8C6, false);
    }
}
