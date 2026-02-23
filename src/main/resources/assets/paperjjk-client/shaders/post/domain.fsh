#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

// std140 layout: 5*vec4(80) + float(4) = 84 bytes
// InvViewProj stored as 4 column vectors (column-major, matches GLSL mat4)
layout(std140) uniform DomainConfig {
    vec4 InvViewProjC0;  // column 0 of inverse view-projection matrix
    vec4 InvViewProjC1;  // column 1
    vec4 InvViewProjC2;  // column 2
    vec4 InvViewProjC3;  // column 3
    vec4 CasterFeetPos;  // camera-relative world position of caster's feet (.w unused)
    float ExpandRadius;  // current expanding circle radius in blocks
};

in vec2 texCoord;
out vec4 fragColor;

// Width of the transition zone at the circle boundary (in blocks)
const float BORDER_WIDTH = 0.8;
// Brightness inside the circle (slightly brighter than normal)
const float INNER_BRIGHTNESS = 1.15;
// Darkness outside the circle
const float OUTER_BRIGHTNESS = 0.28;
// Intensity of the white glowing ring at the boundary
const float RING_INTENSITY = 1.2;

void main() {
    vec4 color = texture(InSampler, texCoord);
    float depth = texture(DepthSampler, texCoord).r;

    // Sky / far-plane pixels: just darken, skip world-position reconstruction
    if (depth >= 0.9999) {
        fragColor = vec4(color.rgb * OUTER_BRIGHTNESS, color.a);
        return;
    }

    // Reconstruct camera-relative world position from depth buffer
    // texCoord (0,0)=bottom-left → NDC (-1,-1); (1,1)=top-right → NDC (1,1)
    vec2 ndc    = texCoord * 2.0 - 1.0;
    float ndcZ  = depth * 2.0 - 1.0;
    vec4 clipPos = vec4(ndc, ndcZ, 1.0);

    mat4 invViewProj = mat4(InvViewProjC0, InvViewProjC1, InvViewProjC2, InvViewProjC3);
    vec4 worldPos    = invViewProj * clipPos;
    worldPos /= worldPos.w;

    // Horizontal (XZ) distance from caster's feet
    float horizDist = length(worldPos.xz - CasterFeetPos.xz);

    // insideFactor: 1.0 = fully inside circle, 0.0 = fully outside
    float insideFactor = 1.0 - smoothstep(
        ExpandRadius - BORDER_WIDTH,
        ExpandRadius + BORDER_WIDTH,
        horizDist
    );

    // Blend brightness from dark (outside) to bright (inside)
    float brightness = mix(OUTER_BRIGHTNESS, INNER_BRIGHTNESS, insideFactor);

    // White glowing ring: peaks at the midpoint of the transition (insideFactor ≈ 0.5)
    float ringPeak = 1.0 - abs(insideFactor * 2.0 - 1.0); // 0 at edges, 1 at boundary
    float ringGlow = pow(ringPeak, 2.5) * RING_INTENSITY;

    vec3 result = color.rgb * brightness + vec3(ringGlow);
    fragColor = vec4(result, color.a);
}
