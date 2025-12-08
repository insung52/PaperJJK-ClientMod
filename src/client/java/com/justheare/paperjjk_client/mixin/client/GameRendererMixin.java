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

        // Apply each refraction effect
        for (com.justheare.paperjjk_client.shader.RefractionEffectManager.RefractionEffect effect : effects) {
            // Convert world position to screen coordinates
            net.minecraft.util.math.Vec3d screenPos =
                com.justheare.paperjjk_client.util.WorldToScreenUtil.worldToScreen(
                    effect.worldPos, camera, projectionMatrix);

            if (screenPos != null) {
                float distance = (float) screenPos.z;
                float baseRadius = effect.radius;
                float scaledRadius = baseRadius / Math.max(1.0f, distance / 10.0f);
                float finalStrength = effect.strength * 0.1f;

                /*.out.println("[GameRendererMixin] Applying distortion: center=(" +
                    String.format("%.3f", screenPos.x) + "," + String.format("%.3f", screenPos.y) +
                    ") radius=" + String.format("%.3f", scaledRadius) +
                    " strength=" + String.format("%.3f", finalStrength));*/

                // Apply custom post-processing
                com.justheare.paperjjk_client.render.CustomPostProcessing.render(
                    (float) screenPos.x,
                    (float) screenPos.y,
                    scaledRadius,
                    finalStrength
                );
            }
        }
    }
}
