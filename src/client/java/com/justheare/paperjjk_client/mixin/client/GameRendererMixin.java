package com.justheare.paperjjk_client.mixin.client;

import com.justheare.paperjjk_client.render.DebugRenderer;
import com.justheare.paperjjk_client.render.JJKDepthCache;
import com.justheare.paperjjk_client.shader.AmbientKaiSlashManager;
import com.justheare.paperjjk_client.shader.DomainEffectManager;
import com.justheare.paperjjk_client.shader.HachiSlashEffectManager;
import com.justheare.paperjjk_client.shader.KaiSlashEffectManager;
import com.justheare.paperjjk_client.shader.MizushiChargeEffectManager;
import com.justheare.paperjjk_client.shader.MizushiThermobaricManager;
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

    private static final Identifier MIZUSHI_DUST_STORM_EFFECT_ID =
        Identifier.of("paperjjk-client", "mizushi_dust_storm");

    private static final Identifier MIZUSHI_CHARGE_EFFECT_ID =
        Identifier.of("paperjjk-client", "mizushi_charge");

    private static final Identifier MIZUSHI_SHOCKWAVE_EFFECT_ID =
        Identifier.of("paperjjk-client", "mizushi_shockwave");

    private static final Identifier MIZUSHI_THERMOBARIC_EFFECT_ID =
        Identifier.of("paperjjk-client", "mizushi_thermobaric");

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
                && !AmbientKaiSlashManager.hasActiveDomains()
                && !MizushiChargeEffectManager.isActive()
                && !MizushiThermobaricManager.isActive()) return;

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
                        KaiSlashEffectManager.getDebugTime(),
                        0.0f, 0.0f); // 디버그: depth 0 = 항상 앞
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

                    float kDepth1 = WorldToScreenUtil.worldToDepth(p1World, camera, projectionMatrix);
                    float kDepth2 = WorldToScreenUtil.worldToDepth(p2World, camera, projectionMatrix);

                    updateKaiSlashUniforms(kaiProcessor,
                        kp1x, kp1y, kp2x, kp2y,
                        KaiSlashEffectManager.getCoreHalfWidth() * distScale,
                        KaiSlashEffectManager.getBloomWidth() * distScale,
                        1.0f, e.getTime(), kDepth1, kDepth2);
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
                    float hDepth = WorldToScreenUtil.worldToDepth(center, camera, projectionMatrix);
                    updateHachiSlashUniforms(hachiProcessor, cx, cy, e.angle, e.getTime(),
                            e.skewAngle, distScale, hDepth);
                    hachiProcessor.render(mainFb, ObjectAllocator.TRIVIAL);
                }
            }
        }

        // ── Mizushi Charge Effect (결없영 2초 충전 애니메이션) ───────────────────
        if (MizushiChargeEffectManager.isActive()) {
            float chargeProgress = MizushiChargeEffectManager.getProgress();
            float sphereRadius   = MizushiChargeEffectManager.getSphereRadius();
            float chargeTime     = (float)(System.currentTimeMillis() % 100000L) / 1000.0f;

            Vec3d chargeHeadWorld = MizushiChargeEffectManager.getWorldCenter();
            Vec3d camPos3 = ((CameraAccessor) camera).getPos();
            float headRelX = (float)(chargeHeadWorld.x - camPos3.x);
            float headRelY = (float)(chargeHeadWorld.y - camPos3.y);
            float headRelZ = (float)(chargeHeadWorld.z - camPos3.z);

            // Project head to screen UV (for spike center) + NDC depth (for occlusion)
            Vec3d headScreen = WorldToScreenUtil.worldToScreen(chargeHeadWorld, camera, projectionMatrix);
            float headUVX   = headScreen == null ? -1.0f : (float) headScreen.x;
            float headUVY   = headScreen == null ? -1.0f : 1.0f - (float) headScreen.y; // flip Y
            float headDepth = headScreen == null ? -1.0f
                : WorldToScreenUtil.worldToDepth(chargeHeadWorld, camera, projectionMatrix);

            Matrix4f chargeViewMatrix = new Matrix4f()
                .rotation(camera.getRotation().conjugate(new Quaternionf()));
            Matrix4f chargeInvViewProj = projectionMatrix
                .mul(chargeViewMatrix, new Matrix4f())
                .invert(new Matrix4f());

            PostEffectProcessor chargeProcessor;
            try {
                chargeProcessor = client.getShaderLoader().loadPostEffect(
                    MIZUSHI_CHARGE_EFFECT_ID,
                    Set.of(net.minecraft.client.render.DefaultFramebufferSet.MAIN)
                );
            } catch (Exception e) {
                System.err.println("[JJKMixin] Failed to load mizushi_charge post effect: " + e.getMessage());
                chargeProcessor = null;
            }
            if (chargeProcessor != null) {
                updateChargeUniforms(chargeProcessor, chargeInvViewProj,
                    headRelX, headRelY, headRelZ, chargeProgress,
                    headUVX, headUVY, headDepth, chargeTime);
                chargeProcessor.render(mainFb, ObjectAllocator.TRIVIAL);
            }
        }

        // ── Ambient Kai Slash (결없영 배경 참격) — 다중 도메인 지원 ──────────────
        {
            Vec3d ambCamPos = ((CameraAccessor) camera).getPos();
            float ambientDtMs = renderTickCounter.getDynamicDeltaTicks() * 50f;
            AmbientKaiSlashManager.updateAll(ambCamPos, ambientDtMs);
        }
        if (AmbientKaiSlashManager.hasActiveDomains()) {
            // 공유 행렬 한 번만 계산
            Matrix4f ambViewMatrix = new Matrix4f()
                .rotation(camera.getRotation().conjugate(new Quaternionf()));
            Matrix4f ambInvViewProj = projectionMatrix
                .mul(ambViewMatrix, new Matrix4f())
                .invert(new Matrix4f());
            Vec3d ambCamPos = AmbientKaiSlashManager.cameraPos;
            float stormTime = (float)(System.currentTimeMillis() % 100000L) / 1000.0f;

            for (AmbientKaiSlashManager.DomainInstance domain
                    : AmbientKaiSlashManager.getActiveDomains()) {
                if (domain.smoothRadius <= 0f) continue;

                // ── 1. Mizushi Dust Storm ────────────────────────────────────
                {
                    float dcRelX = (float)(domain.center.x - ambCamPos.x);
                    float dcRelY = (float)(domain.center.y - ambCamPos.y);
                    float dcRelZ = (float)(domain.center.z - ambCamPos.z);

                    PostEffectProcessor dustProcessor;
                    try {
                        dustProcessor = client.getShaderLoader().loadPostEffect(
                            MIZUSHI_DUST_STORM_EFFECT_ID,
                            Set.of(net.minecraft.client.render.DefaultFramebufferSet.MAIN)
                        );
                    } catch (Exception e) {
                        System.err.println("[JJKMixin] Failed to load mizushi_dust_storm post effect: " + e.getMessage());
                        dustProcessor = null;
                    }
                    if (dustProcessor != null) {
                        updateDustStormUniforms(dustProcessor, ambInvViewProj,
                            dcRelX, dcRelY, dcRelZ, domain.smoothRadius, stormTime,
                            domain.getFadeAlpha());
                        dustProcessor.render(mainFb, ObjectAllocator.TRIVIAL);
                    }
                }

                // ── 2. Ambient Kai Slash ─────────────────────────────────────
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
                    AmbientKaiSlashManager.AmbientSlash[] slashes = domain.slashes;
                    final int SLOT = 12; // floats per slash (3 vec4)
                    float[] slashData = new float[AmbientKaiSlashManager.MAX_SLASHES * SLOT];
                    int activeCount = 0;
                    int maxActiveSlashes = domain.getActiveSlashCount();
                    Vec3d ambientCenter = domain.center;
                    float domainAlpha = domain.getFadeAlpha();

                    for (AmbientKaiSlashManager.AmbientSlash slash : slashes) {
                        if (activeCount >= maxActiveSlashes) break;

                        float localT = slash.getLocalT();
                        float fade   = slash.getFade(localT) * domainAlpha;
                        if (fade < 0.002f) continue;

                        Vec3d ep1 = new Vec3d(
                            ambientCenter.x + slash.sx + slash.dx * slash.halfLen,
                            ambientCenter.y + slash.sy + slash.dy * slash.halfLen,
                            ambientCenter.z + slash.sz + slash.dz * slash.halfLen);
                        Vec3d ep2 = new Vec3d(
                            ambientCenter.x + slash.sx - slash.dx * slash.halfLen,
                            ambientCenter.y + slash.sy - slash.dy * slash.halfLen,
                            ambientCenter.z + slash.sz - slash.dz * slash.halfLen);

                        Vec3d sp1 = WorldToScreenUtil.worldToScreen(ep1, camera, projectionMatrix);
                        Vec3d sp2 = WorldToScreenUtil.worldToScreen(ep2, camera, projectionMatrix);
                        if (sp1 == null || sp2 == null) continue;

                        float headT = slash.getHeadT(localT);
                        float tailT = slash.getTailT(localT);

                        float dist      = (float)((sp1.z + sp2.z) * 0.5);
                        float distScale = 3.0f / Math.max(3.0f, dist);

                        float depth1 = WorldToScreenUtil.worldToDepth(ep1, camera, projectionMatrix);
                        float depth2 = WorldToScreenUtil.worldToDepth(ep2, camera, projectionMatrix);

                        int base = activeCount * SLOT;
                        slashData[base     ] = (float) sp1.x;
                        slashData[base +  1] = 1.0f - (float) sp1.y;
                        slashData[base +  2] = (float) sp2.x;
                        slashData[base +  3] = 1.0f - (float) sp2.y;
                        slashData[base +  4] = headT;
                        slashData[base +  5] = tailT;
                        slashData[base +  6] = fade;
                        slashData[base +  7] = distScale;
                        slashData[base +  8] = depth1;
                        slashData[base +  9] = depth2;
                        slashData[base + 10] = (float) sp1.z;
                        slashData[base + 11] = (float) sp2.z;

                        activeCount++;
                    }

                    updateAmbientKaiUniforms(ambientProcessor, activeCount, slashData);
                    ambientProcessor.render(mainFb, ObjectAllocator.TRIVIAL);
                }
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

        // ── Mizushi Thermobaric Explosion ─────────────────────────────────────
        if (MizushiThermobaricManager.isActive()) {
            Matrix4f thermoViewMatrix = new Matrix4f()
                .rotation(camera.getRotation().conjugate(new Quaternionf()));
            Matrix4f thermoInvViewProj = projectionMatrix
                .mul(thermoViewMatrix, new Matrix4f())
                .invert(new Matrix4f());

            Vec3d thermoCamPos = ((CameraAccessor) camera).getPos();
            Vec3d thermoCenter = MizushiThermobaricManager.getCenter();
            float tcRelX = (float)(thermoCenter.x - thermoCamPos.x);
            float tcRelY = (float)(thermoCenter.y - thermoCamPos.y);
            float tcRelZ = (float)(thermoCenter.z - thermoCamPos.z);

            // Project center to screen UV; WorldToScreenUtil: y=0 top → flip for shader (y=0 bottom)
            Vec3d centerScreen = WorldToScreenUtil.worldToScreen(thermoCenter, camera, projectionMatrix);
            float screenU     = centerScreen != null ? (float) centerScreen.x       : 0.5f;
            float screenVFlip = centerScreen != null ? 1.0f - (float) centerScreen.y : 0.5f;

            float effectR    = MizushiThermobaricManager.getEffectRadius();
            float riseAmount = MizushiThermobaricManager.getRiseAmount();

            // ── 1. Shockwave (UV 왜곡 + 수증기 + 지표면 먼지) ─────────────────
            float swRadius   = MizushiThermobaricManager.getShockwaveRadius();
            float swStrength = MizushiThermobaricManager.getShockwaveStrength();
            float vaporAlpha = MizushiThermobaricManager.getVaporAlpha();
            float dustAlpha  = MizushiThermobaricManager.getDustAlpha();
            if (swRadius > 0.1f && (swStrength > 0.001f || vaporAlpha > 0.001f || dustAlpha > 0.001f)) {
                PostEffectProcessor swProcessor;
                try {
                    swProcessor = client.getShaderLoader().loadPostEffect(
                        MIZUSHI_SHOCKWAVE_EFFECT_ID,
                        Set.of(net.minecraft.client.render.DefaultFramebufferSet.MAIN)
                    );
                } catch (Exception e) {
                    System.err.println("[JJKMixin] Failed to load mizushi_shockwave: " + e.getMessage());
                    swProcessor = null;
                }
                if (swProcessor != null) {
                    updateShockwaveUniforms(swProcessor, thermoInvViewProj,
                        tcRelX, tcRelY, tcRelZ, swRadius,
                        swStrength, vaporAlpha, dustAlpha, effectR);
                    swProcessor.render(mainFb, ObjectAllocator.TRIVIAL);
                }
            }

            // ── 2. Fireball + Flash + Smoke ──────────────────────────────────
            float fbRadius   = MizushiThermobaricManager.getFireballRadius();
            float fireAlpha  = MizushiThermobaricManager.getFireAlpha();
            float flashAlpha = MizushiThermobaricManager.getFlashAlpha();
            float smokeAlpha = MizushiThermobaricManager.getSmokeAlpha();
            float thermoTime = MizushiThermobaricManager.getAnimTime();
            if (fbRadius > 0.1f || flashAlpha > 0.001f || smokeAlpha > 0.001f) {
                PostEffectProcessor thermoProcessor;
                try {
                    thermoProcessor = client.getShaderLoader().loadPostEffect(
                        MIZUSHI_THERMOBARIC_EFFECT_ID,
                        Set.of(net.minecraft.client.render.DefaultFramebufferSet.MAIN)
                    );
                } catch (Exception e) {
                    System.err.println("[JJKMixin] Failed to load mizushi_thermobaric: " + e.getMessage());
                    thermoProcessor = null;
                }
                if (thermoProcessor != null) {
                    updateThermobaricUniforms(thermoProcessor, thermoInvViewProj,
                        tcRelX, tcRelY, tcRelZ, fbRadius,
                        fireAlpha, flashAlpha, smokeAlpha, thermoTime,
                        screenU, screenVFlip, effectR, riseAmount);
                    thermoProcessor.render(mainFb, ObjectAllocator.TRIVIAL);
                }
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
     *   float Time          →  4 bytes
     *   float Depth1        →  4 bytes
     *   float Depth2        →  4 bytes
     *   Total               = 40 bytes
     */
    @SuppressWarnings("unchecked")
    private void updateKaiSlashUniforms(PostEffectProcessor processor,
                                         float p1x, float p1y,
                                         float p2x, float p2y,
                                         float coreHalfWidth, float bloomWidth,
                                         float alpha, float time,
                                         float depth1, float depth2) {
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

            if ((buf.usage() & 8) == 0 || buf.size() < 40) {
                buf.close();
                com.mojang.blaze3d.buffers.GpuBuffer newBuf =
                    RenderSystem.getDevice().createBuffer(() -> "JJK KaiSlashConfig", 8 | 128, 40);
                ubs.put("KaiSlashConfig", newBuf);
                buf = newBuf;
            }

            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            try {
                com.mojang.blaze3d.buffers.Std140Builder b =
                    com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, 40);
                b.putVec2(p1x, p1y);
                b.putVec2(p2x, p2y);
                b.putFloat(coreHalfWidth);
                b.putFloat(bloomWidth);
                b.putFloat(alpha);
                b.putFloat(time);
                b.putFloat(depth1);
                b.putFloat(depth2);
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
     *   float Depth     →  4 bytes
     *   Total           = 28 bytes
     */
    @SuppressWarnings("unchecked")
    private void updateHachiSlashUniforms(PostEffectProcessor processor,
                                          float cx, float cy, float angle, float time,
                                          float skewAngle, float distScale, float depth) {
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

            if ((buf.usage() & 8) == 0 || buf.size() < 28) {
                buf.close();
                com.mojang.blaze3d.buffers.GpuBuffer newBuf =
                    RenderSystem.getDevice().createBuffer(() -> "JJK HachiSlashConfig", 8 | 128, 28);
                ubs.put("HachiSlashConfig", newBuf);
                buf = newBuf;
            }

            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            try {
                com.mojang.blaze3d.buffers.Std140Builder b =
                    com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, 28);
                b.putVec2(cx, cy);
                b.putFloat(angle);
                b.putFloat(time);
                b.putFloat(skewAngle);
                b.putFloat(distScale);
                b.putFloat(depth);
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
     * Updates the DustStormConfig uniform buffer.
     *
     * std140 layout:
     *   vec4 InvViewProjC0..C3 → 4 × 16 = 64 bytes
     *   vec4 DomainCenter      → 16 bytes (.w = 0)
     *   float DomainRadius     →  4 bytes
     *   float Time             →  4 bytes
     *   float _pad0, _pad1     →  8 bytes
     *   Total                  = 96 bytes
     */
    @SuppressWarnings("unchecked")
    private void updateDustStormUniforms(PostEffectProcessor processor,
                                          Matrix4f invViewProj,
                                          float domCX, float domCY, float domCZ,
                                          float domRadius, float time, float alpha) {
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

            com.mojang.blaze3d.buffers.GpuBuffer buf = ubs.get("DustStormConfig");
            if (buf == null) return;

            if ((buf.usage() & 8) == 0 || buf.size() < 96) {
                buf.close();
                com.mojang.blaze3d.buffers.GpuBuffer newBuf =
                    RenderSystem.getDevice().createBuffer(() -> "JJK DustStormConfig", 8 | 128, 96);
                ubs.put("DustStormConfig", newBuf);
                buf = newBuf;
            }

            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            try {
                com.mojang.blaze3d.buffers.Std140Builder b =
                    com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, 96);
                // InvViewProj columns
                b.putVec4(invViewProj.m00(), invViewProj.m01(), invViewProj.m02(), invViewProj.m03());
                b.putVec4(invViewProj.m10(), invViewProj.m11(), invViewProj.m12(), invViewProj.m13());
                b.putVec4(invViewProj.m20(), invViewProj.m21(), invViewProj.m22(), invViewProj.m23());
                b.putVec4(invViewProj.m30(), invViewProj.m31(), invViewProj.m32(), invViewProj.m33());
                // DomainCenter (camera-relative)
                b.putVec4(domCX, domCY, domCZ, 0.0f);
                // DomainRadius, Time, padding
                b.putFloat(domRadius);
                b.putFloat(time);
                b.putFloat(alpha);
                b.putFloat(0.0f);
                RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(buf.slice(), b.get());
            } finally {
                stack.pop();
            }
        } catch (Exception e) {
            System.err.println("[JJKMixin] updateDustStormUniforms failed: " + e.getMessage());
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
     *   float SlashCount    →    4 bytes
     *   float CoreHalfWidth →    4 bytes
     *   float BloomWidth    →    4 bytes
     *   float _pad          →    4 bytes
     *   vec4  Slashes[N×3]  → N×48 bytes  (MAX_SLASHES슬래시 × 3 vec4)
     *   Total               = 16 + MAX_SLASHES×48 bytes
     *
     * slashData: float[] of length slashCount × 12
     *   [i*12+0..3]  = p1x, p1y, p2x, p2y  (screen texCoord UV)
     *   [i*12+4..7]  = headT, tailT, fade, distScale
     *   [i*12+8..9]  = depth1, depth2  (NDC depth [0,1])
     *   [i*12+10..11]= 0, 0 (padding)
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

            // std140: 16(header) + MAX_SLASHES×3×vec4 bytes
            final int REQUIRED = 16 + AmbientKaiSlashManager.MAX_SLASHES * 3 * 16;
            if ((buf.usage() & 8) == 0 || buf.size() < REQUIRED) {
                // 새 버퍼 생성 후 맵 전체를 교체 (원본 맵이 unmodifiable일 경우 대응)
                com.mojang.blaze3d.buffers.GpuBuffer newBuf =
                    RenderSystem.getDevice().createBuffer(() -> "JJK AmbientKaiConfig", 8 | 128, REQUIRED);
                java.util.HashMap<String, com.mojang.blaze3d.buffers.GpuBuffer> newMap = new java.util.HashMap<>(ubs);
                newMap.put("AmbientKaiConfig", newBuf);
                ubField.set(firstPass, newMap);
                buf.close();
                buf = newBuf;
            }

            // MemoryStack 대신 direct ByteBuffer 사용
            java.nio.ByteBuffer rawBuf = java.nio.ByteBuffer
                .allocateDirect(REQUIRED)
                .order(java.nio.ByteOrder.nativeOrder());

            // Header (16 bytes)
            rawBuf.putFloat((float) slashCount);
            rawBuf.putFloat(AmbientKaiSlashManager.CORE_HALF_WIDTH);
            rawBuf.putFloat(AmbientKaiSlashManager.BLOOM_WIDTH);
            rawBuf.putFloat(0.0f);

            // Slashes array: 384 vec4 (3 per slash × 128 슬래시)
            int maxSlashes = AmbientKaiSlashManager.MAX_SLASHES;
            for (int i = 0; i < maxSlashes; i++) {
                if (i < slashCount) {
                    int base = i * 12;
                    rawBuf.putFloat(slashData[base    ]); rawBuf.putFloat(slashData[base + 1]);
                    rawBuf.putFloat(slashData[base + 2]); rawBuf.putFloat(slashData[base + 3]);
                    rawBuf.putFloat(slashData[base + 4]); rawBuf.putFloat(slashData[base + 5]);
                    rawBuf.putFloat(slashData[base + 6]); rawBuf.putFloat(slashData[base + 7]);
                    rawBuf.putFloat(slashData[base + 8]); rawBuf.putFloat(slashData[base + 9]);
                    rawBuf.putFloat(0f); rawBuf.putFloat(0f);
                } else {
                    for (int j = 0; j < 12; j++) rawBuf.putFloat(0f);
                }
            }
            rawBuf.flip();

            RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(buf.slice(), rawBuf);
        } catch (Exception e) {
            System.err.println("[JJKMixin] updateAmbientKaiUniforms failed: " + e.getMessage());
        }
    }

    /**
     * Updates the ChargeConfig uniform buffer.
     *
     * std140 layout:
     *   vec4 InvViewProjC0..C3  → 4 × 16 = 64 bytes
     *   vec4 CasterHeadRel      → 16 bytes  (xyz = cam-relative head, w = progress)
     *   vec4 ScreenParams       → 16 bytes  (xy = head UV, z = sphere radius, w = time)
     *   Total                   = 96 bytes
     */
    @SuppressWarnings("unchecked")
    private void updateChargeUniforms(PostEffectProcessor processor,
                                       Matrix4f invViewProj,
                                       float headX, float headY, float headZ,
                                       float progress,
                                       float headUVX, float headUVY,
                                       float headDepth, float time) {
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

            com.mojang.blaze3d.buffers.GpuBuffer buf = ubs.get("ChargeConfig");
            if (buf == null) return;

            if ((buf.usage() & 8) == 0 || buf.size() < 96) {
                buf.close();
                com.mojang.blaze3d.buffers.GpuBuffer newBuf =
                    RenderSystem.getDevice().createBuffer(() -> "JJK ChargeConfig", 8 | 128, 96);
                ubs.put("ChargeConfig", newBuf);
                buf = newBuf;
            }

            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            try {
                com.mojang.blaze3d.buffers.Std140Builder b =
                    com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, 96);
                b.putVec4(invViewProj.m00(), invViewProj.m01(), invViewProj.m02(), invViewProj.m03());
                b.putVec4(invViewProj.m10(), invViewProj.m11(), invViewProj.m12(), invViewProj.m13());
                b.putVec4(invViewProj.m20(), invViewProj.m21(), invViewProj.m22(), invViewProj.m23());
                b.putVec4(invViewProj.m30(), invViewProj.m31(), invViewProj.m32(), invViewProj.m33());
                b.putVec4(headX, headY, headZ, progress);
                b.putVec4(headUVX, headUVY, headDepth, time);
                RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(buf.slice(), b.get());
            } finally {
                stack.pop();
            }
        } catch (Exception e) {
            System.err.println("[JJKMixin] updateChargeUniforms failed: " + e.getMessage());
        }
    }

    /**
     * Updates the ShockwaveConfig uniform buffer.
     *
     * std140 layout (96 bytes):
     *   vec4 InvViewProjC0-C3  → 4 × 16 = 64 bytes
     *   vec4 CenterRel         → 16 bytes  (xyz = cam-relative center, w = shockwave radius)
     *   vec4 Params            → 16 bytes  (x = swStrength, y = vaporAlpha, z = dustAlpha, w = effectRadius)
     */
    @SuppressWarnings("unchecked")
    private void updateShockwaveUniforms(PostEffectProcessor processor,
                                          Matrix4f invViewProj,
                                          float cx, float cy, float cz, float radius,
                                          float swStrength, float vaporAlpha,
                                          float dustAlpha, float effectRadius) {
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

            com.mojang.blaze3d.buffers.GpuBuffer buf = ubs.get("ShockwaveConfig");
            if (buf == null) return;

            if ((buf.usage() & 8) == 0 || buf.size() < 96) {
                buf.close();
                com.mojang.blaze3d.buffers.GpuBuffer newBuf =
                    RenderSystem.getDevice().createBuffer(() -> "JJK ShockwaveConfig", 8 | 128, 96);
                ubs.put("ShockwaveConfig", newBuf);
                buf = newBuf;
            }

            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            try {
                com.mojang.blaze3d.buffers.Std140Builder b =
                    com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, 96);
                b.putVec4(invViewProj.m00(), invViewProj.m01(), invViewProj.m02(), invViewProj.m03());
                b.putVec4(invViewProj.m10(), invViewProj.m11(), invViewProj.m12(), invViewProj.m13());
                b.putVec4(invViewProj.m20(), invViewProj.m21(), invViewProj.m22(), invViewProj.m23());
                b.putVec4(invViewProj.m30(), invViewProj.m31(), invViewProj.m32(), invViewProj.m33());
                b.putVec4(cx, cy, cz, radius);
                b.putVec4(swStrength, vaporAlpha, dustAlpha, effectRadius);
                RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(buf.slice(), b.get());
            } finally {
                stack.pop();
            }
        } catch (Exception e) {
            System.err.println("[JJKMixin] updateShockwaveUniforms failed: " + e.getMessage());
        }
    }

    /**
     * Updates the ThermobaricConfig uniform buffer.
     *
     * std140 layout (112 bytes):
     *   vec4 InvViewProjC0-C3  → 4 × 16 = 64 bytes
     *   vec4 CenterRel         → 16 bytes  (xyz = cam-relative center, w = fireball radius)
     *   vec4 AlphaParams       → 16 bytes  (x = fireAlpha, y = flashAlpha, z = smokeAlpha, w = time)
     *   float CenterScreenU    →  4 bytes
     *   float CenterScreenV    →  4 bytes
     *   float EffectRadius     →  4 bytes
     *   float RiseAmount       →  4 bytes
     */
    @SuppressWarnings("unchecked")
    private void updateThermobaricUniforms(PostEffectProcessor processor,
                                            Matrix4f invViewProj,
                                            float cx, float cy, float cz, float fbRadius,
                                            float fireAlpha, float flashAlpha,
                                            float smokeAlpha, float time,
                                            float screenU, float screenV,
                                            float effectRadius, float riseAmount) {
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

            com.mojang.blaze3d.buffers.GpuBuffer buf = ubs.get("ThermobaricConfig");
            if (buf == null) return;

            if ((buf.usage() & 8) == 0 || buf.size() < 112) {
                buf.close();
                com.mojang.blaze3d.buffers.GpuBuffer newBuf =
                    RenderSystem.getDevice().createBuffer(() -> "JJK ThermobaricConfig", 8 | 128, 112);
                ubs.put("ThermobaricConfig", newBuf);
                buf = newBuf;
            }

            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            try {
                com.mojang.blaze3d.buffers.Std140Builder b =
                    com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, 112);
                b.putVec4(invViewProj.m00(), invViewProj.m01(), invViewProj.m02(), invViewProj.m03());
                b.putVec4(invViewProj.m10(), invViewProj.m11(), invViewProj.m12(), invViewProj.m13());
                b.putVec4(invViewProj.m20(), invViewProj.m21(), invViewProj.m22(), invViewProj.m23());
                b.putVec4(invViewProj.m30(), invViewProj.m31(), invViewProj.m32(), invViewProj.m33());
                b.putVec4(cx, cy, cz, fbRadius);
                b.putVec4(fireAlpha, flashAlpha, smokeAlpha, time);
                b.putFloat(screenU);
                b.putFloat(screenV);
                b.putFloat(effectRadius);
                b.putFloat(riseAmount);
                RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(buf.slice(), b.get());
            } finally {
                stack.pop();
            }
        } catch (Exception e) {
            System.err.println("[JJKMixin] updateThermobaricUniforms failed: " + e.getMessage());
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
