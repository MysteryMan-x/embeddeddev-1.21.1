package com.mysteryman.embeddeddev.block;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import com.mysteryman.embeddeddev.network.BoardNetwork;
import com.mysteryman.embeddeddev.wire.BoardWiring;
import com.mysteryman.embeddeddev.wire.CableNetwork;

public final class RedstonePortEntity extends BlockEntity {
    private int output;
    private Direction face = Direction.NORTH;
    private boolean reads, wired;
    public RedstonePortEntity(BlockPos p, BlockState s) { super(ModBlockEntities.PORT.get(), p, s); }
    public int output() { return output; }
    public int output(Direction towardSource) { return !wired || towardSource == face.getOpposite() ? output : 0; }
    public Direction face() { return face; }
    public boolean reads() { return reads; }
    public int wiredInput() {
        if (level == null || !reads) return 0;
        BlockPos neighbor = worldPosition.relative(face);
        return level.hasChunkAt(neighbor) ? level.getSignal(neighbor, face) : 0;
    }
    public void wireConfigure(Direction face, boolean reads) {
        this.face = face;
        this.reads = reads;
        setChanged();
        if (level != null) {
            refresh();
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }
    public BlockPos connectedBoardPos() {
        SystemBoardEntity board = level == null ? null : SystemBoardEntity.adjacentBoard(level, worldPosition);
        return board == null ? null : board.getBlockPos();
    }
    public int inputSignal() {
        if (level == null) return 0;
        int power = 0;
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            if (level.hasChunkAt(p) && !(level.getBlockEntity(p) instanceof SystemBoardEntity)) power = Math.max(power, level.getSignal(p, d));
        }
        return power;
    }
    public void configure(Player player) {
        if (!(player instanceof ServerPlayer server)) return;
        SystemBoardEntity board = level == null ? null : SystemBoardEntity.adjacentBoard(level, worldPosition);
        if (board != null && !player.isShiftKeyDown() && !hasCable()) BoardNetwork.sendState(server, board, true, "");
        else BoardNetwork.sendPortState(server, this, true);
    }
    private boolean hasCable() {
        return level instanceof ServerLevel server && CableNetwork.get(server).has(new CableNetwork.End(worldPosition, CableNetwork.REDSTONE));
    }
    private void refresh() {
        int next = 0;
        boolean hadCable = wired;
        wired = hasCable();
        if (wired && !reads && level instanceof ServerLevel server) {
            var end = new CableNetwork.End(worldPosition, CableNetwork.REDSTONE);
            var route = CableNetwork.get(server).controller(level, end);
            if (route != null && level.getBlockEntity(route.end().pos()) instanceof SystemBoardEntity board && board.isRunning()) {
                for (int id : route.lanes().keySet()) {
                    var pin = CableNetwork.pin(board, route.end().socket(), id);
                    if (pin != null && BoardWiring.accepts(this, pin)) next = board.hardware.outputPin(pin);
                }
            }
        } else if (!wired && level != null) {
            SystemBoardEntity board = SystemBoardEntity.adjacentBoard(level, worldPosition);
            if (board != null) for (Direction side : Direction.values()) {
                if (board.getBlockPos().relative(side).equals(worldPosition)) next = board.output(side.get3DDataValue());
            }
        }
        if (next != output || hadCable != wired) {
            output = next; setChanged(); level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }
    public static void tick(Level l, BlockPos p, BlockState s, RedstonePortEntity port) { port.refresh(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        tag.putInt("WireFace", face.get3DDataValue());
        tag.putBoolean("WireReads", reads);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r); output = 0;
        if (tag.contains("WireFace")) face = Direction.from3DDataValue(tag.getInt("WireFace"));
        reads = tag.getBoolean("WireReads");
    }
}
