#version 450

layout (push_constant) uniform PC {
    int width;
    int height;
} pc;

layout (set = 0, binding = 0) readonly buffer Display { vec4 pixels[]; } displayBuf;

layout (location = 0) in vec2 texCoord;
layout (location = 0) out vec4 outColor;

void main() {
    int x = clamp(int(texCoord.x * float(pc.width)), 0, pc.width - 1);
    int y = clamp(int(texCoord.y * float(pc.height)), 0, pc.height - 1);
    vec4 pixel = displayBuf.pixels[y * pc.width + x];
    // Tonemap and gamma
    vec3 col = pixel.rgb / (1.0 + pixel.rgb);
    outColor = vec4(pow(col, vec3(1.0 / 2.2)), 1.0);
}
