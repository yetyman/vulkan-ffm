#version 450

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inTexCoord;
layout(location = 3) in vec3 inMorphTarget;  // NEW: morph target position
layout(location = 4) in mat4 instanceMatrix;

layout(binding = 0) uniform CameraUBO {
    mat4 viewProj;
} camera;

layout(push_constant) uniform PushConstants {
    float time;
    float lodBlend;  // NEW: 0.0 = current LOD, 1.0 = next LOD
} pc;

layout(location = 0) out vec3 fragColor;
layout(location = 1) out vec2 fragTexCoord;

void main() {
    // Geomorph between current and next LOD
    vec3 position = mix(inPosition, inMorphTarget, pc.lodBlend);
    
    vec4 worldPos = instanceMatrix * vec4(position, 1.0);
    gl_Position = camera.viewProj * worldPos;
    
    fragColor = inNormal;
    fragTexCoord = inTexCoord;
}
