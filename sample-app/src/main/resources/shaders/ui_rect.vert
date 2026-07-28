#version 450

layout (push_constant) uniform PC {
    float windowW;
    float windowH;
} pc;

// Pre-expanded vertex data: 8 floats per vertex (x, y, u, v, r, g, b, a)
layout (set = 0, binding = 0) readonly buffer VertexData {
    float data[];
} vertices;

layout (location = 0) out vec4 fragColor;
layout (location = 1) out vec2 fragUV;

void main() {
    int base = gl_VertexIndex * 8;

    float px = vertices.data[base + 0];
    float py = vertices.data[base + 1];
    float u  = vertices.data[base + 2];
    float v  = vertices.data[base + 3];
    float r  = vertices.data[base + 4];
    float g  = vertices.data[base + 5];
    float b  = vertices.data[base + 6];
    float a  = vertices.data[base + 7];

    // Convert pixel coords to NDC
    vec2 ndc = (vec2(px, py) / vec2(pc.windowW, pc.windowH)) * 2.0 - 1.0;
    gl_Position = vec4(ndc, 0.0, 1.0);

    fragColor = vec4(r, g, b, a);
    fragUV = vec2(u, v);
}
