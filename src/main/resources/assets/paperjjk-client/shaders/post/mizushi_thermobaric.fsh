#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform ThermobaricConfig {
    vec4  InvViewProjC0;
    vec4  InvViewProjC1;
    vec4  InvViewProjC2;
    vec4  InvViewProjC3;
    vec4  CenterRel;      // xyz = explosion center (camera-relative), w = fireball radius
    vec4  AlphaParams;    // x = fireAlpha, y = flashAlpha, z = smokeAlpha, w = animTime
    float CenterScreenU;
    float CenterScreenV;
    float EffectRadius;
    float _pad1;
};

in  vec2 texCoord;
out vec4 fragColor;

// ── Noise ────────────────────────────────────────────────────────────────────

float hash3(vec3 p) {
    p  = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float vNoise(vec3 p) {
    vec3 i = floor(p); vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash3(i),             hash3(i+vec3(1,0,0)),f.x),
            mix(hash3(i+vec3(0,1,0)), hash3(i+vec3(1,1,0)),f.x),f.y),
        mix(mix(hash3(i+vec3(0,0,1)), hash3(i+vec3(1,0,1)),f.x),
            mix(hash3(i+vec3(0,1,1)), hash3(i+vec3(1,1,1)),f.x),f.y),
        f.z);
}

float fbm(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { v += a * vNoise(p); p *= 2.0; a *= 0.5; }
    return v;
}

// ── Ray-sphere 교차 헬퍼 ──────────────────────────────────────────────────────
// tEnter, tExit 반환. disc < 0 이면 false.
bool sphereHit(vec3 rd, vec3 cen, float radius,
               out float tEnter, out float tExit) {
    vec3  oc   = -cen;
    float b    = dot(oc, rd);
    float c    = dot(oc, oc) - radius * radius;
    float disc = b * b - c;
    if (disc < 0.0) return false;
    float sq = sqrt(disc);
    tEnter = -b - sq;
    tExit  = -b + sq;
    return true;
}

// ── Main ─────────────────────────────────────────────────────────────────────

void main() {
    vec4  orig       = texture(InSampler, texCoord);
    float fireAlpha  = AlphaParams.x;
    float flashAlpha = AlphaParams.y;
    float smokeAlpha = AlphaParams.z;
    float animTime   = AlphaParams.w;
    float fbRadius   = CenterRel.w;
    float effectR    = max(EffectRadius, 1.0);

    // ── 1. 섬광 — 거리 기반 fade ─────────────────────────────────────────────
    float camDist       = length(CenterRel.xyz);
    float flashDistFade = exp(-pow(camDist / effectR, 2.0) * 0.1);
    vec3  result        = mix(orig.rgb, vec3(1.0, 0.97, 0.88), flashAlpha * flashDistFade);

    if (fbRadius < 0.1 && fireAlpha < 0.005 && smokeAlpha < 0.005) {
        fragColor = vec4(result, orig.a);
        return;
    }

    // ── Ray 복원 ─────────────────────────────────────────────────────────────
    mat4  invVP   = mat4(InvViewProjC0, InvViewProjC1, InvViewProjC2, InvViewProjC3);
    vec4  ndc0    = vec4(texCoord * 2.0 - 1.0, 0.0, 1.0);
    vec4  wd4     = invVP * ndc0;
    vec3  rd      = normalize(wd4.xyz / wd4.w);

    float depth   = texture(DepthSampler, texCoord).r;
    bool  isSky   = depth >= 0.9999;
    vec4  ndcD    = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4  wp4     = invVP * ndcD;
    float pixDist = isSky ? 1e10 : length(wp4.xyz / wp4.w);

    vec3  cen = CenterRel.xyz;

    // ── 2. 화염구 볼류메트릭 ─────────────────────────────────────────────────
    if (fireAlpha > 0.005) {
        float tF, tB;
        if (sphereHit(rd, cen, fbRadius, tF, tB)) {
            float tEnter    = max(0.0, tF);
            float tExit     = min(pixDist, tB);
            float insideLen = max(0.0, tExit - tEnter);

            if (insideLen > 0.01) {
                vec3  samplePt = rd * (tEnter + insideLen * 0.4);

                // 노이즈
                vec3  nPos   = samplePt * (4.0 / max(fbRadius, 1.0)) + vec3(0.0, -animTime * 0.6, 0.0);
                float n1     = fbm(nPos);
                float n2     = fbm(nPos * 2.0 + vec3(animTime * 0.25, 0.0, animTime * 0.4));
                float fNoise = mix(n1, n2, 0.45);

                // 구 경계 페이드 (가장자리를 부드럽게)
                float edgeFade = smoothstep(0.0, fbRadius * 0.18, insideLen);

                float density  = clamp(insideLen / (fbRadius * 1.2), 0.0, 1.0);
                density        = pow(density, 1.2) * edgeFade; // 1.2로 키워 경계 더 부드럽게
                density       *= (0.4 + fNoise * 0.9);
                density        = clamp(density, 0.0, 1.0);

                // 화염 색상
                float g       = clamp(fNoise * 1.3, 0.0, 1.0);
                vec3  coreCol = vec3(1.0, 0.95, 0.70);
                vec3  midCol  = vec3(1.0, 0.40, 0.04);
                vec3  edgeCol = vec3(0.45, 0.08, 0.02);
                vec3  fireCol = mix(edgeCol, mix(midCol, coreCol, g), g);

                // Bloom additive glow
                result += fireCol * (fireAlpha * density * 0.4);

                // 메인 화염
                result = mix(result, fireCol, clamp(fireAlpha * density, 0.0, 1.0));
            }
        }
    }

    // ── 3. 연기 볼류메트릭 — 구 중심이 위로 상승 ─────────────────────────────
    if (smokeAlpha > 0.005) {
        // 연기 구 중심을 smokeAlpha에 비례해 위로 이동 → 연기가 상승하는 느낌
        float riseOffset = smokeAlpha * fbRadius * 0.9;
        vec3  smokeCen   = cen + vec3(0.0, riseOffset, 0.0);
        // 연기가 올라갈수록 구가 약간 퍼짐
        float smokeRad   = fbRadius * (1.0 + smokeAlpha * 0.25);

        float tF, tB;
        if (sphereHit(rd, smokeCen, smokeRad, tF, tB)) {
            float tEnter    = max(0.0, tF);
            float tExit     = min(pixDist, tB);
            float insideLen = max(0.0, tExit - tEnter);

            if (insideLen > 0.01) {
                vec3  samplePt = rd * (tEnter + insideLen * 0.5);

                // 구 경계 페이드
                float edgeFade = smoothstep(0.0, smokeRad * 0.2, insideLen);

                // 위로 흐르는 연기 노이즈
                vec3  sPos   = samplePt * (2.0 / max(fbRadius, 1.0)) + vec3(0.0, -animTime * 0.22, 0.0);
                float sn     = fbm(sPos);

                float density  = clamp(insideLen / (smokeRad * 1.3), 0.0, 1.0);
                density        = pow(density, 1.1) * edgeFade;
                density       *= (0.35 + sn * 0.85);
                density        = clamp(density, 0.0, 1.0);

                // 높이에 따른 연기 색
                float heightF   = clamp((samplePt.y - cen.y) / max(fbRadius, 1.0), 0.0, 1.5);
                vec3  smokeLow  = vec3(0.20, 0.13, 0.07);
                vec3  smokeHigh = vec3(0.28, 0.24, 0.21);
                vec3  smokeCol  = mix(smokeLow, smokeHigh, clamp(heightF, 0.0, 1.0));

                result = mix(result, smokeCol, clamp(smokeAlpha * density, 0.0, 1.0));
            }
        }
    }

    fragColor = vec4(result, orig.a);
}
