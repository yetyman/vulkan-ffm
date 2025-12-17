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
    // Use vertex position from buffer
    vec4 worldPos = instanceMatrix * vec4(inPosition, 1.0);
    gl_Position = worldPos;
    
    // Use texture coordinates for color variation
    fragColor = vec3(inTexCoord, 0.5);
    fragTexCoord = inTexCoord;
}