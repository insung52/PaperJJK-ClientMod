package com.justheare.paperjjk_client.shader;

import net.minecraft.util.math.Vec3d;

/**
 * 미즈시 열압력탄 폭발 시각 효과 매니저.
 *
 * totalDurationMs = 4000 + radius×20 ms
 * shockwaveReach  = radius × 10 블록
 *
 * 화염구 팽창: easeOut quadratic, 0.5초에 최대 크기
 * 수증기:   swRadius = effectRadius×1.5 에서 50%, effectRadius×3 에서 0
 * 지표면 먼지: swRadius = effectRadius×6.5 에서 0 (선형)
 * 섬광 거리 fade: 셰이더에서 exp(-d²/r²*0.1) 로 처리 (effectRadius를 uniform 전달)
 */
public class MizushiThermobaricManager {

    private static boolean active          = false;
    private static Vec3d   center          = Vec3d.ZERO;
    private static float   effectRadius    = 50f;
    private static long    startMs         = 0L;
    private static float   shockwaveReach  = 0f;
    private static float   totalDurationMs = 0f;

    // ── API ──────────────────────────────────────────────────────────────────

    public static void trigger(Vec3d worldCenter, float radius) {
        center          = worldCenter;
        effectRadius    = radius;
        shockwaveReach  = radius * 10f;
        totalDurationMs = 4000f + radius * 20f;
        startMs         = System.currentTimeMillis();
        active          = true;
    }

    public static void stop() { active = false; }

    public static boolean isActive() {
        if (!active) return false;
        if (System.currentTimeMillis() - startMs > totalDurationMs) {
            active = false;
            return false;
        }
        return true;
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────────

    private static float getT() {
        return Math.min(1f, (System.currentTimeMillis() - startMs) / totalDurationMs);
    }

    private static float ss(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }

    // ── Thermobaric getters ───────────────────────────────────────────────────

    public static Vec3d getCenter()        { return center; }
    public static float getEffectRadius()  { return effectRadius; }

    /** easeOut quadratic: 1초에 최대 크기, 초반 빠르게 팽창 후 감속 */
    public static float getFireballRadius() {
        float t     = getT();
        float tHalf = 1000f / totalDurationMs; // t at 1.0 second
        float k     = Math.min(t / tHalf, 1f);
        return effectRadius * (1f - (1f - k) * (1f - k));
    }

    public static float getFireAlpha() {
        float t = getT();
        return ss(0f, 0.05f, t) * (1f - ss(0.15f, 0.65f, t));
    }

    public static float getFlashAlpha() {
        float t = getT();
        return ss(0f, 0.02f, t) * (1f - ss(0.02f, 0.12f, t));
    }

    public static float getSmokeAlpha() {
        float t = getT();
        return ss(0.2f, 0.5f, t) * (1f - ss(0.75f, 1.0f, t));
    }

    public static float getAnimTime() {
        return (float)(System.currentTimeMillis() % 100_000L) / 1000f;
    }

    // ── Shockwave getters ─────────────────────────────────────────────────────

    public static float getShockwaveRadius() {
        return shockwaveReach * getT();
    }

    /** 충격파 UV 왜곡 강도. 초기 급상승 후 선형 감쇠 (2×effectRadius 에서 50%). */
    public static float getShockwaveStrength() {
        float t   = getT();
        float swR = shockwaveReach * t;
        // ramp-up: 첫 0.05t 동안 0→1
        float ramp = ss(0f, 0.05f, t);
        // 거리 감쇠: 4×effectRadius 에서 0 (선형)
        float distFade = Math.max(0f, 1f - swR / (effectRadius * 4f));
        return ramp * distFade * 1.5f;
    }

    /** 수증기 응축 강도. swRadius=effectRadius×1.5 에서 50%, effectRadius×3 에서 0. */
    public static float getVaporAlpha() {
        float t   = getT();
        float swR = shockwaveReach * t;
        return ss(0f, 0.02f, t) * Math.max(0f, 1f - swR / (effectRadius * 3f));
    }

    /** 지표면 먼지 강도. swRadius=effectRadius×6.5 에서 0 (선형). */
    public static float getDustAlpha() {
        float t   = getT();
        float swR = shockwaveReach * t;
        return ss(0f, 0.01f, t) * Math.max(0f, 1f - swR / (effectRadius * 6.5f));
    }

    /**
     * 단조증가 상승 진행도 (0→1). t=0.5 에서 최대.
     * smokeAlpha 페이드에 연동되지 않으므로 절대 내려가지 않음.
     */
    public static float getRiseAmount() {
        return Math.min(1f, getT() * 2f);
    }
}
