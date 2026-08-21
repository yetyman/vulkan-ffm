package io.github.yetyman.vulkan.sample.bufferdemo;

import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferReadScope;
import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.layers.gizmo.Gizmo;
import io.github.yetyman.vulkan.layers.scene3d.DepthMode;
import io.github.yetyman.vulkan.layers.scene3d.OverlayDrawList;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * A gizmo that reads back vertex positions from a ManagedBuffer (via the buffer's
 * observability/mirror) and renders wireframe quad outlines with a cycling rainbow color.
 *
 * <p>Optimized for zero per-frame allocation: reuses a cached float array for bulk reads
 * and scratch float arrays for vertex/color data.
 */
public class MeshOutlineGizmo implements Gizmo {

    private final String label;
    private final ManagedBuffer buffer;
    private final VkQueue queue;
    private final float[] screenBounds; // [x, y, w, h] in pixels
    private final int quadCols;
    private final int quadRows;
    private final int vertsPerQuad;
    private final int vertexStride; // bytes per vertex (8 for vec2)

    // Reusable bulk-read array (avoids per-frame allocation)
    private final float[] positions;

    // Scratch arrays for line endpoints and color (avoids per-call allocation)
    private final float[] v0 = new float[3];
    private final float[] v1 = new float[3];
    private final float[] v2 = new float[3];
    private final float[] v3 = new float[3];
    private final float[] color = new float[4];

    /**
     * @param label        display name
     * @param buffer       the vertex buffer to read from
     * @param queue        queue for any readback operations
     * @param screenBounds pixel-space [x, y, width, height] where this grid is rendered
     * @param quadCols     number of quad columns
     * @param quadRows     number of quad rows
     */
    public MeshOutlineGizmo(String label, ManagedBuffer buffer, VkQueue queue,
                            float[] screenBounds, int quadCols, int quadRows) {
        this.label = label;
        this.buffer = buffer;
        this.queue = queue;
        this.screenBounds = screenBounds;
        this.quadCols = quadCols;
        this.quadRows = quadRows;
        this.vertsPerQuad = 4;
        this.vertexStride = 8; // vec2 = 8 bytes
        this.positions = new float[quadCols * quadRows * vertsPerQuad * 2];
    }

    @Override
    public String name() {
        return label;
    }

    @Override
    public float[] bounds() {
        return screenBounds;
    }

    @Override
    public void render(OverlayDrawList drawList, long frameNumber) {
        int totalQuads = quadCols * quadRows;
        int totalVertices = totalQuads * vertsPerQuad;
        long bufSize = (long) totalVertices * vertexStride;

        // Bulk copy into reusable array - one FFM call
        try (BufferReadScope readScope = buffer.acquireRead(0, bufSize, queue)) {
            MemorySegment mem = readScope.segment();
            MemorySegment.copy(mem, ValueLayout.JAVA_FLOAT, 0, positions, 0, positions.length);
        }

        // Color cycling
        float baseHue = (frameNumber * 0.0025f) % 1.0f;

        float sx = screenBounds[0];
        float sy = screenBounds[1];
        float sw = screenBounds[2];
        float sh = screenBounds[3];

        int skipX = Math.max(1, quadCols / 8);
        int skipY = Math.max(1, quadRows / 8);

        for (int row = 0; row < quadRows; row += skipY) {
            for (int col = 0; col < quadCols; col += skipX) {
                int quadIdx = row * quadCols + col;
                int fi = quadIdx * vertsPerQuad * 2; // float index

                // Map grid-local [0,1] to pixel coords, z=0
                v0[0] = sx + positions[fi] * sw;
                v0[1] = sy + positions[fi + 1] * sh;
                v0[2] = 0;
                v1[0] = sx + positions[fi + 2] * sw;
                v1[1] = sy + positions[fi + 3] * sh;
                v1[2] = 0;
                v2[0] = sx + positions[fi + 4] * sw;
                v2[1] = sy + positions[fi + 5] * sh;
                v2[2] = 0;
                v3[0] = sx + positions[fi + 6] * sw;
                v3[1] = sy + positions[fi + 7] * sh;
                v3[2] = 0;

                // Rainbow color
                float hue = (baseHue + (float) quadIdx / totalQuads * 2.0f) % 1.0f;
                hueToRgba(hue, color);

                // Draw quad outline
                drawList.addLine(v0, v1, color, DepthMode.ALWAYS_ON_TOP);
                drawList.addLine(v1, v3, color, DepthMode.ALWAYS_ON_TOP);
                drawList.addLine(v3, v2, color, DepthMode.ALWAYS_ON_TOP);
                drawList.addLine(v2, v0, color, DepthMode.ALWAYS_ON_TOP);
            }
        }
    }

    // -- HSV hue to RGBA, writes into existing array --

    private static void hueToRgba(float hue, float[] out) {
        out[0] = hueComponent(hue, 0.0f);
        out[1] = hueComponent(hue, 1.0f / 3.0f);
        out[2] = hueComponent(hue, 2.0f / 3.0f);
        out[3] = 0.85f;
    }

    private static float hueComponent(float h, float offset) {
        float k = (h + offset) % 1.0f;
        if (k < 0) k += 1.0f;
        if (k < 1.0f / 6.0f) return k * 6.0f;
        if (k < 0.5f) return 1.0f;
        if (k < 2.0f / 3.0f) return (2.0f / 3.0f - k) * 6.0f;
        return 0.0f;
    }
}
