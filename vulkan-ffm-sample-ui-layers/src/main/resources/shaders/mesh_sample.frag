#version 450

layout (location = 0) in vec3 fragNormal;
layout (location = 1) in vec2 fragUV;

layout (location = 0) out vec4 outColor;

void main() {
    // Simple directional lighting
    vec3 lightDir = normalize(vec3(1.0, 1.0, 1.0));
    float diffuse = max(dot(normalize(fragNormal), lightDir), 0.0);
    float ambient = 0.2;
    vec3 color = vec3(0.6, 0.7, 0.9) * (ambient + diffuse * 0.8);
    outColor = vec4(color, 1.0);
}
