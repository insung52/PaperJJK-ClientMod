package com.justheare.paperjjk_client.shader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

/**
 * InfinityPassive 배리어 시각 효과 상태 관리.
 * 현재는 /jjkdebug barrier 디버그 전용.
 */
public class PassiveBarrierManager {

    // ── 배리어 상태 ───────────────────────────────────────────────────────

    public static boolean debugActive = false;
    public static Vec3d   center      = Vec3d.ZERO;
    public static float   radius      = 5.0f;
    public static float   power       = 0.5f;  // 0.0 ~ 1.0

    // ── 충돌 파동 (순환 버퍼, 8슬롯) ─────────────────────────────────────

    public static final int MAX_RIPPLES = 8;

    /** 파동 world 좌표 */
    public static Vec3d[] ripplePos       = new Vec3d[MAX_RIPPLES];
    /** 파동 진행 정도 (0→1). 1.0 이상 = 비활성 */
    public static float[] rippleAge       = new float[MAX_RIPPLES];
    /** 파동 세기 (0~1) */
    public static float[] rippleIntensity = new float[MAX_RIPPLES];

    private static int    rippleHead = 0;
    private static double prevDist   = Double.MAX_VALUE;

    static {
        Arrays.fill(ripplePos, Vec3d.ZERO);
        Arrays.fill(rippleAge, 1.0f);
        Arrays.fill(rippleIntensity, 0.0f);
    }

    // ── 공개 API ──────────────────────────────────────────────────────────

    public static boolean isActive() { return debugActive; }

    public static void activate(Vec3d pos, float r, float p) {
        center = pos;
        radius = r;
        power  = Math.max(0.0f, Math.min(1.0f, p));
        debugActive = true;
        prevDist = Double.MAX_VALUE;
        clearRipples();
    }

    public static void deactivate() {
        debugActive = false;
        clearRipples();
    }

    public static void toggle(Vec3d pos, float r, float p) {
        if (debugActive) deactivate();
        else activate(pos, r, p);
    }

    public static void addRipple(Vec3d worldPos, float intensity) {
        int slot = rippleHead % MAX_RIPPLES;
        rippleHead++;
        ripplePos[slot]       = worldPos;
        rippleAge[slot]       = 0.0f;
        rippleIntensity[slot] = Math.min(1.0f, Math.max(0.0f, intensity));
    }

    // ── 틱 처리 (PaperJJKClientClient.END_CLIENT_TICK 에서 호출) ──────────

    public static void tick(MinecraftClient client) {
        if (!debugActive || client.player == null) return;

        // 파동 진행 (~1초 수명, 20틱/s → 틱당 ≈0.033)
        for (int i = 0; i < MAX_RIPPLES; i++) {
            if (rippleAge[i] < 1.0f) {
                rippleAge[i] = Math.min(1.0f, rippleAge[i] + 0.033f);
            }
        }

        Vec3d playerCenter = new Vec3d(client.player.getX(),
            client.player.getY() + client.player.getHeight() / 2.0,
            client.player.getZ());
        double dist        = playerCenter.distanceTo(center);
        // 엔티티 폭 보정 반경 (서버 processEntities와 동일 공식)
        double adjRadius   = radius + client.player.getWidth() / 3.0;

        // 경계 진입 시 충돌 파동 추가
        if (dist < adjRadius + 0.5 && prevDist >= adjRadius + 0.5) {
            Vec3d dir    = playerCenter.subtract(center);
            Vec3d hitDir = dir.lengthSquared() > 0.0001 ? dir.normalize() : new Vec3d(1, 0, 0);
            Vec3d hitPos = center.add(hitDir.multiply(radius));
            float speed  = (float) client.player.getVelocity().length();
            addRipple(hitPos, Math.max(0.4f, speed * 2.5f));
        }

        // 배리어 내부 진입 시 밀어내기
        if (dist < adjRadius) {
            Vec3d pushDir = playerCenter.subtract(center);
            double pLen   = pushDir.length();
            if (pLen > 0.01) {
                double strength = 0.25 * (1.0 - dist / adjRadius);
                Vec3d  norm     = pushDir.normalize();
                Vec3d  vel      = client.player.getVelocity();
                client.player.setVelocity(
                    vel.x + norm.x * strength,
                    vel.y + norm.y * strength * 0.3,
                    vel.z + norm.z * strength
                );
            }
        }

        prevDist = dist;
    }

    // ── 내부 ──────────────────────────────────────────────────────────────

    private static void clearRipples() {
        Arrays.fill(rippleAge, 1.0f);
        Arrays.fill(rippleIntensity, 0.0f);
        Arrays.fill(ripplePos, Vec3d.ZERO);
        rippleHead = 0;
    }
}
