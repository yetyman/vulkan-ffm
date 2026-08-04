package io.github.yetyman.vulkan.layers.text;

/**
 * A single positioned, colored glyph quad ready for GPU upload.
 * Matches the std430 layout of GlyphInstance in text.vert exactly:
 * vec2 posMin, vec2 posMax, vec2 uvMin, vec2 uvMax, vec4 color = 48 bytes, 16-byte aligned.
 */
public record GlyphInstance(
    float posMinX, float posMinY,
    float posMaxX, float posMaxY,
    float uvMinX, float uvMinY,
    float uvMaxX, float uvMaxY,
    float r, float g, float b, float a
) {
    /** Byte size of one instance matching the shader's std430 layout (with padding). */
    public static final int SIZE_BYTES = 48;
}
