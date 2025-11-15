#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform vec2 InSize;
uniform vec2 EffectCenter;
uniform float EffectRadius;
uniform float EffectStrength;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    // Calculate distance from effect center
    vec2 toCenter = texCoord - EffectCenter;
    float dist = length(toCenter);

    // Only apply effect within radius
    if (dist < EffectRadius && dist > 0.001) {
        // Normalize direction
        vec2 direction = normalize(toCenter);

        // Smooth falloff from center to edge
        float falloff = smoothstep(EffectRadius, 0.0, dist);

        // Calculate distortion strength
        float distortionAmount = EffectStrength * falloff;

        // Refraction: pull pixels toward center (gravity lens effect)
        vec2 distortion = -direction * distortionAmount;
        vec2 distortedUV = texCoord + distortion;

        // Sample with distorted UV
        vec3 color = texture(DiffuseSampler, distortedUV).rgb;

        // Add blue glow at the edges
        float glowStrength = falloff * 0.3;
        color += vec3(0.2, 0.4, 0.8) * glowStrength;

        fragColor = vec4(color, 1.0);
    } else {
        // Outside effect radius - render normally
        fragColor = texture(DiffuseSampler, texCoord);
    }
}
