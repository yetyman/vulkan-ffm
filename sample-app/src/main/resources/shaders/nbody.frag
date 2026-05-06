#version 450

layout (location = 0) in float speed;
layout (location = 0) out vec4 outColor;

void main() {
    // Radial soft dot so points aren't hard squares
    vec2 coord = gl_PointCoord * 2.0 - 1.0;
    float r = dot(coord, coord);
    if (r > 1.0) discard;
    float alpha = 1.0 - r;

    // Blue (slow) -> white (medium) -> orange (fast)
    float t = clamp(speed * 8.0, 0.0, 1.0);
    vec3 cold = vec3(0.2, 0.4, 1.0);
    vec3 hot  = vec3(1.0, 0.5, 0.1);
    vec3 col  = mix(cold, hot, t);

    outColor = vec4(col * alpha, alpha);
}
