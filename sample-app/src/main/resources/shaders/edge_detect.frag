#version 450

layout(location = 0) in vec2 texCoord;
layout(location = 0) out float edgeStrength;

layout(binding = 0) uniform sampler2D colorTexture;
layout(binding = 1) uniform sampler2D depthTexture;

void main() {
    vec2 texelSize = 1.0 / textureSize(colorTexture, 0);
    
    // Sample neighbors for edge detection with clamped coordinates
    vec2 leftCoord  = clamp(texCoord + vec2(-texelSize.x, 0), vec2(0), vec2(1));
    vec2 rightCoord = clamp(texCoord + vec2( texelSize.x, 0), vec2(0), vec2(1));
    vec2 upCoord    = clamp(texCoord + vec2(0, -texelSize.y), vec2(0), vec2(1));
    vec2 downCoord  = clamp(texCoord + vec2(0,  texelSize.y), vec2(0), vec2(1));
    
    vec3 center = texture(colorTexture, texCoord).rgb;
    vec3 left   = texture(colorTexture, leftCoord).rgb;
    vec3 right  = texture(colorTexture, rightCoord).rgb;
    vec3 up     = texture(colorTexture, upCoord).rgb;
    vec3 down   = texture(colorTexture, downCoord).rgb;
    
    // Combined luminance and color edge detection
    float centerLum = dot(center, vec3(0.299, 0.587, 0.114));
    float leftLum   = dot(left,   vec3(0.299, 0.587, 0.114));
    float rightLum  = dot(right,  vec3(0.299, 0.587, 0.114));
    float upLum     = dot(up,     vec3(0.299, 0.587, 0.114));
    float downLum   = dot(down,   vec3(0.299, 0.587, 0.114));
    
    float lumEdgeX = abs(-leftLum + rightLum);
    float lumEdgeY = abs(-upLum + downLum);
    float lumEdge = sqrt(lumEdgeX * lumEdgeX + lumEdgeY * lumEdgeY);
    
    vec3 colorEdgeX = abs(-left + right);
    vec3 colorEdgeY = abs(-up + down);
    float colorEdge = length(colorEdgeX) + length(colorEdgeY);
    
    // Boost edge detection in dark areas
    float brightness = max(centerLum, 0.01);
    float darkBoost = 1.0 / sqrt(brightness);
    
    float combinedEdge = max(lumEdge, colorEdge) * 5.0 * darkBoost;
    
    // Depth edge detection
    float centerDepth = texture(depthTexture, texCoord).r;
    float leftDepth   = texture(depthTexture, leftCoord).r;
    float rightDepth  = texture(depthTexture, rightCoord).r;
    float upDepth     = texture(depthTexture, upCoord).r;
    float downDepth   = texture(depthTexture, downCoord).r;
    
    float depthEdgeX = abs(-leftDepth + rightDepth);
    float depthEdgeY = abs(-upDepth + downDepth);
    float depthEdge = sqrt(depthEdgeX * depthEdgeX + depthEdgeY * depthEdgeY) * 50.0;
    
    // Combine color and depth edges
    edgeStrength = clamp(max(combinedEdge, depthEdge), 0.0, 1.0);
}