#version 120

// Pass-through fragment shader for terrain rendering
// This ensures blocks are rendered normally

uniform sampler2D texture;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 color = texture2D(texture, texcoord) * glcolor;
    gl_FragData[0] = color;
}
