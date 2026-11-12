package com.mysteryman.embeddeddev.block;

import com.mysteryman.embeddeddev.mcu.GpioDevice;
import com.mysteryman.embeddeddev.mcu.SevenSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

public final class SevenSegmentEntity extends BlockEntity implements GpioModule {
    public final SevenSegment display = new SevenSegment();
    private int segments;
    private boolean connected;

    public SevenSegmentEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.SEVEN_SEGMENT.get(), pos, state); }
    @Override public GpioDevice gpioDevice() { return display; }
    public int segments() { return segments; }
    public boolean connected() { return connected; }

    public void commonAnode(boolean value) {
        if (display.commonAnode() == value) return;
        display.commonAnode(value);
        setChanged();
        if (level != null && !level.isClientSide) {
            updateDisplay();
            sync();
        }
    }

    public void inspect(Player player) {
        if (player.isShiftKeyDown() && player.mayBuild() && level.mayInteract(player, worldPosition)) {
            commonAnode(!display.commonAnode());
        }
        player.displayClientMessage(Component.translatable("message.embeddeddev.seven_segment",
                Component.translatable(display.commonAnode() ? "segment.embeddeddev.anode" : "segment.embeddeddev.cathode"),
                String.format(Locale.ROOT, "%02X", segments),
                Component.translatable(connected ? "segment.embeddeddev.connected" : "segment.embeddeddev.disconnected")), true);
    }

    private void updateDisplay() {
        boolean nextConnected = SystemBoardEntity.connectedModuleBoard(level, worldPosition) != null;
        int nextSegments = nextConnected ? display.segments() : 0;
        if (segments == nextSegments && connected == nextConnected) return;
        segments = nextSegments;
        connected = nextConnected;
        sync();
    }

    private void sync() { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); }
    public static void tick(Level level, BlockPos pos, BlockState state, SevenSegmentEntity entity) { entity.updateDisplay(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("CommonAnode", display.commonAnode());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        display.commonAnode(tag.getBoolean("CommonAnode"));
        segments = 0;
        connected = false;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putBoolean("CommonAnode", display.commonAnode());
        tag.putInt("Segments", segments);
        tag.putBoolean("Connected", connected);
        return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        display.commonAnode(tag.getBoolean("CommonAnode"));
        segments = tag.getInt("Segments") & 255;
        connected = tag.getBoolean("Connected");
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }
}
