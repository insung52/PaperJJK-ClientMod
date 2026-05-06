#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

// std140 layout (256 bytes)
// 0-63:   InvViewProjC0-C3    (4 × vec4)
// 64-79:  BarrierCenterRadius (vec4: xyz = cam-relative center, w = radius)
// 80-95:  BarrierControl      (vec4: x = power 0-1, y = time)
// 96-223: Ripple0-7           (8 × vec4: xyz = cam-relative hit pos, w = age 0→1)
// 224-239: RippleIntensityA   (vec4: x..w = ripple 0..3 intensity)
// 240-255: RippleIntensityB   (vec4: x..w = ripple 4..7 intensity)
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

const vec3 BARRIER_COLOR = vec3(0.35, 0.82, 1.0);

void main() {
    vec4  scene = texture(InSampler, texCoord);
    float depth = texture(DepthSampler, texCoord).r;
    float power = BarrierControl.x;

    if (power <= 0.0) { fragColor = scene; return; }

    vec2 ndc = texCoord * 2.0 - 1.0;
    mat4 ivp = mat4(InvViewProjC0, InvViewProjC1, InvViewProjC2, InvViewProjC3);

    // 화면 픽셀을 통과하는 ray (카메라가 cam-relative 원점)
    vec4 nearW = ivp * vec4(ndc, -1.0, 1.0); nearW /= nearW.w;
    vec4 farW  = ivp * vec4(ndc,  1.0, 1.0); farW  /= farW.w;
    vec3 rayDir = normalize(farW.xyz - nearW.xyz);

    // 씬 지오메트리까지의 거리 (occlusion 용)
    float sceneDist = 1.0e30;
    if (depth < 0.9999) {
        vec4 wp = ivp * vec4(ndc, depth * 2.0 - 1.0, 1.0);
        wp /= wp.w;
        sceneDist = length(wp.xyz);
    }

    vec3  center = BarrierCenterRadius.xyz;
    float radius = BarrierCenterRadius.w;

    // Ray-sphere 교차 (원점 = 카메라, ray: t * rayDir)
    vec3  oc   = -center;
    float b    = dot(rayDir, oc);
    float c    = dot(oc, oc) - radius * radius;
    float disc = b * b - c;

    if (disc < 0.0) { fragColor = scene; return; }

    float sqrtD = sqrt(disc);
    float ta    = -b - sqrtD;  // 가까운 교차점
    float tb    = -b + sqrtD;  // 먼 교차점

    // 카메라 앞쪽이고 지오메트리 앞쪽인 교차점 선택
    float t = 0.0;
    if      (ta > 0.001 && ta <= sceneDist + 0.05) t = ta;
    else if (tb > 0.001 && tb <= sceneDist + 0.05) t = tb;

    if (t <= 0.0) { fragColor = scene; return; }

    // 구 표면 좌표 (cam-relative)
    vec3 shellPos = rayDir * t;
    vec3 N = normalize(shellPos - center);  // 구 표면 법선 (바깥 방향)
    vec3 V = normalize(-shellPos);          // 시선 벡터 (카메라 향함)

    // Fresnel: 테두리(N⊥V)에서 1, 정면(N∥V)에서 0
    float fresnel = pow(max(0.0, 1.0 - abs(dot(N, V))), 2.0);

    // ── 구 껍질 표면 위를 퍼지는 충돌 파동 ───────────────────────────────
    // 각 파동은 hit point에서 각도로 퍼짐 (3D 구면파 → 구 표면 2D 파동)
    vec4 ripples[8] = vec4[8](
        Ripple0, Ripple1, Ripple2, Ripple3,
        Ripple4, Ripple5, Ripple6, Ripple7
    );
    float intensities[8] = float[8](
        RippleIntensityA.x, RippleIntensityA.y, RippleIntensityA.z, RippleIntensityA.w,
        RippleIntensityB.x, RippleIntensityB.y, RippleIntensityB.z, RippleIntensityB.w
    );

    float rippleAccum = 0.0;
    float uvAccum     = 0.0;  // UV 굴절 전용 (5배 넓은 링)
    for (int i = 0; i < 8; i++) {
        float age = ripples[i].w;
        if (age >= 1.0) continue;

        vec3  hitDir  = normalize(ripples[i].xyz - center);
        float angDist = acos(clamp(dot(N, hitDir), -1.0, 1.0));
        // 속도 고정, 이동하면서 지수 감쇠 — power가 작을수록 빠르게 소멸
        float maxAngle    = max(0.001, 3.14159265 * power);
        float rippleAngle = age * 3.14159265;
        float ringW    = 0.04 + age * 0.08;
        float uvRingW  = ringW * 2.5;          // UV 전용 (범위 50% 감소)
        float delta    = abs(angDist - rippleAngle);
        // 이동 거리에 따른 지수 감쇠: maxAngle 도달 시 ~13% 밝기, 계속 이동하며 소멸
        float energyFade = exp(-2.0 * rippleAngle / maxAngle);
        float fade       = energyFade * (1.0 - age) * intensities[i];

        if (delta < ringW) {
            float shape = 1.0 - smoothstep(0.0, ringW, delta);
            rippleAccum += shape * fade * 0.8;
        }
        if (delta < uvRingW) {
            float shape = 1.0 - smoothstep(0.0, uvRingW, delta);
            uvAccum += shape * fade;
        }
    }
    rippleAccum = clamp(rippleAccum, 0.0, 1.0);
    uvAccum     = clamp(uvAccum,     0.0, 1.0);

    // ── 최종 합성 ─────────────────────────────────────────────────────────
    float shellAlpha   = fresnel * power;
    float rippleAlpha  = rippleAccum * (0.32 + power * 0.4);
    float totalAlpha   = clamp(shellAlpha + rippleAlpha, 0.0, 0.92);

    vec3 emitColor = mix(BARRIER_COLOR, vec3(0.65, 0.96, 1.0), rippleAccum * 0.48);

    // 파동 위치에서 씬 굴절 (UV 전용 넓은 영역 사용)
    vec3 base = scene.rgb;
    if (uvAccum > 0.01) {
        vec2 uvShift = N.xy * uvAccum * 0.025;
        base = mix(base,
            texture(InSampler, clamp(texCoord + uvShift, 0.0, 1.0)).rgb,
            min(uvAccum * 0.35, 0.325));
    }

    fragColor = vec4(mix(base, emitColor * 1.5 + base * 0.1, totalAlpha), scene.a);
}
