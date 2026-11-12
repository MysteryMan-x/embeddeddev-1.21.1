package com.mysteryman.embeddeddev.block;

import com.mysteryman.embeddeddev.mcu.Ds1302;
import com.mysteryman.embeddeddev.mcu.GpioDevice;
import com.mysteryman.embeddeddev.mcu.I2cDevice;
import com.mysteryman.embeddeddev.mcu.Pcf8591;
import com.mysteryman.embeddeddev.network.BoardNetwork;
import com.mysteryman.embeddeddev.wire.CableNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.List;

public final class PeripheralEntity extends BlockEntity implements I2cModule, GpioModule {
    private static final Direction[] ADC_INPUTS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private final PeripheralBlock.Kind kind;
    private final GpioDevice gpio;
    private final Pcf8591 adc;
    private final ButtonPanel buttons;
    private long clockRevision;
    private boolean lit;
    private int previousOutput;
    private int[] savedAdcState = new int[0];

    public PeripheralEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PERIPHERAL.get(), pos, state);
        kind = ((PeripheralBlock) state.getBlock()).kind();
        adc = kind == PeripheralBlock.Kind.PCF8591 ? new Pcf8591() : null;
        buttons = kind == PeripheralBlock.Kind.BUTTON ? new ButtonPanel() : null;
        gpio = switch (kind) {
            case DS1302 -> new Ds1302();
            case LED -> new GpioDevice() {
                private final List<String> pins = List.of("LED");
                public String label() { return "LED"; }
                public List<String> terminals() { return pins; }
                public void update(int levels) { lit = (levels & 1) != 0; }
            };
            case BUTTON, PCF8591 -> null;
        };
    }

    public GpioDevice gpioDevice() {
        if (buttons != null) return buttons.device(0);
        if (gpio instanceof Ds1302 clock && level != null) clock.setTick(level.getGameTime());
        return gpio;
    }
    @Override public I2cDevice i2cDevice() { return adc; }
    public int analogOutput() { return adc == null ? 0 : adc.redstoneOutput(); }
    public ButtonPanel buttons() { return buttons; }

    public int buttonAt(BlockHitResult hit) {
        return buttons == null || hit.getDirection() == Direction.DOWN ? -1
                : buttons.keyAt(hit.getLocation().x - worldPosition.getX(), hit.getLocation().z - worldPosition.getZ());
    }

    public void configureButtons(List<String> labels) {
        if (buttons == null || !(level instanceof ServerLevel server)) return;
        int previousCount = buttons.count();
        buttons.configure(labels);
        var network = CableNetwork.get(server);
        for (int key = buttons.count(); key < previousCount; key++) {
            network.removeEndpoint(server, new CableNetwork.End(worldPosition, CableNetwork.buttonSocket(key)), true);
        }
        setChanged();
        syncButtons();
    }

    public void interact(Player player, BlockHitResult hit) {
        switch (kind) {
            case BUTTON -> {
                int key = buttonAt(hit);
                if ((player.isShiftKeyDown() || key < 0) && player instanceof ServerPlayer server) {
                    BoardNetwork.sendButtonState(server, this, true, "");
                } else if (!player.isShiftKeyDown() && buttons.press(key, level.getGameTime())) syncButtons();
            }
            case LED -> player.displayClientMessage(Component.translatable(
                    lit ? "message.embeddeddev.led_on" : "message.embeddeddev.led_off"), true);
            case DS1302 -> {
                Ds1302 clock = (Ds1302) gpioDevice();
                player.displayClientMessage(Component.translatable("message.embeddeddev.rtc", clock.timeLabel()), true);
            }
            case PCF8591 -> {
                if (player.isShiftKeyDown() && player.mayBuild() && level.mayInteract(player, worldPosition)) {
                    adc.nextAddress();
                    setChanged();
                }
                player.displayClientMessage(Component.translatable("message.embeddeddev.pcf",
                        String.format("%02X", adc.address()), adc.redstoneOutput()), true);
            }
        }
    }

    private void syncButtons() {
        showActive(buttons.anyPressed());
        var state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private void showActive(boolean active) {
        BlockState state = getBlockState();
        if (state.getValue(PeripheralBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(PeripheralBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    private void updateAdc(Level level, BlockPos pos) {
        for (int i = 0; i < ADC_INPUTS.length; i++) {
            Direction face = ADC_INPUTS[i];
            BlockPos neighbor = pos.relative(face);
            int signal = level.hasChunkAt(neighbor) && !(level.getBlockEntity(neighbor) instanceof SystemBoardEntity)
                    ? level.getSignal(neighbor, face) : 0;
            adc.sampleRedstone(i, signal);
        }
        int output = adc.redstoneOutput();
        if (output != previousOutput) {
            previousOutput = output;
            level.updateNeighborsAt(pos, getBlockState().getBlock());
        }
        int[] state = adc.save();
        if (!Arrays.equals(savedAdcState, state)) {
            savedAdcState = state;
            setChanged();
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PeripheralEntity module) {
        switch (module.kind) {
            case BUTTON -> {
                if (module.buttons.tick(level.getGameTime())) module.syncButtons();
                else module.showActive(module.buttons.anyPressed());
            }
            case LED -> module.showActive(module.lit && SystemBoardEntity.connectedModuleBoard(level, pos) != null);
            case DS1302 -> {
                Ds1302 clock = (Ds1302) module.gpioDevice();
                if (module.clockRevision != clock.revision()) {
                    module.clockRevision = clock.revision();
                    module.setChanged();
                }
            }
            case PCF8591 -> module.updateAdc(level, pos);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (buttons != null) buttons.save(tag);
        if (gpio instanceof Ds1302 clock) {
            tag.putLongArray("Clock", clock.save());
            tag.putByteArray("Ram", clock.saveRam());
        }
        if (adc != null) tag.putIntArray("Converter", adc.save());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (buttons != null) buttons.load(tag);
        if (gpio instanceof Ds1302 clock) clock.restore(tag.getLongArray("Clock"), tag.getByteArray("Ram"));
        if (adc != null) adc.restore(tag.getIntArray("Converter"));
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = super.getUpdateTag(registries);
        if (buttons != null) { buttons.save(tag); buttons.updatePressed(tag); }
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (buttons == null) super.handleUpdateTag(tag, registries);
        else { buttons.load(tag); buttons.readPressed(tag); }
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return buttons == null ? null : ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }
}
