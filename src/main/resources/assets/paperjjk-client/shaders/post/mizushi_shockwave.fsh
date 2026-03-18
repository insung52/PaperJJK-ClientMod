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

    // ── 2. 수증기 응축 ──────────────────────────────────────────────────────────
    if (vaporAlpha > 0.001) {
        vec3  oc2   = -cen;
        float b2    = dot(oc2, rd);
        float c2    = dot(oc2, oc2) - swRadius * swRadius;
        float disc2 = b2 * b2 - c2;
        if (disc2 >= 0.0) {
            float sq2       = sqrt(disc2);
            float tEnter    = max(0.0, -b2 - sq2);
            float tExit     = min(pixDist, -b2 + sq2);
            float insideLen = max(0.0, tExit - tEnter);
            if (insideLen > 0.01) {
                vec3  samplePt = rd * (tEnter + insideLen * 0.5);

                // 경계 변위용 각도 노이즈 (구 표면만 사용 → 여기는 normalize 괜찮음)
                vec3  dirPos     = normalize(samplePt - cen) * 4.0;
                float nDisp      = fbm(dirPos) * 0.35;
                float effectiveR = swRadius * (1.0 + nDisp);

                float c3    = dot(oc2, oc2) - effectiveR * effectiveR;
                float disc3 = b2 * b2 - c3;
                float inside2 = 0.0;
                if (disc3 >= 0.0) {
                    float sq3 = sqrt(disc3);
                    inside2 = max(0.0, min(pixDist, -b2 + sq3) - max(0.0, -b2 - sq3));
                }

                // 구 형태 기반 페이드
                float baseDensity = clamp(inside2 / (effectiveR * 1.0), 0.0, 1.0);
                baseDensity = pow(baseDensity, 1.5);

                // ── 뭉게구름 패턴 — 월드스페이스 3D 노이즈 ────────────────────
                // * 0.5 + 0.5 바이어스 제거 → raw fbm 범위 [0.05~0.75] 사용
                // (바이어스 적용 시 cloudNoise가 항상 0.55 이상 → 구멍 수학적으로 불가)
                vec3  wsPos = samplePt * (3.5 / max(effectRadius, 1.0));
                float nPuff = fbm(wsPos);                              // 저주파: 큰 덩어리 [0.05~0.75]
                float nTex  = fbm(wsPos * 2.5 + vec3(0.7, 1.3, 0.5)); // 중주파: 내부 결
                float nFine = fbm(wsPos * 5.0 + vec3(1.7, 0.9, 2.3)); // 고주파: 잔털

                float cloudNoise = nPuff * 0.55 + nTex * 0.30 + nFine * 0.15; // 범위 [0.03~0.75]

                // 0.58 아래 구멍(투명), 0.72 이상 불투명 — 약 90%가 구멍
                float cloudDensity = smoothstep(0.28, 0.62, cloudNoise);

                float density = baseDensity * cloudDensity;
                density = clamp(density, 0.0, 1.0);

                // lightScale: 밤에는 완전 불가시
                float sceneLum   = dot(result, vec3(0.299, 0.587, 0.114));
                float lightScale = smoothstep(0.18, 0.40, sceneLum);

                // 구름 내부 명암: 노이즈 피크는 밝은 흰색, 낮은 곳은 회백
                float cloudN01 = clamp(cloudNoise / 0.75, 0.0, 1.0); // [0~0.75] → [0~1] 정규화
                vec3 vaporCol = mix(vec3(0.78, 0.80, 0.87), vec3(0.95, 0.96, 1.00), cloudN01);
                result = mix(result, vaporCol, vaporAlpha * density * 0.85 * lightScale);
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
