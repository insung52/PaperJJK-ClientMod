#version 330

out vec2 texCoord;

void main(){
    // Full-screen quad using gl_VertexID
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
    texCoord = uv;
}
