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
    float Alpha;
    float _pad1;
};

// 사전 계산된 64×64 seamless noise corner 값 테이블.
// vec4 에 4개씩 패킹 — 1024 × 16 bytes = 16384 bytes (std140).
// lookupCorner(x, y) = Noise[(y*64+x) >> 2][(y*64+x) & 3]
// & 63 으로 2^6 타일링 (seamless)
layout(std140) uniform NoiseTable {
    vec4 Noise[1024];
};

in vec2 texCoord;
out vec4 fragColor;

// TV 정적 노이즈는 고주파 랜덤 패턴이 필요하므로 hash 함수 유지
float hash(vec2 p) {
    p = fract(p * vec2(0.1031, 0.1030));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

float hash2(vec2 p) {
    return hash(vec2(hash(p) * 7321.9, hash(p.yx) * 3917.5));
}

// 사전 계산된 테이블에서 (x, y) corner 값 조회
// & 63 = bitwise modulo 64 (x < 0 포함, 두의 보수상 올바름)
float lookupCorner(int x, int y) {
    int idx = (y & 63) * 64 + (x & 63);
    return Noise[idx >> 2][idx & 3];
}

// hash+vNoise 연산(ALU 16회) → 테이블 조회 + bilinear 보간 (메모리 4회)
float tableNoise(vec2 p) {
    vec2  uv = p * 64.0;
    ivec2 i  = ivec2(floor(uv));
    vec2  f  = fract(uv);
    f = f * f * (3.0 - 2.0 * f); // smoothstep
    return mix(
        mix(lookupCorner(i.x,     i.y    ),
            lookupCorner(i.x + 1, i.y    ), f.x),
        mix(lookupCorner(i.x,     i.y + 1),
            lookupCorner(i.x + 1, i.y + 1), f.x),
        f.y);
}

float dirNoise(vec3 rd, float scale, vec2 timeOffset) {
    float a = tableNoise(rd.xy * scale + timeOffset);
    float b = tableNoise(rd.yz * scale + timeOffset.yx);
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

    // ── 1. 기본 Fog — 거리 비례 회색 ─────────────────────────────────────────
    float baseFog = clamp(0.1 + insideLen / 120.0, 0.0, 1.0);

    // ── 2. 얼룩 Fog — rd 기반 테이블 노이즈 (hash 연산 → UBO 조회로 대체) ────
    float n1 = dirNoise(rd,  5.0, vec2(0.0,        Time * 3.6));
    float n2 = dirNoise(rd, 12.0, vec2(Time * 2.8, Time * 1.8));
    float turbulence = mix(n1, n2, 0.5);
    float blobIntensity = (1.0 - turbulence) * baseFog * 0.75;

    // ── 3. 왜곡 — turbulence 기반 밝기 변조 (P1: 랜덤 UV 재샘플 제거) ────────
    float frameTime = floor(Time * 60.0);
    vec3 sceneDistorted = orig.rgb * (1.0 - baseFog * 0.15 * (1.0 - turbulence));

    // 기본 fog 적용 (회색)
    vec3 afterBaseFog = mix(sceneDistorted, vec3(0.20, 0.20, 0.20), baseFog);
    // 얼룩 fog 추가 (검은색)
    vec3 afterBlob = mix(afterBaseFog, vec3(0.25, 0.03, 0.03), blobIntensity);
    vec3 result    = afterBlob;

    // ── 4. TV 정적 노이즈 (~1% 확률 흰 픽셀) ────────────────────────────────
    float camDistFromCenter = length(DomainCenter.xyz);
    float distOutside  = max(0.0, camDistFromCenter - DomainRadius);
    float staticFade   = 1.0 - clamp(distOutside / 20.0, 0.0, 1.0);

    float staticHash  = hash2(vec2(texCoord.x * 1920.0 + frameTime * 13.7,
                                   texCoord.y * 1080.0 + frameTime *  9.3));
    float staticAlpha = step(0.99, staticHash)
                      * (0.5 + hash2(vec2(staticHash + frameTime, texCoord.x + texCoord.y)) * 0.5)
                      * staticFade;
    result = mix(result, vec3(1.0), staticAlpha * baseFog);

    fragColor = vec4(mix(orig.rgb, result, Alpha), orig.a);
}
