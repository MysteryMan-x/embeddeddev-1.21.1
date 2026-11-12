package com.mysteryman.embeddeddev.block;

import com.mysteryman.embeddeddev.wire.CableNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WireRelayBlock extends Block {
    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 12, 13);

    public WireRelayBlock(Properties properties) { super(properties); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        CableNetwork.endpointRemoved(state, level, pos, next);
        super.onRemove(state, level, pos, next, moving);
    }
}
