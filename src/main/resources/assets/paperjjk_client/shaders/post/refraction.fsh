#version 330

uniform sampler2D InSampler;

// Refraction effect parameters
layout(std140) uniform RefractionConfig {
    vec2 EffectCenter;   // Screen space center (0.0 - 1.0)
    float EffectRadius;   // Radius in screen space
    float EffectStrength; // Distortion strength
};

in vec2 texCoord;
out vec4 fragColor;

void main(){
    // Calculate distance from effect center
    vec2 toCenter = texCoord - EffectCenter;
    float dist = length(toCenter);

    // Apply refraction inside the radius
    vec2 sampleCoord = texCoord;

    if (dist < EffectRadius && dist > 0.0) {
        // Gravitational lensing formula
        // Objects closer to center are more distorted
        float normalizedDist = dist / EffectRadius;

        // Smooth falloff using smoothstep
        float falloff = 1.0 - smoothstep(0.0, 1.0, normalizedDist);

        // Distortion amount based on distance
        float distortAmount = EffectStrength * falloff / dist;

        // Bend light inward (gravitational attraction)
        sampleCoord = texCoord + toCenter * distortAmount;
    }

    // Sample the texture with distorted coordinates
    fragColor = texture(InSampler, sampleCoord);
}
