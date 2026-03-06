#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform DustStormConfig {
    vec4  InvViewProjC0;
    vec4  InvViewProjC1;
    vec4  InvViewProjC2;
    vec4  InvViewProjC3;
    vec4  DomainCenter;
    float DomainRadius;
    float Time;
    float _pad0;
    float _pad1;
};

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(0.1031, 0.1030));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

// 두 번 거쳐 패턴 완전 파괴 — TV 정적 노이즈용
float hash2(vec2 p) {
    return hash(vec2(hash(p) * 7321.9, hash(p.yx) * 3917.5));
}

float vNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i),             hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float dirNoise(vec3 rd, float scale, vec2 timeOffset) {
    float a = vNoise(rd.xy * scale + timeOffset);
    float b = vNoise(rd.yz * scale + timeOffset.yx);
    return mix(a, b, 0.5);
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    bool  isSky = depth >= 0.9999;

    if (DomainRadius < 0.5) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    // ── 월드 방향 복원 ────────────────────────────────────────────────────────
    vec4 ndc   = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    mat4 invVP = mat4(InvViewProjC0, InvViewProjC1, InvViewProjC2, InvViewProjC3);
    vec4 wp4   = invVP * ndc;
    vec3 worldPos = wp4.xyz / wp4.w;

    vec3  rd      = normalize(worldPos);
    float pixDist = isSky ? 1e10 : length(worldPos);

    // ── Ray-Sphere 교차 → insideLen ───────────────────────────────────────────
    vec3  domC = DomainCenter.xyz;
    vec3  oc   = -domC;
    float b    = dot(oc, rd);
    float cVal = dot(oc, oc) - DomainRadius * DomainRadius;
    float disc = b * b - cVal;

    if (disc < 0.0) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    float sq        = sqrt(disc);
    float tEntry    = max(0.0, -b - sq);
    float tExit     = min(pixDist, -b + sq);
    float insideLen = max(0.0, tExit - tEntry);

    if (insideLen < 0.01) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    vec4 orig = texture(InSampler, texCoord);

    // ── 1. 기본 Fog — 거리 비례 회색, 플레이어 위치 10% 시작, 70m 에서 100% ──
    float baseFog = clamp(0.1 + insideLen / 120.0, 0.0, 1.0);

    // ── 2. 얼룩 Fog — rd 기반 검은 얼룩, 기본 fog 위에 추가 ──────────────────
    float n1 = dirNoise(rd,  5.0, vec2(0.0,        Time * 3.6));
    float n2 = dirNoise(rd, 12.0, vec2(Time * 2.8, Time * 1.8));
    float turbulence = mix(n1, n2, 0.5);
    float blobIntensity = (1.0 - turbulence) * baseFog * 0.75;

    // ── 3. 왜곡 — 인접 픽셀 대체 (fog 적용 전 scene 에 먼저 적용) ────────────
    float frameTime = floor(Time * 60.0);

    float rx = hash(vec2(texCoord.x * 2341.0 + frameTime, texCoord.y * 1289.0));
    float ry = hash(vec2(texCoord.y * 2341.0 + frameTime, texCoord.x * 1289.0));
    vec2 pixOffset = (vec2(rx, ry) - 0.5) * 0.005;
    vec3 neighbor  = texture(InSampler, clamp(texCoord + pixOffset, 0.001, 0.999)).rgb;
    // fog가 강할수록 인접 픽셀로 더 대체됨 (fog가 그 위에 덮이므로 scene 새어나옴 없음)
    vec3 sceneDistorted = mix(orig.rgb, neighbor, baseFog * 0.5);

    // 기본 fog 적용 (회색) — baseFog=1 이면 완전히 회색, scene 안 보임
    vec3 afterBaseFog = mix(sceneDistorted, vec3(0.20, 0.20, 0.20), baseFog);
    // 얼룩 fog 추가 (검은색)
    vec3 afterBlob = mix(afterBaseFog, vec3(0.25, 0.03, 0.03), blobIntensity);
    vec3 result    = afterBlob;

    // (b) TV 정적 노이즈: ~1% 확률로 흰색 픽셀 (불투명도 50~100%)
    // 카메라가 영역 경계 밖으로 나갈수록 노이즈 감쇠 (20블록 밖에서 0%)
    float camDistFromCenter = length(DomainCenter.xyz); // DomainCenter는 카메라 상대 좌표
    float distOutside  = max(0.0, camDistFromCenter - DomainRadius);
    float staticFade   = 1.0 - clamp(distOutside / 20.0, 0.0, 1.0);

    float staticHash  = hash2(vec2(texCoord.x * 1920.0 + frameTime * 13.7,
                                   texCoord.y * 1080.0 + frameTime *  9.3));
    float staticAlpha = step(0.99, staticHash)  // ~1% 확률
                      * (0.5 + hash2(vec2(staticHash + frameTime, texCoord.x + texCoord.y)) * 0.5)
                      * staticFade;
    result = mix(result, vec3(1.0), staticAlpha * baseFog);

    fragColor = vec4(result, orig.a);
}
