#version 330

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

layout(std140) uniform NoiseTable {
    vec4 Noise[1024];
};

in vec2 texCoord;
out vec4 fragColor;

// TV 정적 노이즈용 (고주파 랜덤 패턴, hash 1회로 충분)
float hash(vec2 p) {
    p = fract(p * vec2(0.1031, 0.1030));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

float lookupCorner(int x, int y) {
    int idx = (y & 63) * 64 + (x & 63);
    return Noise[idx >> 2][idx & 3];
}

float tableNoise(vec2 p) {
    vec2  uv = p * 64.0;
    ivec2 i  = ivec2(floor(uv));
    vec2  f  = fract(uv);
    f = f * f * (3.0 - 2.0 * f);
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
        fragColor = vec4(0.0);
        return;
    }

    vec4 ndc   = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    mat4 invVP = mat4(InvViewProjC0, InvViewProjC1, InvViewProjC2, InvViewProjC3);
    vec4 wp4   = invVP * ndc;
    vec3 worldPos = wp4.xyz / wp4.w;

    vec3  rd      = normalize(worldPos);
    float pixDist = isSky ? 1e10 : length(worldPos);

    vec3  domC = DomainCenter.xyz;
    vec3  oc   = -domC;
    float b    = dot(oc, rd);
    float cVal = dot(oc, oc) - DomainRadius * DomainRadius;
    float disc = b * b - cVal;

    if (disc < 0.0) {
        fragColor = vec4(0.0);
        return;
    }

    float sq        = sqrt(disc);
    float tEntry    = max(0.0, -b - sq);
    float tExit     = min(pixDist, -b + sq);
    float insideLen = max(0.0, tExit - tEntry);

    if (insideLen < 0.01) {
        fragColor = vec4(0.0);
        return;
    }

    float baseFog = clamp(0.1 + insideLen / 120.0, 0.0, 1.0);
    float fogAlpha = baseFog * Alpha;
    if (fogAlpha < 0.002) {
        fragColor = vec4(0.0);
        return;
    }

    float n1 = dirNoise(rd,  5.0, vec2(0.0,        Time * 3.6));
    float n2 = dirNoise(rd, 12.0, vec2(Time * 2.8, Time * 1.8));
    float turbulence = mix(n1, n2, 0.5);
    float blobIntensity = (1.0 - turbulence) * baseFog * 0.75;

    // fog 색상: 회색 ↔ 검붉은 얼룩
    vec3 fogColor = mix(vec3(0.20, 0.20, 0.20), vec3(0.25, 0.03, 0.03), blobIntensity);

    // TV 정적 노이즈 — 카메라가 영역 안에 있을 때만 표시
    float camDistFromCenter = length(DomainCenter.xyz);
    float staticFade = 1.0 - clamp((camDistFromCenter - DomainRadius) / 20.0, 0.0, 1.0);
    if (staticFade > 0.002) {
        float frameTime   = floor(Time * 60.0);
        float staticHash  = hash(vec2(texCoord.x * 1920.0 + frameTime * 13.7,
                                      texCoord.y * 1080.0 + frameTime *  9.3));
        float staticAlpha = step(0.99, staticHash)
                          * (0.5 + hash(vec2(staticHash + frameTime, texCoord.x + texCoord.y)) * 0.5)
                          * staticFade;
        fogColor = mix(fogColor, vec3(1.0), staticAlpha * baseFog);
    }

    // 출력: fog 색상 + 블렌딩 alpha
    fragColor = vec4(fogColor, fogAlpha);
}
