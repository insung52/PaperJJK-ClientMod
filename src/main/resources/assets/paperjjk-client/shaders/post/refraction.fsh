#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

// Refraction effect parameters
layout(std140) uniform RefractionConfig {
    vec2 EffectCenter;    // Screen space center (0.0 - 1.0)
    float EffectRadius;   // Radius in screen space
    float EffectStrength; // Distortion strength
    int EffectType;       // 0=AO (blue), 1=AKA (red), 2=MURASAKI (purple)
    float EffectDepth;    // Depth buffer value of effect center [0, 1]
    float Time;           // Elapsed time in seconds for animation
};

in vec2 texCoord;
out vec4 fragColor;

void main(){
    // Aspect ratio correction (assume 16:9; screenquad provides normalized coords)
    float aspectRatio = 16.0 / 9.0;

    // EffectCenter.y comes from worldToScreen (screen-space: top=0, bottom=1)
    // texCoord.y is OpenGL texture space (bottom=0, top=1) — flip Y to match
    vec2 flippedCenter = vec2(EffectCenter.x, 1.0 - EffectCenter.y);

    vec2 aspectCoord  = vec2(texCoord.x * aspectRatio, texCoord.y);
    vec2 aspectCenter = vec2(flippedCenter.x * aspectRatio, flippedCenter.y);

    vec2 toCenter = aspectCoord - aspectCenter;
    float dist = length(toCenter);

    float absStrength = abs(EffectStrength);
    float effectRadius = EffectRadius * absStrength;

    // Distortion
    vec2 sampleCoord = texCoord;
    if (dist < effectRadius && dist > 0.0001) {
        float normalizedDist = dist / effectRadius;
        float falloff = 1.0 - smoothstep(0.0, 1.0, normalizedDist);
        float distortAmount = EffectStrength * 0.05 * falloff / dist;
        if (EffectType == 0) distortAmount *= 0.2;
        sampleCoord = clamp(texCoord + toCenter * distortAmount, 0.0, 1.0);
    }

    // Occlusion test: skip effect where geometry is closer than effect center
    float pixelDepth = texture(DepthSampler, texCoord).r;
    if (pixelDepth < EffectDepth - 0.0001) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    vec4 color = texture(InSampler, sampleCoord);

    // Bloom
    float bloomRadiusMultiplier = (EffectType == 0) ? 0.4 : 1.2;
    if (dist < effectRadius * bloomRadiusMultiplier) {
        float normalizedDist = dist / effectRadius;

        float fadeStart = 0.8 * bloomRadiusMultiplier / 1.2;
        float edgeFade = 1.0 - smoothstep(fadeStart, bloomRadiusMultiplier, normalizedDist);

        float bloomFactor = exp(-normalizedDist * 4.0) * 5.0 * absStrength;

        // Rotating spiral — Time drives the rotation speed
        float angle = atan(toCenter.y, toCenter.x);
        float spiral = sin(angle * 6.0 + dist * 10.0 - Time * 3.0) * 0.5 + 0.5;
        bloomFactor *= (0.7 + spiral * 0.3);

        // Animated flicker noise
        float noise = fract(sin(dot(texCoord * 100.0 + Time * 0.5, vec2(12.9898, 78.233))) * 43758.5453);
        bloomFactor *= (0.8 + noise * 0.8);

        bloomFactor *= edgeFade;
        if (EffectType == 0) bloomFactor *= 0.5;

        vec3 bloomColor;
        if (EffectType == 0) {
            bloomColor = vec3(0.2, 0.6, 1.0);
        } else if (EffectType == 1) {
            bloomColor = vec3(1.0, 0.2, 0.3);
        } else {
            bloomColor = vec3(0.8, 0.2, 1.0);
        }

        color.rgb += bloomColor * bloomFactor;
    }

    fragColor = color;
}
