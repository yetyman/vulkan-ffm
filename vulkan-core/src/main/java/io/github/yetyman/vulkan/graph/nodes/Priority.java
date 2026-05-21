package io.github.yetyman.vulkan.graph.nodes;

/**
 * Pass priority for graceful degradation. When the frame budget is exceeded,
 * lowest-priority passes are dropped first.
 */
public enum Priority {
    /** Never dropped (present, core geometry, depth) */
    CRITICAL,
    /** Dropped only as last resort */
    HIGH,
    /** Standard optional effects */
    MEDIUM,
    /** First candidates for dropping */
    LOW,
    /** Only runs when frame budget has headroom */
    BACKGROUND
}
