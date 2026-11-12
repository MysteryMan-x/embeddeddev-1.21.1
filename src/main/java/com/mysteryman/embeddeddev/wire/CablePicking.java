package com.mysteryman.embeddeddev.wire;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

public final class CablePicking {
    /** Selects the portion of a cable nearest to the visible part of the player's ray. */
    public static double distanceSquared(Vec3 a, Vec3 b, Vec3 eye, Vec3 tip) {
        if (!new AABB(a, b).expandTowards(0, -.45, 0).inflate(.16).intersects(new AABB(eye, tip))) {
            return Double.POSITIVE_INFINITY;
        }
        Vec3 ray = tip.subtract(eye);
        double rayLength = ray.lengthSqr();
        if (rayLength == 0) return Double.POSITIVE_INFINITY;
        int steps = Math.max(2, (int) Math.ceil(a.distanceTo(b) * 8));
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= steps; i++) {
            Vec3 point = CableNetwork.point(a, b, (double) i / steps);
            double depth = point.subtract(eye).dot(ray) / rayLength;
            if (depth >= 0 && depth <= 1.02) best = Math.min(best, point.distanceToSqr(eye.add(ray.scale(Math.min(depth, 1)))));
        }
        return best;
    }

    private CablePicking() {}
}
