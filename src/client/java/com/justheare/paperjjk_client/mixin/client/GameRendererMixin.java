package com.justheare.paperjjk_client.mixin.client;

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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        // Tick all effects for interpolation
        com.justheare.paperjjk_client.shader.RefractionEffectManager.tickEffects();

        // Get all refraction effects
        java.util.List<com.justheare.paperjjk_client.shader.RefractionEffectManager.RefractionEffect> effects =
            com.justheare.paperjjk_client.shader.RefractionEffectManager.getEffects();

        if (effects.isEmpty()) return;

        //System.out.println("[GameRendererMixin] renderWorld RETURN - applying post-processing!");

        // Get camera and projection matrix
        net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
        org.joml.Matrix4f projectionMatrix = new org.joml.Matrix4f(
            client.gameRenderer.getBasicProjectionMatrix(
                client.options.getFov().getValue().floatValue()
            )
        );

        // Get view matrix (camera transformation)
        org.joml.Matrix4f viewMatrix = new org.joml.Matrix4f();
        viewMatrix.rotationX((float) Math.toRadians(camera.getPitch()));
        viewMatrix.rotateY((float) Math.toRadians(camera.getYaw()+180.0f));
        viewMatrix.translate(
            (float) -camera.getPos().x,
            (float) -camera.getPos().y,
            (float) -camera.getPos().z
        );

        // Apply each refraction effect
        for (com.justheare.paperjjk_client.shader.RefractionEffectManager.RefractionEffect effect : effects) {
            // Convert world position to screen coordinates
            net.minecraft.util.math.Vec3d screenPos =
                com.justheare.paperjjk_client.util.WorldToScreenUtil.worldToScreen(
                    effect.worldPos, camera, projectionMatrix);

            if (screenPos != null) {
                float distance = (float) screenPos.z;

                // Single variable control: effect.strength controls EVERYTHING
                // Base values (when strength = 1.0)
                float baseRadius = 0.2f;
                float scaledRadius = baseRadius / Math.max(1.0f, distance / 10.0f);

                // Calculate effect depth in [0, 1] range for occlusion testing
                float effectDepth = calculateDepth(effect.worldPos, viewMatrix, projectionMatrix);

                /*.out.println("[GameRendererMixin] Applying distortion: center=(" +
                    String.format("%.3f", screenPos.x) + "," + String.format("%.3f", screenPos.y) +
                    ") radius=" + String.format("%.3f", scaledRadius) +
                    " strength=" + String.format("%.3f", effectiveDistortion) +
                    " depth=" + String.format("%.3f", effectDepth));*/

                // Apply custom post-processing with depth
                // Pass raw strength for bloom intensity control in shader
                com.justheare.paperjjk_client.render.CustomPostProcessing.render(
                    (float) screenPos.x,
                    (float) screenPos.y,
                    scaledRadius,
                    effect.strength,
                    effectDepth
                );
            }
        }
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
