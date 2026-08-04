package io.github.yetyman.vulkan.layers.scene3d;

/**
 * Depth handling mode for overlay primitives.
 */
public enum DepthMode {
    /** Occluded by scene geometry (standard depth test against the scene depth buffer). */
    DEPTH_TESTED,
    /** Always drawn on top, ignoring scene depth (typical for debug lines/gizmos). */
    ALWAYS_ON_TOP
}
