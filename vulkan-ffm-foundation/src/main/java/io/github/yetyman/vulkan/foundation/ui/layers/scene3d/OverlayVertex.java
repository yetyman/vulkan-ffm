package io.github.yetyman.vulkan.foundation.ui.layers.scene3d;

/**
 * A single vertex for the 3D overlay renderer: world-space position plus RGBA color.
 * Matches the vertex input layout declared in overlay.vert exactly: vec3 pos, vec4 color = 28 bytes.
 */
public record OverlayVertex(float x, float y, float z, float r, float g, float b, float a) {
    /** Byte size of one vertex matching the shader's vertex input layout. */
    public static final int SIZE_BYTES = 28;
}
