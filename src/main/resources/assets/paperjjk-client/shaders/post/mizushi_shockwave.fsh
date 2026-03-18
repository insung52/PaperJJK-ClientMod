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

    float depth   = texture(DepthSampler, texCoord).r;
    bool  isSky   = depth >= 0.9999;
    vec4  ndcD    = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4  wp4     = invVP * ndcD;
    vec3  worldPos = wp4.xyz / wp4.w;
    float pixDist  = isSky ? 1e10 : length(worldPos);

    // ── Ray-sphere intersection ───────────────────────────────────────────────
    vec3  cen  = CenterRel.xyz;
    vec3  oc   = -cen;
    float b    = dot(oc, rd);
    float c    = dot(oc, oc) - swRadius * swRadius;
    float disc = b * b - c;

    vec4  orig   = texture(InSampler, texCoord);
    vec3  result = orig.rgb;

    if (disc < 0.0) {
        fragColor = orig;
        return;
    }

    float sq        = sqrt(disc);
    float tFront    = -b - sq;
    float tBack     = -b + sq;
    float tEnter    = max(0.0, tFront);
    float tExit     = min(pixDist, tBack);
    float insideLen = max(0.0, tExit - tEnter);

    // ── 1. 충격파 UV 왜곡 (투명, 링 시각 없음) ──────────────────────────────────
    if (swStrength > 0.001) {
        float tHit = (tFront >= 0.001) ? tFront : tBack;
        if (tHit >= 0.001 && tHit < pixDist) {
            vec3  hitPoint = rd * tHit;
            vec3  normal   = normalize(hitPoint - cen);
            // 구 실루엣 방향(grazing)에서 최대 왜곡
            float cosAngle = abs(dot(rd, normal));
            float distort  = pow(max(0.0, 1.0 - cosAngle), 1.8) * swStrength;

            // 화면 중심에서 방사형 inward offset
            vec2  cu       = texCoord - vec2(0.5);
            float cuLen    = length(cu);
            if (cuLen > 0.0001) {
                vec2 offset    = -(cu / cuLen) * distort * 0.035;
                vec2 sampleUV  = clamp(texCoord + offset, 0.001, 0.999);
                result = texture(InSampler, sampleUV).rgb;
            }
        }
    }

    // ── 2. 수증기 응축 ────────────────────────────────────────────────────────
    // 구 내부를 경로에 따라 채우되, fBm 노이즈로 경계를 불규칙하게 만듦.
    if (vaporAlpha > 0.001 && insideLen > 0.01) {
        vec3 samplePt = rd * (tEnter + insideLen * 0.5);

        // 노이즈로 구 경계 변위 → 울퉁불퉁한 가장자리
        vec3  noisePos  = normalize(samplePt - cen) * 4.0;
        float nDisp     = fbm(noisePos) * 0.25; // ±25% 경계 변위
        float effectiveR = swRadius * (1.0 + nDisp);

        // 변위된 반경 기반 insideLen 재계산 (근사)
        float c2   = dot(oc, oc) - effectiveR * effectiveR;
        float disc2 = b * b - c2;
        float inside2 = 0.0;
        if (disc2 >= 0.0) {
            float sq2 = sqrt(disc2);
            inside2 = max(0.0, min(pixDist, -b + sq2) - max(0.0, -b - sq2));
        }

        // 경로 기반 밀도: 짧으면 옅고, 길수록 짙어짐
        float density = clamp(inside2 / (effectiveR * 0.4), 0.0, 1.0);
        // 급격한 감쇠: 경계 근처만 짙게
        density = pow(density, 0.4);

        // 추가 노이즈로 얼룩 텍스처
        float nTex = fbm(noisePos * 2.0) * 0.5 + 0.5;
        density *= (0.5 + nTex * 0.7);
        density  = clamp(density, 0.0, 1.0);

        // 색상: 회청색 계열 (너무 밝지 않게)
        vec3 vaporCol = mix(vec3(0.60, 0.63, 0.70), vec3(0.75, 0.78, 0.85), nTex * 0.5);
        result = mix(result, vaporCol, vaporAlpha * density * 0.75);
    }

    // ── 3. 지표면 먼지 ────────────────────────────────────────────────────────
    // 픽셀의 worldPos.y가 폭발 중심 y 이하인 경우 (지면 픽셀 근사)
    if (dustAlpha > 0.001 && insideLen > 0.01 && !isSky) {
        // 카메라 상대 좌표에서 폭발 중심보다 낮은 픽셀 = 지면
        float belowCenter = clamp((cen.y - worldPos.y) / max(effectRadius * 0.3, 1.0), 0.0, 1.0);
        if (belowCenter > 0.01) {
            // 경로 기반 밀도 (얇은 층이므로 작은 insideLen도 충분)
            float density = clamp(insideLen / (swRadius * 0.2), 0.0, 1.0);

            // 노이즈 텍스처 (먼지 얼룩)
            vec3  dPos    = worldPos * 0.02;
            float dn      = fbm(dPos) * 0.5 + 0.5;
            density      *= (0.4 + dn * 0.8);
            density       = clamp(density, 0.0, 1.0);

            // 높이에 따른 색상: 짙은 갈색(지면) → 밝은 황토(허공)
            vec3 dustCol = mix(vec3(0.38, 0.26, 0.12), vec3(0.62, 0.50, 0.32),
                               clamp(-worldPos.y / max(effectRadius * 0.5, 1.0), 0.0, 1.0));

            result = mix(result, dustCol, dustAlpha * belowCenter * density);
        }
    }

    fragColor = vec4(result, orig.a);
}
