package com.justheare.paperjjk_client.shader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 참격(해) post-processing 효과 매니저.
 *
 * 좌표계: texCoord space (y=0 화면 바닥, y=1 화면 천장)
 *
 * 두 가지 모드:
 *   1. 디버그 정적 슬래시 (/jjkdebug kai) — UV 직접 지정, 루프 반복
 *   2. 서버 전송 인스턴스 — 월드 좌표 + 방향 벡터, 렌더 타임에 투영
 *
 * 애니메이션:
 *   0.0s ~ 0.2s : 선두가 P1→P2 를 빠르게 횡단, 꼬리가 뒤따르다 소멸
 *   (디버그 모드에서는 0.2s ~ 0.5s 정지 후 반복)
 */
public class KaiSlashEffectManager {

    /** 애니메이션 1회 재생 길이 (초) */
    public static final float DURATION = 0.2f;

    /** 디버그 루프 주기 (DURATION + 정지 구간) */
    private static final float LOOP_PERIOD = 0.5f;

    // ── 디버그 단일 슬래시 (기존 /jjkdebug kai 용) ────────────────────────

    private static boolean debugActive = false;
    private static long    debugStartMs = 0L;

    // 대각선 참격: 시각적 좌상("\") → 우하 (texCoord y 높을수록 화면 위)
    private static float p1x = 0.3f,  p1y = 0.65f;
    private static float p2x = 0.7f,  p2y = 0.35f;
    private static float coreHalfWidth = 0.002f;
    private static float bloomWidth    = 0.0035f;
    private static float alpha         = 1.0f;

    public static boolean isDebugActive() { return debugActive; }

    public static void toggleDebug() {
        debugActive = !debugActive;
        if (debugActive) debugStartMs = System.currentTimeMillis();
    }

    /** 디버그 슬래시의 현재 경과 시간(초). LOOP_PERIOD 마다 반복. */
    public static float getDebugTime() {
        if (!debugActive) return 0.0f;
        float elapsed = (System.currentTimeMillis() - debugStartMs) / 1000.0f;
        return elapsed % LOOP_PERIOD;
    }

    public static float getP1x()          { return p1x; }
    public static float getP1y()          { return p1y; }
    public static float getP2x()          { return p2x; }
    public static float getP2y()          { return p2y; }
    public static float getCoreHalfWidth(){ return coreHalfWidth; }
    public static float getBloomWidth()   { return bloomWidth; }
    public static float getAlpha()        { return alpha; }

    // ── 서버 전송 인스턴스 ─────────────────────────────────────────────────

    /**
     * 서버로부터 수신한 참격 한 획.
     * 월드 좌표 + 정규화된 참격 방향 벡터를 저장하고,
     * 렌더 타임에 GameRendererMixin 에서 화면 UV 로 투영한다.
     */
    public static class KaiSlashEffect {
        /** 참격 중심 (월드 좌표) */
        public final float worldX, worldY, worldZ;
        /** 참격 방향 정규화 벡터 */
        public final float axisX, axisY, axisZ;
        /** 생성 시각 */
        public final long startMs;

        public KaiSlashEffect(float wx, float wy, float wz,
                              float ax, float ay, float az) {
            worldX = wx; worldY = wy; worldZ = wz;
            axisX = ax; axisY = ay; axisZ = az;
            startMs = System.currentTimeMillis();
        }

        /** 경과 시간(초) */
        public float getTime() {
            return (System.currentTimeMillis() - startMs) / 1000.0f;
        }

        /** 애니메이션이 끝났으면 true */
        public boolean isDone() {
            return getTime() >= DURATION;
        }
    }

    /** 활성 서버 참격 인스턴스 목록 (클라이언트 메인 스레드에서만 접근) */
    private static final List<KaiSlashEffect> effects = new ArrayList<>();

    /**
     * 서버 패킷 수신 시 호출. 새 참격 인스턴스를 추가한다.
     *
     * @param wx, wy, wz  참격 중심 월드 좌표 (float)
     * @param ax, ay, az  참격 방향 정규화 벡터
     */
    public static void addEffect(float wx, float wy, float wz,
                                 float ax, float ay, float az) {
        effects.add(new KaiSlashEffect(wx, wy, wz, ax, ay, az));
    }

    /**
     * 만료된 인스턴스를 제거하고 활성 목록을 반환한다.
     * GameRendererMixin 의 렌더 루프에서 매 프레임 호출.
     */
    public static List<KaiSlashEffect> getActiveEffects() {
        effects.removeIf(KaiSlashEffect::isDone);
        return Collections.unmodifiableList(effects);
    }

    /** 디버그 슬래시 또는 서버 인스턴스 중 하나라도 활성이면 true */
    public static boolean isAnyActive() {
        if (debugActive) return true;
        return effects.stream().anyMatch(e -> !e.isDone());
    }
}
