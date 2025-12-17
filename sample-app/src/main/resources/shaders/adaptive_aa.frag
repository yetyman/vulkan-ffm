#version 450

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 outColor;

layout(binding = 0) uniform sampler2D currentFrame;
layout(binding = 1) uniform sampler2D depthTexture;
layout(binding = 2) uniform sampler2D previousFrame;
layout(binding = 3) uniform sampler2D edgeTexture;

layout(push_constant) uniform PushConstants {
    float frameIndex;
    float deltaTime;
} pc;

void main() {
    vec2 texelSize = 1.0 / textureSize(currentFrame, 0);
    float edgeStrength = texture(edgeTexture, texCoord).r;
    
    vec4 currentColor = texture(currentFrame, texCoord);
    
    // Early exit for non-edge pixels - preserve performance
    if (edgeStrength < 0.1) {
        outColor = currentColor;
        return;
    }
    
    // Motion detection using temporal difference
    vec4 previousColor = texture(previousFrame, texCoord);
    float colorDiff = length(currentColor.rgb - previousColor.rgb);
    float motionStrength = clamp(colorDiff * 10.0, 0.0, 1.0);
    
    // Adaptive AA selection based on motion and edge strength
    if (motionStrength > 0.3 || edgeStrength > 0.7) {
        // Fast motion or very strong edge - use spatial SMAA
        vec4 left  = texture(currentFrame, texCoord + vec2(-texelSize.x, 0));
        vec4 right = texture(currentFrame, texCoord + vec2( texelSize.x, 0));
        vec4 up    = texture(currentFrame, texCoord + vec2(0, -texelSize.y));
        vec4 down  = texture(currentFrame, texCoord + vec2(0,  texelSize.y));
        
        // Directional spatial filtering
        float edgeX = abs(dot(left.rgb - right.rgb, vec3(0.299, 0.587, 0.114)));
        float edgeY = abs(dot(up.rgb - down.rgb, vec3(0.299, 0.587, 0.114)));
        
        if (edgeX > edgeY) {
            // Horizontal edge - vertical filtering
            outColor = (currentColor * 4.0 + up + down) * 0.1667;
        } else {
            // Vertical edge - horizontal filtering
            outColor = (currentColor * 4.0 + left + right) * 0.1667;
        }
    } else {
        // Slow motion - use temporal AA
        float temporalWeight = 0.9 * (1.0 - edgeStrength * 0.5);
        
        // Temporal accumulation with edge-aware blending
        outColor = mix(currentColor, previousColor, temporalWeight);
        
        // Clamp to prevent ghosting
        vec4 minColor = min(currentColor, previousColor);
        vec4 maxColor = max(currentColor, previousColor);
        outColor = clamp(outColor, minColor, maxColor);
    }
}