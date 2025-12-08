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

        RefractionEffectManager.RefractionEffect effect = RefractionEffectManager.getPrimaryEffect();
        if (effect == null) return;

        // Get camera and projection matrix
        GameRenderer renderer = (GameRenderer) (Object) this;
        Camera camera = renderer.getCamera();
        Matrix4f projectionMatrix = new Matrix4f(
            renderer.getBasicProjectionMatrix(
                client.options.getFov().getValue().floatValue()
            )
        );

        // Convert world position to screen coordinates
        Vec3d screenPos = WorldToScreenUtil.worldToScreen(effect.worldPos, camera, projectionMatrix);

        if (screenPos != null) {
            System.out.println("[GameRendererMixin] Rendering refraction at screen: (" +
                String.format("%.3f", screenPos.x) + ", " + String.format("%.3f", screenPos.y) + ")");

            // Render custom post-processing with dynamic uniforms!
            CustomPostProcessing.render(
                (float) screenPos.x,
                (float) screenPos.y,
                effect.radius,
                effect.strength
            );
        }
    }
}
