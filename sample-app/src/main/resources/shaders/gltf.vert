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
    // Apply instance transformation and simple camera
    vec4 worldPos = instanceMatrix * vec4(inPosition, 1.0);
    
    // Simple camera: translate back 5 units and scale down
    vec3 cameraPos = worldPos.xyz - vec3(0, 0, 5);
    gl_Position = vec4(cameraPos * 0.2, 1.0);
    
    // Simple color based on normal
    fragColor = normalize(inNormal) * 0.5 + 0.5;
    fragTexCoord = inTexCoord;
}