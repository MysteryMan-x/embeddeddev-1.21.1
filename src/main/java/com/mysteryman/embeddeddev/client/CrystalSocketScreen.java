package com.mysteryman.embeddeddev.client;

import com.mysteryman.embeddeddev.menu.CrystalSocketMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CrystalSocketScreen extends AbstractContainerScreen<CrystalSocketMenu> {
    private final BoardScreen parent;

    public CrystalSocketScreen(CrystalSocketMenu menu, Inventory inventory, Component title, BoardScreen parent) {
        super(menu, inventory, title);
        this.parent = parent;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(BoardScreen.closeButton(leftPos + imageWidth - 24, topPos + 4, this::onClose));
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF18232D);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, 0xFF43D9BC);
        for (var slot : menu.slots) {
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1,
                    leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF405461);
            graphics.fill(leftPos + slot.x, topPos + slot.y,
                    leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF0E171E);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 8, 0xFFE0F5F0, false);
        graphics.drawCenteredString(font, Component.translatable("gui.embeddeddev.socket_hint"), imageWidth / 2, 22, 0xFFA4B8C6);
        graphics.drawString(font, playerInventoryTitle, 8, 72, 0xFFA4B8C6, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override public void onClose() {
        super.onClose();
        if (parent != null) minecraft.setScreen(parent);
    }
}
