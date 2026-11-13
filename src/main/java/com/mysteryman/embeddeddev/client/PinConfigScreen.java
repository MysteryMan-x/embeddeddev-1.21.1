package com.mysteryman.embeddeddev.client;

import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.mcu.FaceBinding.Kind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

final class PinConfigScreen extends Screen implements DeviceStateScreen {
    private final FaceConfigScreen parent;
    private final int index;
    private final Kind storedKind;
    private String label;
    private int channel, x, y, w;
    private EditBox name;
    private Component error = Component.empty();

    PinConfigScreen(FaceConfigScreen parent, int index, FaceBinding.Pin original) {
        super(Component.translatable(index < 0 ? "gui.embeddeddev.add_pin" : "gui.embeddeddev.edit_pin"));
        this.parent = parent;
        this.index = index;
        label = original == null ? "" : original.name();
        storedKind = original == null ? Kind.GPIO : original.kind();
        channel = original == null ? 0 : original.channel();
    }

    @Override public boolean matches(BlockPos pos) { return parent.matches(pos); }
    @Override public void update(CompoundTag data) { parent.update(data); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { parent.tick(); }
    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override protected void init() {
        w = Math.min(480, width - 12);
        x = (width - w) / 2;
        y = Math.max(4, (height - 226) / 2);
        addRenderableWidget(BoardScreen.closeButton(x + w - 28, y + 5, this::onClose));
        name = new EditBox(font, x + 10, y + 42, w - 20, 18, Component.translatable("gui.embeddeddev.pin_name"));
        name.setMaxLength(FaceBinding.MAX_NAME_LENGTH);
        name.setHint(Component.translatable("gui.embeddeddev.pin_example"));
        name.setValue(label);
        name.setResponder(value -> label = value);
        addRenderableWidget(name);
        int buttonWidth = (w - 27) / 8;
        for (int i = 0; i < Kind.GPIO.channels(); i++) {
            int selectedChannel = i;
            String text = (channel == i ? "✓ " : "") + Kind.GPIO.channelName(i);
            addRenderableWidget(Button.builder(Component.literal(text), b -> {
                channel = selectedChannel;
                rebuildWidgets();
            }).bounds(x + 10 + (i % 8) * (buttonWidth + 1), y + 105 + (i / 8) * 20, buttonWidth, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.save_pin"), b -> save())
                .bounds(x + w / 2 - 87, y + 196, 84, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.cancel"), b -> onClose())
                .bounds(x + w / 2 + 3, y + 196, 84, 20).build());
    }

    @Override protected void setInitialFocus() { setInitialFocus(name); }

    private void save() {
        try {
            parent.savePin(index, new FaceBinding.Pin(label, storedKind, channel));
            onClose();
        } catch (IllegalArgumentException e) {
            error = Component.translatable(e.getMessage());
        }
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        super.renderBackground(g, mx, my, dt);
        g.fill(x, y, x + w, y + 226, 0xF018232D);
        g.fill(x, y, x + w, y + 2, 0xFF43D9BC);
        g.drawString(font, title, x + 10, y + 10, 0xFFE0F5F0, false);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float dt) {
        super.render(g, mx, my, dt);
        g.drawString(font, Component.translatable("gui.embeddeddev.pin_name"), x + 10, y + 29, 0xFFA4B8C6, false);
        g.drawString(font, BoardScreen.kindName(Kind.GPIO), x + 10, y + 70, 0xFFE0F5F0, false);
        g.drawString(font, Component.translatable("gui.embeddeddev.pin_channel", Kind.GPIO.channelName(channel)),
                x + 10, y + 92, 0xFFA4B8C6, false);
        g.drawString(font, font.plainSubstrByWidth(error.getString(), w - 20), x + 10, y + 183, 0xFFFF8888, false);
    }
}
