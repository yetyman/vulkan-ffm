#version 450

layout(location = 0) in vec2 texCoord;
layout(location = 0) out float edgeStrength;

layout(binding = 0) uniform sampler2D colorTexture;
layout(binding = 1) uniform sampler2D depthTexture;

void main() {
    vec2 texelSize = 1.0 / textureSize(colorTexture, 0);
    
    // Sample neighbors for edge detection
    vec3 center = texture(colorTexture, texCoord).rgb;
    vec3 left   = texture(colorTexture, texCoord + vec2(-texelSize.x, 0)).rgb;
    vec3 right  = texture(colorTexture, texCoord + vec2( texelSize.x, 0)).rgb;
    vec3 up     = texture(colorTexture, texCoord + vec2(0, -texelSize.y)).rgb;
    vec3 down   = texture(colorTexture, texCoord + vec2(0,  texelSize.y)).rgb;
    
    // Luminance-based edge detection
    float centerLum = dot(center, vec3(0.299, 0.587, 0.114));
    float leftLum   = dot(left,   vec3(0.299, 0.587, 0.114));
    float rightLum  = dot(right,  vec3(0.299, 0.587, 0.114));
    float upLum     = dot(up,     vec3(0.299, 0.587, 0.114));
    float downLum   = dot(down,   vec3(0.299, 0.587, 0.114));
    
    // Sobel edge detection
    float edgeX = abs(-leftLum + rightLum);
    float edgeY = abs(-upLum + downLum);
    float colorEdge = sqrt(edgeX * edgeX + edgeY * edgeY);
    
    // Depth edge detection
    float centerDepth = texture(depthTexture, texCoord).r;
    float leftDepth   = texture(depthTexture, texCoord + vec2(-texelSize.x, 0)).r;
    float rightDepth  = texture(depthTexture, texCoord + vec2( texelSize.x, 0)).r;
    float upDepth     = texture(depthTexture, texCoord + vec2(0, -texelSize.y)).r;
    float downDepth   = texture(depthTexture, texCoord + vec2(0,  texelSize.y)).r;
    
    float depthEdgeX = abs(-leftDepth + rightDepth);
    float depthEdgeY = abs(-upDepth + downDepth);
    float depthEdge = sqrt(depthEdgeX * depthEdgeX + depthEdgeY * depthEdgeY) * 50.0;
    
    // Combine color and depth edges
    edgeStrength = clamp(max(colorEdge, depthEdge), 0.0, 1.0);
}