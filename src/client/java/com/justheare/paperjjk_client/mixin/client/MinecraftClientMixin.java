package com.justheare.paperjjk_client.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inject just before blitToScreen() in MinecraftClient.render(boolean).
 *
 * At this point gameRenderer.render() has fully completed, so
 * client.getFramebuffer() contains the finished composited scene.
 * This is the correct time to apply our post-processing on top.
 *
 * Previous approach (renderWorld RETURN) was wrong because
 * MinecraftClient.render() calls clearColorAndDepthTextures() BEFORE
 * gameRenderer.render(), and the framebuffer we were writing to was
 * still being built by the GPU command queue at that point. With Iris
 * it worked because Iris uses its own framebuffer path that happens to
 * be flushed by the time our inject ran.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
            shift = At.Shift.AFTER
        )
    )
    private void paperjjk$postGameRendererRender(boolean tick, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        if (!com.justheare.paperjjk_client.data.PlayerData.isPostProcessingEnabled()) return;

        com.justheare.paperjjk_client.shader.RefractionEffectManager.tickEffects();

        java.util.List<com.justheare.paperjjk_client.shader.RefractionEffectManager.RefractionEffect> effects =
            com.justheare.paperjjk_client.shader.RefractionEffectManager.getEffects();

        if (effects.isEmpty()) return;

        net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
        org.joml.Matrix4f projectionMatrix = new org.joml.Matrix4f(
            client.gameRenderer.getBasicProjectionMatrix(
                client.options.getFov().getValue().floatValue()
            )
        );

        org.joml.Matrix4f viewMatrix = new org.joml.Matrix4f();
        viewMatrix.rotationX((float) Math.toRadians(camera.getPitch()));
        viewMatrix.rotateY((float) Math.toRadians(camera.getYaw() + 180.0f));
        viewMatrix.translate(
            (float) -((CameraAccessor) camera).getPos().x,
            (float) -((CameraAccessor) camera).getPos().y,
            (float) -((CameraAccessor) camera).getPos().z
        );

        for (com.justheare.paperjjk_client.shader.RefractionEffectManager.RefractionEffect effect : effects) {
            net.minecraft.util.math.Vec3d screenPos =
                com.justheare.paperjjk_client.util.WorldToScreenUtil.worldToScreen(
                    effect.worldPos, camera, projectionMatrix);

            if (screenPos == null) continue;

            float distance = (float) screenPos.z;

            float baseRadius;
            if ("MURASAKI_EXPLODE".equals(effect.effectType)) {
                baseRadius = effect.radius / 50.0f;
            } else {
                baseRadius = 0.2f;
            }
            float scaledRadius = baseRadius / Math.max(1.0f, distance / 10.0f);

            float effectDepth = calculateDepth(effect.worldPos, viewMatrix, projectionMatrix);

            int effectType = 0;
            if ("AKA".equals(effect.effectType)) {
                effectType = 1;
            } else if ("MURASAKI".equals(effect.effectType) || "MURASAKI_EXPLODE".equals(effect.effectType)) {
                effectType = 2;
            }

            com.justheare.paperjjk_client.render.CustomPostProcessing.render(
                (float) screenPos.x,
                (float) screenPos.y,
                scaledRadius,
                effect.strength,
                effectDepth,
                effectType
            );
        }
    }

    private float calculateDepth(net.minecraft.util.math.Vec3d worldPos,
                                  org.joml.Matrix4f viewMatrix,
                                  org.joml.Matrix4f projectionMatrix) {
        org.joml.Vector4f clipPos = new org.joml.Vector4f(
            (float) worldPos.x, (float) worldPos.y, (float) worldPos.z, 1.0f
        );
        viewMatrix.transform(clipPos);
        projectionMatrix.transform(clipPos);
        if (clipPos.w != 0) {
            clipPos.x /= clipPos.w;
            clipPos.y /= clipPos.w;
            clipPos.z /= clipPos.w;
        }
        return Math.max(0.0f, Math.min(1.0f, (clipPos.z + 1.0f) * 0.5f));
    }
}
