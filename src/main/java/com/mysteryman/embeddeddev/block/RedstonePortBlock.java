package com.mysteryman.embeddeddev.block;

import com.mysteryman.embeddeddev.wire.CableNetwork;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class RedstonePortBlock extends BaseEntityBlock {
    public static final MapCodec<RedstonePortBlock> CODEC = simpleCodec(RedstonePortBlock::new);
    public RedstonePortBlock(Properties p) { super(p); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Block.box(2, 0, 2, 14, 5, 14); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new RedstonePortEntity(p, s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, ModBlockEntities.PORT.get(), RedstonePortEntity::tick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (!l.isClientSide && player.mayBuild() && l.getBlockEntity(p) instanceof RedstonePortEntity port) port.configure(player);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override protected boolean isSignalSource(BlockState s) { return true; }
    @Override protected int getSignal(BlockState s, BlockGetter l, BlockPos p, Direction d) {
        return l.getBlockEntity(p) instanceof RedstonePortEntity port ? port.output(d) : 0;
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        CableNetwork.endpointRemoved(s, l, p, next);
        super.onRemove(s, l, p, next, moving); if (!s.is(next.getBlock())) l.updateNeighborsAt(p, this);
    }
}
