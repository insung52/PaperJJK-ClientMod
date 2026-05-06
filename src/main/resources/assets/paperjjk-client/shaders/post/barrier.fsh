#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform BarrierConfig {
    vec4 InvViewProjC0, InvViewProjC1, InvViewProjC2, InvViewProjC3;
    vec4 BarrierCenterRadius;
    vec4 BarrierControl;
    vec4 Ripple0, Ripple1, Ripple2, Ripple3;
    vec4 Ripple4, Ripple5, Ripple6, Ripple7;
    vec4 RippleIntensityA;
    vec4 RippleIntensityB;
};

in vec2 texCoord;
out vec4 fragColor;

const vec3  BARRIER_COLOR = vec3(0.35, 0.82, 1.0);
const float PI            = 3.14159265;

void main() {
    vec4  scene = texture(InSampler, texCoord);
    float depth = texture(DepthSampler, texCoord).r;
    float power = BarrierControl.x;

    if (power <= 0.0) { fragColor = scene; return; }

    vec2 ndc = texCoord * 2.0 - 1.0;
    mat4 ivp = mat4(InvViewProjC0, InvViewProjC1, InvViewProjC2, InvViewProjC3);

    // Ray 방향: 행렬 곱 1회로 줄임 (원래 2회)
    // cam-relative 공간에서 카메라 = 원점, far plane 점이 곧 방향
    vec4 farW = ivp * vec4(ndc, 1.0, 1.0);
    farW /= farW.w;
    vec3 rayDir = normalize(farW.xyz);

    // 씬 거리² (length() 대신 dot으로 sqrt 절약)
    float sceneDistSq = 1.0e30;
    if (depth < 0.9999) {
        vec4 wp = ivp * vec4(ndc, depth * 2.0 - 1.0, 1.0);
        wp /= wp.w;
        sceneDistSq = dot(wp.xyz, wp.xyz);
    }

    vec3  center = BarrierCenterRadius.xyz;
    float radius = BarrierCenterRadius.w;

    // Ray-sphere 교차
    vec3  oc   = -center;
    float b    = dot(rayDir, oc);
    float c    = dot(oc, oc) - radius * radius;
    float disc = b * b - c;
    if (disc < 0.0) { fragColor = scene; return; }

    float sqrtD = sqrt(disc);
    float ta    = -b - sqrtD;
    float tb    = -b + sqrtD;

    // t² vs sceneDistSq 비교 (sqrt 생략)
    float t = 0.0;
    if      (ta > 0.001 && ta * ta <= sceneDistSq + 0.5) t = ta;
    else if (tb > 0.001 && tb * tb <= sceneDistSq + 0.5) t = tb;
    if (t <= 0.0) { fragColor = scene; return; }

    vec3 shellPos = rayDir * t;
    vec3 N = normalize(shellPos - center);
    vec3 V = normalize(-shellPos);
    float fresnel = pow(max(0.0, 1.0 - abs(dot(N, V))), 2.0);

    // ── Ripple: 코사인 공간 비교 (acos 완전 제거) ──────────────────────────
    // delta ≈ |dot(N,hitDir) - cos(rippleAngle)| / sin(rippleAngle)
    // cos(rippleAngle) 은 uniform 기반 → 컴파일러가 draw call당 1회 hoist
    vec4  ripples[8]     = vec4[8](Ripple0, Ripple1, Ripple2, Ripple3,
                                   Ripple4, Ripple5, Ripple6, Ripple7);
    float intensities[8] = float[8](
        RippleIntensityA.x, RippleIntensityA.y, RippleIntensityA.z, RippleIntensityA.w,
        RippleIntensityB.x, RippleIntensityB.y, RippleIntensityB.z, RippleIntensityB.w
    );

    float maxAngle    = max(0.001, PI * power);
    float rippleAccum = 0.0;
    float uvAccum     = 0.0;

    for (int i = 0; i < 8; i++) {
        float age = ripples[i].w;
        if (age >= 1.0) continue;

        float rippleAngle = age * PI;

        // exp(-2x) → (1 - x/2)² 다항식 근사 (비슷한 모양, 훨씬 싸다)
        float tRatio     = rippleAngle / maxAngle;
        float energyFade = max(0.0, 1.0 - tRatio * 0.5);
        energyFade      *= energyFade;
        float fade       = energyFade * (1.0 - age) * intensities[i];
        // 눈에 안 보일 정도면 아래 비싼 계산 전에 건너뜀
        if (fade < 0.004) continue;

        // per-pixel 비용: dot 1번 + 비교 (acos/normalize 없음)
        vec3  hitDir    = normalize(ripples[i].xyz - center);  // uniform → 컴파일러 hoist
        float cosRipple = cos(rippleAngle);                    // uniform → hoist
        float sinRipple = max(0.001, sqrt(1.0 - cosRipple * cosRipple));  // hoist
        float ringW     = 0.04 + age * 0.08;
        float uvRingW   = ringW * 2.5;

        float delta = abs(dot(N, hitDir) - cosRipple) / sinRipple;

        if (delta < ringW) {
            rippleAccum += (1.0 - smoothstep(0.0, ringW, delta)) * fade * 0.8;
        }
        if (delta < uvRingW) {
            uvAccum += (1.0 - smoothstep(0.0, uvRingW, delta)) * fade;
        }
    }
    rippleAccum = clamp(rippleAccum, 0.0, 1.0);
    uvAccum     = clamp(uvAccum,     0.0, 1.0);

    // ── 최종 합성 ─────────────────────────────────────────────────────────
    float shellAlpha  = fresnel * power;
    float rippleAlpha = rippleAccum * (0.32 + power * 0.4);
    float totalAlpha  = clamp(shellAlpha + rippleAlpha, 0.0, 0.92);

    vec3 emitColor = mix(BARRIER_COLOR, vec3(0.65, 0.96, 1.0), rippleAccum * 0.48);

    vec3 base = scene.rgb;
    if (uvAccum > 0.01) {
        vec2 uvShift = N.xy * uvAccum * 0.025;
        base = mix(base,
            texture(InSampler, clamp(texCoord + uvShift, 0.0, 1.0)).rgb,
            min(uvAccum * 0.35, 0.325));
    }

    fragColor = vec4(mix(base, emitColor * 1.5 + base * 0.1, totalAlpha), scene.a);
}
