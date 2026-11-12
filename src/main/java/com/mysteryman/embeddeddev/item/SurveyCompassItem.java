package com.mysteryman.embeddeddev.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

public final class SurveyCompassItem extends Item {
    public SurveyCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            Direction facing = player.getDirection();
            String direction = facing.getName();
            player.displayClientMessage(Component.translatable("message.embeddeddev.compass",
                    Component.translatable("gui.embeddeddev." + direction),
                    direction.substring(0, 1).toUpperCase(Locale.ROOT)), true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.embeddeddev.compass.hint").withStyle(ChatFormatting.GRAY));
    }
}
