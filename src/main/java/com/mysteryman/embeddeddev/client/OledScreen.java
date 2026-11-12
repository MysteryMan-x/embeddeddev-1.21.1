package com.mysteryman.embeddeddev.client;

import com.mysteryman.embeddeddev.network.BoardNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Independent module viewer; no firmware/programmer or motherboard controls. */
public final class OledScreen extends Screen implements DeviceStateScreen {
    private final BlockPos pos;
    private CompoundTag state;
    private int ticks, x, y, w, scale;
    public OledScreen(BoardNetwork.State packet) { super(Component.translatable("gui.embeddeddev.oled_title")); pos = packet.pos(); state = packet.data(); }
    @Override public boolean matches(BlockPos p) { return pos.equals(p); }
    @Override public void update(CompoundTag data) { state = data; }
    @Override protected void init() {
        scale = Math.max(1, Math.min(3, Math.min((width - 48) / 128, (height - 100) / 64)));
        w = 128 * scale + 32; x = (width - w) / 2; y = Math.max(8, (height - (64 * scale + 92)) / 2);
        addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.close"), b -> onClose()).bounds(x + w / 2 - 40, y + 64 * scale + 62, 80, 20).build());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        if (++ticks % 5 == 0) PacketDistributor.sendToServer(new BoardNetwork.Action(pos, BoardNetwork.OLED_POLL, "", ""));
        if (minecraft.player == null || minecraft.player.distanceToSqr(pos.getCenter()) > 64) onClose();
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        super.renderBackground(g, mx, my, dt);
        g.fill(x, y, x + w, y + 64 * scale + 88, 0xF018232D); g.fill(x, y, x + w, y + 2, 0xFF72DFF3);
        g.drawString(font, title, x + 12, y + 10, 0xFFE0F5F0, false);
        g.fill(x + 14, y + 28, x + w - 14, y + 32 + 64 * scale, 0xFF02090F);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float dt) {
        super.render(g, mx, my, dt);
        byte[] pixels = state.getByteArray("Pixels");
        if (state.getBoolean("Enabled") && pixels.length == 1024) {
            for (int row = 0; row < 64; row++) for (int col = 0; col < 128; col++)
                if ((pixels[(row >>> 3) * 128 + col] & (1 << (row & 7))) != 0)
                    g.fill(x + 16 + col * scale, y + 30 + row * scale, x + 16 + (col + 1) * scale, y + 30 + (row + 1) * scale, 0xFF9DEEFF);
        }
        Component status = Component.translatable(state.getBoolean("Connected") ? "gui.embeddeddev.oled_connected" : "gui.embeddeddev.oled_unconnected");
        g.drawString(font, font.plainSubstrByWidth(status.getString(), w - 24), x + 12, y + 64 * scale + 41, 0xFFA4B8C6, false);
    }
}
