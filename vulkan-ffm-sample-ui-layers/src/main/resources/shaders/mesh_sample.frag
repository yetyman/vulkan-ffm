#version 450

layout (location = 0) in vec3 fragNormal;
layout (location = 1) in vec2 fragUV;

layout (location = 0) out vec4 outColor;

void main() {
    // Simple directional lighting
    vec3 lightDir = normalize(vec3(1.0, 1.0, 1.0));
//    float diffuse = max(dot(normalize(fragNormal), lightDir), 0.0);
    float r = max(dot(normalize(fragNormal), vec3(1.0,1.0,-1.0)), 0.0);
    float g = max(dot(normalize(fragNormal), vec3(0.0,0.0,-1.0)), 0.0);
    float b = max(dot(normalize(fragNormal), vec3(-1.0,1.0,-1.0)), 0.0);
    float ambient = 0.2;
    vec3 color = (fragNormal+vec3(r, g, b)) / 2.0 * ambient;
    outColor = vec4(color, 1.0);
}
