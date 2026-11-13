package com.mysteryman.embeddeddev.compat;

import com.mysteryman.embeddeddev.EmbeddedDev;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Quaterniondc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.lang.reflect.Method;

/** Optional Sable adapter: reads real physics, never supplies stabilization forces. */
public final class SableCompat {
    public record State(Object body, double angleX, double angleZ, double rateX, double rateZ,
                        Vec3 position, double verticalSpeed, double mass, double gravity, double pressure,
                        Quaterniondc orientation, Vector3dc linearVelocity, double rateY) {}
    private record Api(Object helper, Method containing, Method pose, Method handle, Method angular, Method linear,
                       Method massTracker, Method mass, Method gravity, Method pressure) {}
    private static Api api;
    private static boolean tried, warned;
    private static Api api() {
        if (tried) return api;
        tried = true;
        if (!ModList.get().isLoaded("sable")) return null;
        try {
            Object helper = Class.forName("dev.ryanhcode.sable.Sable").getField("HELPER").get(null);
            Class<?> sub = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            Class<?> serverSub = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");
            Class<?> handle = Class.forName("dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle");
            Class<?> mass = Class.forName("dev.ryanhcode.sable.api.physics.mass.MassData");
            Class<?> physics = Class.forName("dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData");
            api = new Api(helper, helper.getClass().getMethod("getContaining", Level.class, Vec3i.class),
                    sub.getMethod("logicalPose"), handle.getMethod("of", serverSub),
                    handle.getMethod("getAngularVelocity", Vector3d.class), handle.getMethod("getLinearVelocity", Vector3d.class),
                    serverSub.getMethod("getMassTracker"), mass.getMethod("getMass"),
                    physics.getMethod("getGravity", Level.class, Vector3dc.class), physics.getMethod("getAirPressure", Level.class, Vector3dc.class));
        } catch (ReflectiveOperationException | LinkageError error) { warn(error); }
        return api;
    }
    private static void warn(Throwable error) {
        if (!warned) { warned = true; EmbeddedDev.LOGGER.warn("Optional Sable sensor integration unavailable", error); }
    }
    public static Object containing(Level level, BlockPos pos) {
        Api a = api(); if (a == null) return null;
        try { return a.containing.invoke(a.helper, level, pos); }
        catch (ReflectiveOperationException error) { warn(error); return null; }
    }
    public static Vec3 worldPosition(Level level, Vec3 position) {
        Object body = containing(level, BlockPos.containing(position)); if (body == null) return position;
        try {
            Object pose = level.isClientSide ? body.getClass().getMethod("renderPose").invoke(body) : api().pose.invoke(body);
            return (Vec3) pose.getClass().getMethod("transformPosition", Vec3.class).invoke(pose, position);
        } catch (ReflectiveOperationException error) { warn(error); return position; }
    }
    public static State sample(ServerLevel level, BlockPos pos) {
        Api a = api(); Object body = containing(level, pos); if (a == null || body == null) return null;
        try {
            Object pose = a.pose.invoke(body);
            Quaterniondc orientation = (Quaterniondc) pose.getClass().getMethod("orientation").invoke(pose);
            Vector3d down = orientation.transformInverse(new Vector3d(0, -1, 0));
            Object handle = a.handle.invoke(null, body); if (handle == null) return null;
            Vector3d rate = (Vector3d) a.angular.invoke(handle, new Vector3d());
            orientation.transformInverse(rate);
            Vector3d velocity = (Vector3d) a.linear.invoke(handle, new Vector3d());
            Vec3 world = (Vec3) pose.getClass().getMethod("transformPosition", Vec3.class).invoke(pose, pos.getCenter());
            Vector3d point = new Vector3d(world.x, world.y, world.z);
            double mass = ((Number) a.mass.invoke(a.massTracker.invoke(body))).doubleValue();
            double gravity = ((Vector3dc) a.gravity.invoke(null, level, point)).length();
            double pressure = ((Number) a.pressure.invoke(null, level, point)).doubleValue();
            return new State(body, Math.toDegrees(Math.atan2(down.z, -down.y)), Math.toDegrees(Math.atan2(-down.x, -down.y)),
                    Math.toDegrees(rate.x), Math.toDegrees(rate.z), world, velocity.y, mass, gravity, pressure,
                    new Quaterniond(orientation), new Vector3d(velocity), Math.toDegrees(rate.y));
        } catch (ReflectiveOperationException | RuntimeException error) { warn(error); return null; }
    }
    public static double distanceSquared(Level level, Vec3 player, BlockPos target) {
        return worldPosition(level, player).distanceToSqr(worldPosition(level, target.getCenter()));
    }
    private SableCompat() {}
}
