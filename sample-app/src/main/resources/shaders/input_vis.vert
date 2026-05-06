#version 450

// Per-instance data: 9 floats
// [0] cx    center x (NDC)
// [1] cy    center y (NDC)
// [2] hw    half-width (NDC)
// [3] hh    half-height (NDC)
// [4] cr    color red
// [5] cg    color green
// [6] cb    color blue
// [7] ca    color alpha
// [8] angle rotation in radians
layout(set = 0, binding = 0) readonly buffer Instances {
    float data[];
} instances;

layout(location = 0) out vec4 fragColor;

const vec2 quadVerts[6] = vec2[](
    vec2(-1.0, -1.0), vec2( 1.0, -1.0), vec2( 1.0,  1.0),
    vec2(-1.0, -1.0), vec2( 1.0,  1.0), vec2(-1.0,  1.0)
);

void main() {
    int stride = 9;
    int base   = gl_InstanceIndex * stride;

    float cx    = instances.data[base + 0];
    float cy    = instances.data[base + 1];
    float hw    = instances.data[base + 2];
    float hh    = instances.data[base + 3];
    float cr    = instances.data[base + 4];
    float cg    = instances.data[base + 5];
    float cb    = instances.data[base + 6];
    float ca    = instances.data[base + 7];
    float angle = instances.data[base + 8];

    vec2 local = quadVerts[gl_VertexIndex] * vec2(hw, hh);

    float cosA = cos(angle);
    float sinA = sin(angle);
    float rx = local.x * cosA - local.y * sinA;
    float ry = local.x * sinA + local.y * cosA;

    gl_Position = vec4(cx + rx, cy + ry, 0.0, 1.0);
    fragColor   = vec4(cr, cg, cb, ca);
}
