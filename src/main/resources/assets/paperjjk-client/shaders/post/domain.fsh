#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

// std140 layout: 5*vec4(80) + 3*float(12) = 92 bytes
layout(std140) uniform DomainConfig {
    vec4 InvViewProjC0;   // column 0 of inverse view-projection matrix
    vec4 InvViewProjC1;   // column 1
    vec4 InvViewProjC2;   // column 2
    vec4 InvViewProjC3;   // column 3
    vec4 CasterFeetPos;   // camera-relative world position of caster's feet (.w unused)
    float ExpandRadius;   // white circle radius in blocks (0 when power <= expansionDelay)
    float DarkRadius;     // inner dark zone radius = power/100 * MAX_RADIUS * 3 (blocks)
    float DarknessLevel;  // inner zone darkness (0.0 = no change, 0.5 = 50% darker)
};

in vec2 texCoord;
out vec4 fragColor;

// Gradient zone width = DarkRadius * (3/2 - 1) = DarkRadius/2
// i.e. gradient spans DarkRadius to DarkRadius*(3/2)
const float GRAD_SCALE    = 3.0 / 2.0;  // outer gradient boundary = DarkRadius * GRAD_SCALE

// White ring parameters
const float BORDER_WIDTH     = 0.3;   // smoothstep half-width in blocks
const float INNER_BRIGHTNESS = 1.1;   // brightness inside the white circle

void main() {
    vec4 color = texture(InSampler, texCoord);
    float depth = texture(DepthSampler, texCoord).r;

    // ── Sky / far-plane: use camera→caster distance instead of pixel world pos ──
    // CasterFeetPos is camera-relative; camera is at origin (0,0,0).
    // So length(CasterFeetPos.xz) = horizontal distance from viewer to caster.
    if (depth >= 0.9999) {
        float camDist = length(CasterFeetPos.xz);
        float skyDarkFactor = 0.0;
        if (DarkRadius > 0.001) {
            float gradEnd = DarkRadius * GRAD_SCALE;
            skyDarkFactor = 1.0 - smoothstep(DarkRadius, gradEnd, camDist);
        }
        float skyLuma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        float skyExemption = smoothstep(0.85, 0.98, skyLuma);
        float skyBrightness = mix(1.0 - DarknessLevel * skyDarkFactor, 1.0, skyExemption);
        fragColor = vec4(color.rgb * skyBrightness, color.a);
        return;
    }

    // ── Reconstruct camera-relative world position from depth ──
    vec2 ndc     = texCoord * 2.0 - 1.0;
    float ndcZ   = depth * 2.0 - 1.0;
    vec4 clipPos = vec4(ndc, ndcZ, 1.0);

    mat4 invViewProj = mat4(InvViewProjC0, InvViewProjC1, InvViewProjC2, InvViewProjC3);
    vec4 worldPos    = invViewProj * clipPos;
    worldPos /= worldPos.w;

    // Horizontal (XZ) distance from caster's feet
    float horizDist = length(worldPos.xz - CasterFeetPos.xz);

    // ── Dark zone ──────────────────────────────────────────────────────────
    // Inner zone (horizDist <= DarkRadius): full DarknessLevel darkness
    // Gradient zone (DarkRadius to DarkRadius*GRAD_SCALE): smooth fade to 0
    // Outside gradient: no effect (brightness = 1.0)
    float darkFactor = 0.0;
    if (DarkRadius > 0.001) {
        float gradEnd = DarkRadius * GRAD_SCALE;
        darkFactor = 1.0 - smoothstep(DarkRadius, gradEnd, horizDist);
    }

    // ── Emissive/fullbright exemption ──────────────────────────────────────
    // Pixels with very high luminance (fullbright particles, etc.) are exempt
    // from darkening so they punch through the domain shadow.
    // smoothstep(0.85, 0.98): luma < 0.85 → fully darkened, luma > 0.98 → fully exempt.
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    float emissiveExemption = smoothstep(0.85, 0.98, luma);

    float brightness = mix(1.0 - DarknessLevel * darkFactor, 1.0, emissiveExemption);

    // ── Inner bright zone ──────────────────────────────────────────────────
    // Only active when ExpandRadius > 0 (power > expansionDelay)
    if (ExpandRadius > 0.001) {
        float insideFactor = 1.0 - smoothstep(
            ExpandRadius - BORDER_WIDTH,
            ExpandRadius + BORDER_WIDTH,
            horizDist
        );

        // Inside the white circle: restore brightness to INNER_BRIGHTNESS
        // (overrides the dark zone within the circle)
        brightness = mix(brightness, INNER_BRIGHTNESS, insideFactor);
    }

    fragColor = vec4(color.rgb * brightness, color.a);
}
