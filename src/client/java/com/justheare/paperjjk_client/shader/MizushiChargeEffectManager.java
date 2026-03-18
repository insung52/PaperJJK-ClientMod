package com.justheare.paperjjk_client.shader;

import net.minecraft.util.math.Vec3d;

/**
 * 미즈시 결없영 2초 충전 애니메이션 효과 매니저.
 *
 * 타임라인:
 *   서버 DOMAIN_VISUAL(MIZUSHI, isOpen=true) START 수신
 *   → start(worldHeadPos) 호출
 *   → DURATION_MS(2000ms) 동안 progress 0→1 애니메이션
 *   → 만료 후 isActive() false → 실제 결없영 이펙트(ambient slash/dust storm) 시작
 *
 * debug 모드: /jjkdebug charge → 0,150,0 고정 좌표, 루프 재생
 */
public class MizushiChargeEffectManager {

    /** 충전 구 최대 반경 (블록). 애니메이션 종료 시 도달하는 크기. */
    public static final float MAX_SPHERE_RADIUS = 10.0f;

    /** 충전 지속 시간 (ms). 서버 START_DELAY_TICKS=40 = 2000ms 와 일치. */
    public static final long DURATION_MS = 2000L;

    private static boolean active    = false;
    private static Vec3d worldCenter = Vec3d.ZERO; // 시전자 머리 월드 좌표
    private static long  startTimeMs = 0L;

    private static boolean debugMode = false;

    // ── 외부 API ──────────────────────────────────────────────────────────────

    /** 결없영 영역전개 START 패킷 수신 시 호출. worldHeadPos = 시전자 눈 위치. */
    public static void start(Vec3d worldHeadPos) {
        worldCenter = worldHeadPos;
        startTimeMs = System.currentTimeMillis();
        active      = true;
    }

    public static void stop() {
        active    = false;
        debugMode = false;
    }

    /** /jjkdebug charge — 0,150+1.6,0 에서 루핑 테스트 */
    public static void toggleDebug() {
        debugMode = !debugMode;
        if (debugMode) {
            worldCenter = new Vec3d(0, 151.6, 0);
            startTimeMs = System.currentTimeMillis();
        } else {
            active = false;
        }
    }

    public static boolean isDebugMode() { return debugMode; }

    public static boolean isActive() {
        if (debugMode) return true;
        if (!active) return false;
        if (System.currentTimeMillis() - startTimeMs >= DURATION_MS) {
            active = false;
            return false;
        }
        return true;
    }

    /**
     * 애니메이션 진행도 (0.0 → 1.0).
     * debug 모드에서는 0→1 루핑.
     */
    public static float getProgress() {
        long elapsed = System.currentTimeMillis() - startTimeMs;
        if (debugMode) {
            return (float)(elapsed % DURATION_MS) / DURATION_MS;
        }
        return Math.min(1.0f, (float) elapsed / DURATION_MS);
    }

    /** 현재 충전 구 반경 (블록). progress 에 비례해 0→MAX_SPHERE_RADIUS. */
    public static float getSphereRadius() {
        return getProgress() * MAX_SPHERE_RADIUS;
    }

    public static Vec3d getWorldCenter() { return worldCenter; }
}
