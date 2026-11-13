package com.mysteryman.embeddeddev.client;

import com.mysteryman.embeddeddev.block.ButtonPanel;
import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.network.BoardNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.stream.IntStream;

public final class ButtonPanelScreen extends Screen implements DeviceStateScreen {
    private final BlockPos pos;
    private final String[] names = new String[ButtonPanel.MAX_KEYS];
    private final int originalCount;
    private int count, x, y, w;
    private Button apply;
    private Component message = Component.empty();

    public ButtonPanelScreen(BoardNetwork.State packet) {
        super(Component.translatable("buttons.embeddeddev.title"));
        pos = packet.pos();
        var labels = packet.data().getList("ButtonNames", Tag.TAG_STRING);
        originalCount = count = Math.clamp(labels.size(), 1, ButtonPanel.MAX_KEYS);
        for (int key = 0; key < names.length; key++) names[key] = key < labels.size() ? labels.getString(key) : ButtonPanel.defaultName(key);
    }

    @Override protected void init() {
        w = Math.min(width - 12, 340);
        x = (width - w) / 2;
        y = Math.max(2, (height - 167) / 2);
        addRenderableWidget(BoardScreen.closeButton(x + w - 28, y + 3, this::onClose));
        addRenderableWidget(CycleButton.<Integer>builder(n -> Component.literal(n.toString()))
                .withValues(IntStream.rangeClosed(1, ButtonPanel.MAX_KEYS).boxed().toList()).withInitialValue(count)
                .create(x + 10, y + 23, w - 20, 20, Component.translatable("buttons.embeddeddev.count"), (b, value) -> {
                    count = value;
                    message = Component.empty();
                    rebuildWidgets();
                }));
        int cellWidth = (w - 28) / 3;
        for (int key = 0; key < count; key++) {
            int index = key;
            int left = x + 10 + ButtonPanel.column(key) * (cellWidth + 4);
            int top = y + 48 + ButtonPanel.row(key) * 26;
            var label = Component.translatable("buttons.embeddeddev.key", key + 1);
            var input = new EditBox(font, left + 15, top + 2, cellWidth - 17, 18, label);
            input.setMaxLength(FaceBinding.MAX_NAME_LENGTH);
            input.setValue(names[key]);
            input.setResponder(value -> names[index] = value);
            addRenderableWidget(input);
        }
        apply = addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.apply"), b -> save())
                .bounds(x + w / 2 - 84, y + 142, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.cancel"), b -> onClose())
                .bounds(x + w / 2 + 4, y + 142, 80, 20).build());
    }

    private void save() {
        try {
            var labels = ButtonPanel.validate(Arrays.asList(names).subList(0, count));
            PacketDistributor.sendToServer(new BoardNetwork.Action(pos, BoardNetwork.BUTTON_CONFIGURE, "", String.join("\n", labels)));
            apply.active = false;
        } catch (IllegalArgumentException error) { message = Component.translatable(error.getMessage()); }
    }

    @Override public boolean matches(BlockPos pos) { return this.pos.equals(pos); }
    @Override public void update(CompoundTag tag) {
        if (!tag.getString("Screen").equals("buttons")) return;
        String result = tag.getString("Message");
        if (result.isEmpty()) onClose();
        else { message = Component.translatable(result); apply.active = true; }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        if (minecraft.player == null || minecraft.player.distanceToSqr(pos.getCenter()) > 64) onClose();
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        super.renderBackground(g, mx, my, dt);
        g.fill(x, y, x + w, y + 167, 0xF018232D);
        g.fill(x, y, x + w, y + 2, 0xFF43D9BC);
        g.drawString(font, title, x + 10, y + 8, 0xFFE0F5F0, false);
        int cellWidth = (w - 28) / 3;
        for (int key = 0; key < names.length; key++) {
            int left = x + 10 + ButtonPanel.column(key) * (cellWidth + 4);
            int top = y + 48 + ButtonPanel.row(key) * 26;
            g.fill(left, top, left + cellWidth, top + 22, key < count ? 0xFF29434D : 0xFF22313A);
            g.drawString(font, Integer.toString(key + 1), left + 5, top + 7, key < count ? 0xFFE0F5F0 : 0xFF667A86, false);
        }
        boolean removed = count < originalCount;
        String footer = message.getString();
        if (footer.isEmpty()) footer = Component.translatable(removed ? "buttons.embeddeddev.shrink" : "buttons.embeddeddev.hint").getString();
        g.drawString(font, font.plainSubstrByWidth(footer, w - 20), x + 10, y + 128, removed ? 0xFFFFC080 : 0xFFA4B8C6, false);
    }
}
