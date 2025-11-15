#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

// Camera uniforms
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;
uniform vec3 CameraPosition;

// Domain sphere uniforms
uniform vec3 DomainCenter;
uniform float DomainRadius;
uniform vec4 DomainColor;
uniform int DomainActive;

in vec2 texCoord;
out vec4 fragColor;

// Convert screen space to world space
vec3 screenToWorld(vec2 screenPos, float depth) {
    vec4 clipSpace = vec4(screenPos * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewSpace = InvProjMat * clipSpace;
    viewSpace /= viewSpace.w;
    vec4 worldSpace = InvViewMat * viewSpace;
    return worldSpace.xyz;
}

// Ray-sphere intersection
// Returns vec2(near, far) distance along ray, or vec2(-1.0) if no hit
vec2 raySphereIntersect(vec3 rayOrigin, vec3 rayDir, vec3 sphereCenter, float sphereRadius) {
    vec3 oc = rayOrigin - sphereCenter;
    float b = dot(oc, rayDir);
    float c = dot(oc, oc) - sphereRadius * sphereRadius;
    float discriminant = b * b - c;

    if (discriminant < 0.0) {
        return vec2(-1.0);
    }

    float sqrtD = sqrt(discriminant);
    float t1 = -b - sqrtD;
    float t2 = -b + sqrtD;

    return vec2(t1, t2);
}

// Fresnel effect for edge glow
float fresnel(vec3 rayDir, vec3 normal, float power) {
    float facing = abs(dot(rayDir, normal));
    return pow(1.0 - facing, power);
}

void main() {
    vec4 originalColor = texture(DiffuseSampler, texCoord);
    float depth = texture(DiffuseDepthSampler, texCoord).r;

    // If no active domain, just output original
    if (DomainActive == 0) {
        fragColor = originalColor;
        return;
    }

    // Reconstruct world position
    vec3 worldPos = screenToWorld(texCoord, depth);
    vec3 rayDir = normalize(worldPos - CameraPosition);

    // Ray-sphere intersection test
    vec2 hit = raySphereIntersect(CameraPosition, rayDir, DomainCenter, DomainRadius);

    if (hit.x > 0.0) {
        // We hit the sphere
        vec3 hitPoint = CameraPosition + rayDir * hit.x;
        vec3 normal = normalize(hitPoint - DomainCenter);

        // Calculate fresnel for edge glow
        float fresnelValue = fresnel(rayDir, normal, 3.0);

        // Mix domain color with fresnel
        vec4 sphereColor = DomainColor;
        sphereColor.a = DomainColor.a + fresnelValue * 0.3;

        // Blend with original color
        fragColor = mix(originalColor, sphereColor, sphereColor.a);
    } else {
        // No hit, output original
        fragColor = originalColor;
    }
}
