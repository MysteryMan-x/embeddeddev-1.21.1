package com.mysteryman.embeddeddev.block;

import com.mysteryman.embeddeddev.wire.CableNetwork;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class SensorBlock extends BaseEntityBlock {
    public static final MapCodec<SensorBlock> CODEC = simpleCodec(SensorBlock::new);
    public SensorBlock(Properties p) { super(p); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Block.box(2, 0, 2, 14, 6, 14); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new SensorEntity(p, s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, ModBlockEntities.SENSOR.get(), SensorEntity::tick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (!l.isClientSide && l.getBlockEntity(p) instanceof SensorEntity sensor) {
            boolean linked = SystemBoardEntity.connectedModuleBoard(l, p) != null;
            Component wiring = Component.translatable(linked
                    ? "gui.embeddeddev.module_connected" : "gui.embeddeddev.module_unconnected");
            player.displayClientMessage(Component.translatable("message.embeddeddev.sensor",
                    sensor.sensor.values[0], sensor.sensor.values[1], sensor.sensor.values[2], wiring), true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        CableNetwork.endpointRemoved(state, level, pos, next);
        super.onRemove(state, level, pos, next, moving);
    }
}
