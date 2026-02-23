package com.justheare.paperjjk_client.mixin.client;

import com.justheare.paperjjk_client.render.DebugRenderer;
import com.justheare.paperjjk_client.render.JJKDepthCache;
import com.justheare.paperjjk_client.shader.RefractionEffectManager;
import com.justheare.paperjjk_client.util.WorldToScreenUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    private static final Identifier REFRACTION_EFFECT_ID =
        Identifier.of("paperjjk-client", "refraction");

    /**
     * Apply refraction post-processing BEFORE the HUD renders.
     * This ensures the crosshair, health bar, hotbar etc. are NOT distorted.
     *
     * Injection point: just before InGameHud.render() inside GameRenderer.render().
     * At this point the 3D world is fully rendered on client.getFramebuffer(),
     * the depth buffer has been cleared for GUI, and JJKDepthCache holds world depth.
     */
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"
        )
    )
    private void paperjjk$beforeHudRender(RenderTickCounter renderTickCounter, boolean tick, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        if (!com.justheare.paperjjk_client.data.PlayerData.isPostProcessingEnabled()) return;

        RefractionEffectManager.tickEffects();

        List<RefractionEffectManager.RefractionEffect> effects = RefractionEffectManager.getEffects();
        if (effects.isEmpty()) return;

        Camera camera = client.gameRenderer.getCamera();
        Matrix4f projectionMatrix = new Matrix4f(
            client.gameRenderer.getBasicProjectionMatrix(
                client.options.getFov().getValue().floatValue()
            )
        );

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

        // Copy world depth (captured by WorldRendererMixin) into the main framebuffer's
        // depth attachment so DepthSampler in the shader can do occlusion testing.
        // The depth buffer was cleared for GUI rendering at this point, so we restore
        // world depth specifically for our post-effect pass.
        Framebuffer worldDepthFb = JJKDepthCache.get();
        if (worldDepthFb != null && worldDepthFb.getDepthAttachment() != null
                && mainFb.getDepthAttachment() != null) {
            mainFb.copyDepthFrom(worldDepthFb);
        }

        for (RefractionEffectManager.RefractionEffect effect : effects) {
            net.minecraft.util.math.Vec3d screenPos =
                WorldToScreenUtil.worldToScreen(effect.worldPos, camera, projectionMatrix);
            if (screenPos == null) continue;

            float distance = (float) screenPos.z;
            float baseRadius = "MURASAKI_EXPLODE".equals(effect.effectType) ? effect.radius / 50.0f : 0.2f;
            float scaledRadius = baseRadius / Math.max(1.0f, distance / 10.0f);

            int effectTypeInt = "AKA".equals(effect.effectType) ? 1 :
                                "MURASAKI".equals(effect.effectType) || "MURASAKI_EXPLODE".equals(effect.effectType) ? 2 : 0;

            float effectDepth = WorldToScreenUtil.worldToDepth(effect.worldPos, camera, projectionMatrix);
            float time = (float)(System.currentTimeMillis() % 10000) / 1000.0f;

            updateRefractionUniforms(processor,
                (float) screenPos.x, (float) screenPos.y,
                scaledRadius, effect.strength, effectTypeInt, effectDepth, time, distance);

            processor.render(mainFb, ObjectAllocator.TRIVIAL);
        }
    }

    /**
     * Updates the RefractionConfig uniform buffer in PostEffectProcessor.
     */
    @SuppressWarnings("unchecked")
    private void updateRefractionUniforms(PostEffectProcessor processor,
                                           float centerX, float centerY,
                                           float radius, float strength,
                                           int effectType, float effectDepth,
                                           float time, float worldDist) {
        try {
            java.lang.reflect.Field passesField = null;
            for (java.lang.reflect.Field f : PostEffectProcessor.class.getDeclaredFields()) {
                if (java.util.List.class.isAssignableFrom(f.getType())) {
                    passesField = f;
                    break;
                }
            }
            if (passesField == null) return;
            passesField.setAccessible(true);
            List<?> passes = (List<?>) passesField.get(processor);
            if (passes.isEmpty()) return;
            Object firstPass = passes.get(0);

            java.lang.reflect.Field uniformBuffersField = null;
            for (java.lang.reflect.Field f : firstPass.getClass().getDeclaredFields()) {
                if (java.util.Map.class.isAssignableFrom(f.getType())) {
                    uniformBuffersField = f;
                    break;
                }
            }
            if (uniformBuffersField == null) return;
            uniformBuffersField.setAccessible(true);
            java.util.Map<String, com.mojang.blaze3d.buffers.GpuBuffer> uniformBuffers =
                (java.util.Map<String, com.mojang.blaze3d.buffers.GpuBuffer>) uniformBuffersField.get(firstPass);

            com.mojang.blaze3d.buffers.GpuBuffer buf = uniformBuffers.get("RefractionConfig");
            if (buf == null) return;

            // std140: vec2(8) + float(4) + float(4) + int(4) + float(4) + float(4) + float(4) = 32 bytes
            if ((buf.usage() & 8) == 0 || buf.size() < 32) {
                buf.close();
                com.mojang.blaze3d.buffers.GpuBuffer newBuf =
                    RenderSystem.getDevice().createBuffer(() -> "JJK RefractionConfig", 8 | 128, 32);
                uniformBuffers.put("RefractionConfig", newBuf);
                buf = newBuf;
            }

            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            try {
                com.mojang.blaze3d.buffers.Std140Builder builder =
                    com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, 32);
                builder.putVec2(centerX, centerY);
                builder.putFloat(radius);
                builder.putFloat(strength);
                builder.putInt(effectType);
                builder.putFloat(effectDepth);
                builder.putFloat(time);
                builder.putFloat(worldDist);
                RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToBuffer(buf.slice(), builder.get());
            } finally {
                stack.pop();
            }
        } catch (Exception e) {
            System.err.println("[JJKMixin] updateRefractionUniforms failed: " + e.getMessage());
        }
    }

    /**
     * Inject right after WorldRenderer.render() returns in renderWorld.
     */
    @Inject(method = "renderWorld",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            shift = At.Shift.AFTER))
    private void paperjjk$renderSpheres(RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        Camera camera = client.gameRenderer.getCamera();

        Matrix4fStack mvs = RenderSystem.getModelViewStack();
        mvs.pushMatrix();
        mvs.identity();
        mvs.rotateX((float) Math.toRadians(camera.getPitch()));
        mvs.rotateY((float) Math.toRadians(camera.getYaw() + 180.0f));

        MatrixStack matrices = new MatrixStack();
        DebugRenderer.render(matrices, camera);

        mvs.popMatrix();
    }
}
