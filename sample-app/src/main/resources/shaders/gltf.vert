#version 450

layout(location = 3) in mat4 instanceMatrix;

layout(push_constant) uniform PushConstants {
    float time;
} pc;

layout(location = 0) out vec3 fragColor;
layout(location = 1) out vec2 fragTexCoord;

vec2 positions[3] = vec2[](
    vec2(-0.8, -0.8),
    vec2(0.8, -0.8),
    vec2(0.0, 0.8)
);

void main() {
    // Use the actual instanceMatrix to transform the triangle
    vec4 worldPos = instanceMatrix * vec4(positions[gl_VertexIndex], 0.3, 1.0);
    gl_Position = worldPos;
    
    fragColor = vec3(1.0, 0.0, 0.0); // Red
    fragTexCoord = vec2(0.0, 0.0);
}