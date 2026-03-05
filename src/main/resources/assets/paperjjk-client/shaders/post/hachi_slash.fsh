#version 330

uniform sampler2D InSampler;

// std140: vec2(8) + float(4)*4 = 24 bytes
layout(std140) uniform HachiSlashConfig {
    vec2  Center;    // 화면 UV 중심 (y=0 바닥)
    float Angle;     // 선 집합 A 각도 (라디안)
    float Time;      // 경과 시간 (초), 0 ~ ~0.35
    float SkewAngle; // 선 집합 B 각도 (Angle + 70~110°)
    float DistScale; // 거리 스케일 (1.0 = 3블록, 감소 → 더 먼 거리)
};

in vec2 texCoord;
out vec4 fragColor;

const float ASPECT          = 16.0 / 9.0;
const float CORE_HALF_W     = 0.0012;
const float BLOOM_WIDTH     = 0.004;
const float SPACING         = 0.038;
const float BASE_HALF_LEN   = 0.10;
const float TIME_OFFSET_MAX = 0.15; // 선별 최대 시간차 (초)
const float LINE_ANIM_DUR   = 0.20; // 선 하나의 애니메이션 총 길이 (초)

float hash(float n) {
    return fract(sin(n * 127.1 + 311.7) * 43758.5453);
}

struct LineResult {
    float d;
    float fade;
};

// 평행선 집합 SDF + 애니메이션
// rot   : 회전된 rel 좌표
// setId : 집합 구분값 (해시 다양화)
LineResult parallelLines(vec2 rot, float setId) {
    // Y 방향으로 SPACING 마다 타일링 → 타일 인덱스
    float tileIdx = floor((rot.y + SPACING * 0.5) / SPACING);
    float phase   = mod(rot.y + SPACING * 0.5, SPACING) - SPACING * 0.5;

    // 격자 범위 제한: set 당 6개 선 (-2 ~ 3 타일)
    if (tileIdx < -2.0 || tileIdx > 3.0) {
        LineResult r; r.d = 1.0; r.fade = 0.0; return r;
    }

    // 타일별 랜덤: 길이 배율(1.0~1.5x), 시간 지연(0~0.15s)
    float rndLen  = hash(tileIdx * 3.7  + setId * 137.1);
    float rndTime = hash(tileIdx * 5.3  + setId * 91.7);

    float halfLen    = BASE_HALF_LEN * (1.0 + rndLen * 0.5);
    float timeOffset = rndTime * TIME_OFFSET_MAX;

    // 선 자체의 정규화 시간 (0 ~ 1, LINE_ANIM_DUR 기준)
    float localT = clamp((Time - timeOffset) / LINE_ANIM_DUR, 0.0, 1.0);

    // 길이: 삼각형 커브 — 중심에서 퍼지고 → 중심으로 수렴
    float lenFrac    = smoothstep(0.0, 0.45, localT) * (1.0 - smoothstep(0.55, 1.0, localT));
    float currentLen = lenFrac * halfLen;

    // 캡슐 SDF
    float paraOver = max(0.0, abs(rot.x) - currentLen);
    float d = length(vec2(abs(phase), paraOver));

    // 불투명도 (길이와 함께 fade in/out)
    float fade = smoothstep(0.0, 0.1, localT) * (1.0 - smoothstep(0.9, 1.0, localT));

    LineResult r;
    r.d    = d;
    r.fade = fade;
    return r;
}

void main() {
    // aspect 보정 + 거리 스케일
    // DistScale < 1 → rel 값이 커짐 → 이펙트 화면상 축소
    vec2 rel = (texCoord - Center) * vec2(ASPECT, 1.0) / max(DistScale, 0.05);

    // 선 집합 A (Angle)
    float cosA = cos(Angle), sinA = sin(Angle);
    vec2 rotA = vec2( rel.x * cosA + rel.y * sinA,
                     -rel.x * sinA + rel.y * cosA);
    LineResult lrA = parallelLines(rotA, 0.0);

    // 선 집합 B (SkewAngle — 70~110° 비틀림)
    float cosB = cos(SkewAngle), sinB = sin(SkewAngle);
    vec2 rotB = vec2( rel.x * cosB + rel.y * sinB,
                     -rel.x * sinB + rel.y * cosB);
    LineResult lrB = parallelLines(rotB, 1.0);

    // 두 집합 합성: 가까운 선 기준
    float d, fade;
    if (lrA.d < lrB.d) {
        d = lrA.d; fade = lrA.fade;
    } else {
        d = lrB.d; fade = lrB.fade;
    }

    // 코어 (검정)
    float inCore_raw = step(d, CORE_HALF_W);
    float inCore     = inCore_raw * fade;

    // bloom (흰색, 코어 침범 금지)
    float bloomRaw  = (1.0 - smoothstep(CORE_HALF_W, CORE_HALF_W + BLOOM_WIDTH, d))
                    * (1.0 - inCore_raw);
    float bloomFact = bloomRaw * fade;

    vec4 orig   = texture(InSampler, texCoord);
    vec4 result = orig;
    result      = mix(result, vec4(0.0, 0.0, 0.0, 1.0), inCore);
    result.rgb += vec3(1.0) * bloomFact;

    fragColor = result;
}
