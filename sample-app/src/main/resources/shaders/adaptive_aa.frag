#version 450

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 outColor;

layout(binding = 0) uniform sampler2D sceneTexture;

void main() {
    vec2 texelSize = 1.0 / textureSize(sceneTexture, 0);
    
    // Sample center and neighbors
    vec4 center = texture(sceneTexture, texCoord);
    vec4 left   = texture(sceneTexture, texCoord + vec2(-texelSize.x, 0));
    vec4 right  = texture(sceneTexture, texCoord + vec2( texelSize.x, 0));
    vec4 up     = texture(sceneTexture, texCoord + vec2(0, -texelSize.y));
    vec4 down   = texture(sceneTexture, texCoord + vec2(0,  texelSize.y));
    
    // Luminance-based edge detection (more accurate)
    float centerLum = dot(center.rgb, vec3(0.299, 0.587, 0.114));
    float leftLum   = dot(left.rgb,   vec3(0.299, 0.587, 0.114));
    float rightLum  = dot(right.rgb,  vec3(0.299, 0.587, 0.114));
    float upLum     = dot(up.rgb,     vec3(0.299, 0.587, 0.114));
    float downLum   = dot(down.rgb,   vec3(0.299, 0.587, 0.114));
    
    // Sobel edge detection
    float edgeX = abs(-leftLum + rightLum);
    float edgeY = abs(-upLum + downLum);
    float edgeStrength = sqrt(edgeX * edgeX + edgeY * edgeY);
    
    // Balanced AA - moderate threshold and filtering
    if (edgeStrength > 0.2) {
        // Moderate directional filtering
        if (edgeX > edgeY) {
            // Horizontal edge - vertical blend
            outColor = (center * 4.0 + up + down) * 0.1667; // 4:1:1 ratio
        } else {
            // Vertical edge - horizontal blend  
            outColor = (center * 4.0 + left + right) * 0.1667;
        }
    } else {
        // Preserve original
        outColor = center;
    }
}