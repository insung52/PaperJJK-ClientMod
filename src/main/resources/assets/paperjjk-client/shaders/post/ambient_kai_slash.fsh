#version 330

uniform sampler2D InSampler;

// std140: float*4 header(16) + vec4[256] array(4096) = 4112 bytes
// Slashes[i*2]   = (p1x, p1y, p2x, p2y)  — texCoord UV (y=0 바닥)
// Slashes[i*2+1] = (headT, tailT, fade, 0)
layout(std140) uniform AmbientKaiConfig {
    float SlashCount;    // 활성 참격 수
    float CoreHalfWidth;
    float BloomWidth;
    float _pad;
    vec4  Slashes[256];  // 128슬래시 × 2 vec4
};

in vec2 texCoord;
out vec4 fragColor;

const float ASPECT = 16.0 / 9.0;
const float SOFT_W = 0.06;  // 선두·꼬리 경계 softness (kai_slash 와 동일)

void main() {
    int  n = int(SlashCount);
    vec2 p = texCoord * vec2(ASPECT, 1.0);

    float accumCore  = 0.0;
    float accumBloom = 0.0;

    for (int i = 0; i < n; i++) {
        vec4  ep   = Slashes[i * 2];
        vec4  meta = Slashes[i * 2 + 1];

        float headT = meta.x;
        float tailT = meta.y;
        float fade  = meta.z;

        if (fade < 0.002) continue;

        vec2 a = ep.xy * vec2(ASPECT, 1.0);
        vec2 b = ep.zw * vec2(ASPECT, 1.0);

        // 2D 캡슐 SDF (kai_slash 와 동일)
        vec2  ba      = b - a;
        float denom   = dot(ba, ba);
        if (denom < 1e-6) continue;
        float along_t = clamp(dot(p - a, ba) / denom, 0.0, 1.0);
        float d       = length(p - a - along_t * ba);

        // Kai 스타일 애니메이션: 선두가 P1→P2 빠르게 횡단, 꼬리가 뒤따름
        float headFade  = 1.0 - smoothstep(headT - SOFT_W, headT, along_t);
        float tailFade  = smoothstep(tailT, tailT + SOFT_W, along_t);
        float lenFactor = headFade * tailFade * fade;

        // 코어 + bloom (kai_slash 와 동일 공식)
        float inCore_raw = step(d, CoreHalfWidth);
        float inCore     = inCore_raw * lenFactor;

        float bloomT = 1.0 - smoothstep(CoreHalfWidth, CoreHalfWidth + BloomWidth, d);
        float bloom  = bloomT * bloomT * (1.0 - inCore_raw) * lenFactor;

        accumCore  = max(accumCore, inCore);
        accumBloom += bloom;
    }

    vec4 orig   = texture(InSampler, texCoord);
    vec4 result = orig;
    result      = mix(result, vec4(0.0, 0.0, 0.0, 1.0), clamp(accumCore, 0.0, 1.0));
    result.rgb += vec3(1.0) * accumBloom;

    fragColor = result;
}
