#version 450

struct ControlPoint {
    vec2 pos;
    vec4 color;
};

layout (set = 0, binding = 0) readonly buffer Points { ControlPoint pts[]; } buf;

layout (location = 0) out vec4 fragColor;

void main() {
    ControlPoint cp = buf.pts[gl_VertexIndex];
    gl_Position = vec4(cp.pos, 0.0, 1.0);
    fragColor = cp.color;
}
