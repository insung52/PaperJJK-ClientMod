#version 330

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main(){
    vec4 color = texture(InSampler, texCoord);

    // Red tint for testing
    fragColor = vec4(color.r * 1.5, color.g * 0.5, color.b * 0.5, color.a);
}
