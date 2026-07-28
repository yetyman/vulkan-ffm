#version 450

layout (location = 0) in vec4 fragColor;
layout (location = 1) in vec2 fragUV;

layout (location = 0) out vec4 outColor;

void main() {
    // Simple solid color output. UV available for future texture/nine-slice border effects.
    outColor = fragColor;
}
