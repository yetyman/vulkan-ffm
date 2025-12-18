#version 450

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inTexCoord;
layout(location = 3) in mat4 instanceMatrix;

layout(push_constant) uniform PushConstants {
    float time;
} pc;

layout(location = 0) out vec3 fragColor;
layout(location = 1) out vec2 fragTexCoord;

void main() {
    // Transform to world space
    vec4 worldPos = instanceMatrix * vec4(inPosition, 1.0);
    
    // Simple perspective projection (no uniform buffers needed)
    mat4 view = mat4(
        1.0, 0.0, 0.0, 0.0,
        0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, -5.0, 1.0  // Camera at (0,0,5) looking at origin
    );
    
    mat4 projection = mat4(
        1.0, 0.0, 0.0, 0.0,
        0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, -1.0, -1.0,
        0.0, 0.0, -2.0, 0.0  // Simple perspective
    );
    
    gl_Position = projection * view * worldPos;
    
    // Use texture coordinates for color variation
    fragColor = inNormal;
    fragTexCoord = inTexCoord;
}