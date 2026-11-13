package com.mysteryman.embeddeddev.client;

import com.mysteryman.embeddeddev.network.WireNetwork;
import com.mysteryman.embeddeddev.wire.CableNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WirePairingScreen extends Screen {
    private static final int ROWS = 5;
    private final long token;
    private final List<CableNetwork.Lane> rows;
    private final List<CableNetwork.Lane> choices;
    private final boolean flipped, complete;
    private final Map<Integer, Integer> mapping = new LinkedHashMap<>();
    private Component error = Component.empty();
    private int page, x, y, w;

    WirePairingScreen(CompoundTag data) {
        super(Component.translatable("wire.embeddeddev.pair_title"));
        token = data.getLong("Token");
        var a = CableNetwork.End.load(data.getCompound("First"));
        var b = CableNetwork.End.load(data.getCompound("Second"));
        var left = lanes(data, "A");
        var right = lanes(data, "B");
        boolean leftDevice = !a.board() && !a.relay(), rightDevice = !b.board() && !b.relay();
        flipped = !leftDevice && (rightDevice || right.size() < left.size());
        rows = flipped ? right : left;
        choices = flipped ? left : right;
        complete = flipped ? rightDevice : leftDevice;
        for (var row : rows) for (var choice : choices) {
            if (row.name().equalsIgnoreCase(choice.name())) mapping.put(row.id(), choice.id());
        }
    }

    private static List<CableNetwork.Lane> lanes(CompoundTag data, String key) {
        List<CableNetwork.Lane> lanes = new ArrayList<>();
        for (Tag tag : data.getList(key, Tag.TAG_COMPOUND)) lanes.add(CableNetwork.Lane.load((CompoundTag) tag));
        return lanes;
    }

    @Override protected void init() {
        w = Math.min(360, width - 16);
        x = (width - w) / 2;
        y = Math.max(4, (height - 201) / 2);
        addRenderableWidget(BoardScreen.closeButton(x + w - 28, y + 5, this::onClose));
        var ids = new ArrayList<Integer>();
        ids.add(-1);
        for (var choice : choices) ids.add(choice.id());
        for (int i = page * ROWS; i < Math.min(rows.size(), (page + 1) * ROWS); i++) {
            var row = rows.get(i);
            var label = Component.literal(font.plainSubstrByWidth(row.name(), w / 3 - 12));
            addRenderableWidget(CycleButton.<Integer>builder(id -> id < 0 ? Component.translatable("wire.embeddeddev.unassigned")
                            : Component.literal(CableNetwork.find(choices, id).name()))
                    .withValues(ids).withInitialValue(mapping.getOrDefault(row.id(), -1))
                    .create(x + 10, y + 40 + (i % ROWS) * 23, w - 20, 20, label, (b, id) -> {
                        if (id < 0) mapping.remove(row.id());
                        else mapping.put(row.id(), id);
                        error = Component.empty();
                    }));
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); })
                .bounds(x + 10, y + 156, 25, 18).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); })
                .bounds(x + w - 35, y + 156, 25, 18).build()).active = (page + 1) * ROWS < rows.size();
        addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.apply"), b -> apply())
                .bounds(x + w / 2 - 84, y + 176, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.cancel"), b -> onClose())
                .bounds(x + w / 2 + 4, y + 176, 80, 20).build());
    }

    private void apply() {
        if (mapping.isEmpty() || complete && mapping.size() != rows.size() || new HashSet<>(mapping.values()).size() != mapping.size()) {
            error = Component.translatable("wire.embeddeddev.invalid_mapping");
            return;
        }
        int[] pairs = new int[mapping.size() * 2];
        int i = 0;
        for (var pair : mapping.entrySet()) {
            pairs[i++] = flipped ? pair.getValue() : pair.getKey();
            pairs[i++] = flipped ? pair.getKey() : pair.getValue();
        }
        PacketDistributor.sendToServer(new WireNetwork.Apply(token, pairs));
        minecraft.setScreen(null);
    }

    @Override public void onClose() {
        PacketDistributor.sendToServer(new WireNetwork.Apply(token, new int[0]));
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        super.renderBackground(g, mx, my, dt);
        g.fill(x, y, x + w, y + 201, 0xF018232D);
        g.fill(x, y, x + w, y + 2, 0xFF43D9BC);
        g.drawString(font, title, x + 10, y + 9, 0xFFE0F5F0, false);
        g.drawString(font, Component.translatable("wire.embeddeddev.pair_hint"), x + 10, y + 23, 0xFFA4B8C6, false);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float dt) {
        super.render(g, mx, my, dt);
        g.drawCenteredString(font, error, x + w / 2, y + 161, 0xFFFF8888);
    }
}
