package com.justheare.paperjjk_client.shader;

/**
 * 64×64 seamless noise corner 값 테이블 (한 번만 계산).
 *
 * mizushi_dust_storm.fsh 의 vNoise() — hash(vec2) 16회/pixel — 을
 * std140 UBO 조회 (lookupCorner × 4)로 대체한다.
 *
 * 버퍼 레이아웃: vec4 Noise[1024] (std140)
 *   4096 float 값을 vec4 에 4개씩 패킹 → 1024 × 16 bytes = 16384 bytes
 *
 * 타일링: & 63 (2^6 모듈러) → 64 단위로 seamless 반복
 */
public final class NoiseTableManager {
    private NoiseTableManager() {}

    public static final int GRID = 64;

    /** std140 vec4 Noise[1024] 버퍼 크기: 16384 bytes */
    public static final int BUFFER_BYTES = GRID * GRID * 4; // 4096 floats × 4 bytes

    private static final float[] TABLE = buildTable();

    private static float[] buildTable() {
        float[] t = new float[GRID * GRID];
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                t[y * GRID + x] = hash2d(x, y);
            }
        }
        return t;
    }

    /**
     * GLSL hash(vec2 p) 를 정수 좌표로 재현.
     * 셰이더의 vNoise() 코너 값과 동일한 알고리즘을 사용해 일관성 보장.
     */
    private static float hash2d(int xi, int yi) {
        float px = xi * 0.1031f;
        float py = yi * 0.1030f;
        px -= (float) Math.floor(px);
        py -= (float) Math.floor(py);
        float d = px * (py + 19.19f) + py * (px + 19.19f);
        px += d; px -= (float) Math.floor(px);
        py += d; py -= (float) Math.floor(py);
        float v = (px + py) * px;
        return v - (float) Math.floor(v);
    }

    /**
     * 64×64 corner 값을 Std140Builder 에 기록 (vec4 Noise[1024], 총 16384 bytes).
     * 버퍼가 새로 생성될 때만 호출된다.
     */
    public static void writeToBuilder(com.mojang.blaze3d.buffers.Std140Builder b) {
        for (int i = 0; i < GRID * GRID; i += 4) {
            b.putFloat(TABLE[i]);
            b.putFloat(TABLE[i + 1]);
            b.putFloat(TABLE[i + 2]);
            b.putFloat(TABLE[i + 3]);
        }
    }
}
