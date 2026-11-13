package com.mysteryman.embeddeddev.wire;

import com.mysteryman.embeddeddev.block.I2cModule;
import com.mysteryman.embeddeddev.block.PeripheralEntity;
import com.mysteryman.embeddeddev.block.RedstonePortEntity;
import com.mysteryman.embeddeddev.block.SystemBoardEntity;
import com.mysteryman.embeddeddev.mcu.BoardHardware;
import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.mcu.GpioDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mysteryman.embeddeddev.mcu.FaceBinding.Kind.*;

/** Compiles loaded cable paths into pin routes before the MCU executes a tick. */
public final class BoardWiring {
    private final SystemBoardEntity board;
    private List<Input> inputs = List.of();
    private Set<BlockPos> connected = Set.of();
    private List<Status> statuses = List.of();
    private boolean hadWires;

    private record Input(RedstonePortEntity port, FaceBinding.Pin pin) {}
    private record Status(int face, BlockPos pos, String name, String terminal, String pins, String state, int address) {
        Status withState(String state) { return new Status(face, pos, name, terminal, pins, state, address); }
    }

    public BoardWiring(SystemBoardEntity board) { this.board = board; }

    public boolean connected(BlockPos pos) { return connected.contains(pos); }

    public void refresh(ServerLevel level) {
        CableNetwork cables = CableNetwork.get(level);
        BlockPos pos = board.getBlockPos();
        boolean hasWires = cables.hasBoard(pos);
        if (!hasWires && !hadWires) return;
        var i2c = new LinkedHashMap<CableNetwork.End, BoardHardware.WiredI2c>();
        var gpio = new ArrayList<BoardHardware.WiredGpio>();
        var redstone = new ArrayList<Input>();
        var next = new LinkedHashMap<CableNetwork.End, Status>();
        for (int index = 0; index < 6 * PinGroup.COUNT; index++) {
            int face = index % 6, group = index / 6;
            var end = new CableNetwork.End(pos, CableNetwork.boardSocket(face, group));
            if (!cables.has(end)) {
                if (hadWires && group == 0) board.hardware.reserve(face, Set.of());
                continue;
            }
            if (group == 0) board.hardware.reserve(face, cables.reserved(end));
            face = end.socket();
            Map<Integer, Integer> initial = new HashMap<>();
            for (var pin : board.binding(end.face(), end.group()).pins()) initial.put(pin.wireId(), pin.wireId());
            // Keep offline paths visible. Only the loaded graph is used to deliver signals.
            Set<CableNetwork.End> live = new HashSet<>();
            for (var route : cables.trace(level, end, initial, true)) live.add(route.end());
            for (var route : cables.trace(level, end, initial, false)) {
                var target = route.end();
                if (!live.contains(target)) {
                    next.put(target, new Status(face, target.pos(), "", "", "", "offline", -1));
                    continue;
                }
                var device = level.getBlockEntity(target.pos());
                if (device == null) continue;
                String name = device.getBlockState().getBlock().getDescriptionId();
                String terminal = device instanceof PeripheralEntity peripheral && peripheral.buttons() != null
                        ? peripheral.buttons().names().get(target.buttonKey()) : "";
                String pins = route.lanes().values().stream().map(id -> CableNetwork.pin(board, end.socket(), id))
                        .filter(java.util.Objects::nonNull).map(FaceBinding.Pin::channelName)
                        .collect(java.util.stream.Collectors.joining(", "));
                Status status = new Status(face, target.pos(), name, terminal, pins, "bad_pins", -1);
                GpioDevice moduleGpio = CableNetwork.gpioDevice(level, target);
                if (device instanceof RedstonePortEntity port) {
                    FaceBinding.Pin pin = pin(face, route, 0);
                    if (pin != null && accepts(port, pin)) {
                        if (port.reads()) redstone.add(new Input(port, pin));
                        status = status.withState("connected");
                    }
                } else if (moduleGpio != null) {
                    var channels = new ArrayList<Integer>();
                    for (int lane = 0; lane < moduleGpio.terminals().size(); lane++) {
                        var pin = pin(face, route, lane);
                        if (pin != null && pin.kind().panelKind() == GPIO) channels.add(pin.channel());
                    }
                    if (channels.size() == moduleGpio.terminals().size()) {
                        gpio.add(new BoardHardware.WiredGpio(moduleGpio, channels));
                        status = status.withState("connected");
                    }
                } else if (device instanceof I2cModule module && module.i2cDevice() != null) {
                    var sda = pin(face, route, 0);
                    var scl = pin(face, route, 1);
                    if (sda != null && scl != null && sda.kind().panelKind() == GPIO && scl.kind().panelKind() == GPIO) {
                        var routeI2c = new BoardHardware.WiredI2c(module.i2cDevice(), sda.channel(), scl.channel());
                        i2c.put(target, routeI2c);
                        status = new Status(face, target.pos(), name, terminal, pins, "connected", routeI2c.address());
                    }
                }
                next.put(target, status);
            }
        }
        hadWires = hasWires;
        Map<Integer, Integer> addressCounts = new HashMap<>();
        for (var link : i2c.values()) addressCounts.merge(busAddress(link), 1, Integer::sum);
        i2c.entrySet().removeIf(entry -> {
            if (addressCounts.get(busAddress(entry.getValue())) == 1 && !board.hardware.hasLocalAddress(entry.getValue())) return false;
            next.computeIfPresent(entry.getKey(), (p, status) -> status.withState("address_conflict"));
            return true;
        });
        inputs = List.copyOf(redstone);
        board.hardware.wireDevices(List.copyOf(i2c.values()), gpio);
        statuses = List.copyOf(next.values());
        Set<BlockPos> active = new HashSet<>();
        for (var status : statuses) if (status.state().equals("connected")) active.add(status.pos());
        connected = active;
    }

    private FaceBinding.Pin pin(int face, CableNetwork.Route route, int lane) {
        Integer id = route.lanes().get(lane);
        return id == null ? null : CableNetwork.pin(board, face, id);
    }

    private static int busAddress(BoardHardware.WiredI2c link) { return (link.sda() << 12) | (link.scl() << 7) | link.address(); }

    public static boolean accepts(RedstonePortEntity port, FaceBinding.Pin pin) {
        return pin.kind().panelKind() == GPIO;
    }

    public void sample() {
        for (var input : inputs) board.hardware.samplePin(input.pin(), input.port().wiredInput());
    }

    public ListTag statusTag() {
        var list = new ListTag();
        for (var status : statuses) {
            var tag = new CompoundTag();
            var socket = new CableNetwork.End(board.getBlockPos(), status.face());
            tag.putInt("Face", socket.face());
            tag.putString("Group", board.groups(socket.face()).get(socket.group()).name());
            tag.putLong("Pos", status.pos().asLong());
            tag.putString("Name", status.name());
            tag.putString("Terminal", status.terminal());
            tag.putString("Pins", status.pins());
            tag.putString("Status", "wire.embeddeddev.status." + status.state());
            tag.putInt("Address", status.address());
            list.add(tag);
        }
        return list;
    }
}
