package com.justheare.paperjjk_client.shader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;

/**
 * Manages dynamic post-effect by recreating the processor with new uniform values
 */
public class DynamicPostEffectManager {
    private static final Identifier REFRACTION_SHADER = Identifier.of("paperjjk_client", "refraction");
    private static float lastCenterX = 0.5f;
    private static float lastCenterY = 0.5f;
    private static float lastRadius = 0.3f;
    private static float lastStrength = 0.05f;
    private static long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 50; // Update every 50ms (20 times per second)

    /**
     * Update the refraction effect with new parameters
     * Only recreates if values have changed significantly
     */
    public static void updateEffect(float centerX, float centerY, float radius, float strength) {
        long currentTime = System.currentTimeMillis();

        // Check if enough time has passed since last update
        if (currentTime - lastUpdateTime < UPDATE_INTERVAL_MS) {
            return;
        }

        // Check if values have changed significantly (threshold to avoid unnecessary updates)
        float threshold = 0.01f;
        if (Math.abs(centerX - lastCenterX) < threshold &&
            Math.abs(centerY - lastCenterY) < threshold &&
            Math.abs(radius - lastRadius) < threshold &&
            Math.abs(strength - lastStrength) < threshold) {
            return;
        }

        // Update stored values
        lastCenterX = centerX;
        lastCenterY = centerY;
        lastRadius = radius;
        lastStrength = strength;
        lastUpdateTime = currentTime;

        // Recreate the post-processor
        recreatePostProcessor();
    }

    /**
     * Recreate the post-processor to apply new uniform values
     */
    private static void recreatePostProcessor() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        GameRenderer renderer = client.gameRenderer;
        if (renderer == null) return;

        // Check if post-processing is enabled in settings
        if (!com.justheare.paperjjk_client.data.PlayerData.isPostProcessingEnabled()) {
            // Clear any active post-processor if disabled
            renderer.clearPostProcessor();
            return;
        }

        // Check if our shader is currently active
        Identifier currentId = renderer.getPostProcessorId();
        if (currentId != null && currentId.equals(REFRACTION_SHADER)) {
            // Clear and reload the shader
            // This will read the JSON again with the current uniform values
            renderer.clearPostProcessor();

            // Use accessor to call setPostProcessor
            ((com.justheare.paperjjk_client.mixin.client.GameRendererAccessor) renderer)
                .invokeSetPostProcessor(REFRACTION_SHADER);

            System.out.println("[DynamicPostEffectManager] Recreated post-processor with new values: " +
                String.format("center=(%.3f, %.3f), radius=%.2f, strength=%.3f",
                    lastCenterX, lastCenterY, lastRadius, lastStrength));
        }
    }

    public static float getLastCenterX() {
        return lastCenterX;
    }

    public static float getLastCenterY() {
        return lastCenterY;
    }

    public static float getLastRadius() {
        return lastRadius;
    }

    public static float getLastStrength() {
        return lastStrength;
    }
}
