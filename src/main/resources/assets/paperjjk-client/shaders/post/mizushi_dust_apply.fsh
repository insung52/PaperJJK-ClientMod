#version 330

uniform sampler2D InSampler;  // 풀해상도 scene
uniform sampler2D FogSampler; // 반해상도 fog (GL_LINEAR 업스케일로 blit됨)

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec4 fog   = texture(FogSampler, texCoord);
    fragColor  = vec4(mix(scene.rgb, fog.rgb, fog.a), scene.a);
}
