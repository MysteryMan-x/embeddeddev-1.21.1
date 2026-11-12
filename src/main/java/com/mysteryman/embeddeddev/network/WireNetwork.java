package com.mysteryman.embeddeddev.network;

import com.mysteryman.embeddeddev.EmbeddedDev;
import com.mysteryman.embeddeddev.block.RedstonePortEntity;
import com.mysteryman.embeddeddev.block.SystemBoardEntity;
import com.mysteryman.embeddeddev.item.ModItems;
import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.wire.CableNetwork;
import com.mysteryman.embeddeddev.wire.CablePicking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public final class WireNetwork {
    private static final Map<ServerPlayer, Selection> SELECTED = new WeakHashMap<>();
    private static final Map<ServerPlayer, Scene> SCENES = new WeakHashMap<>();
    private static long nextToken;
    public static Consumer<Update> clientReceiver = update -> {};

    private record Selection(ResourceKey<Level> dimension, InteractionHand hand, CableNetwork.End first,
                             CableNetwork.End second, long token, int expires) {}
    private record Scene(ResourceKey<Level> dimension, ChunkPos chunk, long revision) {}

    public record Update(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EmbeddedDev.MODID, "wire_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeNbt(value.data()), buf -> new Update(buf.readNbt()));
        @Override public Type<Update> type() { return TYPE; }
    }

    public record Apply(long token, int[] lanes) implements CustomPacketPayload {
        public static final Type<Apply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EmbeddedDev.MODID, "wire_apply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Apply> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeVarLong(value.token());
            buf.writeVarIntArray(value.lanes());
        }, buf -> new Apply(buf.readVarLong(), buf.readVarIntArray(2 * FaceBinding.MAX_PINS)));
        @Override public Type<Apply> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Update.TYPE, Update.CODEC, (packet, context) -> clientReceiver.accept(packet));
        registrar.playToServer(Apply.TYPE, Apply.CODEC, WireNetwork::apply);
    }

    public static void click(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) { cancel(player, true); return; }
        var level = player.serverLevel();
        var target = CableNetwork.target(level, hit);
        try {
            if (target == null) throw CableNetwork.error("invalid_endpoint");
            checkPermissions(player, target);
            var cables = CableNetwork.get(level);
            Selection old = selection(player);
            if (old == null || old.hand() != hand) {
                if (!target.relay() && cables.lanes(level, target).isEmpty()) throw CableNetwork.error("no_pins");
                SELECTED.put(player, new Selection(level.dimension(), hand, target, null, 0, player.tickCount + 2400));
                var data = new CompoundTag();
                data.putString("Kind", "start");
                data.put("First", target.save());
                send(player, data);
                message(player, "wire.embeddeddev.selected", target.pos().toShortString());
                return;
            }
            checkPermissions(player, old.first());
            CableNetwork.Draft draft = cables.prepare(level, old.first(), target);
            checkWireStack(player, hand, draft.a(), draft.b());
            if (!draft.automatic().isEmpty()) {
                finish(player, hand, draft, draft.automatic());
            } else {
                var selection = new Selection(level.dimension(), hand, old.first(), target, ++nextToken, player.tickCount + 1200);
                SELECTED.put(player, selection);
                var data = new CompoundTag();
                data.putString("Kind", "pair");
                data.putLong("Token", selection.token());
                data.put("First", draft.a().save());
                data.put("Second", draft.b().save());
                data.put("A", laneTags(draft.aLanes()));
                data.put("B", laneTags(draft.bLanes()));
                send(player, data);
            }
        } catch (IllegalArgumentException error) {
            message(player, error.getMessage());
        }
    }

    private static ListTag laneTags(List<CableNetwork.Lane> lanes) {
        var list = new ListTag();
        for (var lane : lanes) list.add(lane.save());
        return list;
    }

    private static void apply(Apply packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Selection selection = selection(player);
        if (selection == null || selection.second() == null || packet.token() != selection.token()) return;
        if (packet.lanes().length == 0) { cancel(player, false); return; }
        try {
            if (packet.lanes().length % 2 != 0 || !player.canInteractWithBlock(selection.second().pos(), 1.0)) {
                throw CableNetwork.error("invalid_endpoint");
            }
            checkPermissions(player, selection.first());
            checkPermissions(player, selection.second());
            var level = player.serverLevel();
            var draft = CableNetwork.get(level).prepare(level, selection.first(), selection.second());
            var pairs = new ArrayList<CableNetwork.Pair>();
            for (int i = 0; i < packet.lanes().length; i += 2) {
                var a = CableNetwork.find(draft.aLanes(), packet.lanes()[i]);
                var b = CableNetwork.find(draft.bLanes(), packet.lanes()[i + 1]);
                if (a == null || b == null) throw CableNetwork.error("invalid_mapping");
                pairs.add(new CableNetwork.Pair(a, b));
            }
            finish(player, selection.hand(), draft, pairs);
        } catch (IllegalArgumentException error) {
            message(player, error.getMessage());
            cancel(player, false);
        }
    }

    private static void finish(ServerPlayer player, InteractionHand hand, CableNetwork.Draft draft, List<CableNetwork.Pair> pairs) {
        ItemStack stack = checkWireStack(player, hand, draft.a(), draft.b());
        var wire = CableNetwork.get(player.serverLevel()).connect(player.serverLevel(), draft.a(), draft.b(), pairs);
        if (!player.getAbilities().instabuild) stack.shrink(wire.units());
        cancel(player, false);
        message(player, "wire.embeddeddev.connected", wire.pairs().size(), wire.units());
    }

    private static ItemStack checkWireStack(ServerPlayer player, InteractionHand hand, CableNetwork.End a, CableNetwork.End b) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.WIRE_COIL.get()) || !player.getAbilities().instabuild && stack.getCount() < CableNetwork.units(a, b)) {
            throw CableNetwork.error("not_enough");
        }
        return stack;
    }

    private static void checkPermissions(ServerPlayer player, CableNetwork.End end) {
        if (!player.mayBuild() || !player.serverLevel().mayInteract(player, end.pos())) throw CableNetwork.error("permission");
    }

    public static void cut(ServerPlayer player) {
        var level = player.serverLevel();
        var cables = CableNetwork.get(level);
        var eye = player.getEyePosition();
        var tip = player.pick(player.blockInteractionRange(), 1, false).getLocation();
        CableNetwork.Wire selected = null;
        double best = .15 * .15;
        for (var wire : cables.near(player.chunkPosition(), 1, 1024)) {
            double distance = CablePicking.distanceSquared(wire.a().anchor(), wire.b().anchor(), eye, tip);
            if (distance < best) { selected = wire; best = distance; }
        }
        if (selected == null) { message(player, "wire.embeddeddev.no_wire"); return; }
        try {
            checkPermissions(player, selected.a());
            checkPermissions(player, selected.b());
            if (cables.remove(selected.id()) != null) {
                if (!player.getAbilities().instabuild) Block.popResource(level, player.blockPosition(),
                        new ItemStack(ModItems.WIRE_COIL.get(), selected.units()));
                message(player, "wire.embeddeddev.cut", selected.units());
            }
        } catch (IllegalArgumentException error) {
            message(player, error.getMessage());
        }
    }

    private static Selection selection(ServerPlayer player) {
        Selection selection = SELECTED.get(player);
        if (selection != null && (selection.dimension() != player.level().dimension() || player.tickCount > selection.expires())) {
            cancel(player, false);
            return null;
        }
        return selection;
    }

    public static void cancel(ServerPlayer player, boolean announce) {
        SELECTED.remove(player);
        var data = new CompoundTag();
        data.putString("Kind", "clear");
        send(player, data);
        if (announce) message(player, "wire.embeddeddev.cancelled");
    }

    public static void tickPlayer(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        selection(player);
        var level = player.serverLevel();
        var cables = CableNetwork.get(level);
        var scene = new Scene(level.dimension(), player.chunkPosition(), cables.revision());
        if (scene.equals(SCENES.put(player, scene))) return;
        var data = new CompoundTag();
        data.putString("Kind", "scene");
        data.putString("Dimension", level.dimension().location().toString());
        var entries = new ListTag();
        for (var wire : cables.near(scene.chunk(), 4, 1024)) {
            var tag = new CompoundTag();
            tag.put("A", wire.a().save());
            tag.put("B", wire.b().save());
            tag.putBoolean("Bundle", wire.pairs().size() > 1);
            entries.add(tag);
        }
        data.put("Wires", entries);
        send(player, data);
    }

    private static void send(ServerPlayer player, CompoundTag tag) { PacketDistributor.sendToPlayer(player, new Update(tag)); }
    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    private WireNetwork() {}
}
