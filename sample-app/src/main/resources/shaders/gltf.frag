#version 450

layout(location = 0) in vec3 fragColor;
layout(location = 1) in vec2 fragTexCoord;
layout(location = 2) flat in int lodLevel;

layout(location = 0) out vec4 outColor;

layout(push_constant) uniform PushConstants {
    int visualizationMode; // 0=normal, 1=wireframe, 2=lod-color
} push;

void main() {
    vec3 color = fragColor;
    
    // LOD color-coding
    if (push.visualizationMode == 2) {
        vec3 lodColors[5] = vec3[](
            vec3(1.0, 0.0, 0.0),  // LOD0: Red
            vec3(1.0, 0.5, 0.0),  // LOD1: Orange
            vec3(1.0, 1.0, 0.0),  // LOD2: Yellow
            vec3(0.0, 1.0, 0.0),  // LOD3: Green
            vec3(0.0, 0.0, 1.0)   // LOD4: Blue
        );
        color = mix(color, lodColors[lodLevel], 0.6);
    }
    
    outColor = vec4(color, 1.0);
}