package com.justheare.paperjjk_client.shader;

/**
 * 참격(해) post-processing 효과 매니저.
 *
 * 좌표계: texCoord space (y=0 화면 바닥, y=1 화면 천장)
 *   → 시각적 상단 = y 높은 값, 시각적 우측 = x 높은 값
 *
 * 현재는 단일 정적 슬래시(debug용)만 지원.
 * 이후 인스턴스 리스트 + 시간 기반 애니메이션으로 확장 예정.
 */
public class KaiSlashEffectManager {

    private static boolean debugActive = false;

    // 대각선 참격: 시각적 좌상 → 우하 ("\" 방향)
    // texCoord: P1=(0.3, 0.65) = 시각 좌상, P2=(0.7, 0.35) = 시각 우하
    private static float p1x = 0.3f,  p1y = 0.65f;
    private static float p2x = 0.7f,  p2y = 0.35f;
    private static float coreHalfWidth = 0.004f;
    private static float bloomWidth    = 0.015f;
    private static float alpha         = 1.0f;

    public static boolean isDebugActive()  { return debugActive; }

    public static void toggleDebug() {
        debugActive = !debugActive;
    }

    public static float getP1x()          { return p1x; }
    public static float getP1y()          { return p1y; }
    public static float getP2x()          { return p2x; }
    public static float getP2y()          { return p2y; }
    public static float getCoreHalfWidth(){ return coreHalfWidth; }
    public static float getBloomWidth()   { return bloomWidth; }
    public static float getAlpha()        { return alpha; }
}
