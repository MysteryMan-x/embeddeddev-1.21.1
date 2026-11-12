package com.mysteryman.embeddeddev.block;

import com.mysteryman.embeddeddev.Config;
import com.mysteryman.embeddeddev.EmbeddedDev;
import com.mysteryman.embeddeddev.item.CrystalItem;
import com.mysteryman.embeddeddev.item.ModItems;
import com.mysteryman.embeddeddev.mcu.*;
import com.mysteryman.embeddeddev.wire.BoardWiring;
import com.mysteryman.embeddeddev.wire.CableNetwork;
import com.mysteryman.embeddeddev.wire.PinGroup;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

public final class SystemBoardEntity extends BlockEntity {
    public final BoardHardware hardware = new BoardHardware();
    public final Mcs51 cpu = new Mcs51(hardware);
    public final BoardWiring wiring = new BoardWiring(this);
    private final PinGroup[][] groups = new PinGroup[6][PinGroup.COUNT];
    private byte[] firmware = new byte[0];
    private final int[] outputs = new int[6];
    private final int[] signals = new int[6];
    private final SimpleContainer socket = new SimpleContainer(1) {
        @Override public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(SystemBoardEntity.this, player)
                    && player.mayBuild() && player.level().mayInteract(player, worldPosition);
        }
    };
    private boolean running;
    private boolean outputRunning;
    private String firmwareName = "", fault = "";

    public SystemBoardEntity(BlockPos p, BlockState s) {
        super(ModBlockEntities.BOARD.get(), p, s);
        for (int face = 0; face < 6; face++) for (int group = 0; group < PinGroup.COUNT; group++) groups[face][group] = PinGroup.empty(group);
        socket.setItem(0, ModItems.CRYSTAL_11.get().getDefaultInstance());
        socket.addListener(container -> stop());
    }
    public boolean isRunning() { return running; }
    public String firmwareName() { return firmwareName; }
    public String fault() { return fault; }
    public boolean hasFirmware() { return firmware.length != 0; }
    public int output(int face) { return outputs[face]; }
    public Container crystalSocket() { return socket; }
    public ItemStack crystal() { return socket.getItem(0); }
    public int clockHz() { return crystal().getItem() instanceof CrystalItem item ? item.frequencyHz() : 0; }
    public int cyclesPerTick() { return (int) ((long) Config.CYCLES_PER_TICK.get() * clockHz() / CrystalItem.DEFAULT_HZ); }

    public void flash(String hex, String name) {
        IntelHex.Image image = IntelHex.parse(hex); // Validate completely before touching the current machine.
        firmware = image.code(); firmwareName = name; running = false; fault = "";
        hardware.reset(); cpu.install(firmware); synchronizePorts(); refreshConnections(); updateOutputs(); setChanged();
    }
    public void run() {
        if (clockHz() == 0) throw new IllegalArgumentException("gui.embeddeddev.no_crystal");
        if (firmware.length == 0) throw new IllegalArgumentException("No firmware installed");
        if (!fault.isEmpty()) throw new IllegalArgumentException("Reset the CPU before running after a fault");
        running = true; updateOutputs(); setChanged();
    }
    public void stop() { running = false; updateOutputs(); setChanged(); }
    public void reset() { running = false; fault = ""; hardware.reset(); cpu.reset(); synchronizePorts(); refreshConnections(); updateOutputs(); setChanged(); }
    private void synchronizePorts() {
        int[] ports = new int[4]; for (int p = 0; p < 4; p++) ports[p] = cpu.portLatch(p);
        hardware.synchronizePorts(ports);
    }
    public void configure(int face, FaceBinding binding) {
        hardware.bind(face, binding); refreshConnections(); sampleInputs(); updateOutputs(); setChanged();
        if (level instanceof ServerLevel) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    public List<PinGroup> groups(int face) {
        var result = new java.util.ArrayList<PinGroup>(List.of(groups[face]));
        result.set(0, new PinGroup(groups[face][0].name(), hardware.binding(face)));
        return List.copyOf(result);
    }

    public FaceBinding binding(int face, int group) { return group == 0 ? hardware.binding(face) : groups[face][group].binding(); }

    public void configureGroups(int face, List<PinGroup> next) {
        if (face < 0 || face >= 6 || next.size() != PinGroup.COUNT) throw new IllegalArgumentException("gui.embeddeddev.invalid_wiring");
        if (level instanceof ServerLevel server) {
            for (int group = 0; group < PinGroup.COUNT; group++) {
                if (!binding(face, group).pins().isEmpty() && next.get(group).binding().pins().isEmpty()) {
                    CableNetwork.get(server).removeEndpoint(server, new CableNetwork.End(worldPosition, CableNetwork.boardSocket(face, group)), true);
                }
            }
        }
        for (int group = 0; group < PinGroup.COUNT; group++) groups[face][group] = next.get(group);
        configure(face, next.getFirst().binding());
    }

    private void saveGroups(CompoundTag tag) {
        for (int face = 0; face < 6; face++) tag.put("Groups" + face, PinGroup.save(groups(face)));
    }

    private void loadGroups(CompoundTag tag) {
        for (int face = 0; face < 6; face++) {
            if (!tag.contains("Groups" + face)) {
                for (int group = 0; group < PinGroup.COUNT; group++) groups[face][group] = PinGroup.empty(group);
                continue;
            }
            try {
                var loaded = PinGroup.load(tag.getList("Groups" + face, net.minecraft.nbt.Tag.TAG_COMPOUND));
                for (int group = 0; group < PinGroup.COUNT; group++) groups[face][group] = loaded.get(group);
                hardware.bind(face, loaded.getFirst().binding());
            } catch (IllegalArgumentException ignored) { /* Retain the legacy face when the new data is invalid. */ }
        }
    }
    public void singleStep() {
        if (clockHz() == 0) throw new IllegalArgumentException("gui.embeddeddev.no_crystal");
        if (firmware.length == 0) throw new IllegalArgumentException("No firmware installed");
        if (!fault.isEmpty()) throw new IllegalArgumentException("Reset the CPU before stepping after a fault");
        running = false; refreshConnections(); sampleInputs(); execute(true); updateOutputs(); setChanged();
    }
    private void execute(boolean step) {
        hardware.clockHz(clockHz());
        try { if (step) cpu.step(); else cpu.runCycles(cyclesPerTick()); }
        catch (IllegalStateException e) { running = false; fault = e.getMessage(); }
    }
    private void sampleInputs() {
        if (level == null) return;
        for (Direction face : Direction.values()) {
            int index = face.get3DDataValue(); BlockPos adjacent = worldPosition.relative(face);
            int input = level.hasChunkAt(adjacent) ? level.getSignal(adjacent, face) : 0;
            if (level.hasChunkAt(adjacent) && level.getBlockEntity(adjacent) instanceof RedstonePortEntity port)
                input = port.inputSignal();
            signals[index] = input;
        }
        hardware.sample(signals);
        wiring.sample();
    }
    private void refreshConnections() {
        if (level == null) return;
        for (Direction face : Direction.values()) {
            BlockPos adjacent = worldPosition.relative(face);
            BlockEntity neighbor = level.hasChunkAt(adjacent) ? level.getBlockEntity(adjacent) : null;
            I2cDevice i2c = null;
            GpioDevice gpio = null;
            if (neighbor instanceof I2cModule module && adjacentModuleBoard(level, adjacent) == this) {
                i2c = module.i2cDevice();
                if (neighbor instanceof GpioModule moduleGpio) gpio = moduleGpio.gpioDevice();
            }
            hardware.attach(face.get3DDataValue(), i2c);
            hardware.attachGpio(face.get3DDataValue(), gpio);
        }
        if (level instanceof ServerLevel server) wiring.refresh(server);
    }
    private void updateOutputs() {
        boolean changed = outputRunning != running;
        outputRunning = running;
        for (int i = 0; i < 6; i++) {
            int next = hardware.output(i, running);
            if (outputs[i] != next) { outputs[i] = next; changed = true; }
        }
        if (changed && level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }
    public static void tick(Level l, BlockPos p, BlockState s, SystemBoardEntity board) {
        board.refreshConnections(); board.sampleInputs();
        if (!board.running) { board.updateOutputs(); return; }
        board.execute(false); board.updateOutputs();
        // Mark every executing tick so chunk saves preserve the precise CPU/RAM state.
        board.setChanged();
    }
    public static SystemBoardEntity adjacentBoard(Level l, BlockPos p) {
        for (Direction d : Direction.values()) {
            BlockPos next = p.relative(d);
            if (l.hasChunkAt(next) && l.getBlockEntity(next) instanceof SystemBoardEntity board) return board;
        }
        return null;
    }
    public static SystemBoardEntity connectedModuleBoard(Level l, BlockPos module) {
        if (l instanceof ServerLevel server) {
            var cables = CableNetwork.get(server);
            var endpoint = new CableNetwork.End(module, CableNetwork.MODULE);
            if (cables.has(endpoint)) {
                var route = cables.controller(l, endpoint);
                return route != null && l.getBlockEntity(route.end().pos()) instanceof SystemBoardEntity board
                        && board.wiring.connected(module) ? board : null;
            }
        }
        return adjacentModuleBoard(l, module);
    }
    private static SystemBoardEntity adjacentModuleBoard(Level l, BlockPos module) {
        if (l instanceof ServerLevel server && CableNetwork.get(server).has(new CableNetwork.End(module, CableNetwork.MODULE))) return null;
        GpioDevice gpio = l.getBlockEntity(module) instanceof GpioModule peripheral ? peripheral.gpioDevice() : null;
        for (Direction d : Direction.values()) {
            BlockPos p = module.relative(d);
            if (l.hasChunkAt(p) && l.getBlockEntity(p) instanceof SystemBoardEntity board) {
                int face = d.getOpposite().get3DDataValue();
                if (gpio == null ? board.hardware.hasModulePins(face) : board.hardware.hasGpioPins(face, gpio)) return board;
            }
        }
        return null;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        for (int face = 0; face < 6; face++) tag.putString("Pins" + face, hardware.binding(face).wireText());
        saveGroups(tag);
        return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        for (int face = 0; face < 6; face++) hardware.bind(face, FaceBinding.parse(tag.getString("Pins" + face)));
        loadGroups(tag);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet,
                                      HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByteArray("Firmware", firmware); tag.putString("FirmwareName", firmwareName);
        tag.put("Crystal", crystal().saveOptional(registries));
        tag.putBoolean("Running", running); tag.putString("Fault", fault); tag.putByteArray("Cpu", cpu.save());
        tag.putIntArray("Dac", hardware.dac); tag.putString("Serial", hardware.serial());
        for (int f = 0; f < 6; f++) tag.putString("Pins" + f, hardware.binding(f).wireText());
        saveGroups(tag);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ItemStack installed = tag.contains("Crystal") ? ItemStack.parseOptional(registries, tag.getCompound("Crystal"))
                : ModItems.CRYSTAL_11.get().getDefaultInstance();
        setCrystal(installed);
        firmware = tag.getByteArray("Firmware"); firmwareName = tag.getString("FirmwareName");
        running = tag.getBoolean("Running") && firmware.length == IntelHex.FLASH_SIZE && clockHz() > 0;
        fault = tag.getString("Fault");
        hardware.reset();
        for (int f = 0; f < 6; f++) {
            try {
                FaceBinding binding = tag.contains("Pins" + f)
                        ? FaceBinding.parse(tag.getString("Pins" + f))
                        : FaceBinding.fromLegacy(tag.getIntArray("Face" + f));
                hardware.bind(f, binding);
            } catch (IllegalArgumentException e) {
                hardware.bind(f, FaceBinding.EMPTY);
            }
        }
        loadGroups(tag);
        if (firmware.length == IntelHex.FLASH_SIZE) {
            cpu.install(firmware);
            try { cpu.restore(tag.getByteArray("Cpu")); }
            catch (IllegalArgumentException e) { running = false; fault = "Saved CPU state was invalid; reset required"; EmbeddedDev.LOGGER.warn("Invalid CPU at {}", worldPosition, e); }
        } else { firmware = new byte[0]; cpu.reset(); }
        int[] dac = tag.getIntArray("Dac");
        for (int i = 0; i < Math.min(dac.length, hardware.dac.length); i++) hardware.dac[i] = Math.clamp(dac[i], 0, 15);
        hardware.restoreSerial(tag.getString("Serial"));
        synchronizePorts();
        for (int i = 0; i < 6; i++) outputs[i] = hardware.output(i, running);
    }

    private void setCrystal(ItemStack stack) {
        socket.setItem(0, stack.getItem() instanceof CrystalItem ? stack.copyWithCount(1) : ItemStack.EMPTY);
    }

    @Override protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(crystal())));
    }

    @Override protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        ItemContainerContents contents = input.get(DataComponents.CONTAINER);
        if (contents != null) setCrystal(contents.copyOne());
    }

    @Override public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Crystal");
    }
}
