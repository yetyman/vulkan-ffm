package io.github.yetyman.vulkan.auto;

/**
 * Frequency of CPU/GPU access to buffer data.
 */
public enum AccessFrequency {
    /** Never accessed */
    NEVER,
    
    /** Rarely accessed (less than once per minute) */
    RARE,
    
    /** Accessed approximately once per frame (~60 times per second) */
    FRAME,
    
    /** Accessed multiple times per frame (>60 times per second) */
    MULTI_FRAME
}
