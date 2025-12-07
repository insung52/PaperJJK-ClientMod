package com.justheare.paperjjk_client.shader;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages refraction effect positions in the world
 */
public class RefractionEffectManager {
    private static final List<RefractionEffect> effects = new ArrayList<>();

    public static class RefractionEffect {
        public Vec3d worldPos;
        public float radius;
        public float strength;

        public RefractionEffect(Vec3d worldPos, float radius, float strength) {
            this.worldPos = worldPos;
            this.radius = radius;
            this.strength = strength;
        }
    }

    /**
     * Add a refraction effect at world position
     */
    public static void addEffect(Vec3d worldPos, float radius, float strength) {
        effects.add(new RefractionEffect(worldPos, radius, strength));
    }

    /**
     * Clear all effects
     */
    public static void clearEffects() {
        effects.clear();
    }

    /**
     * Get all active effects
     */
    public static List<RefractionEffect> getEffects() {
        return effects;
    }

    /**
     * Get primary effect (for testing - first one)
     */
    public static RefractionEffect getPrimaryEffect() {
        return effects.isEmpty() ? null : effects.get(0);
    }
}
