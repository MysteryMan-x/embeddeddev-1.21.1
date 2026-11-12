package com.mysteryman.embeddeddev.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.level.block.Block;
import com.mysteryman.embeddeddev.network.BoardNetwork;
import com.mysteryman.embeddeddev.wire.CableNetwork;

public final class SystemBoardBlock extends BaseEntityBlock {
    public static final MapCodec<SystemBoardBlock> CODEC = simpleCodec(SystemBoardBlock::new);
    public SystemBoardBlock(Properties p) { super(p); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Block.box(1, 0, 1, 15, 5, 15); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return new SystemBoardEntity(pos, s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, ModBlockEntities.BOARD.get(), SystemBoardEntity::tick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && l.getBlockEntity(p) instanceof SystemBoardEntity board)
            BoardNetwork.sendState(server, board, true, "");
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override protected boolean isSignalSource(BlockState s) { return true; }
    @Override protected int getSignal(BlockState s, BlockGetter l, BlockPos p, Direction towardSource) {
        return l.getBlockEntity(p) instanceof SystemBoardEntity b ? b.output(towardSource.getOpposite().get3DDataValue()) : 0;
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState s) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState s, Level l, BlockPos p) {
        return l.getBlockEntity(p) instanceof SystemBoardEntity b && b.isRunning() ? 15 : 0;
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        CableNetwork.endpointRemoved(s, l, p, next);
        super.onRemove(s, l, p, next, moving);
        if (!s.is(next.getBlock())) l.updateNeighborsAt(p, this);
    }
}
