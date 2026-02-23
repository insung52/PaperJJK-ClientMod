package com.justheare.paperjjk_client.mixin.client;

import com.justheare.paperjjk_client.render.DebugRenderer;
import com.justheare.paperjjk_client.render.JJKDepthCache;
import com.justheare.paperjjk_client.shader.DomainEffectManager;
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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
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

    private static final Identifier DOMAIN_EFFECT_ID =
        Identifier.of("paperjjk-client", "domain");

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

        // Nothing to do if no effects of any kind are active
        List<RefractionEffectManager.RefractionEffect> effects = RefractionEffectManager.getEffects();
        if (effects.isEmpty() && !DomainEffectManager.isActive()) return;

        Camera camera = client.gameRenderer.getCamera();
        Matrix4f projectionMatrix = new Matrix4f(
            client.gameRenderer.getBasicProjectionMatrix(
                client.options.getFov().getValue().floatValue()
            )
        );

        Framebuffer mainFb = client.getFramebuffer();

        // Restore world depth from JJKDepthCache so DepthSampler works for occlusion.
        // (The depth buffer was cleared for GUI rendering at this point.)
        Framebuffer worldDepthFb = JJKDepthCache.get();
        if (worldDepthFb != null && worldDepthFb.getDepthAttachment() != null
                && mainFb.getDepthAttachment() != null) {
            mainFb.copyDepthFrom(worldDepthFb);
        }

        // ── Refraction effects (AO / AKA / MURASAKI) ─────────────────────────
        if (!effects.isEmpty()) {
            PostEffectProcessor processor;
            try {
                processor = client.getShaderLoader().loadPostEffect(
                    REFRACTION_EFFECT_ID,
                    Set.of(net.minecraft.client.render.DefaultFramebufferSet.MAIN)
                );
            } catch (Exception e) {
                System.err.println("[JJKMixin] Failed to load refraction post effect: " + e.getMessage());
                processor = null;
            }
            if (processor != null) for (RefractionEffectManager.RefractionEffect effect : effects) {
            Vec3d screenPos =
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
        } // end refraction block

        // ── Domain effect (간이영역) ──────────────────────────────────────────
        if (DomainEffectManager.isActive()) {
            PostEffectProcessor domainProcessor;
            try {
                domainProcessor = client.getShaderLoader().loadPostEffect(
                    DOMAIN_EFFECT_ID,
                    Set.of(net.minecraft.client.render.DefaultFramebufferSet.MAIN)
                );
            } catch (Exception e) {
                System.err.println("[JJKMixin] Failed to load domain post effect: " + e.getMessage());
                return;
            }
            if (domainProcessor != null) {
                // Inverse view-projection matrix for world-position reconstruction in shader
                // View matrix = camera rotation only (no translation), positions are camera-relative
                Matrix4f viewMatrix = new Matrix4f()
                    .rotation(camera.getRotation().conjugate(new Quaternionf()));
                Matrix4f invViewProj = projectionMatrix
                    .mul(viewMatrix, new Matrix4f())
                    .invert(new Matrix4f());

                // Caster feet position relative to camera (avoids float precision issues)
                Vec3d camPos = ((CameraAccessor) camera).getPos();
                Vec3d feet   = DomainEffectManager.getCasterFeetPos();
                float relX   = (float)(feet.x - camPos.x);
                float relY   = (float)(feet.y - camPos.y);
                float relZ   = (float)(feet.z - camPos.z);

                updateDomainUniforms(domainProcessor, invViewProj, relX, relY, relZ,
                    DomainEffectManager.getExpandRadius());
                domainProcessor.render(mainFb, ObjectAllocator.TRIVIAL);
            }
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
     * Updates the DomainConfig uniform buffer.
     *
     * std140 layout:
     *   vec4 InvViewProjC0..C3  → 4 * 16 = 64 bytes
     *   vec4 CasterFeetPos      → 16 bytes  (.w = 0)
     *   float ExpandRadius      →  4 bytes
     *   Total                   = 84 bytes
     */
    @SuppressWarnings("unchecked")
    private void updateDomainUniforms(PostEffectProcessor processor,
                                       Matrix4f invViewProj,
                                       float casterX, float casterY, float casterZ,
                                       float expandRadius) {
        try {
            java.lang.reflect.Field passesField = null;
            for (java.lang.reflect.Field f : PostEffectProcessor.class.getDeclaredFields()) {
                if (java.util.List.class.isAssignableFrom(f.getType())) { passesField = f; break; }
            }
            if (passesField == null) return;
            passesField.setAccessible(true);
            List<?> passes = (List<?>) passesField.get(processor);
            if (passes.isEmpty()) return;
            Object firstPass = passes.get(0);

            java.lang.reflect.Field ubField = null;
            for (java.lang.reflect.Field f : firstPass.getClass().getDeclaredFields()) {
                if (java.util.Map.class.isAssignableFrom(f.getType())) { ubField = f; break; }
            }
            if (ubField == null) return;
            ubField.setAccessible(true);
            java.util.Map<String, com.mojang.blaze3d.buffers.GpuBuffer> ubs =
                (java.util.Map<String, com.mojang.blaze3d.buffers.GpuBuffer>) ubField.get(firstPass);

            com.mojang.blaze3d.buffers.GpuBuffer buf = ubs.get("DomainConfig");
            if (buf == null) return;

            if ((buf.usage() & 8) == 0 || buf.size() < 84) {
                buf.close();
                com.mojang.blaze3d.buffers.GpuBuffer newBuf =
                    RenderSystem.getDevice().createBuffer(() -> "JJK DomainConfig", 8 | 128, 84);
                ubs.put("DomainConfig", newBuf);
                buf = newBuf;
            }

            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            try {
                com.mojang.blaze3d.buffers.Std140Builder b =
                    com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, 84);
                // InvViewProj as 4 column vec4s (column-major, matches GLSL mat4)
                b.putVec4(invViewProj.m00(), invViewProj.m01(), invViewProj.m02(), invViewProj.m03());
                b.putVec4(invViewProj.m10(), invViewProj.m11(), invViewProj.m12(), invViewProj.m13());
                b.putVec4(invViewProj.m20(), invViewProj.m21(), invViewProj.m22(), invViewProj.m23());
                b.putVec4(invViewProj.m30(), invViewProj.m31(), invViewProj.m32(), invViewProj.m33());
                // CasterFeetPos as vec4 (.w = 0)
                b.putVec4(casterX, casterY, casterZ, 0.0f);
                // ExpandRadius
                b.putFloat(expandRadius);
                RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(buf.slice(), b.get());
            } finally {
                stack.pop();
            }
        } catch (Exception e) {
            System.err.println("[JJKMixin] updateDomainUniforms failed: " + e.getMessage());
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
