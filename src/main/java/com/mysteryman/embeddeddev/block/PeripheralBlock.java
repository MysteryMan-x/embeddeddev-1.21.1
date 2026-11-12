package com.mysteryman.embeddeddev.block;

import com.mysteryman.embeddeddev.wire.CableNetwork;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PeripheralBlock extends BaseEntityBlock {
    public enum Kind { BUTTON, LED, DS1302, PCF8591 }
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    private final Kind kind;
    private final MapCodec<PeripheralBlock> codec;

    public PeripheralBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new PeripheralBlock(p, kind));
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    public Kind kind() { return kind; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (kind == Kind.BUTTON && level.getBlockEntity(pos) instanceof PeripheralEntity module) return module.buttons().shape();
        return Block.box(1, 0, 1, 15, 5, 15);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PeripheralEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.PERIPHERAL.get(), PeripheralEntity::tick);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PeripheralEntity module) module.interact(player, hit);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected boolean isSignalSource(BlockState state) { return kind == Kind.PCF8591; }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction towardSource) {
        if (kind == Kind.PCF8591 && towardSource == Direction.DOWN && level.getBlockEntity(pos) instanceof PeripheralEntity module) {
            return module.analogOutput();
        }
        return 0;
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        CableNetwork.endpointRemoved(state, level, pos, next);
        super.onRemove(state, level, pos, next, moving);
        if (kind == Kind.PCF8591 && !state.is(next.getBlock())) level.updateNeighborsAt(pos, this);
    }
}
