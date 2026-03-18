package com.justheare.paperjjk_client.shader;

import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * 결없영 배경 참격 효과 매니저 — 다중 인스턴스 지원.
 *
 * 각 활성 도메인은 DomainInstance로 관리된다.
 * 매 프레임 updateAll(cam, dtMs)을 한 번 호출한 뒤
 * getActiveDomains()로 순회하여 렌더링한다.
 *
 * 생명주기:
 *   setDomain()         → 도메인 생성 또는 재동기화
 *   syncDomainRadius()  → 반경 갱신 (SYNC 패킷)
 *   startFadeOut()      → 1초 페이드 아웃 시작 (END 패킷)
 *   clearAll()          → 즉시 전체 제거 (연결 해제)
 *
 * 자동 타임아웃: 10초 이상 SYNC가 오지 않으면 자동 페이드 아웃.
 */
public class AmbientKaiSlashManager {

    public static final int MAX_SLASHES = 300;

    /** 슬래시 밀도 계수: count = k * radius^1.3, radius=10 → ~64개 */
    private static final float DENSITY_K = 3.2f;

    /** 카메라 기준 슬래시 생성 반경 (블록). */
    private static final float CAM_RADIUS = 15f;

    /** SYNC 없이 이 시간(ms)이 지나면 자동 페이드 아웃. */
    private static final long TIMEOUT_MS = 10_000L;

    /** 페이드 아웃 지속 시간 (ms). */
    private static final long FADE_DURATION_MS = 1_000L;

    // ── 시각 파라미터 (렌더러에서 참조) ─────────────────────────────────────────
    public static final float CORE_HALF_WIDTH = 0.002f;
    public static final float BLOOM_WIDTH     = 0.0035f;
    public static final float SLASH_HALF_LEN  = 3.5f;

    /** 매 프레임 GameRendererMixin에서 갱신. 모든 인스턴스가 공유. */
    public static Vec3d cameraPos = Vec3d.ZERO;

    private static final Random rand = new Random();

    /** 활성 도메인 맵. 삽입 순서 유지 (LinkedHashMap). */
    private static final Map<UUID, DomainInstance> domains = new LinkedHashMap<>();

    // ── DomainInstance ────────────────────────────────────────────────────────

    public static class DomainInstance {
        public final UUID   id;
        public       Vec3d  center;
        public       float  serverRadius;
        public       float  smoothRadius;
        private      float  estimatedVelocity;
        private      long   lastSyncMs;
        private      long   fadeStartMs = 0L; // 0 = 페이드 중 아님

        public final AmbientSlash[] slashes = new AmbientSlash[MAX_SLASHES];

        DomainInstance(UUID id, Vec3d center, float radius) {
            this.id           = id;
            this.center       = center;
            this.serverRadius = Math.max(0f, radius);
            // smoothRadius는 항상 0에서 시작 (즉시 점프 없음).
            // 복구 케이스(radius>0)는 estimatedVelocity로 ~1초 내 추격.
            this.smoothRadius      = 0f;
            this.estimatedVelocity = radius > 0f ? radius / 1000f : 0f;
            this.lastSyncMs        = System.currentTimeMillis();
            for (int i = 0; i < MAX_SLASHES; i++) slashes[i] = new AmbientSlash(this);
        }

        public boolean isFading()       { return fadeStartMs > 0L; }
        public boolean isFadeComplete() { return isFading() && System.currentTimeMillis() - fadeStartMs >= FADE_DURATION_MS; }

        public boolean isTimedOut() {
            return !isFading() && lastSyncMs > 0L && System.currentTimeMillis() - lastSyncMs > TIMEOUT_MS;
        }

        public void startFadeOut() {
            if (fadeStartMs == 0L) fadeStartMs = System.currentTimeMillis();
        }

        public void syncRadius(float newRadius) {
            long now = System.currentTimeMillis();
            long dt  = now - lastSyncMs;
            if (dt > 10) {
                float instantV = (newRadius - serverRadius) / (float) dt;
                instantV = Math.max(0f, instantV);
                if (estimatedVelocity < 0.0001f) {
                    estimatedVelocity = instantV;
                } else {
                    estimatedVelocity = estimatedVelocity * 0.7f + instantV * 0.3f;
                }
            }
            serverRadius = Math.max(serverRadius, newRadius);
            lastSyncMs   = now;
            fadeStartMs  = 0L; // SYNC를 받으면 페이드 취소
        }

        /** 페이드 투명도 [1→0]. 페이드 중이 아닐 때는 1. */
        public float getFadeAlpha() {
            if (!isFading()) return 1f;
            long elapsed = System.currentTimeMillis() - fadeStartMs;
            return 1f - Math.min(1f, elapsed / (float) FADE_DURATION_MS);
        }

        public void updateSmoothedRadius(float dtMs) {
            if (!isFading()) {
                // 반경은 페이드 중에 변경하지 않음 — 투명도만 감소
                smoothRadius = Math.min(smoothRadius + estimatedVelocity * dtMs, serverRadius);
            }
        }

        public int getActiveSlashCount() {
            if (slashSuppressed) return 0;
            float r = Math.min(Math.max(0f, smoothRadius), CAM_RADIUS);
            return Math.min(MAX_SLASHES, Math.max(1, (int)(DENSITY_K * Math.pow(r, 1.3))));
        }
    }

    // ── AmbientSlash ──────────────────────────────────────────────────────────

    public static class AmbientSlash {
        private final DomainInstance domain;

        /** 구 중심 기준 위치 (블록) — 사이클마다 갱신 */
        public float sx, sy, sz;
        /** 방향 단위벡터 — 사이클마다 갱신 */
        public float dx, dy, dz;
        /** 반길이 (블록) — 사이클마다 갱신 */
        public float halfLen;

        /** 현재 사이클 주기 (초) */
        private float period;
        /** 현재 사이클 시작 시각 (ms) */
        private long cycleStartMs;

        AmbientSlash(DomainInstance domain) {
            this.domain = domain;
            period = nextPeriod();
            long phaseOffsetMs = (long)(rand.nextFloat() * period * 1000L);
            cycleStartMs = System.currentTimeMillis() - phaseOffsetMs;
            randomize();
        }

        private void randomize() {
            float cx = (float)(cameraPos.x - domain.center.x);
            float cy = (float)(cameraPos.y - domain.center.y);
            float cz = (float)(cameraPos.z - domain.center.z);

            float domR  = Math.max(1f, domain.smoothRadius);
            float domR2 = domR * domR;
            float r     = Math.min(domR, CAM_RADIUS);
            float r2    = r * r;

            float x = 0, y = 0, z = 0;
            boolean found = false;
            for (int tries = 0; tries < 30; tries++) {
                x = cx + (rand.nextFloat() * 2f - 1f) * r;
                y = cy + (rand.nextFloat() * 2f - 1f) * r;
                z = cz + (rand.nextFloat() * 2f - 1f) * r;
                float lx = x - cx, ly = y - cy, lz = z - cz;
                if (lx*lx + ly*ly + lz*lz <= r2 && x*x + y*y + z*z <= domR2) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                do {
                    x = (rand.nextFloat() * 2f - 1f) * domR;
                    y = (rand.nextFloat() * 2f - 1f) * domR;
                    z = (rand.nextFloat() * 2f - 1f) * domR;
                } while (x*x + y*y + z*z > domR2);
            }
            sx = x; sy = y; sz = z;

            float az  = rand.nextFloat() * 6.2832f;
            float sel = rand.nextFloat() * 2f - 1f;
            float cel = (float) Math.sqrt(Math.max(0.0, 1.0 - sel * sel));
            dx = cel * (float) Math.cos(az);
            dy = sel;
            dz = cel * (float) Math.sin(az);

            halfLen = SLASH_HALF_LEN * (1.0f + rand.nextFloat() * 0.5f);
        }

        private static float nextPeriod() { return 0.15f + rand.nextFloat() * 0.15f; }

        /**
         * localT [0, 1] — 현재 사이클 진행도.
         * 사이클 종료 시 자동으로 새 위치로 갱신된다.
         */
        public float getLocalT() {
            float elapsed = (System.currentTimeMillis() - cycleStartMs) / 1000f;
            if (elapsed >= period) {
                period       = nextPeriod();
                cycleStartMs = System.currentTimeMillis();
                randomize();
                elapsed = 0f;
            }
            return elapsed / period;
        }

        public float getHeadT(float localT) { return smoothstep(0f, 0.6f, localT); }
        public float getTailT(float localT)  { return smoothstep(0.3f, 1.0f, localT); }
        public float getFade(float localT) {
            return smoothstep(0f, 0.05f, localT) * (1f - smoothstep(0.95f, 1f, localT));
        }

        private static float smoothstep(float e0, float e1, float x) {
            float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
            return t * t * (3f - 2f * t);
        }
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /** true이면 참격 렌더링 억제 (fuga 충전 중). */
    private static boolean slashSuppressed = false;

    public static void setSlashSuppressed(boolean suppressed) { slashSuppressed = suppressed; }
    public static boolean isSlashSuppressed()                 { return slashSuppressed; }

    /** 모든 활성 도메인에 대해 페이드 아웃 시작 (열압력탄 폭발 시 사용). */
    public static void startFadeOutAll() {
        domains.values().forEach(DomainInstance::startFadeOut);
        slashSuppressed = false; // 억제 해제 (도메인이 사라지므로 불필요)
    }

    public static boolean hasActiveDomains()                   { return !domains.isEmpty(); }
    public static Collection<DomainInstance> getActiveDomains() { return domains.values(); }

    /**
     * START 패킷 수신 시 호출. 도메인이 없거나 페이드 중이면 새로 생성, 활성 중이면 반경 동기화.
     *
     * @param radius 현재 서버 반경 (초기 START 시 0, 주기적 재전송 시 현재 반경)
     */
    public static void setDomain(UUID id, Vec3d center, float radius) {
        DomainInstance existing = domains.get(id);
        if (existing != null && !existing.isFading()) {
            existing.syncRadius(radius);
        } else {
            // 없거나 페이드 중(이전 도메인 종료 애니메이션 중) → 새 인스턴스 생성
            domains.put(id, new DomainInstance(id, center, radius));
        }
    }

    /** SYNC 패킷 수신 시 호출. */
    public static void syncDomainRadius(UUID id, float newRadius) {
        DomainInstance d = domains.get(id);
        if (d != null) d.syncRadius(newRadius);
    }

    /** END 패킷 수신 시 호출 — 1초 페이드 아웃 시작. */
    public static void startFadeOut(UUID id) {
        DomainInstance d = domains.get(id);
        if (d != null) d.startFadeOut();
    }

    /** 연결 해제 / 월드 전환 시 모든 효과를 즉시 제거. */
    public static void clearAll() { domains.clear(); }

    /**
     * 매 프레임 GameRendererMixin에서 한 번 호출.
     * 카메라 위치 갱신, 반경 보간, 타임아웃/페이드 완료 제거.
     */
    public static void updateAll(Vec3d cam, float dtMs) {
        cameraPos = cam;
        Iterator<Map.Entry<UUID, DomainInstance>> it = domains.entrySet().iterator();
        while (it.hasNext()) {
            DomainInstance d = it.next().getValue();
            if (d.isTimedOut()) d.startFadeOut();
            d.updateSmoothedRadius(dtMs);
            if (d.isFadeComplete()) it.remove();
        }
    }
}
