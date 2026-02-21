package com.justheare.paperjjk_client.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

/**
 * Injects just after GameRenderer.render() finishes in MinecraftClient.render(boolean).
 *
 * At this point PostEffectProcessor has already composited DefaultFramebufferSet.mainFramebuffer
 * into client.getFramebuffer() (WindowFramebuffer), so the scene is ready to be post-processed.
 * We call PostEffectProcessor.render(client.getFramebuffer(), allocator) which uses
 * FrameGraphBuilder internally — the only correct way to do post-processing in MC 1.21.
 *
 * OpenGL direct operations (glBlitFramebuffer, glTexImage2D, etc.) do NOT work here because
 * MC 1.21 uses GPU Command Queue (CommandEncoder) for all rendering, and the results
 * are not readable via legacy GL calls at this point.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    private static final Identifier REFRACTION_EFFECT_ID =
        Identifier.of("paperjjk-client", "refraction");

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

        List<com.justheare.paperjjk_client.shader.RefractionEffectManager.RefractionEffect> effects =
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

        // Load the PostEffectProcessor for our refraction effect
        PostEffectProcessor processor;
        try {
            processor = client.getShaderLoader().loadPostEffect(
                REFRACTION_EFFECT_ID,
                Set.of(net.minecraft.client.render.DefaultFramebufferSet.MAIN)
            );
        } catch (Exception e) {
            System.err.println("[JJKMixin] Failed to load refraction post effect: " + e.getMessage());
            return;
        }

        if (processor == null) return;

        Framebuffer mainFb = client.getFramebuffer();

        // Apply one pass per effect by updating the uniform buffer before each render
        for (com.justheare.paperjjk_client.shader.RefractionEffectManager.RefractionEffect effect : effects) {
            net.minecraft.util.math.Vec3d screenPos =
                com.justheare.paperjjk_client.util.WorldToScreenUtil.worldToScreen(
                    effect.worldPos, camera, projectionMatrix);
            if (screenPos == null) continue;

            float distance = (float) screenPos.z;
            float baseRadius = "MURASAKI_EXPLODE".equals(effect.effectType) ? effect.radius / 50.0f : 0.2f;
            float scaledRadius = baseRadius / Math.max(1.0f, distance / 10.0f);

            int effectTypeInt = "AKA".equals(effect.effectType) ? 1 :
                                "MURASAKI".equals(effect.effectType) || "MURASAKI_EXPLODE".equals(effect.effectType) ? 2 : 0;

            // Update RefractionConfig uniform buffer via CommandEncoder
            updateRefractionUniforms(processor,
                (float) screenPos.x, (float) screenPos.y,
                scaledRadius, effect.strength, effectTypeInt);

            // Run the post-effect through MC's FrameGraphBuilder pipeline
            processor.render(mainFb, ObjectAllocator.TRIVIAL);
        }
    }

    /**
     * Updates the RefractionConfig uniform buffer in PostEffectProcessor.
     *
     * PostEffectPass creates its GpuBuffer with USAGE_UNIFORM (128) only,
     * which doesn't allow writeToBuffer (needs USAGE_COPY_DST=8).
     * We replace it once with a new buffer that has USAGE_COPY_DST|USAGE_UNIFORM (136),
     * then use writeToBuffer every frame after that.
     */
    @SuppressWarnings("unchecked")
    private void updateRefractionUniforms(PostEffectProcessor processor,
                                           float centerX, float centerY,
                                           float radius, float strength, int effectType) {
        try {
            java.lang.reflect.Field passesField = PostEffectProcessor.class.getDeclaredField("passes");
            passesField.setAccessible(true);
            List<?> passes = (List<?>) passesField.get(processor);
            if (passes.isEmpty()) return;
            Object firstPass = passes.get(0);

            java.lang.reflect.Field uniformBuffersField = firstPass.getClass().getDeclaredField("uniformBuffers");
            uniformBuffersField.setAccessible(true);
            // The map is a HashMap — we need a mutable cast
            @SuppressWarnings("unchecked")
            java.util.Map<String, com.mojang.blaze3d.buffers.GpuBuffer> uniformBuffers =
                (java.util.Map<String, com.mojang.blaze3d.buffers.GpuBuffer>) uniformBuffersField.get(firstPass);

            com.mojang.blaze3d.buffers.GpuBuffer buf = uniformBuffers.get("RefractionConfig");
            if (buf == null) return;

            // USAGE_COPY_DST=8, USAGE_UNIFORM=128 → 136
            // If the buffer doesn't have COPY_DST, replace it with one that does
            if ((buf.usage() & 8) == 0) {
                buf.close(); // free the old buffer
                // std140: vec2(8) + float(4) + float(4) + int(4) = 20 bytes
                com.mojang.blaze3d.buffers.GpuBuffer newBuf =
                    com.mojang.blaze3d.systems.RenderSystem.getDevice()
                        .createBuffer(() -> "JJK RefractionConfig", 8 | 128, 20);
                uniformBuffers.put("RefractionConfig", newBuf);
                buf = newBuf;
            }

            // Write values — std140: vec2(8) + float(4) + float(4) + int(4) = 20 bytes
            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            try {
                com.mojang.blaze3d.buffers.Std140Builder builder =
                    com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, 20);
                builder.putVec2(centerX, centerY);
                builder.putFloat(radius);
                builder.putFloat(strength);
                builder.putInt(effectType);
                com.mojang.blaze3d.systems.RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToBuffer(buf.slice(), builder.get());
            } finally {
                stack.pop();
            }
        } catch (Exception e) {
            System.err.println("[JJKMixin] updateRefractionUniforms failed: " + e.getMessage());
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
