#version 330 core

// Vertex shader for fullscreen post-processing
// Renders a fullscreen quad with texture coordinates

in vec2 in_position;
out vec2 texCoord;

void main() {
    gl_Position = vec4(in_position, 0.0, 1.0);
    texCoord = in_position * 0.5 + 0.5; // Convert from [-1,1] to [0,1]
}
