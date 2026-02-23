package com.justheare.paperjjk_client.shader;

import net.minecraft.util.math.Vec3d;

/**
 * Manages the simple domain (간이영역) visual effect state.
 *
 * A white expanding circle spreads from the caster's feet while
 * the surrounding area darkens. The circle expands at EXPAND_SPEED
 * blocks/second up to MAX_RADIUS blocks.
 */
public class DomainEffectManager {

    public static final float MAX_RADIUS   = 10.0f; // blocks
    public static final float EXPAND_SPEED =  5.0f; // blocks per second

    private static boolean active         = false;
    private static Vec3d   casterFeetPos  = Vec3d.ZERO;
    private static long    startTimeMs    = 0;

    public static void activate(Vec3d feetPos) {
        active       = true;
        casterFeetPos = feetPos;
        startTimeMs  = System.currentTimeMillis();
    }

    public static void deactivate() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static Vec3d getCasterFeetPos() {
        return casterFeetPos;
    }

    /** Returns the current expanding circle radius in world-space blocks. */
    public static float getExpandRadius() {
        if (!active) return 0.0f;
        float elapsed = (System.currentTimeMillis() - startTimeMs) / 1000.0f;
        return Math.min(elapsed * EXPAND_SPEED, MAX_RADIUS);
    }
}
