package com.mysteryman.embeddeddev.block;

import com.mojang.serialization.MapCodec;
import com.mysteryman.embeddeddev.wire.CableNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class BusModuleBlock extends BaseEntityBlock {
    public enum Kind { MPU6050, ALTIMETER, MOTOR }
    private final Kind kind;
    private final MapCodec<BusModuleBlock> codec;
    public BusModuleBlock(Properties p,Kind kind) { super(p); this.kind=kind; codec=simpleCodec(properties->new BusModuleBlock(properties,kind)); }
    public Kind kind() { return kind; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) { return box(1,0,1,15,5,15); }
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s) { return new BusModuleEntity(p,s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t) {
        return l.isClientSide?null:createTickerHelper(t,ModBlockEntities.BUS_MODULE.get(),BusModuleEntity::tick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit) {
        if(!l.isClientSide && l.getBlockEntity(p) instanceof BusModuleEntity e) {
            if(player.isShiftKeyDown()) e.changeAddress();
            player.displayClientMessage(Component.translatable("message.embeddeddev.bus_module",e.getBlockState().getBlock().getName(),
                    String.format(java.util.Locale.ROOT,"%02XH",e.i2cDevice().address()),e.status()),true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving) {
        if(!s.is(next.getBlock()) && l.getBlockEntity(p) instanceof BusModuleEntity e) e.stopMotors();
        CableNetwork.endpointRemoved(s,l,p,next); super.onRemove(s,l,p,next,moving);
    }
}
