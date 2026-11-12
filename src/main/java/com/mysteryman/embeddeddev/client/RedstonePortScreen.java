package com.mysteryman.embeddeddev.client;

import com.mysteryman.embeddeddev.network.BoardNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RedstonePortScreen extends Screen {
    private final BlockPos pos;
    private Direction face;
    private boolean reads;
    private int x, y, w;

    public RedstonePortScreen(BoardNetwork.State packet) {
        super(Component.translatable("wire.embeddeddev.port_title"));
        pos = packet.pos();
        face = Direction.from3DDataValue(packet.data().getInt("Face"));
        reads = packet.data().getBoolean("Reads");
    }

    @Override protected void init() {
        w = Math.min(300, width - 16);
        x = (width - w) / 2;
        y = Math.max(4, (height - 122) / 2);
        addRenderableWidget(CycleButton.<Boolean>builder(in -> Component.translatable(in ? "gui.embeddeddev.input" : "gui.embeddeddev.output"))
                .withValues(false, true).withInitialValue(reads)
                .create(x + 10, y + 29, w - 20, 20, Component.translatable("wire.embeddeddev.port_mode"), (b, value) -> reads = value));
        addRenderableWidget(CycleButton.<Direction>builder(d -> BoardScreen.faceName(d.get3DDataValue()))
                .withValues(Direction.values()).withInitialValue(face)
                .create(x + 10, y + 53, w - 20, 20, Component.translatable("wire.embeddeddev.port_face"), (b, value) -> face = value));
        addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.apply"), b -> {
            PacketDistributor.sendToServer(new BoardNetwork.Action(pos, BoardNetwork.PORT_CONFIGURE,
                    Integer.toString(face.get3DDataValue()), reads ? "in" : "out"));
            onClose();
        }).bounds(x + w / 2 - 84, y + 94, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.cancel"), b -> onClose())
                .bounds(x + w / 2 + 4, y + 94, 80, 20).build());
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        super.renderBackground(g, mx, my, dt);
        g.fill(x, y, x + w, y + 122, 0xF018232D);
        g.fill(x, y, x + w, y + 2, 0xFF43D9BC);
        g.drawString(font, title, x + 10, y + 9, 0xFFE0F5F0, false);
        g.drawString(font, Component.translatable("wire.embeddeddev.port_hint"), x + 10, y + 78, 0xFFA4B8C6, false);
    }
}
