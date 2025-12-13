package com.justheare.paperjjk_client.mixin.client;

import com.justheare.paperjjk_client.mixin.client.CameraAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to GameRenderer for applying post-processing effects
 * Injects at the end of renderWorld, just like Iris does for renderLevel
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void paperjjk$applyPostProcessing(RenderTickCounter tickCounter, CallbackInfo ci) {
        com.justheare.paperjjk_client.DebugConfig.log("GameRendererMixin", "=== renderWorld RETURN - Starting post-processing ===");

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            com.justheare.paperjjk_client.DebugConfig.log("GameRendererMixin", "Skipping: world or player is null");
            return;
        }

        // Tick all effects for interpolation
        com.justheare.paperjjk_client.shader.RefractionEffectManager.tickEffects();

        // Get all refraction effects
        java.util.List<com.justheare.paperjjk_client.shader.RefractionEffectManager.RefractionEffect> effects =
            com.justheare.paperjjk_client.shader.RefractionEffectManager.getEffects();

        com.justheare.paperjjk_client.DebugConfig.log("GameRendererMixin", "Found " + effects.size() + " effect(s)");

        if (effects.isEmpty()) {
            com.justheare.paperjjk_client.DebugConfig.log("GameRendererMixin", "No effects to render, exiting");
            return;
        }

        // Get camera and projection matrix
        net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
        org.joml.Matrix4f projectionMatrix = new org.joml.Matrix4f(
            client.gameRenderer.getBasicProjectionMatrix(
                client.options.getFov().getValue().floatValue()
            )
        );

        com.justheare.paperjjk_client.DebugConfig.log("GameRendererMixin",
            "Camera pos: " + String.format("(%.1f, %.1f, %.1f)",
                ((CameraAccessor) camera).getPos().x,
                ((CameraAccessor) camera).getPos().y,
                ((CameraAccessor) camera).getPos().z));

        // Get view matrix (camera transformation)
        org.joml.Matrix4f viewMatrix = new org.joml.Matrix4f();
        viewMatrix.rotationX((float) Math.toRadians(camera.getPitch()));
        viewMatrix.rotateY((float) Math.toRadians(camera.getYaw()+180.0f));
        viewMatrix.translate(
            (float) -((CameraAccessor) camera).getPos().x,
            (float) -((CameraAccessor) camera).getPos().y,
            (float) -((CameraAccessor) camera).getPos().z
        );

        com.justheare.paperjjk_client.DebugConfig.log("GameRendererMixin", "Starting effect rendering loop...");

        // Apply each refraction effect
        int effectIndex = 0;
        for (com.justheare.paperjjk_client.shader.RefractionEffectManager.RefractionEffect effect : effects) {
            com.justheare.paperjjk_client.DebugConfig.log("GameRendererMixin",
                "Processing effect #" + effectIndex + " - Type: " + effect.effectType +
                ", WorldPos: " + String.format("(%.1f, %.1f, %.1f)", effect.worldPos.x, effect.worldPos.y, effect.worldPos.z));
            // Convert world position to screen coordinates
            net.minecraft.util.math.Vec3d screenPos =
                com.justheare.paperjjk_client.util.WorldToScreenUtil.worldToScreen(
                    effect.worldPos, camera, projectionMatrix);

            if (screenPos != null) {
                com.justheare.paperjjk_client.DebugConfig.log("GameRendererMixin",
                    "  ScreenPos: " + String.format("(%.3f, %.3f, %.3f)", screenPos.x, screenPos.y, screenPos.z));

                float distance = (float) screenPos.z;

                // Calculate radius - use effect.radius for MURASAKI_EXPLODE, otherwise use fixed base
                float baseRadius;
                if ("MURASAKI_EXPLODE".equals(effect.effectType)) {
                    // For unlimit_m explosion, use the expanding radius from server
                    baseRadius = effect.radius / 50.0f;  // Scale down for screen space (50 blocks = 1.0 screen radius)
                } else {
                    // For normal effects (AO, AKA, MURASAKI), use fixed base
                    baseRadius = 0.2f;
                }
                float scaledRadius = baseRadius / Math.max(1.0f, distance / 10.0f);

                // Calculate effect depth in [0, 1] range for occlusion testing
                float effectDepth = calculateDepth(effect.worldPos, viewMatrix, projectionMatrix);

                com.justheare.paperjjk_client.DebugConfig.log("GameRendererMixin",
                    "  Radius: " + String.format("%.3f", scaledRadius) +
                    ", Strength: " + String.format("%.3f", effect.strength) +
                    ", Depth: " + String.format("%.3f", effectDepth));

                // Apply custom post-processing with depth
                // Pass raw strength for bloom intensity control in shader
                // Determine effect type: AO=0, AKA=1, MURASAKI=2
                int effectType = 0; // Default AO
                if ("AKA".equals(effect.effectType)) {
                    effectType = 1;
                } else if ("MURASAKI".equals(effect.effectType) || "MURASAKI_EXPLODE".equals(effect.effectType)) {
                    effectType = 2;
                }

                com.justheare.paperjjk_client.DebugConfig.log("GameRendererMixin",
                    "  Calling CustomPostProcessing.render() with effectType=" + effectType);

                com.justheare.paperjjk_client.render.CustomPostProcessing.render(
                    (float) screenPos.x,
                    (float) screenPos.y,
                    scaledRadius,
                    effect.strength,
                    effectDepth,
                    effectType
                );

                com.justheare.paperjjk_client.DebugConfig.log("GameRendererMixin",
                    "  CustomPostProcessing.render() completed");
            } else {
                com.justheare.paperjjk_client.DebugConfig.log("GameRendererMixin",
                    "  ScreenPos is NULL - effect is behind camera or off-screen");
            }

            effectIndex++;
        }

        com.justheare.paperjjk_client.DebugConfig.log("GameRendererMixin", "=== Post-processing complete ===");
    }

    /**
     * Calculate depth value [0, 1] for a world position
     * This converts world space → view space → clip space → depth space
     */
    private float calculateDepth(net.minecraft.util.math.Vec3d worldPos,
                                  org.joml.Matrix4f viewMatrix,
                                  org.joml.Matrix4f projectionMatrix) {
        // Transform world position to clip space
        org.joml.Vector4f clipPos = new org.joml.Vector4f(
            (float) worldPos.x,
            (float) worldPos.y,
            (float) worldPos.z,
            1.0f
        );

        // Apply view matrix (world → view space)
        viewMatrix.transform(clipPos);

        // Apply projection matrix (view → clip space)
        projectionMatrix.transform(clipPos);

        // Perspective divide (clip space → NDC [-1, 1])
        if (clipPos.w != 0) {
            clipPos.x /= clipPos.w;
            clipPos.y /= clipPos.w;
            clipPos.z /= clipPos.w;
        }

        // Convert NDC depth to [0, 1] range (same as OpenGL depth buffer)
        // NDC z is in [-1, 1], depth buffer is [0, 1]
        float depth = (clipPos.z + 1.0f) * 0.5f;

        // Clamp to valid range
        return Math.max(0.0f, Math.min(1.0f, depth));
    }
}
