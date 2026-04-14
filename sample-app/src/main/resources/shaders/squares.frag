#version 450

layout (location = 0) in vec3 fragColor;
layout (location = 1) in vec2 fragLocalPx;
layout (location = 2) in float fragRadius;
layout (location = 3) in float fragBorderWidth;
layout (location = 4) in vec3 fragBorderColor;
layout (location = 5) in vec2 fragSquareOrigin;

layout (location = 0) out vec4 outColor;

const float SIZE = 80.0;

float roundedRectSDF(vec2 p, float hx, float hy, float r) {
    vec2 q = abs(p) - vec2(hx - r, hy - r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    float halfSize = SIZE * 0.5;
    vec2 p = fragLocalPx - vec2(halfSize, halfSize);

    float sdf = roundedRectSDF(p, halfSize, halfSize, fragRadius);

    // Outer edge AA over 1px
    float alpha = 1.0 - smoothstep(-0.5, 0.5, sdf);
    if (alpha <= 0.0) discard;

    float borderBlend = 0.0;
    if (fragBorderWidth > 0.0) {
        float inner = -fragBorderWidth;
        // AA on inner border edge everywhere (needed for curved corners).
        // For integer border widths the inner edge lands on a pixel boundary;
        // shift the smoothstep window to [-0.5, 0.5] around that boundary.
        borderBlend = smoothstep(inner - 0.5, inner + 0.5, sdf);
    }

    vec3 color = mix(fragColor, fragBorderColor, borderBlend);
    outColor = vec4(color, alpha);
}
