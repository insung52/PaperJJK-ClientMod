#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform ShockwaveConfig {
    vec4  InvViewProjC0;
    vec4  InvViewProjC1;
    vec4  InvViewProjC2;
    vec4  InvViewProjC3;
    vec4  CenterRel;  // xyz = explosion center (camera-relative), w = shockwave sphere radius
    vec4  Params;     // x = swStrength, y = vaporAlpha, z = dustAlpha, w = effectRadius
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
        mix(mix(hash3(i),               hash3(i+vec3(1,0,0)),f.x),
            mix(hash3(i+vec3(0,1,0)),   hash3(i+vec3(1,1,0)),f.x),f.y),
        mix(mix(hash3(i+vec3(0,0,1)),   hash3(i+vec3(1,0,1)),f.x),
            mix(hash3(i+vec3(0,1,1)),   hash3(i+vec3(1,1,1)),f.x),f.y),
        f.z);
}

float fbm(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 3; i++) { v += a * vNoise(p); p *= 2.0; a *= 0.5; }
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
    float swRadius    = CenterRel.w;
    float swStrength  = Params.x;
    float vaporAlpha  = Params.y;
    float dustAlpha   = Params.z;
    float effectRadius = Params.w;

    if (swRadius < 0.1 || (swStrength < 0.001 && vaporAlpha < 0.001 && dustAlpha < 0.001)) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    // ── Ray reconstruction ────────────────────────────────────────────────────
    mat4  invVP   = mat4(InvViewProjC0, InvViewProjC1, InvViewProjC2, InvViewProjC3);
    vec4  ndc0    = vec4(texCoord * 2.0 - 1.0, 0.0, 1.0);
    vec4  wd4     = invVP * ndc0;
    vec3  rd      = normalize(wd4.xyz / wd4.w);

    float depth    = texture(DepthSampler, texCoord).r;
    bool  isSky    = depth >= 0.9999;
    vec4  ndcD     = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4  wp4      = invVP * ndcD;
    vec3  worldPos = wp4.xyz / wp4.w;
    float pixDist  = isSky ? 1e10 : length(worldPos);

    vec3  cen    = CenterRel.xyz;
    vec4  orig   = texture(InSampler, texCoord);
    vec3  result = orig.rgb;

    // ── 1. 충격파 UV 왜곡 (투명, 링 시각 없음) ──────────────────────────────────
    if (swStrength > 0.001) {
        float tF, tB;
        if (sphereHit(rd, cen, swRadius, tF, tB)) {
            float tHit = (tF >= 0.001) ? tF : tB;
            if (tHit >= 0.001 && tHit < pixDist) {
                vec3  hitPoint = rd * tHit;
                vec3  normal   = normalize(hitPoint - cen);
                float cosAngle = abs(dot(rd, normal));
                float distort  = pow(max(0.0, 1.0 - cosAngle), 1.8) * swStrength;

                vec2  cu    = texCoord - vec2(0.5);
                float cuLen = length(cu);
                if (cuLen > 0.0001) {
                    vec2 offset   = -(cu / cuLen) * distort * 0.035;
                    vec2 sampleUV = clamp(texCoord + offset, 0.001, 0.999);
                    result = texture(InSampler, sampleUV).rgb;
                }
            }
        }
    }

    // ── 2. 수증기 응축 — 가우시안 볼 ────────────────────────────────────────────
    if (vaporAlpha > 0.001) {
        float tF, tB;
        if (sphereHit(rd, cen, swRadius * 2.5, tF, tB)) {
            float tEnter = max(0.0, tF);
            float tExit  = min(pixDist, tB);
            if (tExit >= tEnter) {
                float proj    = clamp(dot(cen, rd), tEnter, tExit);
                vec3  samplePt = rd * proj;
                float d = length(samplePt - cen);

                // 구 중심 기준 각도 방향으로 노이즈 → swRadius 변화에 무관하게 안정적
                vec3  noisePos = normalize(samplePt - cen) * 3.5;
                float nTex     = fbm(noisePos) * 0.5 + 0.5;

                // sigma를 swRadius에만 의존하지 않고 최솟값 보장
                float sigma   = max(swRadius * 0.55, effectRadius * 0.15);
                float density = exp(-pow(d / sigma, 2.0));
                density *= (0.6 + nTex * 0.6);
                density  = clamp(density, 0.0, 1.0);

                // 낮에는 강하게, 밤에는 약하게 — 하지만 완전히 0으로 자르지 않음
                float sceneLum   = dot(result, vec3(0.299, 0.587, 0.114));
                float blendScale = mix(0.35, 0.85, smoothstep(0.05, 0.30, sceneLum));

                vec3 vaporCol = mix(vec3(0.76, 0.79, 0.86), vec3(0.88, 0.91, 0.97), nTex * 0.5);
                result = mix(result, vaporCol, vaporAlpha * density * blendScale);
            }
        }
    }

    // ── 3. 지표면 먼지 ────────────────────────────────────────────────────────
    if (dustAlpha > 0.001 && !isSky) {
        float tF, tB;
        if (sphereHit(rd, cen, swRadius, tF, tB)) {
            float tEnter    = max(0.0, tF);
            float tExit     = min(pixDist, tB);
            float insideLen = max(0.0, tExit - tEnter);
            if (insideLen > 0.01) {
                float belowCenter = clamp((cen.y - worldPos.y) / max(effectRadius * 0.3, 1.0), 0.0, 1.0);
                if (belowCenter > 0.01) {
                    float density = clamp(insideLen / (swRadius * 0.2), 0.0, 1.0);
                    vec3  dPos    = worldPos * 0.02;
                    float dn      = fbm(dPos) * 0.5 + 0.5;
                    density      *= (0.4 + dn * 0.8);
                    density       = clamp(density, 0.0, 1.0);

                    vec3 dustCol = mix(vec3(0.38, 0.26, 0.12), vec3(0.62, 0.50, 0.32),
                                       clamp(-worldPos.y / max(effectRadius * 0.5, 1.0), 0.0, 1.0));
                    result = mix(result, dustCol, dustAlpha * belowCenter * density);
                }
            }
        }
    }

    fragColor = vec4(result, orig.a);
}
