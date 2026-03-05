package com.justheare.paperjjk_client.shader;

import java.util.Random;

/**
 * 결없영 배경 참격 효과 매니저.
 *
 * MAX_SLASHES개 참격 인스턴스를 월드 좌표계에서 관리.
 * 각 슬래시는 한 사이클(kai 스타일: 한쪽→반대쪽 스윕)이 끝나면
 * 새로운 랜덤 위치로 이동한다.
 */
public class AmbientKaiSlashManager {

    public static final int MAX_SLASHES = 128;

    // ── 구 정의 (월드 좌표) ──────────────────────────────────────────────────
    public static double sphereX      = -100.0;
    public static double sphereY      =  150.0;
    public static double sphereZ      =  -50.0;
    public static float  sphereRadius =   50.0f;

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

        /** 위치/방향/길이 랜덤 갱신 */
        private void randomize() {
            float x, y, z;
            float r2 = sphereRadius * sphereRadius;
            do {
                x = (rand.nextFloat() * 2f - 1f) * sphereRadius;
                y = (rand.nextFloat() * 2f - 1f) * sphereRadius;
                z = (rand.nextFloat() * 2f - 1f) * sphereRadius;
            } while (x * x + y * y + z * z > r2);
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
            return 0.30f + rand.nextFloat() * 0.30f;
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

    public static void toggle() {
        active = !active;
        if (active) startMs = System.currentTimeMillis();
    }

    public static void activate() {
        if (!active) { active = true; startMs = System.currentTimeMillis(); }
    }

    public static void deactivate() { active = false; }

    public static AmbientSlash[] getSlashes() { return slashes; }
}
