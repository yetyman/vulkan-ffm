#version 450

layout(push_constant) uniform PushConstants {
    float time;
} pc;

vec2 positions[3] = vec2[](
    vec2(0.0, -0.5),
    vec2(0.5, 0.5),
    vec2(-0.5, 0.5)
);

vec3 colors[3] = vec3[](
    vec3(1.0, 0.0, 0.0),
    vec3(0.0, 1.0, 0.0),
    vec3(0.0, 0.0, 1.0)
);

layout(location = 0) out vec3 fragColor;

void main() {
    // Use instance index to vary position and depth for early Z rejection
    vec2 offset = vec2(
        float(gl_InstanceIndex % 32) * 0.05 - 0.8,  // Spread across X
        float(gl_InstanceIndex / 32) * 0.05 - 0.8   // Spread across Y
    );

    // Front-to-back depth ordering for early Z rejection
    float depth = float(gl_InstanceIndex) / 1000.0 * 0.8;  // 0.0 to 0.8 depth range
    
    vec2 pos = positions[gl_VertexIndex] * 0.06;
    
    // Rotate only half the triangles (even instance indices)
    if (gl_InstanceIndex % 2 == 0) {
        float angle = pc.time * 2.0;
        float s = sin(angle);
        float c = cos(angle);
        mat2 rotation = mat2(c, -s, s, c);
        pos = rotation * pos;
    }
    
    gl_Position = vec4(pos + offset, depth, 1.0);
    fragColor = colors[gl_VertexIndex] * (1.0 - depth * 0.5);  // Darken distant triangles
}
