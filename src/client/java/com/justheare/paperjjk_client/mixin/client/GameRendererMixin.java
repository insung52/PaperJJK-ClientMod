package com.justheare.paperjjk_client.mixin.client;

import com.justheare.paperjjk_client.render.DebugRenderer;
import com.justheare.paperjjk_client.render.JJKDepthCache;
import com.justheare.paperjjk_client.shader.AmbientKaiSlashManager;
import com.justheare.paperjjk_client.shader.DomainEffectManager;
import com.justheare.paperjjk_client.shader.HachiSlashEffectManager;
import com.justheare.paperjjk_client.shader.KaiSlashEffectManager;
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

    private static final Identifier KAI_SLASH_EFFECT_ID =
        Identifier.of("paperjjk-client", "kai_slash");

    private static final Identifier HACHI_SLASH_EFFECT_ID =
        Identifier.of("paperjjk-client", "hachi_slash");

    private static final Identifier AMBIENT_KAI_SLASH_EFFECT_ID =
        Identifier.of("paperjjk-client", "ambient_kai_slash");

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
        if (effects.isEmpty() && !DomainEffectManager.hasActiveDomains()
                && !KaiSlashEffectManager.isAnyActive()
                && !HachiSlashEffectManager.hasActiveEffects()
                && !AmbientKaiSlashManager.isActive()) return;

        Camera camera = client.gameRenderer.getCamera();
        // Use the actual dynamic FOV (includes sprint/fly/speed effect/bow draw modifiers)
        // so world-to-screen projection matches what was used to render the world.
        float dynamicFov = ((GameRendererAccessor) client.gameRenderer)
            .invokeFov(camera, renderTickCounter.getDynamicDeltaTicks(), true);
        Matrix4f projectionMatrix = new Matrix4f(
            client.gameRenderer.getBasicProjectionMatrix(dynamicFov)
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

        // ── Kai slash (해) — debug + server instances ────────────────────────
        List<KaiSlashEffectManager.KaiSlashEffect> kaiEffects = KaiSlashEffectManager.getActiveEffects();
        if (KaiSlashEffectManager.isDebugActive() || !kaiEffects.isEmpty()) {
            PostEffectProcessor kaiProcessor;
            try {
                kaiProcessor = client.getShaderLoader().loadPostEffect(
                    KAI_SLASH_EFFECT_ID,
                    Set.of(net.minecraft.client.render.DefaultFramebufferSet.MAIN)
                );
            } catch (Exception e) {
                System.err.println("[JJKMixin] Failed to load kai_slash post effect: " + e.getMessage());
                kaiProcessor = null;
            }
            if (kaiProcessor != null) {
                // 디버그 단일 슬래시 (루프 반복)
                if (KaiSlashEffectManager.isDebugActive()) {
                    updateKaiSlashUniforms(kaiProcessor,
                        KaiSlashEffectManager.getP1x(), KaiSlashEffectManager.getP1y(),
                        KaiSlashEffectManager.getP2x(), KaiSlashEffectManager.getP2y(),
                        KaiSlashEffectManager.getCoreHalfWidth(),
                        KaiSlashEffectManager.getBloomWidth(),
                        KaiSlashEffectManager.getAlpha(),
                        KaiSlashEffectManager.getDebugTime());
                    kaiProcessor.render(mainFb, ObjectAllocator.TRIVIAL);
                }

                // 서버 전송 인스턴스 — 월드 좌표를 화면 UV 로 투영
                final float SLASH_HALF_LEN = 1.8f;
                for (KaiSlashEffectManager.KaiSlashEffect e : kaiEffects) {
                    Vec3d p1World = new Vec3d(
                        e.worldX + e.axisX * SLASH_HALF_LEN,
                        e.worldY + e.axisY * SLASH_HALF_LEN,
                        e.worldZ + e.axisZ * SLASH_HALF_LEN);
                    Vec3d p2World = new Vec3d(
                        e.worldX - e.axisX * SLASH_HALF_LEN,
                        e.worldY - e.axisY * SLASH_HALF_LEN,
                        e.worldZ - e.axisZ * SLASH_HALF_LEN);

                    Vec3d sp1 = WorldToScreenUtil.worldToScreen(p1World, camera, projectionMatrix);
                    Vec3d sp2 = WorldToScreenUtil.worldToScreen(p2World, camera, projectionMatrix);
                    if (sp1 == null || sp2 == null) continue;

                    // WorldToScreenUtil: y=0 위쪽. 셰이더 texCoord: y=0 아래쪽 → Y 반전
                    float kp1x = (float) sp1.x;
                    float kp1y = 1.0f - (float) sp1.y;
                    float kp2x = (float) sp2.x;
                    float kp2y = 1.0f - (float) sp2.y;

                    // 중심까지의 거리로 두께 스케일 (기준 거리 3블록)
                    float dist = (float)((sp1.z + sp2.z) * 0.5);
                    float distScale = 3.0f / Math.max(3.0f, dist);

                    updateKaiSlashUniforms(kaiProcessor,
                        kp1x, kp1y, kp2x, kp2y,
                        KaiSlashEffectManager.getCoreHalfWidth() * distScale,
                        KaiSlashEffectManager.getBloomWidth() * distScale,
                        1.0f, e.getTime());
                    kaiProcessor.render(mainFb, ObjectAllocator.TRIVIAL);
                }
            }
        }

        // ── Hachi slash (팔) — 격자 참격, 인스턴스당 1 pass ─────────────────────
        List<HachiSlashEffectManager.HachiSlashEffect> hachiEffects = HachiSlashEffectManager.getActiveEffects();
        if (!hachiEffects.isEmpty()) {
            PostEffectProcessor hachiProcessor;
            try {
                hachiProcessor = client.getShaderLoader().loadPostEffect(
                    HACHI_SLASH_EFFECT_ID,
                    Set.of(net.minecraft.client.render.DefaultFramebufferSet.MAIN)
                );
            } catch (Exception e) {
                System.err.println("[JJKMixin] Failed to load hachi_slash post effect: " + e.getMessage());
                hachiProcessor = null;
            }
            if (hachiProcessor != null) {
                for (HachiSlashEffectManager.HachiSlashEffect e : hachiEffects) {
                    Vec3d center = new Vec3d(e.worldX, e.worldY, e.worldZ);
                    Vec3d sp = WorldToScreenUtil.worldToScreen(center, camera, projectionMatrix);
                    if (sp == null) continue;

                    float cx = (float) sp.x;
                    float cy = 1.0f - (float) sp.y;  // WorldToScreenUtil y=0 위쪽 → 셰이더 y=0 아래쪽
                    float dist = (float) sp.z;
                    float distScale = 3.0f / Math.max(3.0f, dist);
                    updateHachiSlashUniforms(hachiProcessor, cx, cy, e.angle, e.getTime(),
                            e.skewAngle, distScale);
                    hachiProcessor.render(mainFb, ObjectAllocator.TRIVIAL);
                }
            }
        }

        // ── Ambient Kai Slash (결없영 배경 참격) — screen-space 2D, 단일 pass ──
        if (AmbientKaiSlashManager.isActive()) {
            PostEffectProcessor ambientProcessor;
            try {
                ambientProcessor = client.getShaderLoader().loadPostEffect(
                    AMBIENT_KAI_SLASH_EFFECT_ID,
                    Set.of(net.minecraft.client.render.DefaultFramebufferSet.MAIN)
                );
            } catch (Exception e) {
                System.err.println("[JJKMixin] Failed to load ambient_kai_slash post effect: " + e.getMessage());
                ambientProcessor = null;
            }
            if (ambientProcessor != null) {
                AmbientKaiSlashManager.AmbientSlash[] slashes = AmbientKaiSlashManager.getSlashes();

                // Slashes[i*2]   = (p1x, p1y, p2x, p2y)
                // Slashes[i*2+1] = (headT, tailT, fade, 0)
                final int SLOT = 8; // floats per slash
                float[] slashData = new float[AmbientKaiSlashManager.MAX_SLASHES * SLOT];
                int activeCount = 0;

                for (AmbientKaiSlashManager.AmbientSlash slash : slashes) {
                    float localT = slash.getLocalT();  // 사이클 완료 시 자동 regenerate
                    float fade   = slash.getFade(localT);
                    if (fade < 0.002f) continue;

                    // 슬래시 끝점 (full halfLen — kai는 길이 고정, 스윕으로 애니메이션)
                    Vec3d ep1 = new Vec3d(
                        AmbientKaiSlashManager.sphereX + slash.sx + slash.dx * slash.halfLen,
                        AmbientKaiSlashManager.sphereY + slash.sy + slash.dy * slash.halfLen,
                        AmbientKaiSlashManager.sphereZ + slash.sz + slash.dz * slash.halfLen);
                    Vec3d ep2 = new Vec3d(
                        AmbientKaiSlashManager.sphereX + slash.sx - slash.dx * slash.halfLen,
                        AmbientKaiSlashManager.sphereY + slash.sy - slash.dy * slash.halfLen,
                        AmbientKaiSlashManager.sphereZ + slash.sz - slash.dz * slash.halfLen);

                    Vec3d sp1 = WorldToScreenUtil.worldToScreen(ep1, camera, projectionMatrix);
                    Vec3d sp2 = WorldToScreenUtil.worldToScreen(ep2, camera, projectionMatrix);
                    if (sp1 == null || sp2 == null) continue;

                    float headT = slash.getHeadT(localT);
                    float tailT = slash.getTailT(localT);

                    // WorldToScreenUtil: y=0 위쪽 → 셰이더 texCoord: y=0 아래쪽
                    int base = activeCount * SLOT;
                    slashData[base    ] = (float) sp1.x;
                    slashData[base + 1] = 1.0f - (float) sp1.y;
                    slashData[base + 2] = (float) sp2.x;
                    slashData[base + 3] = 1.0f - (float) sp2.y;
                    slashData[base + 4] = headT;
                    slashData[base + 5] = tailT;
                    slashData[base + 6] = fade;
                    // base+7 = 0 (padding, array is zero-initialized)

                    activeCount++;
                    if (activeCount >= AmbientKaiSlashManager.MAX_SLASHES) break;
                }

                updateAmbientKaiUniforms(ambientProcessor, activeCount, slashData);
                ambientProcessor.render(mainFb, ObjectAllocator.TRIVIAL);
            }
        }

        // ── Domain effect (간이영역) — one render pass per active domain ────────
        if (DomainEffectManager.hasActiveDomains()) {
            Vec3d playerFeet = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
            float deltaTicks = renderTickCounter.getDynamicDeltaTicks();

            // Compute shared matrices once for all domains
            Matrix4f viewMatrix = new Matrix4f()
                .rotation(camera.getRotation().conjugate(new Quaternionf()));
            Matrix4f invViewProj = projectionMatrix
                .mul(viewMatrix, new Matrix4f())
                .invert(new Matrix4f());
            Vec3d camPos = ((CameraAccessor) camera).getPos();

            for (DomainEffectManager.DomainState domain : DomainEffectManager.getActiveDomains()) {
                // Track domain center toward the caster's current position
                if (domain.isLocalPlayerCaster) {
                    domain.tickPosition(playerFeet, deltaTicks);
                } else {
                    // Look up the caster entity in the world (visible within render distance)
                    net.minecraft.entity.player.PlayerEntity casterEntity =
                        client.world.getPlayerByUuid(domain.casterUuid);
                    if (casterEntity != null) {
                        Vec3d casterFeet = new Vec3d(casterEntity.getX(), casterEntity.getY(), casterEntity.getZ());
                        domain.tickPosition(casterFeet, deltaTicks);
                    }
                }

                PostEffectProcessor domainProcessor;
                try {
                    domainProcessor = client.getShaderLoader().loadPostEffect(
                        DOMAIN_EFFECT_ID,
                        Set.of(net.minecraft.client.render.DefaultFramebufferSet.MAIN)
                    );
                } catch (Exception e) {
                    System.err.println("[JJKMixin] Failed to load domain post effect: " + e.getMessage());
                    continue;
                }
                if (domainProcessor == null) continue;

                Vec3d feet = domain.casterFeetPos;
                float relX = (float)(feet.x - camPos.x);
                float relY = (float)(feet.y - camPos.y);
                float relZ = (float)(feet.z - camPos.z);

                updateDomainUniforms(domainProcessor, invViewProj, relX, relY, relZ,
                    domain.getExpandRadius(),
                    domain.getDarkRadius(),
                    domain.getDarknessLevel());
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
     * Updates the KaiSlashConfig uniform buffer.
     *
     * std140 layout:
     *   vec2 SlashP1        →  8 bytes
     *   vec2 SlashP2        →  8 bytes
     *   float CoreHalfWidth →  4 bytes
     *   float BloomWidth    →  4 bytes
     *   float Alpha         →  4 bytes
     *   float _pad          →  4 bytes
     *   Total               = 32 bytes
     */
    @SuppressWarnings("unchecked")
    private void updateKaiSlashUniforms(PostEffectProcessor processor,
                                         float p1x, float p1y,
                                         float p2x, float p2y,
                                         float coreHalfWidth, float bloomWidth,
                                         float alpha, float time) {
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

            com.mojang.blaze3d.buffers.GpuBuffer buf = ubs.get("KaiSlashConfig");
            if (buf == null) return;

            if ((buf.usage() & 8) == 0 || buf.size() < 32) {
                buf.close();
                com.mojang.blaze3d.buffers.GpuBuffer newBuf =
                    RenderSystem.getDevice().createBuffer(() -> "JJK KaiSlashConfig", 8 | 128, 32);
                ubs.put("KaiSlashConfig", newBuf);
                buf = newBuf;
            }

            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            try {
                com.mojang.blaze3d.buffers.Std140Builder b =
                    com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, 32);
                b.putVec2(p1x, p1y);
                b.putVec2(p2x, p2y);
                b.putFloat(coreHalfWidth);
                b.putFloat(bloomWidth);
                b.putFloat(alpha);
                b.putFloat(time);
                RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(buf.slice(), b.get());
            } finally {
                stack.pop();
            }
        } catch (Exception e) {
            System.err.println("[JJKMixin] updateKaiSlashUniforms failed: " + e.getMessage());
        }
    }

    /**
     * Updates the HachiSlashConfig uniform buffer.
     *
     * std140 layout:
     *   vec2  Center    →  8 bytes
     *   float Angle     →  4 bytes
     *   float Time      →  4 bytes
     *   float SkewAngle →  4 bytes
     *   float DistScale →  4 bytes
     *   Total           = 24 bytes
     */
    @SuppressWarnings("unchecked")
    private void updateHachiSlashUniforms(PostEffectProcessor processor,
                                          float cx, float cy, float angle, float time,
                                          float skewAngle, float distScale) {
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

            com.mojang.blaze3d.buffers.GpuBuffer buf = ubs.get("HachiSlashConfig");
            if (buf == null) return;

            if ((buf.usage() & 8) == 0 || buf.size() < 24) {
                buf.close();
                com.mojang.blaze3d.buffers.GpuBuffer newBuf =
                    RenderSystem.getDevice().createBuffer(() -> "JJK HachiSlashConfig", 8 | 128, 24);
                ubs.put("HachiSlashConfig", newBuf);
                buf = newBuf;
            }

            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            try {
                com.mojang.blaze3d.buffers.Std140Builder b =
                    com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, 24);
                b.putVec2(cx, cy);
                b.putFloat(angle);
                b.putFloat(time);
                b.putFloat(skewAngle);
                b.putFloat(distScale);
                RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(buf.slice(), b.get());
            } finally {
                stack.pop();
            }
        } catch (Exception e) {
            System.err.println("[JJKMixin] updateHachiSlashUniforms failed: " + e.getMessage());
        }
    }

    /**
     * Updates the DomainConfig uniform buffer.
     *
     * std140 layout:
     *   vec4 InvViewProjC0..C3  → 4 * 16 = 64 bytes
     *   vec4 CasterFeetPos      → 16 bytes  (.w = 0)
     *   float ExpandRadius      →  4 bytes
     *   float DarkRadius        →  4 bytes
     *   float DarknessLevel     →  4 bytes
     *   Total                   = 92 bytes
     */
    @SuppressWarnings("unchecked")
    private void updateDomainUniforms(PostEffectProcessor processor,
                                       Matrix4f invViewProj,
                                       float casterX, float casterY, float casterZ,
                                       float expandRadius, float darkRadius, float darknessLevel) {
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

            if ((buf.usage() & 8) == 0 || buf.size() < 92) {
                buf.close();
                com.mojang.blaze3d.buffers.GpuBuffer newBuf =
                    RenderSystem.getDevice().createBuffer(() -> "JJK DomainConfig", 8 | 128, 92);
                ubs.put("DomainConfig", newBuf);
                buf = newBuf;
            }

            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            try {
                com.mojang.blaze3d.buffers.Std140Builder b =
                    com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, 92);
                // InvViewProj as 4 column vec4s (column-major, matches GLSL mat4)
                b.putVec4(invViewProj.m00(), invViewProj.m01(), invViewProj.m02(), invViewProj.m03());
                b.putVec4(invViewProj.m10(), invViewProj.m11(), invViewProj.m12(), invViewProj.m13());
                b.putVec4(invViewProj.m20(), invViewProj.m21(), invViewProj.m22(), invViewProj.m23());
                b.putVec4(invViewProj.m30(), invViewProj.m31(), invViewProj.m32(), invViewProj.m33());
                // CasterFeetPos as vec4 (.w = 0)
                b.putVec4(casterX, casterY, casterZ, 0.0f);
                // ExpandRadius / DarkRadius / DarknessLevel
                b.putFloat(expandRadius);
                b.putFloat(darkRadius);
                b.putFloat(darknessLevel);
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
     * Updates the AmbientKaiConfig uniform buffer.
     *
     * std140 layout:
     *   float SlashCount    →  4 bytes
     *   float CoreHalfWidth →  4 bytes
     *   float BloomWidth    →  4 bytes
     *   float _pad          →  4 bytes
     *   vec4  Slashes[64]   → 64 × 16 = 1024 bytes
     *   Total               = 1040 bytes
     *
     * slashData: float[] of length slashCount × 8
     *   [i*8+0..3] = p1x, p1y, p2x, p2y  (screen texCoord UV)
     *   [i*8+4]    = fade
     *   [i*8+5..7] = 0 (padding)
     */
    @SuppressWarnings("unchecked")
    private void updateAmbientKaiUniforms(PostEffectProcessor processor,
                                           int slashCount, float[] slashData) {
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

            com.mojang.blaze3d.buffers.GpuBuffer buf = ubs.get("AmbientKaiConfig");
            if (buf == null) return;

            // std140: 16(header) + 256×vec4(4096) = 4112 bytes  (128슬래시 × 2 vec4)
            final int REQUIRED = 4112;
            if ((buf.usage() & 8) == 0 || buf.size() < REQUIRED) {
                buf.close();
                com.mojang.blaze3d.buffers.GpuBuffer newBuf =
                    RenderSystem.getDevice().createBuffer(() -> "JJK AmbientKaiConfig", 8 | 128, REQUIRED);
                ubs.put("AmbientKaiConfig", newBuf);
                buf = newBuf;
            }

            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            try {
                com.mojang.blaze3d.buffers.Std140Builder b =
                    com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, REQUIRED);

                // Header (16 bytes)
                b.putFloat((float) slashCount);
                b.putFloat(AmbientKaiSlashManager.CORE_HALF_WIDTH);
                b.putFloat(AmbientKaiSlashManager.BLOOM_WIDTH);
                b.putFloat(0.0f);

                // Slashes array: 256 vec4 (2 per slash × 128 슬래시)
                int maxSlashes = AmbientKaiSlashManager.MAX_SLASHES;
                for (int i = 0; i < maxSlashes; i++) {
                    if (i < slashCount) {
                        int base = i * 8;
                        // vec4: p1x, p1y, p2x, p2y
                        b.putVec4(slashData[base], slashData[base + 1],
                                  slashData[base + 2], slashData[base + 3]);
                        // vec4: headT, tailT, fade, 0
                        b.putVec4(slashData[base + 4], slashData[base + 5], slashData[base + 6], 0f);
                    } else {
                        b.putVec4(0f, 0f, 0f, 0f);
                        b.putVec4(0f, 0f, 0f, 0f);
                    }
                }

                RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(buf.slice(), b.get());
            } finally {
                stack.pop();
            }
        } catch (Exception e) {
            System.err.println("[JJKMixin] updateAmbientKaiUniforms failed: " + e.getMessage());
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
