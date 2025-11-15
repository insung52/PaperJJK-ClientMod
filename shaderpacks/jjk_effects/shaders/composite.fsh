#version 120

/**
 * JJK Effects Composite Shader
 * Applies refraction and bloom effects for technique visualizations
 */

uniform sampler2D colortex0;  // Main color buffer
uniform sampler2D depthtex0;  // Depth buffer

uniform float viewWidth;
uniform float viewHeight;
uniform vec3 cameraPosition;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;

varying vec2 texcoord;

// Effect strength (adjustable in shader options)
const float JJK_EFFECT_STRENGTH = 0.05;
const float JJK_BLOOM_STRENGTH = 0.3;

// Hard-coded effect positions (will be replaced with uniforms from mod)
// Effect 1: Blue at (0, 150, 0)
const vec3 effect1Pos = vec3(0.0, 150.0, 0.0);
const float effect1Radius = 5.0;
const vec3 effect1Color = vec3(0.2, 0.4, 0.8);

// Effect 2: Yellow/Red at (10, 150, 10)
const vec3 effect2Pos = vec3(10.0, 150.0, 10.0);
const float effect2Radius = 2.5;
const vec3 effect2Color = vec3(1.0, 0.3, 0.1);  // Red-orange for bloom

/**
 * Convert screen space to world space
 */
vec3 screenToWorld(vec2 screenPos, float depth) {
    vec4 clipPos = vec4(screenPos * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = gbufferProjectionInverse * clipPos;
    viewPos /= viewPos.w;
    vec4 worldPos = gbufferModelViewInverse * viewPos;
    return worldPos.xyz + cameraPosition;
}

/**
 * Project world position to screen space
 */
vec2 worldToScreen(vec3 worldPos) {
    vec3 relativePos = worldPos - cameraPosition;
    vec4 viewPos = inverse(gbufferModelViewInverse) * vec4(relativePos, 1.0);
    vec4 clipPos = inverse(gbufferProjectionInverse) * viewPos;
    clipPos.xyz /= clipPos.w;
    return clipPos.xy * 0.5 + 0.5;
}

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
        return texture2D(colortex0, distortedUV).rgb;
    }

    return texture2D(colortex0, uv).rgb;
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
    vec2 uv = texcoord;
    vec3 color = texture2D(colortex0, uv).rgb;

    // Simple approximation: assume effects are at fixed screen positions
    // In a real implementation, we'd project world positions to screen space

    // Effect 1 (Blue): Assume it's at screen center for testing
    vec2 effect1Screen = vec2(0.5, 0.5);
    float effect1ScreenRadius = 0.2;

    // Apply refraction for effect 1
    color = applyRefraction(uv, effect1Screen, effect1ScreenRadius, JJK_EFFECT_STRENGTH);

    // Add blue glow
    color = addBloom(color, uv, effect1Screen, effect1ScreenRadius, effect1Color, JJK_BLOOM_STRENGTH * 0.5);

    // Effect 2 (Yellow/Red): Assume it's offset from center
    vec2 effect2Screen = vec2(0.6, 0.5);
    float effect2ScreenRadius = 0.1;

    // Apply refraction for effect 2
    color = applyRefraction(uv, effect2Screen, effect2ScreenRadius, JJK_EFFECT_STRENGTH * 0.8);

    // Add RED bloom (stronger)
    color = addBloom(color, uv, effect2Screen, effect2ScreenRadius, effect2Color, JJK_BLOOM_STRENGTH * 1.5);

    gl_FragColor = vec4(color, 1.0);
}
