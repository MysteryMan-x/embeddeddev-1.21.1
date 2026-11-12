package com.mysteryman.embeddeddev.menu;

import com.mysteryman.embeddeddev.item.CrystalItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class CrystalSocketMenu extends AbstractContainerMenu {
    private final Container socket;

    public CrystalSocketMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(1));
    }

    public CrystalSocketMenu(int id, Inventory inventory, Container socket) {
        super(ModMenus.CRYSTAL_SOCKET.get(), id);
        this.socket = socket;
        addSlot(new Slot(socket, 0, 80, 38) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof CrystalItem; }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
    }

    @Override public boolean stillValid(Player player) { return socket.stillValid(player); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index == 0 ? moveItemStackTo(stack, 1, slots.size(), true)
                : moveItemStackTo(stack, 0, 1, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
