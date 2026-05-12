#version 450

layout (location = 0) in vec2 texCoord;
layout (location = 0) out vec4 outColor;

layout (binding = 0) uniform sampler2D colorTexture;

void main() {
    vec2 texelSize = 1.0 / textureSize(colorTexture, 0);

    vec3 center = texture(colorTexture, texCoord).rgb;
    vec3 left   = texture(colorTexture, texCoord + vec2(-texelSize.x, 0)).rgb;
    vec3 right  = texture(colorTexture, texCoord + vec2( texelSize.x, 0)).rgb;
    vec3 up     = texture(colorTexture, texCoord + vec2(0, -texelSize.y)).rgb;
    vec3 down   = texture(colorTexture, texCoord + vec2(0,  texelSize.y)).rgb;

    // Sobel-like edge detection on luminance
    float centerLum = dot(center, vec3(0.299, 0.587, 0.114));
    float leftLum   = dot(left,   vec3(0.299, 0.587, 0.114));
    float rightLum  = dot(right,  vec3(0.299, 0.587, 0.114));
    float upLum     = dot(up,     vec3(0.299, 0.587, 0.114));
    float downLum   = dot(down,   vec3(0.299, 0.587, 0.114));

    float edgeX = abs(-leftLum + rightLum);
    float edgeY = abs(-upLum + downLum);
    float edge = clamp(sqrt(edgeX * edgeX + edgeY * edgeY) * 8.0, 0.0, 1.0);

    // Output: original color with bright green edge overlay
    vec3 edgeColor = mix(center, vec3(0.0, 1.0, 0.2), edge);
    outColor = vec4(edgeColor, 1.0);
}
