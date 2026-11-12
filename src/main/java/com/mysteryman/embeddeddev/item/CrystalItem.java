package com.mysteryman.embeddeddev.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class CrystalItem extends Item {
    public static final int DEFAULT_HZ = 11_059_200;
    private final int frequencyHz;

    public CrystalItem(int frequencyHz) {
        super(new Item.Properties().stacksTo(1));
        this.frequencyHz = frequencyHz;
    }

    public int frequencyHz() { return frequencyHz; }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.embeddeddev.crystal.hint"));
    }
}
