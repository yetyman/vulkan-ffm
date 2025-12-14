#version 450

layout(location = 0) in vec2 texCoord;
layout(location = 0) out float edgeStrength;

void main() {
    // Simple test pattern for now
    edgeStrength = sin(texCoord.x * 10.0) * sin(texCoord.y * 10.0) * 0.5 + 0.5;
}