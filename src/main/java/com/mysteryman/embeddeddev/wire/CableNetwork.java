package com.mysteryman.embeddeddev.wire;

import com.mysteryman.embeddeddev.block.I2cModule;
import com.mysteryman.embeddeddev.block.GpioModule;
import com.mysteryman.embeddeddev.block.ButtonPanel;
import com.mysteryman.embeddeddev.block.ModBlocks;
import com.mysteryman.embeddeddev.block.PeripheralEntity;
import com.mysteryman.embeddeddev.block.RedstonePortEntity;
import com.mysteryman.embeddeddev.block.SystemBoardEntity;
import com.mysteryman.embeddeddev.item.ModItems;
import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.mcu.GpioDevice;
import com.mysteryman.embeddeddev.mcu.I2cDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Authoritative, dimension-local graph. Every segment is stored and refunded once. */
public final class CableNetwork extends SavedData {
    public static final int MAX_LENGTH = 32;
    public static final int MODULE = 6, RELAY = 7, REDSTONE = 8;
    private static final int EXTRA_BUTTON = 9, EXTRA_BOARD = EXTRA_BUTTON + ButtonPanel.MAX_KEYS - 1;
    private static final int MAX_SOCKET = EXTRA_BOARD + (PinGroup.COUNT - 1) * 6 - 1;
    private static final int MAX_BRANCHES = 8, MAX_SOCKETS = 64;
    private static final Factory<CableNetwork> FACTORY = new Factory<>(CableNetwork::new, CableNetwork::load, null);
    private final Map<Long, Wire> wires = new HashMap<>();
    private final Map<End, Set<Wire>> sockets = new HashMap<>();
    private final Map<Long, Set<Wire>> chunks = new HashMap<>();
    private long nextId = 1, revision;

    public record End(BlockPos pos, int socket) {
        public End {
            pos = pos.immutable();
            if (socket < 0 || socket > MAX_SOCKET) throw error("invalid_endpoint");
        }
        public boolean board() { return socket < MODULE || socket >= EXTRA_BOARD; }
        public int face() { return socket < MODULE ? socket : (socket - EXTRA_BOARD) % 6; }
        public int group() { return socket < MODULE ? 0 : 1 + (socket - EXTRA_BOARD) / 6; }
        public boolean relay() { return socket == RELAY; }
        public boolean module() { return socket == MODULE || socket >= EXTRA_BUTTON && socket < EXTRA_BOARD; }
        public int buttonKey() { return socket == MODULE ? 0 : socket >= EXTRA_BUTTON && socket < EXTRA_BOARD ? socket - EXTRA_BUTTON + 1 : -1; }
        public Vec3 anchor() {
            if (board()) {
                Direction face = Direction.from3DDataValue(face());
                double y = face == Direction.UP ? .37 : face == Direction.DOWN ? .04 : .18;
                double tangent = switch (group()) { case 1 -> .19; case 2 -> .81; case 3 -> .34; default -> .5; };
                double x = face.getAxis() == Direction.Axis.Z ? tangent : .5 + .43 * face.getStepX();
                double z = face.getAxis() == Direction.Axis.X ? tangent : .5 + .43 * face.getStepZ();
                if (face.getAxis() == Direction.Axis.Y) {
                    x = group() == 1 ? .28 : group() == 2 ? .72 : .5;
                    z = group() == 3 ? .72 : .5;
                }
                return Vec3.atLowerCornerOf(pos).add(x, y, z);
            }
            if (socket >= EXTRA_BUTTON && socket < EXTRA_BOARD) return Vec3.atLowerCornerOf(pos).add(ButtonPanel.x(buttonKey()), .35, ButtonPanel.z(buttonKey()));
            return Vec3.atLowerCornerOf(pos).add(.5, relay() ? .68 : .35, .5);
        }
        public CompoundTag save() {
            var tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            tag.putInt("Socket", socket);
            return tag;
        }
        public static End load(CompoundTag tag) { return new End(BlockPos.of(tag.getLong("Pos")), tag.getInt("Socket")); }
    }

    public record Lane(int id, String name) {
        public Lane {
            if (id < 0 || id >= FaceBinding.Kind.values().length * 32 || name.isBlank()
                    || name.length() > FaceBinding.MAX_NAME_LENGTH || name.codePoints().anyMatch(Character::isISOControl)) {
                throw error("invalid_mapping");
            }
        }
        public CompoundTag save() {
            var tag = new CompoundTag();
            tag.putInt("Id", id);
            tag.putString("Name", name);
            return tag;
        }
        public static Lane load(CompoundTag tag) { return new Lane(tag.getInt("Id"), tag.getString("Name")); }
    }

    public record Pair(Lane a, Lane b) {}

    public record Wire(long id, End a, End b, List<Pair> pairs, int units) {
        public Wire { pairs = List.copyOf(pairs); }
        public End other(End end) { return a.equals(end) ? b : a; }
        public Vec3 point(double t) { return CableNetwork.point(a.anchor(), b.anchor(), t); }
    }

    public record Draft(End a, End b, List<Lane> aLanes, List<Lane> bLanes, List<Pair> automatic) {}
    public record Route(End end, Map<Integer, Integer> lanes) {}
    private record Hop(End end, Map<Integer, Integer> lanes) {}

    public static CableNetwork get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(FACTORY, "embeddeddev_cables"); }
    // The center key retains the original single-button socket ID for existing worlds.
    public static int buttonSocket(int key) { return key == 0 ? MODULE : EXTRA_BUTTON + key - 1; }
    public static int boardSocket(int face, int group) { return group == 0 ? face : EXTRA_BOARD + (group - 1) * 6 + face; }
    public long revision() { return revision; }
    public boolean has(End end) { return sockets.containsKey(end); }
    public boolean hasBoard(BlockPos pos) {
        for (int face = 0; face < 6; face++) for (int group = 0; group < PinGroup.COUNT; group++) {
            if (has(new End(pos, boardSocket(face, group)))) return true;
        }
        return false;
    }
    public Set<Wire> at(End end) { return sockets.getOrDefault(end, Set.of()); }
    public Wire wire(long id) { return wires.get(id); }

    public static End target(Level level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        if (!level.hasChunkAt(pos)) return null;
        if (level.getBlockEntity(pos) instanceof SystemBoardEntity board) {
            Direction face = hit.getDirection();
            if (face == Direction.UP) {
                double x = hit.getLocation().x - pos.getX() - .5, z = hit.getLocation().z - pos.getZ() - .5;
                if (Math.max(Math.abs(x), Math.abs(z)) > .28) {
                    face = Math.abs(x) > Math.abs(z) ? (x < 0 ? Direction.WEST : Direction.EAST)
                            : (z < 0 ? Direction.NORTH : Direction.SOUTH);
                }
            }
            End selected = new End(pos, face.get3DDataValue());
            double distance = selected.anchor().distanceToSqr(hit.getLocation());
            for (int group = 1; group < PinGroup.COUNT; group++) {
                if (board.binding(face.get3DDataValue(), group).pins().isEmpty()) continue;
                End candidate = new End(pos, boardSocket(face.get3DDataValue(), group));
                double nextDistance = candidate.anchor().distanceToSqr(hit.getLocation());
                if (nextDistance < distance) { selected = candidate; distance = nextDistance; }
            }
            return selected;
        }
        if (level.getBlockState(pos).is(ModBlocks.WIRE_RELAY.get())) return new End(pos, RELAY);
        if (level.getBlockEntity(pos) instanceof RedstonePortEntity) return new End(pos, REDSTONE);
        if (level.getBlockEntity(pos) instanceof PeripheralEntity peripheral && peripheral.buttons() != null) {
            int key = peripheral.buttonAt(hit);
            return key < 0 ? null : new End(pos, buttonSocket(key));
        }
        if (level.getBlockEntity(pos) instanceof PeripheralEntity peripheral && peripheral.leds() != null) {
            int key=peripheral.ledAt(hit);return key<0 ? null : new End(pos,buttonSocket(key));
        }
        if (level.getBlockEntity(pos) instanceof I2cModule || level.getBlockEntity(pos) instanceof GpioModule) return new End(pos, MODULE);
        return null;
    }

    public static boolean loaded(Level level, End end) {
        if (!level.hasChunkAt(end.pos())) return false;
        if (end.board()) return level.getBlockEntity(end.pos()) instanceof SystemBoardEntity;
        return switch (end.socket()) {
            case MODULE -> level.getBlockEntity(end.pos()) instanceof I2cModule || level.getBlockEntity(end.pos()) instanceof GpioModule;
            case RELAY -> level.getBlockState(end.pos()).is(ModBlocks.WIRE_RELAY.get());
            case REDSTONE -> level.getBlockEntity(end.pos()) instanceof RedstonePortEntity;
            default -> level.getBlockEntity(end.pos()) instanceof PeripheralEntity peripheral && peripheral.panelCount() > 0
                    && end.buttonKey() >= 0 && end.buttonKey() < peripheral.panelCount();
        };
    }

    public static FaceBinding.Pin pin(SystemBoardEntity board, int socket, int id) {
        var end = new End(board.getBlockPos(), socket);
        for (var pin : board.binding(end.face(), end.group()).pins()) if (pin.wireId() == id) return pin;
        return null;
    }

    public List<Lane> lanes(Level level, End end) {
        if (!loaded(level, end)) return List.of();
        if (end.relay()) {
            Map<Integer, Lane> lanes = new LinkedHashMap<>();
            for (Wire wire : at(end)) for (Pair pair : wire.pairs()) {
                Lane lane = wire.a().equals(end) ? pair.a() : pair.b();
                lanes.putIfAbsent(lane.id(), lane);
            }
            return List.copyOf(lanes.values());
        }
        return deviceLanes(level, end);
    }

    public static List<Lane> deviceLanes(Level level, End end) {
        var entity = level.getBlockEntity(end.pos());
        if (end.board() && entity instanceof SystemBoardEntity board) {
            return board.binding(end.face(), end.group()).pins().stream().map(p -> new Lane(p.wireId(), p.name())).toList();
        }
        if (entity instanceof RedstonePortEntity) return List.of(new Lane(0, "SIGNAL"));
        var gpio = gpioDevice(level, end);
        if (gpio != null) {
            var names = gpio.terminals();
            var lanes = new ArrayList<Lane>(names.size());
            for (int i = 0; i < names.size(); i++) lanes.add(new Lane(i, names.get(i)));
            return lanes;
        }
        if (entity instanceof I2cModule) return List.of(new Lane(0, I2cDevice.SDA), new Lane(1, I2cDevice.SCL));
        return List.of();
    }

    public static GpioDevice gpioDevice(Level level, End end) {
        var entity = level.getBlockEntity(end.pos());
        if (entity instanceof PeripheralEntity peripheral && peripheral.buttons() != null) return peripheral.buttons().device(end.buttonKey());
        if (entity instanceof PeripheralEntity peripheral && peripheral.leds() != null) return peripheral.leds().device(end.buttonKey());
        return entity instanceof GpioModule module && end.socket() == MODULE ? module.gpioDevice() : null;
    }

    public Draft prepare(ServerLevel level, End a, End b) {
        if (!loaded(level, a) || !loaded(level, b)) throw error("invalid_endpoint");
        if (a.pos().equals(b.pos())) throw error("same_endpoint");
        if (!a.board() && !a.relay() && !b.board() && !b.relay()) throw error("needs_controller");
        if (com.mysteryman.embeddeddev.compat.SableCompat.worldPosition(level,a.anchor()).distanceTo(
                com.mysteryman.embeddeddev.compat.SableCompat.worldPosition(level,b.anchor())) > MAX_LENGTH) throw error("too_far");
        if (at(a).size() >= capacity(a) || at(b).size() >= capacity(b)) throw error("occupied");
        Set<End> first = component(a), second = component(b);
        if (first.contains(b)) throw error("loop");
        if (first.size() + second.size() > MAX_SOCKETS) throw error("network_full");
        if (first.stream().anyMatch(End::board) && second.stream().anyMatch(End::board)) throw error("two_boards");
        if (blocked(level, a, b)) throw error("blocked");

        List<Lane> left = lanes(level, a), right = lanes(level, b);
        if (a.relay() && left.isEmpty()) left = right;
        if (b.relay() && right.isEmpty()) right = left;
        if (left.isEmpty() || right.isEmpty()) throw error("no_pins");
        var automatic = new ArrayList<Pair>();
        if (left.size() == 1 && right.size() == 1) automatic.add(new Pair(left.getFirst(), right.getFirst()));
        else for (Lane l : left) for (Lane r : right) if (l.name().equalsIgnoreCase(r.name())) automatic.add(new Pair(l, r));
        int required = Math.max(a.module() ? left.size() : 0, b.module() ? right.size() : 0);
        if (automatic.size() < Math.max(required, Math.min(left.size(), right.size()))
                || automatic.stream().anyMatch(pair -> !compatible(level, a, b, pair))) automatic.clear();
        return new Draft(a, b, left, right, List.copyOf(automatic));
    }

    private static int capacity(End end) { return end.board() ? 16 : end.relay() ? MAX_BRANCHES : 1; }

    private Set<End> component(End start) {
        Set<End> seen = new HashSet<>();
        var next = new ArrayDeque<End>();
        seen.add(start);
        next.add(start);
        while (!next.isEmpty()) {
            End from = next.remove();
            for (Wire wire : at(from)) if (seen.add(wire.other(from))) next.add(wire.other(from));
        }
        return seen;
    }

    public Wire connect(ServerLevel level, End a, End b, List<Pair> requested) {
        Draft draft = prepare(level, a, b);
        List<Pair> pairs = new ArrayList<>();
        Set<Integer> left = new HashSet<>(), right = new HashSet<>();
        for (Pair pair : requested) {
            Lane l = find(draft.aLanes(), pair.a().id()), r = find(draft.bLanes(), pair.b().id());
            if (l == null || r == null || !left.add(l.id()) || !right.add(r.id())) throw error("invalid_mapping");
            Pair resolved = new Pair(l, r);
            if (!compatible(level, a, b, resolved)) throw error("invalid_mapping");
            pairs.add(resolved);
        }
        if (pairs.isEmpty() || a.module() && pairs.size() != draft.aLanes().size()
                || b.module() && pairs.size() != draft.bLanes().size()) throw error("invalid_mapping");
        Wire wire = new Wire(nextId++, a, b, pairs, units(level,a,b));
        index(wire);
        revision++;
        setDirty();
        return wire;
    }

    public static Lane find(List<Lane> lanes, int id) {
        for (Lane lane : lanes) if (lane.id() == id) return lane;
        return null;
    }

    private static boolean compatible(ServerLevel level, End a, End b, Pair pair) {
        if (a.relay() || b.relay()) return true;
        End source = a.board() ? a : b, target = a.board() ? b : a;
        var board = (SystemBoardEntity) level.getBlockEntity(source.pos());
        var pin = pin(board, source.socket(), (a.board() ? pair.a() : pair.b()).id());
        if (pin == null) return false;
        var entity = level.getBlockEntity(target.pos());
        if (entity instanceof RedstonePortEntity port) return BoardWiring.accepts(port, pin);
        return pin.kind().panelKind() == FaceBinding.Kind.GPIO;
    }

    public static int units(End a, End b) { return Math.max(1, (int) Math.ceil(a.anchor().distanceTo(b.anchor()))); }
    public static int units(Level level,End a,End b) {
        return Math.max(1,(int)Math.ceil(com.mysteryman.embeddeddev.compat.SableCompat.worldPosition(level,a.anchor()).distanceTo(
                com.mysteryman.embeddeddev.compat.SableCompat.worldPosition(level,b.anchor()))));
    }

    public List<Route> trace(Level level, End start, Map<Integer, Integer> initial, boolean live) {
        if (!has(start)) return List.of();
        var result = new ArrayList<Route>();
        var next = new ArrayDeque<Hop>();
        var seen = new HashSet<End>();
        next.add(new Hop(start, initial));
        seen.add(start);
        while (!next.isEmpty()) {
            Hop from = next.remove();
            for (Wire wire : at(from.end())) {
                End to = wire.other(from.end());
                if (!seen.add(to) || live && !loaded(level, to)) continue;
                boolean forward = wire.a().equals(from.end());
                Map<Integer, Integer> mapped = new LinkedHashMap<>();
                for (Pair pair : wire.pairs()) {
                    Lane source = forward ? pair.a() : pair.b(), destination = forward ? pair.b() : pair.a();
                    Integer original = from.lanes().get(source.id());
                    if (original != null) mapped.put(destination.id(), original);
                }
                if (to.relay()) next.add(new Hop(to, mapped));
                else result.add(new Route(to, mapped));
            }
        }
        return result;
    }

    public Route controller(Level level, End end) {
        var initial = new HashMap<Integer, Integer>();
        for (var lane : deviceLanes(level, end)) initial.put(lane.id(), lane.id());
        for (Route route : trace(level, end, initial, true)) if (route.end().board()) return route;
        return null;
    }

    public Set<Integer> reserved(End end) {
        if (!has(end)) return Set.of();
        Set<Integer> pins = new HashSet<>();
        for (Wire wire : at(end)) for (Pair pair : wire.pairs()) pins.add((wire.a().equals(end) ? pair.a() : pair.b()).id());
        return pins;
    }

    public void removeAt(ServerLevel level, BlockPos pos, boolean drops) {
        for (int i = 0; i <= MAX_SOCKET; i++) removeEndpoint(level, new End(pos, i), drops);
    }

    public void removeEndpoint(ServerLevel level, End end, boolean drops) {
        for (Wire wire : List.copyOf(at(end))) {
            if (remove(wire.id()) != null && drops) Block.popResource(level, end.pos(), new ItemStack(ModItems.WIRE_COIL.get(), wire.units()));
        }
    }

    public static void endpointRemoved(BlockState before, Level level, BlockPos pos, BlockState next) {
        if (!before.is(next.getBlock()) && level instanceof ServerLevel server) get(server).removeAt(server, pos, true);
    }

    public Wire remove(long id) {
        Wire wire = wires.remove(id);
        if (wire == null) return null;
        for (End end : List.of(wire.a(), wire.b())) {
            Set<Wire> at = sockets.get(end);
            at.remove(wire);
            if (at.isEmpty()) sockets.remove(end);
        }
        for (long chunk : chunkKeys(wire)) {
            Set<Wire> at = chunks.get(chunk);
            at.remove(wire);
            if (at.isEmpty()) chunks.remove(chunk);
        }
        revision++;
        setDirty();
        return wire;
    }

    private void index(Wire wire) {
        wires.put(wire.id(), wire);
        for (End end : List.of(wire.a(), wire.b())) sockets.computeIfAbsent(end, key -> new LinkedHashSet<>()).add(wire);
        for (long chunk : chunkKeys(wire)) chunks.computeIfAbsent(chunk, key -> new LinkedHashSet<>()).add(wire);
    }

    private static List<Long> chunkKeys(Wire wire) {
        ChunkPos a = new ChunkPos(wire.a().pos()), b = new ChunkPos(wire.b().pos());
        // Plot coordinates can be millions of blocks from world coordinates. Never enumerate that rectangle.
        return a.equals(b)?List.of(a.toLong()):List.of(a.toLong(),b.toLong());
    }

    public List<Wire> near(ChunkPos center, int radius, int limit) {
        Set<Wire> result = new HashSet<>();
        for (int x = center.x - radius; x <= center.x + radius; x++) for (int z = center.z - radius; z <= center.z + radius; z++) {
            Collection<Wire> at = chunks.get(ChunkPos.asLong(x, z));
            if (at != null) result.addAll(at);
        }
        return result.stream().sorted(Comparator.comparingInt((Wire wire) -> Math.min(center.getChessboardDistance(new ChunkPos(wire.a().pos())),
                center.getChessboardDistance(new ChunkPos(wire.b().pos())))).thenComparingLong(Wire::id)).limit(limit).toList();
    }

    /** Native moving-plot coordinates must be compared in world space for scene delivery and picking. */
    public List<Wire> nearWorld(Level level,ChunkPos center,int radius,int limit) {
        return wires.values().stream().filter(wire -> {
            Vec3 a=com.mysteryman.embeddeddev.compat.SableCompat.worldPosition(level,wire.a().anchor());
            Vec3 b=com.mysteryman.embeddeddev.compat.SableCompat.worldPosition(level,wire.b().anchor());
            return Math.min(center.getChessboardDistance(new ChunkPos(BlockPos.containing(a))),
                    center.getChessboardDistance(new ChunkPos(BlockPos.containing(b))))<=radius;
        }).sorted(Comparator.comparingLong(Wire::id)).limit(limit).toList();
    }

    public static Vec3 point(Vec3 a, Vec3 b, double t) {
        double clearance = Math.min(a.y - Math.floor(a.y), b.y - Math.floor(b.y));
        double sag = Math.min(clearance * .7, Math.min(.45, a.distanceTo(b) * .035));
        return a.lerp(b, t).add(0, -4 * t * (1 - t) * sag, 0);
    }

    private static boolean blocked(ServerLevel level, End a, End b) {
        Object first=com.mysteryman.embeddeddev.compat.SableCompat.containing(level,a.pos());
        Object second=com.mysteryman.embeddeddev.compat.SableCompat.containing(level,b.pos());
        if(first!=second) {
            Vec3 from=com.mysteryman.embeddeddev.compat.SableCompat.worldPosition(level,a.anchor());
            Vec3 to=com.mysteryman.embeddeddev.compat.SableCompat.worldPosition(level,b.anchor());
            int steps=units(level,a,b)*8;Vec3 previous=from;
            for(int i=1;i<=steps;i++) {
                Vec3 point=point(from,to,(double)i/steps);
                var hit=level.clip(new net.minecraft.world.level.ClipContext(previous,point,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null));
                if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&!hit.getBlockPos().equals(a.pos())&&!hit.getBlockPos().equals(b.pos()))return true;
                previous=point;
            }
            return false;
        }
        int steps = units(a, b) * 8;
        Vec3 previous = a.anchor();
        for (int i = 1; i <= steps; i++) {
            Vec3 point = point(a.anchor(), b.anchor(), (double) i / steps);
            BlockPos cell = BlockPos.containing(point);
            if (!cell.equals(a.pos()) && !cell.equals(b.pos())) {
                if (!level.hasChunkAt(cell)) return true;
                var shape = level.getBlockState(cell).getCollisionShape(level, cell, CollisionContext.empty());
                if (shape.clip(previous, point, cell) != null) return true;
            }
            previous = point;
        }
        return false;
    }

    public static IllegalArgumentException error(String reason) { return new IllegalArgumentException("wire.embeddeddev." + reason); }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        wires.values().stream().sorted(Comparator.comparingLong(Wire::id)).forEach(wire -> {
            var entry = new CompoundTag();
            entry.putLong("Id", wire.id());
            entry.put("A", wire.a().save());
            entry.put("B", wire.b().save());
            var pairs = new ListTag();
            for (Pair pair : wire.pairs()) {
                var row = new CompoundTag();
                row.put("A", pair.a().save());
                row.put("B", pair.b().save());
                pairs.add(row);
            }
            entry.put("Pairs", pairs);
            entries.add(entry);
        });
        tag.put("Wires", entries);
        tag.putLong("NextId", nextId);
        return tag;
    }

    public static CableNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        var network = new CableNetwork();
        network.nextId = Math.max(1, tag.getLong("NextId"));
        for (Tag value : tag.getList("Wires", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            try {
                End a = End.load(entry.getCompound("A")), b = End.load(entry.getCompound("B"));
                long id = entry.getLong("Id");
                if (a.pos().equals(b.pos()) || a.anchor().distanceTo(b.anchor()) > MAX_LENGTH || id < 1 || network.wires.containsKey(id)) continue;
                var pairs = new ArrayList<Pair>();
                for (Tag row : entry.getList("Pairs", Tag.TAG_COMPOUND)) {
                    var pair = (CompoundTag) row;
                    pairs.add(new Pair(Lane.load(pair.getCompound("A")), Lane.load(pair.getCompound("B"))));
                }
                if (pairs.isEmpty() || pairs.size() > FaceBinding.MAX_PINS) continue;
                network.index(new Wire(id, a, b, pairs, units(a, b)));
                network.nextId = Math.max(network.nextId, id + 1);
            } catch (IllegalArgumentException ignored) {
                // Malformed segments cannot be attached to any endpoint.
            }
        }
        return network;
    }
}
