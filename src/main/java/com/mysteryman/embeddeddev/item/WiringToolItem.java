package com.mysteryman.embeddeddev.item;

import com.mysteryman.embeddeddev.network.WireNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public final class WiringToolItem extends Item {
    private final boolean cutter;

    public WiringToolItem(boolean cutter) {
        super(new Properties().stacksTo(cutter ? 1 : 64));
        this.cutter = cutter;
    }

    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            if (cutter) WireNetwork.cut(player);
            else WireNetwork.click(player, context.getHand(), new BlockHitResult(context.getClickLocation(),
                    context.getClickedFace(), context.getClickedPos(), context.isInside()));
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!cutter && !player.isShiftKeyDown()) return InteractionResultHolder.pass(player.getItemInHand(hand));
        if (player instanceof ServerPlayer server) {
            if (cutter) WireNetwork.cut(server);
            else WireNetwork.cancel(server, true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(cutter ? "wire.embeddeddev.cutter_hint" : "wire.embeddeddev.coil_hint"));
    }
}
