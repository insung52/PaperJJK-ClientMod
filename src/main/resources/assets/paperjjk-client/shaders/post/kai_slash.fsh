#version 330

uniform sampler2D InSampler;

// std140: vec2(8) + vec2(8) + float(4)*4 = 32 bytes
layout(std140) uniform KaiSlashConfig {
    vec2  SlashP1;        // 선분 시작점 (texCoord space: y=0 바닥, y=1 천장)
    vec2  SlashP2;        // 선분 끝점
    float CoreHalfWidth;  // 검은 중심 반폭 (aspect-corrected UV 단위)
    float BloomWidth;     // 코어 바깥 흰색 bloom 폭
    float Alpha;          // 전체 불투명도 [0, 1]
    float _pad;
};

in vec2 texCoord;
out vec4 fragColor;

// 점 p에서 선분 [a, b] 까지의 최단 거리
float segmentDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

void main() {
    // aspect 보정: 픽셀이 정사각형처럼 보이도록 x를 늘림
    const float aspect = 16.0 / 9.0;
    vec2 scale = vec2(aspect, 1.0);

    vec2 p = texCoord  * scale;
    vec2 a = SlashP1   * scale;
    vec2 b = SlashP2   * scale;

    float d = segmentDist(p, a, b);

    // ── 검은 코어 (d ≤ CoreHalfWidth) ─────────────────────────────────
    float inCore = step(d, CoreHalfWidth);

    // ── 흰색 bloom (코어 바깥만, 절대 코어 안으로 침범하지 않음) ────────
    float bloomFactor = (1.0 - smoothstep(CoreHalfWidth, CoreHalfWidth + BloomWidth, d))
                        * (1.0 - inCore);

    vec4 orig   = texture(InSampler, texCoord);
    vec4 result = orig;

    // 검은 코어: 원본 덮어쓰기
    result      = mix(result, vec4(0.0, 0.0, 0.0, 1.0), inCore * Alpha);

    // 흰색 bloom: 코어 바깥에 덧칠 (additive)
    result.rgb += vec3(1.0) * bloomFactor * Alpha;

    fragColor = result;
}
