#version 330

uniform sampler2D InSampler;

// std140: vec2(8) + vec2(8) + float(4)*4 = 32 bytes
layout(std140) uniform KaiSlashConfig {
    vec2  SlashP1;        // 선분 시작점 (texCoord: y=0 바닥, y=1 천장)
    vec2  SlashP2;        // 선분 끝점
    float CoreHalfWidth;  // 검은 코어 반폭 (aspect-corrected UV)
    float BloomWidth;     // 코어 바깥 흰색 bloom 폭
    float Alpha;          // 전체 불투명도 [0, 1]
    float Time;           // 경과 시간(초), 0 ~ 0.2
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    const float aspect = 16.0 / 9.0;
    vec2 scale = vec2(aspect, 1.0);

    vec2 p = texCoord * scale;
    vec2 a = SlashP1   * scale;
    vec2 b = SlashP2   * scale;

    // 선분 분해: along_t (0=P1, 1=P2), perp_dist (코어까지 수직 거리)
    vec2  pa        = p - a;
    vec2  ba        = b - a;
    float along_t   = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    float perp_dist = length(pa - along_t * ba);

    // ── 애니메이션 (총 0.2초) ───────────────────────────────────────────
    // t: 정규화 진행도 [0, 1]
    float t = clamp(Time / 0.2, 0.0, 1.0);

    // headT: 선두가 P1(0) → P2(1) 를 빠르게 횡단 (t=0.6 에서 완료)
    float headT = smoothstep(0.0, 0.6, t);
    // tailT: 꼬리가 뒤따름 (t=0.3 에서 출발, t=1.0 에서 P2 도달)
    float tailT = smoothstep(0.3, 1.0, t);

    // 선두·꼬리 경계에서 부드러운 페이드 (날카로운 끝 방지)
    const float softW = 0.06;
    float headFade = 1.0 - smoothstep(headT - softW, headT, along_t);
    float tailFade = smoothstep(tailT, tailT + softW, along_t);
    float lenFactor = headFade * tailFade;

    // ── 코어 · bloom 계산 (lenFactor 마지막에 적용) ──────────────────────
    float inCore_raw = step(perp_dist, CoreHalfWidth);
    float bloomT     = 1.0 - smoothstep(CoreHalfWidth, CoreHalfWidth + BloomWidth, perp_dist);
    float bloomRaw   = bloomT * bloomT               // 2제곱: 빠른 감쇠
                       * (1.0 - inCore_raw);         // 코어 안에는 bloom 침범 금지

    float inCore    = inCore_raw * lenFactor;
    float bloomFact = bloomRaw   * lenFactor;

    vec4 orig   = texture(InSampler, texCoord);
    vec4 result = orig;

    // 검은 코어 (원본 덮어쓰기)
    result      = mix(result, vec4(0.0, 0.0, 0.0, 1.0), inCore   * Alpha);
    // 흰색 bloom (additive, 코어 바깥)
    result.rgb += vec3(1.0) * bloomFact * Alpha;

    fragColor = result;
}
