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
    float RiseAmount;     // monotonically 0→1, controls shared risen center height
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
bool sphereHit(vec3 rd, vec3 cen, float radius, out float tEnter, out float tExit) {
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
    float riseAmt    = RiseAmount;

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

    // 화염구+연기 공통 상승 중심 — riseAmt 단조증가이므로 절대 내려가지 않음
    vec3  risenCen = cen + vec3(0.0, riseAmt * fbRadius * 0.9, 0.0);

    // ── 2. 화염구 가우시안 볼 ─────────────────────────────────────────────────
    // insideLen 대신 구 중심까지의 최근접 거리를 가우시안으로 변환
    // → 구 경계가 완전히 보이지 않게 됨
    if (fireAlpha > 0.005 && fbRadius > 0.1) {
        // 2배 반경으로 교차 검사 → 가우시안이 그 경계 안에서 자연 소멸
        float tF, tB;
        if (sphereHit(rd, risenCen, fbRadius * 2.0, tF, tB)) {
            float tEnter = max(0.0, tF);
            float tExit  = min(pixDist, tB);
            if (tExit >= tEnter) {
                // 가시 구간 내 구 중심에 가장 가까운 레이 지점
                float proj    = clamp(dot(risenCen, rd), tEnter, tExit);
                vec3  samplePt = rd * proj;
                float d = length(samplePt - risenCen);

                // 노이즈 — effectRadius(고정값)로 스케일하여 fbRadius 팽창 중 UV 급변 방지
                // fbRadius로 나누면 팽창 속도에 따라 스케일이 급변해 반짝거림 발생
                float noiseScale = 4.0 / max(effectR, 1.0);
                vec3  nPos   = samplePt * noiseScale + vec3(0.0, -animTime * 0.4, 0.0);
                float n1     = fbm(nPos);
                float n2     = fbm(nPos * 2.0 + vec3(animTime * 0.25, 0.0, animTime * 0.4));
                float fNoise = mix(n1, n2, 0.45);

                // 가우시안 밀도 (sigma = fbRadius * 0.45 → fbRadius 지점에서 거의 0)
                float sigma   = fbRadius * 0.45;
                float density = exp(-pow(d / sigma, 2.0));
                density *= (0.4 + fNoise * 0.9);
                density  = clamp(density, 0.0, 1.0);

                float g       = clamp(fNoise * 1.3, 0.0, 1.0);
                vec3  coreCol = vec3(1.0, 0.95, 0.70);
                vec3  midCol  = vec3(1.0, 0.40, 0.04);
                vec3  edgeCol = vec3(0.45, 0.08, 0.02);
                vec3  fireCol = mix(edgeCol, mix(midCol, coreCol, g), g);

                // 블룸 + 메인 화염
                result += fireCol * (fireAlpha * density * 0.4);
                result  = mix(result, fireCol, clamp(fireAlpha * density, 0.0, 1.0));
            }
        }
    }

    // ── 3. 연기 가우시안 볼 ───────────────────────────────────────────────────
    // 화염구와 동일한 risenCen 사용 → 둘이 같이 올라감
    if (smokeAlpha > 0.005 && fbRadius > 0.1) {
        float smokeRad = fbRadius * 1.25;
        float tF, tB;
        if (sphereHit(rd, risenCen, smokeRad * 2.0, tF, tB)) {
            float tEnter = max(0.0, tF);
            float tExit  = min(pixDist, tB);
            if (tExit >= tEnter) {
                float proj    = clamp(dot(risenCen, rd), tEnter, tExit);
                vec3  samplePt = rd * proj;
                float d = length(samplePt - risenCen);

                vec3  sPos   = samplePt * (2.0 / max(fbRadius, 1.0)) + vec3(0.0, -animTime * 0.22, 0.0);
                float sn     = fbm(sPos);

                float sigma   = smokeRad * 0.45;
                float density = exp(-pow(d / sigma, 2.0));
                density *= (0.35 + sn * 0.85);
                density  = clamp(density, 0.0, 1.0);

                float heightF  = clamp((samplePt.y - cen.y) / max(fbRadius, 1.0), 0.0, 1.5);
                vec3  smokeLow  = vec3(0.20, 0.13, 0.07);
                vec3  smokeHigh = vec3(0.28, 0.24, 0.21);
                vec3  smokeCol  = mix(smokeLow, smokeHigh, clamp(heightF, 0.0, 1.0));

                result = mix(result, smokeCol, clamp(smokeAlpha * density, 0.0, 1.0));
            }
        }
    }

    fragColor = vec4(result, orig.a);
}
