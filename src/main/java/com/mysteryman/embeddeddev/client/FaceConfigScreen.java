package com.mysteryman.embeddeddev.client;

import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.wire.PinGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Edit one side as a draft and apply its complete wiring in a single update. */
public final class FaceConfigScreen extends Screen implements DeviceStateScreen {
    private static final int ROWS = 4;
    private final BoardScreen parent;
    private final int face;
    private final FaceBinding[] drafts = new FaceBinding[PinGroup.COUNT];
    private final String[] names = new String[PinGroup.COUNT];
    private int group, page, x, y, w;
    private Component error = Component.empty();

    public FaceConfigScreen(BoardScreen parent, int face, List<PinGroup> groups) {
        super(Component.translatable("gui.embeddeddev.face_title", BoardScreen.faceName(face)));
        this.parent = parent;
        this.face = face;
        for (int i = 0; i < PinGroup.COUNT; i++) { drafts[i] = groups.get(i).binding(); names[i] = groups.get(i).name(); }
    }

    @Override public boolean matches(BlockPos p) { return parent.matches(p); }
    @Override public void update(CompoundTag data) { parent.update(data); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { parent.tick(); }
    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override protected void init() {
        w = Math.min(480, width - 12);
        x = (width - w) / 2;
        y = Math.max(4, (height - 226) / 2);
        int count = drafts[group].pins().size();
        int lastPage = Math.max(0, (count - 1) / ROWS);
        page = Math.min(page, lastPage);
        addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal("G" + (i + 1)))
                .withValues(0, 1, 2, 3).withInitialValue(group)
                .create(x + 10, y + 28, (w - 24) / 2, 20, Component.translatable("groups.embeddeddev.group"), (b, value) -> {
                    group = value; page = 0; error = Component.empty(); rebuildWidgets();
                }));
        var name = new EditBox(font, x + w / 2 + 2, y + 29, w / 2 - 12, 18, Component.translatable("groups.embeddeddev.name"));
        name.setMaxLength(FaceBinding.MAX_NAME_LENGTH);
        name.setValue(names[group]);
        name.setResponder(value -> names[group] = value);
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.add_pin"),
                b -> minecraft.setScreen(new PinConfigScreen(this, -1, null)))
                .bounds(x + 10, y + 53, w - 20, 18).build()).active = count < FaceBinding.MAX_PINS;

        for (int i = page * ROWS; i < Math.min(count, (page + 1) * ROWS); i++) {
            int index = i;
            var pin = drafts[group].pins().get(index);
            int row = y + 76 + (i % ROWS) * 22;
            String description = pin.name() + "  ·  " + pin.channelName() + "  ·  " + BoardScreen.kindName(pin.kind()).getString();
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(description, w - 52)),
                    b -> minecraft.setScreen(new PinConfigScreen(this, index, pin)))
                    .tooltip(Tooltip.create(Component.literal(description)))
                    .bounds(x + 10, row, w - 46, 20).build());
            addRenderableWidget(Button.builder(Component.literal("×"), b -> removePin(index))
                    .tooltip(Tooltip.create(Component.translatable("gui.embeddeddev.remove_pin", pin.name())))
                    .bounds(x + w - 32, row, 22, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1))
                .bounds(x + 10, y + 169, 24, 18).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1))
                .bounds(x + w - 34, y + 169, 24, 18).build()).active = page < lastPage;
        addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.apply"), b -> {
            try {
                var result = new ArrayList<PinGroup>();
                for (int i = 0; i < PinGroup.COUNT; i++) result.add(new PinGroup(names[i], drafts[i]));
                parent.configureGroups(face, result);
                onClose();
            } catch (IllegalArgumentException exception) { error = Component.translatable(exception.getMessage()); }
        }).bounds(x + w / 2 - 87, y + 196, 84, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.cancel"), b -> onClose())
                .bounds(x + w / 2 + 3, y + 196, 84, 20).build());
    }

    private void changePage(int offset) {
        page += offset;
        rebuildWidgets();
    }

    private void removePin(int index) {
        var pins = new ArrayList<>(drafts[group].pins());
        pins.remove(index);
        drafts[group] = new FaceBinding(pins);
        rebuildWidgets();
    }

    void savePin(int index, FaceBinding.Pin pin) {
        var pins = new ArrayList<>(drafts[group].pins());
        int target = index < 0 ? pins.size() : index;
        if (index < 0) pins.add(pin);
        else pins.set(index, pin);
        drafts[group] = new FaceBinding(pins);
        page = target / ROWS;
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        super.renderBackground(g, mx, my, dt);
        g.fill(x, y, x + w, y + 226, 0xF018232D);
        g.fill(x, y, x + w, y + 2, 0xFF43D9BC);
        g.drawString(font, title, x + 10, y + 10, 0xFFE0F5F0, false);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float dt) {
        super.render(g, mx, my, dt);
        int count = drafts[group].pins().size();
        if (count == 0) {
            g.drawString(font, Component.translatable("gui.embeddeddev.empty_pins"), x + 10, y + 87, 0xFFA4B8C6, false);
        }
        int pages = Math.max(1, (count + ROWS - 1) / ROWS);
        g.drawCenteredString(font, Component.translatable("gui.embeddeddev.pin_pages", page + 1, pages, count),
                x + w / 2, y + 174, 0xFFA4B8C6);
        g.drawString(font, error, x + 10, y + 185, 0xFFFF8888, false);
    }
}
