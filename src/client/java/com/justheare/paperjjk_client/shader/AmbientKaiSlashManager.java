package com.justheare.paperjjk_client.shader;

import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.UUID;

/**
 * 결없영 배경 참격 효과 매니저.
 *
 * MAX_SLASHES개 참격 인스턴스를 월드 좌표계에서 관리.
 * 각 슬래시는 한 사이클(kai 스타일: 한쪽→반대쪽 스윕)이 끝나면
 * 새로운 랜덤 위치로 이동한다.
 *
 * 서버에서 DOMAIN_VISUAL(MIZUSHI) 패킷을 받으면 setDomain()으로 연동.
 * 반경^1.3에 비례하여 활성 참격 개수 조절.
 */
public class AmbientKaiSlashManager {

    public static final int MAX_SLASHES = 300;

    /** 슬래시 밀도 계수: count = k * radius^1.3, radius=10 → ~64개 */
    private static final float DENSITY_K = 3.2f;

    /** 카메라 기준 슬래시 생성 반경 (블록). 이 범위 내에 집중 배치. */
    private static final float CAM_RADIUS = 15f;

    // ── 도메인 연동 상태 ─────────────────────────────────────────────────────
    private static UUID   activeDomainId = null;
    /** 참격이 생성되는 구의 중심 (월드 좌표). 서버 도메인 중심으로 업데이트됨. */
    public static Vec3d  domainCenter   = new Vec3d(-100.0, 150.0, -50.0);
    /** 서버에서 마지막으로 수신한 반경 (권위값). */
    public static float  domainRadius   = 50.0f;
    /** 보간된 부드러운 반경. 렌더링에 실제로 사용됨. */
    public static float  smoothRadius   = 0f;

    // ── Dead Reckoning 상태 ───────────────────────────────────────────────
    /** EMA 평활된 성장 속도 (blocks/ms). sync마다 순간 속도로 블렌딩. */
    private static float estimatedVelocity = 0f;
    /** 마지막 syncDomainRadius() 수신 시각 (ms). */
    private static long  lastSyncMs        = 0L;

    /** 매 프레임 GameRendererMixin에서 갱신되는 카메라 월드 좌표. */
    public static Vec3d  cameraPos      = Vec3d.ZERO;

    // ── 시각 파라미터 ────────────────────────────────────────────────────────
    public static final float CORE_HALF_WIDTH = 0.002f;
    public static final float BLOOM_WIDTH     = 0.0035f;
    /** 참격 반길이 기본값 (블록). 원근 투영으로 거리 자동 반영. */
    public static final float SLASH_HALF_LEN  = 3.5f;

    // ── 내부 상태 ────────────────────────────────────────────────────────────
    private static boolean active  = false;
    private static long    startMs = 0L;
    private static final Random rand = new Random();

    // ── 참격 인스턴스 ────────────────────────────────────────────────────────
    public static class AmbientSlash {
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

        public AmbientSlash() {
            period = nextPeriod();
            // 랜덤 초기 위상: 사이클 시작 시각을 무작위 오프셋만큼 당김
            long phaseOffsetMs = (long)(rand.nextFloat() * period * 1000L);
            cycleStartMs = System.currentTimeMillis() - phaseOffsetMs;
            randomize();
        }

        /** 위치/방향/길이 랜덤 갱신. 카메라 기준 CAM_RADIUS 내부 AND 도메인 구 내부에 생성. */
        private void randomize() {
            // 카메라의 도메인-로컬 좌표 (도메인 중심을 원점으로)
            float cx = (float)(cameraPos.x - domainCenter.x);
            float cy = (float)(cameraPos.y - domainCenter.y);
            float cz = (float)(cameraPos.z - domainCenter.z);

            float domR  = Math.max(1f, smoothRadius);
            float domR2 = domR * domR;
            // 유효 반경: 도메인이 작으면 도메인 반경, 크면 CAM_RADIUS
            float r  = Math.min(domR, CAM_RADIUS);
            float r2 = r * r;

            // 카메라 기준 구 AND 도메인 구 교집합 내 rejection sampling
            // 최대 30회 시도 후 실패하면 도메인 구 내부로만 fallback
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
                // fallback: 도메인 구 내부에서만 샘플링 (카메라가 도메인 밖에 있는 경우)
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

        private static float nextPeriod() {
            return 0.15f + rand.nextFloat() * 0.15f;
        }

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

        // ── Kai 스타일 애니메이션 (kai_slash.fsh 와 동일 커브) ───────────────

        /** 선두 위치 [0→1]: P1 에서 P2 로 빠르게 이동, t=0.6 에서 완료 */
        public float getHeadT(float localT) {
            return smoothstep(0f, 0.6f, localT);
        }

        /** 꼬리 위치 [0→1]: t=0.3 에서 출발, t=1.0 에서 P2 도달 */
        public float getTailT(float localT) {
            return smoothstep(0.3f, 1.0f, localT);
        }

        /** 전체 불투명도: 양 끝에서 빠르게 fade in/out */
        public float getFade(float localT) {
            return smoothstep(0f, 0.05f, localT)
                 * (1f - smoothstep(0.95f, 1f, localT));
        }

        private static float smoothstep(float e0, float e1, float x) {
            float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
            return t * t * (3f - 2f * t);
        }
    }

    private static final AmbientSlash[] slashes = new AmbientSlash[MAX_SLASHES];

    static {
        for (int i = 0; i < MAX_SLASHES; i++) slashes[i] = new AmbientSlash();
    }

    // ── API ──────────────────────────────────────────────────────────────────

    public static boolean isActive() { return active; }

    public static UUID getActiveDomainId() { return activeDomainId; }

    public static void toggle() {
        active = !active;
        if (active) startMs = System.currentTimeMillis();
    }

    public static void activate() {
        if (!active) { active = true; startMs = System.currentTimeMillis(); }
    }

    public static void deactivate() { active = false; }

    public static AmbientSlash[] getSlashes() { return slashes; }

    // ── 도메인 연동 API ───────────────────────────────────────────────────────

    /**
     * MIZUSHI 도메인 시작 시 호출.
     * 도메인 중심/반경을 설정하고 ambient slash 효과를 활성화한다.
     */
    public static void setDomain(UUID id, Vec3d center, float radius) {
        activeDomainId    = id;
        domainCenter      = center;
        domainRadius      = Math.max(0f, radius);
        // radius > 0 이면 이미 전개된 상태 (debug 커맨드 등) → 즉시 표시
        // radius = 0 이면 서버 sync가 반경을 올려줌
        smoothRadius      = domainRadius;
        estimatedVelocity = 0f;
        // 0L이 아닌 현재 시각으로 초기화 → 첫 sync에서 dt가 올바르게 계산됨
        lastSyncMs        = System.currentTimeMillis();
        activate();
    }

    /**
     * 서버 SYNC 패킷으로 반경 갱신.
     * 순간 속도를 계산해 EMA(α=0.3)로 estimatedVelocity에 블렌딩.
     * 첫 sync(cold start)는 EMA 없이 직접 설정 → 즉각 반응.
     * domainRadius는 천장 역할 — 절대 낮추지 않음.
     */
    public static void syncDomainRadius(UUID id, float newRadius) {
        if (!id.equals(activeDomainId)) return;
        long now = System.currentTimeMillis();
        long dt  = now - lastSyncMs;
        if (dt > 10) {
            float instantV = (newRadius - domainRadius) / (float) dt;
            instantV = Math.max(0f, instantV);
            if (estimatedVelocity < 0.0001f) {
                // Cold start: EMA 수렴 대기 없이 즉시 설정
                estimatedVelocity = instantV;
            } else {
                estimatedVelocity = estimatedVelocity * 0.7f + instantV * 0.3f;
            }
        }
        domainRadius = Math.max(domainRadius, newRadius);
        lastSyncMs   = now;
    }

    /**
     * 매 프레임 호출.
     * estimatedVelocity로 적분하되 domainRadius(서버 천장)를 절대 초과하지 않는다.
     * TPS 드랍 → sync 간격 증가 → instantV 감소 → EMA로 velocity 완만 감소 → 자연스러운 감속.
     */
    public static void updateSmoothedRadius(float dtMs) {
        smoothRadius = Math.min(
            smoothRadius + estimatedVelocity * dtMs,
            domainRadius
        );
    }

    /**
     * 도메인 종료 시 호출 (END/COMPLETE 후 제거).
     */
    public static void clearDomain(UUID id) {
        if (id.equals(activeDomainId)) {
            activeDomainId    = null;
            smoothRadius      = 0f;
            domainRadius      = 0f;
            estimatedVelocity = 0f;
            lastSyncMs        = 0L;
            deactivate();
        }
    }

    /**
     * 현재 반경에 따른 활성 참격 개수. smoothRadius 기준.
     * count = min(MAX_SLASHES, round(DENSITY_K * radius^1.3))
     */
    public static int getActiveSlashCount() {
        float r = Math.min(Math.max(0f, smoothRadius), CAM_RADIUS);
        return Math.min(MAX_SLASHES, Math.max(1, (int)(DENSITY_K * Math.pow(r, 1.3))));
    }
}
