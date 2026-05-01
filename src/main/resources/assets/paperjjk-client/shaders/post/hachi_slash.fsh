#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

#define MAX_HACHI 32

// std140:
//   vec4 Params0[32]  →  512 bytes  (xy=center, z=angle, w=skewAngle)
//   vec4 Params1[32]  →  512 bytes  (x=time, y=distScale, z=depth, w=unused)
//   float Count       →   16 bytes  (+ 12 padding to 16-byte boundary)
//   Total             = 1040 bytes
layout(std140) uniform HachiSlashConfig {
    vec4  Params0[MAX_HACHI];
    vec4  Params1[MAX_HACHI];
    float Count;
};

in vec2 texCoord;
out vec4 fragColor;

const float ASPECT          = 16.0 / 9.0;
const float CORE_HALF_W     = 0.0012;
const float BLOOM_WIDTH     = 0.002;
const float SPACING         = 0.038;
const float BASE_HALF_LEN   = 0.10;
const float TIME_OFFSET_MAX = 0.15;
const float LINE_ANIM_DUR   = 0.20;

float hash(float n) {
    return fract(sin(n * 127.1 + 311.7) * 43758.5453);
}

struct LineResult {
    float d;
    float fade;
};

// 평행선 집합 SDF + 애니메이션
// time 을 파라미터로 받아 인스턴스별 독립 시간 사용
LineResult parallelLines(vec2 rot, float setId, float time) {
    float tileIdx = floor((rot.y + SPACING * 0.5) / SPACING);
    float phase   = mod(rot.y + SPACING * 0.5, SPACING) - SPACING * 0.5;

    if (tileIdx < -2.0 || tileIdx > 3.0) {
        LineResult r; r.d = 1.0; r.fade = 0.0; return r;
    }

    float rndLen  = hash(tileIdx * 3.7  + setId * 137.1);
    float rndTime = hash(tileIdx * 5.3  + setId * 91.7);

    float halfLen    = BASE_HALF_LEN * (1.5 + rndLen * 2.5);
    float timeOffset = rndTime * TIME_OFFSET_MAX;

    float localT = clamp((time - timeOffset) / LINE_ANIM_DUR, 0.0, 1.0);

    float currentLen = smoothstep(0.0, 0.45, localT) * halfLen;
    float innerLen   = smoothstep(0.55, 1.0, localT) * halfLen;

    float paraOver = max(0.0, abs(rot.x) - currentLen);
    float paraIn   = max(0.0, innerLen   - abs(rot.x));
    float d = length(vec2(abs(phase), max(paraOver, paraIn)));

    float fade = smoothstep(0.0, 0.1, localT) * (1.0 - smoothstep(0.9, 1.0, localT));

    LineResult r;
    r.d    = d;
    r.fade = fade;
    return r;
}

void main() {
    vec4  result     = texture(InSampler, texCoord);
    float worldDepth = texture(DepthSampler, texCoord).r; // 프레임당 1회만 샘플링

    int n = int(Count);
    for (int i = 0; i < n; i++) {
        vec2  center    = Params0[i].xy;
        float angle     = Params0[i].z;
        float skewAngle = Params0[i].w;
        float time      = Params1[i].x;
        float distScale = Params1[i].y;
        float depth     = Params1[i].z;

        // 이펙트 중심이 geometry 뒤에 있으면 스킵
        if (depth > worldDepth) continue;

        // aspect 보정 + 거리 스케일
        vec2 rel = (texCoord - center) * vec2(ASPECT, 1.0) / max(distScale, 0.05);

        // 선 집합 A (Angle)
        float cosA = cos(angle), sinA = sin(angle);
        vec2 rotA = vec2( rel.x * cosA + rel.y * sinA,
                         -rel.x * sinA + rel.y * cosA);
        LineResult lrA = parallelLines(rotA, 0.0, time);

        // 선 집합 B (SkewAngle)
        float cosB = cos(skewAngle), sinB = sin(skewAngle);
        vec2 rotB = vec2( rel.x * cosB + rel.y * sinB,
                         -rel.x * sinB + rel.y * cosB);
        LineResult lrB = parallelLines(rotB, 1.0, time);

        // 집합 A
        float inCoreA_raw = step(lrA.d, CORE_HALF_W);
        float bloomTA     = 1.0 - smoothstep(CORE_HALF_W, CORE_HALF_W + BLOOM_WIDTH, lrA.d);
        float bloomA      = bloomTA * bloomTA * (1.0 - inCoreA_raw) * lrA.fade;
        float inCoreA     = inCoreA_raw * lrA.fade;

        // 집합 B
        float inCoreB_raw = step(lrB.d, CORE_HALF_W);
        float bloomTB     = 1.0 - smoothstep(CORE_HALF_W, CORE_HALF_W + BLOOM_WIDTH, lrB.d);
        float bloomB      = bloomTB * bloomTB * (1.0 - inCoreB_raw) * lrB.fade;
        float inCoreB     = inCoreB_raw * lrB.fade;

        float inCore    = max(inCoreA, inCoreB);
        float bloomFact = bloomA + bloomB;

        result = mix(result, vec4(0.0, 0.0, 0.0, 1.0), inCore);
        result.rgb += vec3(1.0) * bloomFact;
    }

    fragColor = result;
}
