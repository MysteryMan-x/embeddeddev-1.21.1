package com.mysteryman.embeddeddev.network;

import com.mysteryman.embeddeddev.EmbeddedDev;
import com.mysteryman.embeddeddev.block.*;
import com.mysteryman.embeddeddev.menu.CrystalSocketMenu;
import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.mcu.I2cSensor;
import com.mysteryman.embeddeddev.mcu.IntelHex;
import com.mysteryman.embeddeddev.mcu.Pcf8591;
import com.mysteryman.embeddeddev.mcu.Ssd1306;
import com.mysteryman.embeddeddev.wire.CableNetwork;
import com.mysteryman.embeddeddev.wire.PinGroup;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.function.Consumer;
import java.util.List;

public final class BoardNetwork {
    public static final int POLL = 0, FLASH = 1, RUN = 2, STOP = 3, RESET = 4, STEP = 5, SERIAL = 6, CONFIGURE = 7, OLED_POLL = 8;
    public static final int CRYSTAL_SOCKET = 9;
    public static final int PORT_CONFIGURE = 10;
    public static final int BUTTON_CONFIGURE = 11;
    public static final int CONFIGURE_GROUPS = 12;
    // Installed only from the Dist.CLIENT entry point; dedicated servers never load client classes.
    public static Consumer<State> clientReceiver = state -> {};

    public record Action(BlockPos pos, int action, String name, String text) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EmbeddedDev.MODID, "board_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((b, v) -> {
            b.writeBlockPos(v.pos); b.writeVarInt(v.action); b.writeUtf(v.name, 96); b.writeUtf(v.text, IntelHex.MAX_PAYLOAD_LENGTH);
        }, b -> new Action(b.readBlockPos(), b.readVarInt(), b.readUtf(96), b.readUtf(IntelHex.MAX_PAYLOAD_LENGTH)));
        @Override public Type<Action> type() { return TYPE; }
    }
    public record State(BlockPos pos, boolean open, CompoundTag data) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EmbeddedDev.MODID, "board_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of((b, v) -> {
            b.writeBlockPos(v.pos); b.writeBoolean(v.open); b.writeNbt(v.data);
        }, b -> new State(b.readBlockPos(), b.readBoolean(), b.readNbt()));
        @Override public Type<State> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("8");
        registrar.playToServer(Action.TYPE, Action.CODEC, BoardNetwork::handle);
        registrar.playToClient(State.TYPE, State.CODEC, (state, context) -> clientReceiver.accept(state));
        WireNetwork.register(registrar);
    }
    private static void handle(Action action, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var level = player.serverLevel();
        if (!level.hasChunkAt(action.pos) || player.distanceToSqr(action.pos.getCenter()) > 64) return;
        if (action.action == BUTTON_CONFIGURE) {
            if (level.getBlockEntity(action.pos) instanceof PeripheralEntity module && module.buttons() != null) {
                String message = "";
                try {
                    if (!player.mayBuild() || !level.mayInteract(player, action.pos)) throw CableNetwork.error("permission");
                    if (action.text.length() > ButtonPanel.MAX_KEYS * (FaceBinding.MAX_NAME_LENGTH + 1)) {
                        throw new IllegalArgumentException("buttons.embeddeddev.invalid_count");
                    }
                    var data = player.getPersistentData();
                    long tick = level.getGameTime();
                    if (data.contains("EmbeddedDevButtonConfig") && data.getLong("EmbeddedDevButtonConfig") == tick) return;
                    data.putLong("EmbeddedDevButtonConfig", tick);
                    module.configureButtons(List.of(action.text.split("\n", -1)));
                } catch (IllegalArgumentException error) { message = error.getMessage(); }
                sendButtonState(player, module, false, message);
            }
            return;
        }
        if (action.action == PORT_CONFIGURE) {
            if (player.mayBuild() && level.mayInteract(player, action.pos) && level.getBlockEntity(action.pos) instanceof RedstonePortEntity port
                    && action.name.length() == 1 && action.name.charAt(0) >= '0' && action.name.charAt(0) <= '5'
                    && (action.text.equals("in") || action.text.equals("out"))) {
                var data = player.getPersistentData();
                long tick = level.getGameTime();
                if (data.contains("EmbeddedDevPortConfig") && data.getLong("EmbeddedDevPortConfig") == tick) return;
                data.putLong("EmbeddedDevPortConfig", tick);
                port.wireConfigure(Direction.from3DDataValue(action.name.charAt(0) - '0'), action.text.equals("in"));
            }
            return;
        }
        if (action.action == OLED_POLL) {
            var data = player.getPersistentData(); long now = level.getGameTime();
            if (data.contains("EmbeddedDevOledPoll") && now == data.getLong("EmbeddedDevOledPoll")) return;
            data.putLong("EmbeddedDevOledPoll", now);
            if (level.getBlockEntity(action.pos) instanceof OledEntity oled) sendOledState(player, oled, false);
            return;
        }
        if (!(level.getBlockEntity(action.pos) instanceof SystemBoardEntity board)) return;
        long tick = level.getGameTime();
        var playerData = player.getPersistentData();
        String rateKey = action.action == POLL ? "EmbeddedDevPoll" : "EmbeddedDevAction";
        if (playerData.contains(rateKey) && tick == playerData.getLong(rateKey)) return;
        playerData.putLong(rateKey, tick);
        if (action.action == POLL) { sendState(player, board, false, ""); return; }
        if (!player.mayBuild() || !level.mayInteract(player, action.pos)) return;
        String message = "";
        try {
            switch (action.action) {
                case FLASH -> {
                    if (playerData.contains("EmbeddedDevFlash") && tick - playerData.getLong("EmbeddedDevFlash") < 20) return;
                    playerData.putLong("EmbeddedDevFlash", tick);
                    String name = action.name.replaceAll("[\\p{Cntrl}]", "");
                    board.flash(action.text, name); message = "gui.embeddeddev.flashed";
                }
                case RUN -> board.run();
                case STOP -> board.stop();
                case RESET -> board.reset();
                case STEP -> board.singleStep();
                case SERIAL -> { if (!action.text.isEmpty()) { board.cpu.receive(action.text.charAt(0)); board.setChanged(); } }
                case CRYSTAL_SOCKET -> {
                    player.openMenu(new SimpleMenuProvider(
                            (id, inventory, viewer) -> new CrystalSocketMenu(id, inventory, board.crystalSocket()),
                            Component.translatable("gui.embeddeddev.crystal_socket")));
                    return;
                }
                case CONFIGURE -> {
                    int face;
                    try { face = Integer.parseInt(action.name); }
                    catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid direction"); }
                    board.configure(face, FaceBinding.parse(action.text)); message = "gui.embeddeddev.configured";
                }
                case CONFIGURE_GROUPS -> {
                    int face = Integer.parseInt(action.name);
                    if (face < 0 || face >= 6) throw new IllegalArgumentException("gui.embeddeddev.invalid_wiring");
                    try {
                        var groups = PinGroup.load(TagParser.parseTag(action.text).getList("Groups", net.minecraft.nbt.Tag.TAG_COMPOUND));
                        board.configureGroups(face, groups);
                        message = "gui.embeddeddev.configured";
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
                        throw new IllegalArgumentException("gui.embeddeddev.invalid_wiring");
                    }
                }
                default -> { return; }
            }
        } catch (IllegalArgumentException e) { message = e.getMessage(); }
        sendState(player, board, false, message);
    }
    public static void sendState(ServerPlayer player, SystemBoardEntity board, boolean open, String message) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Running", board.isRunning()); tag.putString("Name", board.firmwareName()); tag.putString("Fault", board.fault());
        tag.put("Crystal", board.crystal().saveOptional(player.registryAccess()));
        tag.putInt("PC", board.cpu.pc()); tag.putInt("A", board.cpu.accumulator()); tag.putLong("Cycles", board.cpu.cycles());
        if (board.hasFirmware()) {
            byte[] instructions = new byte[12];
            for (int i = 0; i < instructions.length; i++) instructions[i] = (byte) board.cpu.codeAt(board.cpu.pc() + i);
            tag.putByteArray("Instructions", instructions);
        }
        int[] ports = new int[4], output = new int[6];
        for (int i = 0; i < 4; i++) ports[i] = board.cpu.portLatch(i);
        for (int i = 0; i < 6; i++) output[i] = board.output(i);
        tag.putIntArray("Ports", ports); tag.putIntArray("ADC", board.hardware.adc); tag.putIntArray("Output", output);
        tag.put("Wires", board.wiring.statusTag());
        for (int f = 0; f < 6; f++) {
            tag.putString("Pins" + f, board.hardware.binding(f).wireText());
            tag.put("Groups" + f, PinGroup.save(board.groups(f)));
            var i2c = board.hardware.device(f);
            var gpio = board.hardware.gpioDevice(f);
            String label = switch (i2c) {
                case Ssd1306 oled -> "OLED 0x3C";
                case I2cSensor sensor -> "Sensor 0x48";
                case Pcf8591 adc -> String.format("PCF8591 0x%02X", adc.address());
                case null, default -> gpio == null ? "" : gpio.label();
            };
            tag.putString("Device" + f, label);
        }
        tag.putString("Serial", board.hardware.serial()); tag.putString("Message", message);
        PacketDistributor.sendToPlayer(player, new State(board.getBlockPos(), open, tag));
    }
    public static void sendOledState(ServerPlayer player, OledEntity oled, boolean open) {
        CompoundTag tag = new CompoundTag(); tag.putString("Screen", "oled");
        tag.putByteArray("Pixels", oled.display.frame()); tag.putBoolean("Enabled", oled.display.enabled);
        tag.putBoolean("Connected", SystemBoardEntity.connectedModuleBoard(player.serverLevel(), oled.getBlockPos()) != null);
        PacketDistributor.sendToPlayer(player, new State(oled.getBlockPos(), open, tag));
    }
    public static void sendPortState(ServerPlayer player, RedstonePortEntity port, boolean open) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Screen", "port");
        tag.putInt("Face", port.face().get3DDataValue());
        tag.putBoolean("Reads", port.reads());
        PacketDistributor.sendToPlayer(player, new State(port.getBlockPos(), open, tag));
    }

    public static void sendButtonState(ServerPlayer player, PeripheralEntity module, boolean open, String message) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Screen", "buttons");
        tag.putString("Message", message);
        module.buttons().save(tag);
        PacketDistributor.sendToPlayer(player, new State(module.getBlockPos(), open, tag));
    }
    public static void sendManual(ServerPlayer player, InteractionHand hand) {
        var tag = new CompoundTag();
        tag.putString("Screen", "manual");
        tag.putString("Hand", hand.name());
        PacketDistributor.sendToPlayer(player, new State(player.blockPosition(), true, tag));
    }
    private BoardNetwork() {}
}
