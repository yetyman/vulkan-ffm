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

// Interleaved gradient noise for high-quality dithering
float interleavedGradientNoise(vec2 coord) {
    vec3 magic = vec3(0.06711056, 0.00583715, 52.9829189);
    return fract(magic.z * fract(dot(coord, magic.xy)));
}

// Blue noise approximation for smoother dither
float blueNoise(vec2 coord, float seed) {
    return fract(sin(dot(coord + seed, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 texelSize = 1.0 / textureSize(currentFrame, 0);
    vec2 pixelCoord = texCoord * textureSize(currentFrame, 0);
    float edgeStrength = texture(edgeTexture, texCoord).r;
    
    vec4 currentColor = texture(currentFrame, texCoord);
    
    // Early exit for non-edge pixels (lowered threshold)
    if (edgeStrength < 0.05) {
        outColor = currentColor;
        return;
    }
    
    // Motion detection using temporal difference
    vec4 previousColor = texture(previousFrame, texCoord);
    float colorDiff = length(currentColor.rgb - previousColor.rgb);
    float motionStrength = clamp(colorDiff * 10.0, 0.0, 1.0);
    
    // Adaptive AA selection based on motion and edge strength
    if (motionStrength > 0.2 || edgeStrength > 0.3) {
        // Fast motion or very strong edge - use 8-tap box filter with edge-adaptive weight
        const float radius = 0.7;
        vec4 samples[8];
        samples[0] = texture(currentFrame, clamp(texCoord + vec2( radius,  0.0) * texelSize, vec2(0), vec2(1)));
        samples[1] = texture(currentFrame, clamp(texCoord + vec2(-radius,  0.0) * texelSize, vec2(0), vec2(1)));
        samples[2] = texture(currentFrame, clamp(texCoord + vec2( 0.0,  radius) * texelSize, vec2(0), vec2(1)));
        samples[3] = texture(currentFrame, clamp(texCoord + vec2( 0.0, -radius) * texelSize, vec2(0), vec2(1)));
        samples[4] = texture(currentFrame, clamp(texCoord + vec2( radius,  radius) * texelSize * 0.707, vec2(0), vec2(1)));
        samples[5] = texture(currentFrame, clamp(texCoord + vec2(-radius,  radius) * texelSize * 0.707, vec2(0), vec2(1)));
        samples[6] = texture(currentFrame, clamp(texCoord + vec2( radius, -radius) * texelSize * 0.707, vec2(0), vec2(1)));
        samples[7] = texture(currentFrame, clamp(texCoord + vec2(-radius, -radius) * texelSize * 0.707, vec2(0), vec2(1)));
        
        // Simple average with strong edge-based blending
        vec4 avg = (samples[0] + samples[1] + samples[2] + samples[3] + 
                    samples[4] + samples[5] + samples[6] + samples[7]) / 8.0;
        
        float weight = clamp(edgeStrength * 1.2, 0.4, 0.85);
        outColor = mix(currentColor, avg, weight);
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
    
    // Apply edge-adaptive dither to reduce banding
    float ditherStrength = mix(1.0, 3.0, edgeStrength); // Stronger on edges
    float dither1 = interleavedGradientNoise(pixelCoord + vec2(pc.frameIndex * 1.61803398875));
    float dither2 = blueNoise(pixelCoord, pc.frameIndex * 0.1);
    float dither = mix(dither1, dither2, 0.5) * 2.0 - 1.0;
    outColor.rgb += (dither * ditherStrength) / 255.0;
}