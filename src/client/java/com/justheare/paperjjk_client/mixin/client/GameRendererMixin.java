package com.justheare.paperjjk_client.mixin.client;

import com.justheare.paperjjk_client.render.CustomPostProcessing;
import com.justheare.paperjjk_client.shader.RefractionEffectManager;
import com.justheare.paperjjk_client.util.WorldToScreenUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to render custom post-processing effects
 * Injects at TAIL (end) of render method
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderTail(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Only render if we have active effects and are in-game
        if (client.world == null || client.player == null) return;

        java.util.List<RefractionEffectManager.RefractionEffect> effects =
            RefractionEffectManager.getEffects();

        if (effects.isEmpty()) return;

        // Get camera and projection matrix
        GameRenderer renderer = (GameRenderer) (Object) this;
        Camera camera = renderer.getCamera();
        Matrix4f projectionMatrix = new Matrix4f(
            renderer.getBasicProjectionMatrix(
                client.options.getFov().getValue().floatValue()
            )
        );

        // Render each effect
        for (RefractionEffectManager.RefractionEffect effect : effects) {
            // Convert world position to screen coordinates
            Vec3d screenPos = WorldToScreenUtil.worldToScreen(effect.worldPos, camera, projectionMatrix);

            if (screenPos != null) {
                // Calculate radius based on distance
                float distance = (float) screenPos.z;
                float baseRadius = effect.radius;
                float scaledRadius = baseRadius / Math.max(1.0f, distance / 10.0f);

                // Render custom post-processing with dynamic uniforms!
                CustomPostProcessing.render(
                    (float) screenPos.x,
                    (float) screenPos.y,
                    scaledRadius,
                    effect.strength * 100.0f  // MUCH higher strength for visibility
                );
            }
        }
    }
}
