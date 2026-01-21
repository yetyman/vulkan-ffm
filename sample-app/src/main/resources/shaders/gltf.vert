#version 450

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inTexCoord;
layout(location = 3) in mat4 instanceMatrix;

layout(binding = 0) uniform CameraUBO {
    mat4 viewProj;
} camera;

layout(location = 0) out vec3 fragColor;
layout(location = 1) out vec2 fragTexCoord;

void main() {
    vec4 worldPos = instanceMatrix * vec4(inPosition, 1.0);
    gl_Position = camera.viewProj * worldPos;
    
    fragColor = inNormal;
    fragTexCoord = inTexCoord;
}