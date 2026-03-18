#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform ChargeConfig {
    vec4 InvViewProjC0;
    vec4 InvViewProjC1;
    vec4 InvViewProjC2;
    vec4 InvViewProjC3;
    // CasterHeadRel: xyz = camera-relative head position, w = progress (0→1)
    vec4 CasterHeadRel;
    // ScreenParams: xy = head screen UV (y=0 bottom), z = head NDC depth, w = time (s, wall clock mod 100s)
    vec4 ScreenParams;
};

in vec2 texCoord;
out vec4 fragColor;

const float PI             = 3.14159265359;
const float N_SPIKES       = 24.0;
const float MAX_SPHERE_RAD = 10.0; // blocks

float hash(vec2 p) {
    p = fract(p * vec2(0.1031, 0.1030));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

void main() {
    float progress = CasterHeadRel.w;
    float time     = ScreenParams.w;

    if (progress <= 0.0 || progress >= 1.0) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 구 어두움 타이밍
    //   0.00→0.25  (0→0.5s)   안 보임
    //   0.25→0.75  (0.5→1.5s) 팽창 (97% 불투명)
    //   0.75→1.00  (1.5→2.0s) 점차 투명
    // ──────────────────────────────────────────────────────────────────────────
    float sphereFadeIn  = smoothstep(0.25, 0.32, progress);
    float sphereFadeOut = 1.0 - smoothstep(0.75, 1.0, progress);
    float sphereEnv     = sphereFadeIn * sphereFadeOut;

    float sphereGrow   = clamp((progress - 0.25) / 0.50, 0.0, 1.0);
    float sphereRadius = sphereGrow * MAX_SPHERE_RAD;

    // ── 깊이 / 월드 방향 복원 ──────────────────────────────────────────────────
    float depth = texture(DepthSampler, texCoord).r;
    bool  isSky = depth >= 0.9999;

    vec4 ndc   = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    mat4 invVP = mat4(InvViewProjC0, InvViewProjC1, InvViewProjC2, InvViewProjC3);
    vec4 wp4   = invVP * ndc;
    vec3 worldPos = wp4.xyz / wp4.w;

    vec3  rd      = normalize(worldPos);
    float pixDist = isSky ? 1e10 : length(worldPos);

    // ── Ray-Sphere 교차 (구 어두움, 블록 + 하늘 모두) ─────────────────────────
    vec3  headPos = CasterHeadRel.xyz;
    float darkness = 0.0;

    if (sphereRadius > 0.05 && sphereEnv > 0.001) {
        vec3  oc   = -headPos;
        float bv   = dot(oc, rd);
        float cv   = dot(oc, oc) - sphereRadius * sphereRadius;
        float disc = bv * bv - cv;

        if (disc >= 0.0) {
            float sq     = sqrt(disc);
            float tEntry = max(0.0, -bv - sq);
            float tExit  = -bv + sq;

            if (tExit > 0.0 && tEntry < pixDist) {
                float insideLen   = max(0.0, min(pixDist, tExit) - tEntry);
                float thickFactor = clamp(insideLen / (sphereRadius * 1.5), 0.0, 1.0);
                darkness = 0.97 * sphereEnv * thickFactor;
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 방사형 침 무늬 (스크린 스페이스)
    //
    //   불투명도 타이밍:
    //     0.00→0.10  (0→0.2s)   서서히 나타남 (0→95%)
    //     0.10→0.25  (0.2→0.5s) 최대 불투명도 95% 유지
    //     0.25→1.00  (0.5→2.0s) 서서히 사라짐 (95%→0%)
    //
    //   색상: 0→0.25 동안 빨간색 → 검은색 (progress 0에서 빨간, 0.25에서 검은)
    //   크기: progress 에 따라 점점 커짐 (구 팽창과 어우러지도록)
    //   두께: 처음 얇은 선 → 끝에 두꺼운 면
    //
    //   회전 버그 수정: time * rotationRate(progress) 는 미분 값이 예측 불가 →
    //   상수 회전 속도로 변경 (time * CONST) 하여 항상 일정하게 회전
    // ──────────────────────────────────────────────────────────────────────────
    vec2  headUV       = ScreenParams.xy;
    float spikeIntensity = 0.0;
    vec3  spikeColor     = vec3(0.0);

    if (headUV.x >= 0.0) {
        const float ASPECT = 16.0 / 9.0;

        vec2  fromHead     = texCoord - headUV;
        fromHead.x        *= ASPECT;
        float distFromHead  = length(fromHead);
        float angle         = atan(fromHead.y, fromHead.x); // -PI..PI

        // ── 회전 (상수 속도 — 버그 수정) ──────────────────────────────────────
        const float ROTATION_SPEED = 5.0; // rad/s
        float rotation = time * ROTATION_SPEED;

        // ── 침 패턴 ──────────────────────────────────────────────────────────
        // 각 침의 고유 인덱스 산출 (회전과 함께 이동하는 정수 ID)
        float spikePhaseRaw = (N_SPIKES * 0.5 * angle + rotation) / PI;
        float spikeId       = floor(spikePhaseRaw);
        float rawSpike      = abs(sin(spikePhaseRaw * PI));

        // 두께: 0.1s(progress 0.05)부터 성장 시작
        float thickT    = smoothstep(0.05, 1.0, progress);
        float sharpness = mix(40.0, 2.0, thickT);
        float spike     = pow(rawSpike, sharpness);

        // 파도: 경계면 물결
        float wave = 0.5 + 0.5 * sin(distFromHead * 40.0 - time * 1.5 + progress * 8.0);
        spike *= (0.65 + 0.35 * wave);

        // ── 침 길이 랜덤화 (각 침마다 고유, 최대 3배 차이) ─────────────────
        float lengthMult = 0.33 + 0.67 * hash(vec2(mod(spikeId, N_SPIKES), 13.7));

        // ── 크기: 0.1s(progress 0.05)부터 2.0s까지 1x→20x(2000%) 성장 ────
        float sizeScale = 1.0 + 19.0 * smoothstep(0.05, 1.0, progress);

        // 거리 기반 스크린 반경 (1블럭 앞 기준)
        float headDist       = max(0.3, length(headPos));
        float spikeScreenRad = 0.30 / headDist * sizeScale;

        // normDist: 0=머리, 1=기본 침 끝 (lengthMult 로 개별 길이 조절)
        float normDist  = distFromHead / max(0.001, spikeScreenRad * lengthMult);

        // 방사 엔벨로프: ~70% 지점 최대, 안쪽/바깥쪽 감쇠
        float radialEnv = exp(-pow(normDist - 0.7, 2.0) * 14.0);
        float innerFade = smoothstep(0.0, 0.15, normDist);

        // ── 침 불투명도 타이밍 ────────────────────────────────────────────────
        float spikeFadeOut = 1.0 - smoothstep(0.25, 1.00, progress); // 0.5s→2s 서서히 소멸
        float spikeEnv     = 0.95 * spikeFadeOut;

        spikeIntensity = spike * radialEnv * innerFade * spikeEnv;

        // ── Depth occlusion: 머리보다 앞에 있는 블럭이 침 무늬를 가림 ─────────
        // ScreenParams.z = 시전자 머리의 NDC depth (0=near, 1=far)
        // 현재 픽셀의 depth < 머리 depth → 해당 픽셀에 블럭이 있음 → 가림
        float headNdcDepth = ScreenParams.z;
        if (headNdcDepth >= 0.0 && depth < headNdcDepth - 0.0002) {
            spikeIntensity = 0.0;
        }

        // ── 색상: progress 0→0.2 빨간색, 0.2→0.5 점진적으로 검은색 ──────────
        float colorT = smoothstep(0.2, 0.5, progress);
        spikeColor   = mix(vec3(1.0, 0.02, 0.02), vec3(0.0, 0.0, 0.0), colorT);
    }

    // ── 합성 ──────────────────────────────────────────────────────────────────
    vec4 orig   = texture(InSampler, texCoord);
    vec3 result = orig.rgb;

    // 1. 구 어두움 (97% 불투명 검은색, 블록 + 하늘 픽셀 모두)
    result = mix(result, vec3(0.0), darkness);

    // 2. 방사형 침 무늬
    result = mix(result, spikeColor, clamp(spikeIntensity, 0.0, 1.0));

    fragColor = vec4(result, orig.a);
}
