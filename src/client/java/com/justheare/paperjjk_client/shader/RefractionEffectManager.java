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
        public String effectType;  // "AO" or "AKA" to distinguish effects

        // Interpolation fields
        private Vec3d startPos;        // Position at start of interpolation
        private Vec3d targetPos;       // Target position
        private float startStrength;   // Strength at start of interpolation
        private float targetStrength;  // Target strength
        private long interpolationStartTime;
        private static final long INTERPOLATION_TIME_MS = 250; // 250ms smooth interpolation

        public RefractionEffect(Vec3d worldPos, float radius, float strength, String effectType) {
            this.worldPos = worldPos;
            this.radius = radius;
            this.strength = strength;
            this.effectType = effectType;
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
    public static void addEffect(Vec3d worldPos, float radius, float strength, String effectType) {
        com.justheare.paperjjk_client.DebugConfig.log("RefractionEffectManager",
            "addEffect() - Type: " + effectType + ", Pos: " +
            String.format("(%.1f, %.1f, %.1f)", worldPos.x, worldPos.y, worldPos.z) +
            ", Radius: " + radius + ", Strength: " + strength);

        // Remove existing effect of the same type first
        int removedCount = (int) effects.stream().filter(effect -> effect.effectType.equals(effectType)).count();
        effects.removeIf(effect -> effect.effectType.equals(effectType));

        if (removedCount > 0) {
            com.justheare.paperjjk_client.DebugConfig.log("RefractionEffectManager",
                "Removed " + removedCount + " existing effect(s) of type: " + effectType);
        }

        effects.add(new RefractionEffect(worldPos, radius, strength, effectType));

        com.justheare.paperjjk_client.DebugConfig.log("RefractionEffectManager",
            "Effect added successfully. Total effects: " + effects.size());
    }

    /**
     * Clear all effects
     */
    public static void clearEffects() {
        int count = effects.size();
        com.justheare.paperjjk_client.DebugConfig.log("RefractionEffectManager",
            "clearEffects() - Clearing " + count + " effect(s)");

        effects.clear();

        com.justheare.paperjjk_client.DebugConfig.log("RefractionEffectManager",
            "All effects cleared. Total effects: " + effects.size());
    }

    /**
     * Clear effects of a specific type (AO or AKA)
     */
    public static void clearEffectsByType(String effectType) {
        effects.removeIf(effect -> effect.effectType.equals(effectType));
    }

    /**
     * Get all active effects
     */
    public static List<RefractionEffect> getEffects() {
        // Only log occasionally to avoid spam
        if (System.currentTimeMillis() % 1000 < 16 && !effects.isEmpty()) {
            com.justheare.paperjjk_client.DebugConfig.log("RefractionEffectManager",
                "getEffects() - Returning " + effects.size() + " effect(s)");
        }
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
    public static void updateEffect(Vec3d worldPos, float radius, float strength, String effectType) {
        // Find effect of matching type
        RefractionEffect targetEffect = null;
        for (RefractionEffect effect : effects) {
            if (effect.effectType.equals(effectType)) {
                targetEffect = effect;
                break;
            }
        }

        if (targetEffect == null) {
            // No existing effect of this type, create new one
            addEffect(worldPos, radius, strength, effectType);
        } else {
            // Update existing effect of this type
            targetEffect.updateTarget(worldPos, strength);
        }
    }

    /**
     * Tick all effects for interpolation
     * Should be called every render frame
     */
    public static void tickEffects() {
        // Only log occasionally to avoid spam (once per second)
        if (System.currentTimeMillis() % 1000 < 16 && !effects.isEmpty()) {
            com.justheare.paperjjk_client.DebugConfig.log("RefractionEffectManager",
                "tickEffects() - Ticking " + effects.size() + " effect(s)");
        }

        for (RefractionEffect effect : effects) {
            effect.tick();
        }
    }
}
