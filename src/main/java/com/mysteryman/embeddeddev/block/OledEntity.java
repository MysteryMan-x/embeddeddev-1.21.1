package com.mysteryman.embeddeddev.block;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.mysteryman.embeddeddev.mcu.*;

public final class OledEntity extends BlockEntity implements I2cModule {
    public final Ssd1306 display = new Ssd1306();
    public final byte[] pixels = new byte[1024];
    public boolean enabled, connected;
    private long lastRevision = -1;
    public OledEntity(BlockPos p, BlockState s) { super(ModBlockEntities.OLED.get(), p, s); }
    @Override public I2cDevice i2cDevice() { return display; }
    public static void tick(Level l, BlockPos p, BlockState s, OledEntity oled) {
        if (oled.lastRevision != oled.display.revision()) oled.setChanged();
        if ((l.getGameTime() & 3) != 0) return;
        boolean connected = SystemBoardEntity.connectedModuleBoard(l, p) != null;
        if (oled.lastRevision != oled.display.revision() || oled.connected != connected) {
            oled.lastRevision = oled.display.revision(); oled.enabled = oled.display.enabled; oled.connected = connected;
            System.arraycopy(oled.display.frame(), 0, oled.pixels, 0, 1024);
            oled.setChanged(); l.sendBlockUpdated(p, s, s, 2);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r); tag.putByteArray("Pixels", pixels); tag.putBoolean("Enabled", enabled); tag.putBoolean("Connected", connected);
        tag.putByteArray("DisplayRam", display.ram); tag.putIntArray("DisplayRegisters", display.saveRegisters());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r); byte[] saved = tag.getByteArray("Pixels");
        if (saved.length == 1024) System.arraycopy(saved, 0, pixels, 0, 1024);
        enabled = tag.getBoolean("Enabled");
        connected = tag.getBoolean("Connected");
        byte[] ram = tag.contains("DisplayRam") ? tag.getByteArray("DisplayRam") : saved;
        if (ram.length == 1024) System.arraycopy(ram, 0, display.ram, 0, 1024);
        if (tag.contains("DisplayRegisters")) display.restoreRegisters(tag.getIntArray("DisplayRegisters"));
        else display.legacyControl(enabled ? 1 : 0);
        lastRevision = -1;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r) { return saveCustomOnly(r); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
