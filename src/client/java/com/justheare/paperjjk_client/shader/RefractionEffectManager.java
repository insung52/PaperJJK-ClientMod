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

        // Interpolation fields
        private Vec3d startPos;        // Position at start of interpolation
        private Vec3d targetPos;       // Target position
        private float startStrength;   // Strength at start of interpolation
        private float targetStrength;  // Target strength
        private long interpolationStartTime;
        private static final long INTERPOLATION_TIME_MS = 250; // 500ms smooth interpolation (0.5초)

        public RefractionEffect(Vec3d worldPos, float radius, float strength) {
            this.worldPos = worldPos;
            this.radius = radius;
            this.strength = strength;
            this.startPos = worldPos;
            this.targetPos = worldPos;
            this.startStrength = strength;
            this.targetStrength = strength;
            this.interpolationStartTime = System.currentTimeMillis();
        }

        /**
         * Update target position and strength for interpolation
         */
        public void updateTarget(Vec3d newPos, float newStrength) {
            // Set current position/strength as start point
            this.startPos = this.worldPos;
            this.startStrength = this.strength;

            // Set new target
            this.targetPos = newPos;
            this.targetStrength = newStrength;

            // Reset interpolation timer
            this.interpolationStartTime = System.currentTimeMillis();
        }

        /**
         * Perform interpolation tick - call this every render frame
         */
        public void tick() {
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - interpolationStartTime;

            if (elapsed >= INTERPOLATION_TIME_MS) {
                // Interpolation complete
                this.worldPos = targetPos;
                this.strength = targetStrength;
            } else {
                // Linear interpolation from start to target
                float alpha = (float) elapsed / INTERPOLATION_TIME_MS;

                // Interpolate position from startPos to targetPos
                this.worldPos = new Vec3d(
                    lerp(startPos.x, targetPos.x, alpha),
                    lerp(startPos.y, targetPos.y, alpha),
                    lerp(startPos.z, targetPos.z, alpha)
                );

                // Interpolate strength from startStrength to targetStrength
                this.strength = lerp(startStrength, targetStrength, alpha);
            }
        }

        /**
         * Linear interpolation helper
         */
        private float lerp(double start, double end, float alpha) {
            return (float) (start + (end - start) * alpha);
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

    /**
     * Update existing effect with new position and strength (with interpolation)
     * If no effect exists, creates a new one
     */
    public static void updateEffect(Vec3d worldPos, float radius, float strength) {
        if (effects.isEmpty()) {
            addEffect(worldPos, radius, strength);
        } else {
            // Update the primary (first) effect
            RefractionEffect effect = effects.get(0);
            effect.updateTarget(worldPos, strength);
        }
    }

    /**
     * Tick all effects for interpolation
     * Should be called every render frame
     */
    public static void tickEffects() {
        for (RefractionEffect effect : effects) {
            effect.tick();
        }
    }
}
