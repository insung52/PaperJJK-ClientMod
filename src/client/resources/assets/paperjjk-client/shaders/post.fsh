#version 330 core

/**
 * JJK Post-Processing Fragment Shader
 * Applies refraction and bloom effects for technique visualizations
 */

uniform sampler2D textureSampler;
uniform float viewWidth;
uniform float viewHeight;

// Effect 1 (Blue)
uniform int effect1Enabled;
uniform vec2 effect1ScreenPos;
uniform float effect1ScreenRadius;

// Effect 2 (Yellow/Red)
uniform int effect2Enabled;
uniform vec2 effect2ScreenPos;
uniform float effect2ScreenRadius;

in vec2 texCoord;
out vec4 fragColor;

// Effect parameters
const float JJK_EFFECT_STRENGTH = 0.05;
const float JJK_BLOOM_STRENGTH = 0.3;

const vec3 effect1Color = vec3(0.2, 0.4, 0.8); // Blue
const vec3 effect2Color = vec3(1.0, 0.3, 0.1); // Red-orange

/**
 * Apply refraction effect around a point
 */
vec3 applyRefraction(vec2 uv, vec2 effectCenter, float effectRadius, float strength) {
    vec2 toCenter = uv - effectCenter;
    float dist = length(toCenter);

    if (dist < effectRadius && dist > 0.001) {
        vec2 direction = normalize(toCenter);
        float falloff = smoothstep(effectRadius, 0.0, dist);
        vec2 distortion = -direction * strength * falloff;
        vec2 distortedUV = uv + distortion;

        // Clamp to screen bounds
        distortedUV = clamp(distortedUV, vec2(0.0), vec2(1.0));
        return texture(textureSampler, distortedUV).rgb;
    }

    return texture(textureSampler, uv).rgb;
}

/**
 * Add bloom/glow effect
 */
vec3 addBloom(vec3 color, vec2 uv, vec2 effectCenter, float effectRadius, vec3 glowColor, float strength) {
    vec2 toCenter = uv - effectCenter;
    float dist = length(toCenter);

    if (dist < effectRadius) {
        float falloff = smoothstep(effectRadius, 0.0, dist);
        color += glowColor * falloff * strength;
    }

    return color;
}

void main() {
    vec2 uv = texCoord;
    vec3 color = texture(textureSampler, uv).rgb;

    // Apply Effect 1 (Blue)
    if (effect1Enabled == 1) {
        color = applyRefraction(uv, effect1ScreenPos, effect1ScreenRadius, JJK_EFFECT_STRENGTH);
        color = addBloom(color, uv, effect1ScreenPos, effect1ScreenRadius, effect1Color, JJK_BLOOM_STRENGTH * 0.5);
    }

    // Apply Effect 2 (Yellow/Red)
    if (effect2Enabled == 1) {
        color = applyRefraction(uv, effect2ScreenPos, effect2ScreenRadius, JJK_EFFECT_STRENGTH * 0.8);
        color = addBloom(color, uv, effect2ScreenPos, effect2ScreenRadius, effect2Color, JJK_BLOOM_STRENGTH * 1.5);
    }

    fragColor = vec4(color, 1.0);
}
