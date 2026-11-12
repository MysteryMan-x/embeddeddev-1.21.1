package com.mysteryman.embeddeddev.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mysteryman.embeddeddev.block.PeripheralEntity;
import com.mysteryman.embeddeddev.item.ModItems;
import com.mysteryman.embeddeddev.network.WireNetwork;
import com.mysteryman.embeddeddev.wire.CableNetwork;
import com.mysteryman.embeddeddev.wire.CablePicking;
import com.mysteryman.embeddeddev.wire.PinGroup;
import com.mysteryman.embeddeddev.block.SystemBoardEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class WireRenderer {
    private static final RenderType LINE = RenderType.lines();
    private static final RenderType CABLE = RenderType.debugQuads();
    private static final int[] KEY_COLORS = {0xFF59D0C6, 0xFFEE8658, 0xFFAAC766, 0xFFB88AE8, 0xFFE899C0,
            0xFF62A5EB, 0xFFF0D077, 0xFF85DBC2, 0xFFDFD3FF};
    private static ClientLevel sceneLevel;
    private static List<Segment> segments = List.of();
    private static CableNetwork.End start;
    private record Segment(Vec3 a, Vec3 b, int color, AABB bounds, Vec3[][] rings) {
        Segment(CableNetwork.End first, CableNetwork.End second, boolean bundle) {
            this(first.anchor(), second.anchor(), bundle ? 0xFFE7B66D : KEY_COLORS[Math.max(0, Math.max(first.buttonKey(), second.buttonKey()))],
                    new AABB(first.anchor(), second.anchor()).expandTowards(0, -.45, 0).inflate(.06),
                    buildRings(first.anchor(), second.anchor(), bundle ? .016 : .011));
        }
    }

    public static void receive(WireNetwork.Update packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        CompoundTag data = packet.data();
        if (sceneLevel != mc.level) {
            sceneLevel = mc.level;
            segments = List.of();
            start = null;
        }
        switch (data.getString("Kind")) {
            case "scene" -> {
                if (!mc.level.dimension().location().toString().equals(data.getString("Dimension"))) return;
                var next = new ArrayList<Segment>();
                for (Tag value : data.getList("Wires", Tag.TAG_COMPOUND)) {
                    var wire = (CompoundTag) value;
                    next.add(new Segment(CableNetwork.End.load(wire.getCompound("A")),
                            CableNetwork.End.load(wire.getCompound("B")), wire.getBoolean("Bundle")));
                }
                segments = List.copyOf(next);
            }
            case "start" -> start = CableNetwork.End.load(data.getCompound("First"));
            case "pair" -> mc.setScreen(new WirePairingScreen(data));
            case "clear" -> start = null;
            default -> {}
        }
    }

    private static boolean holding(Minecraft mc, Item tool) {
        return mc.player != null && (mc.player.getMainHandItem().is(tool) || mc.player.getOffhandItem().is(tool));
    }

    private static CableNetwork.End target(Minecraft mc) {
        return mc.level != null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? CableNetwork.target(mc.level, hit) : null;
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || sceneLevel != mc.level) return;
        boolean coil = holding(mc, ModItems.WIRE_COIL.get());
        CableNetwork.End target = coil ? target(mc) : null;
        Segment selected = null;
        if (holding(mc, ModItems.WIRE_CUTTER.get())) {
            Vec3 eye = mc.player.getEyePosition();
            Vec3 tip = mc.player.pick(mc.player.blockInteractionRange(), 1, false).getLocation();
            double best = .15 * .15;
            for (var wire : segments) {
                double distance = CablePicking.distanceSquared(wire.a(), wire.b(), eye, tip);
                if (distance < best) { best = distance; selected = wire; }
            }
        }
        if (segments.isEmpty() && target == null && (!coil || start == null)) return;
        var stack = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer solid = buffers.getBuffer(CABLE);
        for (Segment wire : segments) {
            if (event.getFrustum() != null && !event.getFrustum().isVisible(wire.bounds())) continue;
            drawSolid(stack.last(), solid, wire, camera, wire == selected ? 0xFFFF6868 : wire.color());
        }
        buffers.endBatch(CABLE);
        VertexConsumer buffer = buffers.getBuffer(LINE);
        if (target != null) {
            if (target.board()) for (int side = 0; side <= 5; side++) for (int group = 0; group < PinGroup.COUNT; group++) {
                if (group > 0 && mc.level.getBlockEntity(target.pos()) instanceof SystemBoardEntity board && board.binding(side, group).pins().isEmpty()) continue;
                var socket = new CableNetwork.End(target.pos(), CableNetwork.boardSocket(side, group));
                var point = socket.anchor();
                mark(stack, buffer, point.subtract(camera), socket.equals(target));
            }
            else if (mc.level.getBlockEntity(target.pos()) instanceof PeripheralEntity panel && panel.buttons() != null) {
                for (int key = 0; key < panel.buttons().count(); key++) {
                    var socket = new CableNetwork.End(target.pos(), CableNetwork.buttonSocket(key));
                    mark(stack, buffer, socket.anchor().subtract(camera), socket.equals(target));
                }
            } else mark(stack, buffer, target.anchor().subtract(camera), true);
        }
        if (coil && start != null) {
            mark(stack, buffer, start.anchor().subtract(camera), true);
            if (target != null && !start.equals(target)) {
                boolean valid = !start.pos().equals(target.pos()) && start.anchor().distanceTo(target.anchor()) <= CableNetwork.MAX_LENGTH;
                drawCable(stack.last(), buffer, start.anchor(), target.anchor(), camera, valid ? 0xFF99F5B7 : 0xFFFF6868);
            }
        }
        buffers.endBatch(LINE);
    }

    private static Vec3[][] buildRings(Vec3 from, Vec3 to, double radius) {
        int steps = Math.max(4, (int) Math.ceil(from.distanceTo(to)));
        var rings = new Vec3[steps + 1][4];
        Vec3 across = to.subtract(from).cross(new Vec3(0, 1, 0));
        if (across.lengthSqr() < 1e-8) across = new Vec3(1, 0, 0);
        across = across.normalize().scale(radius);
        for (int step = 0; step <= steps; step++) {
            double t = (double) step / steps;
            Vec3 point = CableNetwork.point(from, to, t);
            Vec3 tangent = CableNetwork.point(from, to, Math.min(1, t + .01))
                    .subtract(CableNetwork.point(from, to, Math.max(0, t - .01)));
            Vec3 up = tangent.cross(across).normalize().scale(radius);
            rings[step][0] = point.subtract(across).subtract(up);
            rings[step][1] = point.add(across).subtract(up);
            rings[step][2] = point.add(across).add(up);
            rings[step][3] = point.subtract(across).add(up);
        }
        return rings;
    }

    private static void drawSolid(PoseStack.Pose pose, VertexConsumer buffer, Segment wire, Vec3 camera, int color) {
        Vec3[][] rings = wire.rings();
        for (int side = 0; side < 4; side++) {
            int next = (side + 1) % 4;
            float shade = switch (side) { case 0 -> .6F; case 2 -> 1F; default -> .8F; };
            int r = (int) ((color >> 16 & 255) * shade), g = (int) ((color >> 8 & 255) * shade), b = (int) ((color & 255) * shade);
            for (int step = 1; step < rings.length; step++) {
                cableVertex(pose, buffer, rings[step - 1][side], camera, r, g, b);
                cableVertex(pose, buffer, rings[step - 1][next], camera, r, g, b);
                cableVertex(pose, buffer, rings[step][next], camera, r, g, b);
                cableVertex(pose, buffer, rings[step][side], camera, r, g, b);
            }
        }
    }

    private static void cableVertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 point, Vec3 camera, int r, int g, int b) {
        buffer.addVertex(pose, (float) (point.x - camera.x), (float) (point.y - camera.y), (float) (point.z - camera.z)).setColor(r, g, b, 255);
    }

    private static void mark(PoseStack stack, VertexConsumer buffer, Vec3 p, boolean selected) {
        LevelRenderer.renderLineBox(stack, buffer, new AABB(p, p).inflate(selected ? .065 : .04),
                selected ? .7F : .25F, selected ? 1 : .65F, .75F, 1);
    }

    private static void drawCable(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, Vec3 to, Vec3 camera, int color) {
        Vec3 previous = from.subtract(camera);
        int count = Math.max(4, (int) Math.ceil(from.distanceTo(to)));
        for (int step = 1; step <= count; step++) {
            Vec3 point = CableNetwork.point(from, to, (double) step / count).subtract(camera);
            Vec3 normal = point.subtract(previous).normalize();
            vertex(buffer, pose, previous, normal, color);
            vertex(buffer, pose, point, normal, color);
            previous = point;
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, Vec3 normal, int color) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        boolean coil = holding(mc, ModItems.WIRE_COIL.get());
        var entity = mc.level.getBlockEntity(hit.getBlockPos());
        boolean button = entity instanceof PeripheralEntity panel && panel.buttons() != null;
        if (!button && !coil) return;
        CableNetwork.End target = target(mc);
        var graphics = event.getGuiGraphics();
        int x = graphics.guiWidth() / 2, y = graphics.guiHeight() / 2 + 14;
        if (target == null) {
            if (button && !coil) graphics.drawCenteredString(mc.font, Component.translatable("buttons.embeddeddev.configure_hint"), x, y, 0xFFE2FBFA);
            return;
        }
        String pins = CableNetwork.deviceLanes(mc.level, target).stream().map(CableNetwork.Lane::name).collect(Collectors.joining(", "));
        Component name = button ? Component.translatable("buttons.embeddeddev.key", target.buttonKey() + 1)
                : target.board() ? BoardScreen.faceName(target.face()) : mc.level.getBlockState(target.pos()).getBlock().getName();
        if (target.board() && mc.level.getBlockEntity(target.pos()) instanceof SystemBoardEntity board) {
            name = name.copy().append(" · " + board.groups(target.face()).get(target.group()).name());
        }
        String label = name.getString() + (pins.isEmpty() ? "" : " · " + pins);
        graphics.drawCenteredString(mc.font, mc.font.plainSubstrByWidth(label, graphics.guiWidth() - 20), x, y, 0xFFE2FBFA);
        if (coil && start != null && sceneLevel == mc.level && !start.pos().equals(target.pos())) {
            int cost = CableNetwork.units(start, target);
            graphics.drawCenteredString(mc.font, Component.translatable(cost <= CableNetwork.MAX_LENGTH
                    ? "wire.embeddeddev.cost" : "wire.embeddeddev.too_far", cost), x, y + 11, 0xFFFFDE9E);
        }
    }

    private WireRenderer() {}
}
