#version 450

layout (push_constant) uniform PC {
    int width;
    int height;
} pc;

layout (set = 0, binding = 0) readonly buffer Cells { uint cells[]; } cellBuf;

layout (location = 0) in vec2 texCoord;
layout (location = 0) out vec4 outColor;

void main() {
    int x = clamp(int(texCoord.x * float(pc.width)), 0, pc.width - 1);
    int y = clamp(int(texCoord.y * float(pc.height)), 0, pc.height - 1);
    uint alive = cellBuf.cells[y * pc.width + x];
    outColor = alive != 0u ? vec4(0.0, 1.0, 0.2, 1.0) : vec4(0.02, 0.02, 0.02, 1.0);
}
