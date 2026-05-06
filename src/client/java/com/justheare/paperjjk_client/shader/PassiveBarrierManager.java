package com.justheare.paperjjk_client.shader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InfinityPassive 배리어 시각 효과 상태 관리.
 * 서버 패킷(INFINITY_PASSIVE_*) 으로 구동되며,
 * /jjkdebug barrier 디버그 커맨드도 지원.
 */
public class PassiveBarrierManager {

    // ── 디버그 전용 UUID ──────────────────────────────────────────────────
    private static final UUID DEBUG_UUID =
        UUID.fromString("00000000-0000-0000-0000-000000000002");

    // ── 배리어 상태 ───────────────────────────────────────────────────────

    public static class BarrierState {
        public final UUID  casterUuid;
        public float radius;
        public float power;          // 0.005 ~ 1.0
        public Vec3d cachedCenter;   // 매 프레임 갱신 (엔티티 추적 or 고정 debug 좌표)

        // 충돌 파동 순환 버퍼 (8슬롯)
        public static final int MAX_RIPPLES = 8;
        public Vec3d[] ripplePos       = new Vec3d[MAX_RIPPLES];
        public float[] rippleAge       = new float[MAX_RIPPLES];
        public float[] rippleIntensity = new float[MAX_RIPPLES];
        public int     rippleHead      = 0;

        // debug 배리어는 위치가 고정
        public final boolean isDebug;
        final Vec3d   debugCenter;

        BarrierState(UUID uuid, float radius, float power,
                     Vec3d initialCenter, boolean isDebug) {
            this.casterUuid  = uuid;
            this.radius      = radius;
            this.power       = power;
            this.cachedCenter = initialCenter;
            this.isDebug     = isDebug;
            this.debugCenter = isDebug ? initialCenter : null;
            Arrays.fill(ripplePos, Vec3d.ZERO);
            Arrays.fill(rippleAge, 1.0f);
            Arrays.fill(rippleIntensity, 0.0f);
        }

        public void addRipple(Vec3d worldPos, float intensity) {
            int slot = rippleHead % MAX_RIPPLES;
            rippleHead++;
            ripplePos[slot]       = worldPos;
            rippleAge[slot]       = 0.0f;
            rippleIntensity[slot] = Math.min(1.0f, Math.max(0.0f, intensity));
        }

        public void clearRipples() {
            Arrays.fill(rippleAge, 1.0f);
            Arrays.fill(rippleIntensity, 0.0f);
            Arrays.fill(ripplePos, Vec3d.ZERO);
            rippleHead = 0;
        }
    }

    private static final Map<UUID, BarrierState> states = new ConcurrentHashMap<>();

    // ── 서버 패킷 API ─────────────────────────────────────────────────────

    public static void onActivate(UUID casterUuid, float radius, float power) {
        BarrierState s = new BarrierState(casterUuid, radius, power, Vec3d.ZERO, false);
        states.put(casterUuid, s);
    }

    public static void onSync(UUID casterUuid, float radius, float power) {
        BarrierState s = states.get(casterUuid);
        if (s != null) {
            s.radius = radius;
            s.power  = power;
        } else {
            // ACTIVATE 없이 SYNC 수신 시 생성
            states.put(casterUuid, new BarrierState(casterUuid, radius, power, Vec3d.ZERO, false));
        }
    }

    public static void onCollision(UUID casterUuid, Vec3d hitPos, float intensity) {
        BarrierState s = states.get(casterUuid);
        if (s != null) s.addRipple(hitPos, intensity);
    }

    public static void onDeactivate(UUID casterUuid) {
        states.remove(casterUuid);
    }

    // ── 디버그 API (/jjkdebug barrier) ───────────────────────────────────

    public static void debugActivate(Vec3d center, float radius, float power) {
        BarrierState s = new BarrierState(DEBUG_UUID, radius, power, center, true);
        states.put(DEBUG_UUID, s);
    }

    public static void debugDeactivate() {
        states.remove(DEBUG_UUID);
    }

    public static void debugToggle(Vec3d center, float radius, float power) {
        if (states.containsKey(DEBUG_UUID)) debugDeactivate();
        else debugActivate(center, radius, power);
    }

    public static boolean isDebugActive() {
        return states.containsKey(DEBUG_UUID);
    }

    public static void debugAddRipple(Vec3d worldPos, float intensity) {
        BarrierState s = states.get(DEBUG_UUID);
        if (s != null) s.addRipple(worldPos, intensity);
    }

    // ── 조회 ──────────────────────────────────────────────────────────────

    public static boolean isActive() { return !states.isEmpty(); }

    public static Collection<BarrierState> getActiveStates() { return states.values(); }

    // ── 틱 처리 ───────────────────────────────────────────────────────────

    public static void tick(MinecraftClient client) {
        if (states.isEmpty()) return;

        List<UUID> toRemove = null;
        for (BarrierState s : states.values()) {
            // 파동 age 진행 (~1초 수명)
            for (int i = 0; i < BarrierState.MAX_RIPPLES; i++) {
                if (s.rippleAge[i] < 1.0f)
                    s.rippleAge[i] = Math.min(1.0f, s.rippleAge[i] + 0.033f);
            }

            // 디버그 배리어: 플레이어 근접 시 밀어내기 + 파동 시뮬레이션
            // (서버 배리어 위치는 GameRendererMixin 에서 매 프레임 보간 갱신)
            if (s.isDebug && client.player != null) {
                Vec3d playerCenter = new Vec3d(
                    client.player.getX(),
                    client.player.getY() + client.player.getHeight() / 2.0,
                    client.player.getZ());
                double dist = playerCenter.distanceTo(s.cachedCenter);
                double adjR = s.radius + client.player.getWidth() / 3.0;

                if (dist < adjR + 0.5 && dist > adjR - 1.5) {
                    Vec3d dir = playerCenter.subtract(s.cachedCenter);
                    Vec3d hDir = dir.lengthSquared() > 0.0001
                        ? dir.normalize() : new Vec3d(1, 0, 0);
                    s.addRipple(s.cachedCenter.add(hDir.multiply(s.radius)),
                        (float) Math.max(0.4, client.player.getVelocity().length() * 2.5));
                }
                if (dist < adjR) {
                    Vec3d pd = playerCenter.subtract(s.cachedCenter);
                    if (pd.length() > 0.01) {
                        double str = 0.25 * (1.0 - dist / adjR);
                        Vec3d n   = pd.normalize();
                        Vec3d vel = client.player.getVelocity();
                        client.player.setVelocity(
                            vel.x + n.x * str,
                            vel.y + n.y * str * 0.3,
                            vel.z + n.z * str);
                    }
                }
            }
        }
    }
}
