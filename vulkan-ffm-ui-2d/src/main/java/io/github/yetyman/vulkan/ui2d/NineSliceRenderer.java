package io.github.yetyman.vulkan.ui2d;

import io.github.yetyman.vulkan.foundation.ecs.*;

/**
 * Tree-scoped bulk renderer for nine-slice rectangles.
 *
 * Maintains a dense vertex buffer for all RectangleComponents in the tree, rendered
 * in a single draw call. Uses TraversalView for incremental tracking and
 * BulkPropertyObserver for targeted vertex buffer updates on property changes.
 *
 * Integration pattern:
 * 1. Registers a TraversalView<RectangleComponent> on the tree
 * 2. On each rect's afterResolve, assigns a slot index and registers as bulk observer
 * 3. On property change, writes only the affected slot's vertex data
 * 4. On render, issues one draw call covering all live slots
 *
 * The actual Vulkan buffer management (VkBuffer, descriptor sets, pipeline) is deferred
 * to a GPU-specific subclass or delegate. This class handles the logical slot management
 * and dirty tracking only.
 *
 * Each nine-slice rectangle expands to 9 quads (18 triangles, 54 vertices) or fewer
 * if some insets are zero.
 */
public class NineSliceRenderer implements TreeComponent, BulkPropertyObserver {

    /** Floats per vertex: x, y, u, v, r, g, b, a = 8 */
    public static final int FLOATS_PER_VERTEX = 8;

    /** Vertices per rect (9 quads = 54 vertices for full nine-slice) */
    public static final int VERTICES_PER_RECT = 54;

    /** Floats per rect slot */
    public static final int FLOATS_PER_SLOT = FLOATS_PER_VERTEX * VERTICES_PER_RECT;

    // Slot management
    private float[] vertexData;
    private int slotCount = 0;
    private int slotCapacity = 0;
    private boolean[] slotDirty;

    // View
    private TraversalView<RectangleComponent> view;
    private Tree tree;

    // Dirty tracking for the renderer
    private boolean anySlotDirty = false;
    private int dirtySlotCount = 0;

    public NineSliceRenderer() {
        this(64); // initial capacity for 64 rects
    }

    public NineSliceRenderer(int initialCapacity) {
        ensureCapacity(initialCapacity);
    }

    // --- TreeComponent lifecycle ---

    @Override
    public void onInit(Tree tree) {
        this.tree = tree;
    }

    @Override
    public void afterResolve(Tree tree) {
        this.view = tree.getOrCreateTraversalView("nine-slice-renderer",
                RectangleComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER);

        // Assign slots to all existing rects
        view.forEach((node, rect) -> assignSlot(rect));
    }

    @Override
    public void close(Tree tree) {
        if (view != null) {
            tree.releaseTraversalView("nine-slice-renderer");
        }
    }

    // --- BulkPropertyObserver ---

    @Override
    public void onPropertyChanged(Component source, int propertyOrdinal, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slotCount) return;

        // Mark this slot dirty — will be re-written on next flush
        if (!slotDirty[slotIndex]) {
            slotDirty[slotIndex] = true;
            dirtySlotCount++;
            anySlotDirty = true;
        }

        // Write vertex data immediately for this slot
        writeSlotVertices(slotIndex, (RectangleComponent) source);
    }

    // --- Slot management ---

    /**
     * Assigns a new slot to a rectangle and writes its initial vertex data.
     *
     * @param rect the rectangle component
     * @return the assigned slot index
     */
    public int assignSlot(RectangleComponent rect) {
        if (slotCount >= slotCapacity) {
            ensureCapacity(slotCapacity * 2);
        }

        int slot = slotCount++;
        rect.properties().setBulkObserver(this, rect, slot);
        writeSlotVertices(slot, rect);
        markSlotDirty(slot);
        return slot;
    }

    /**
     * Releases a slot (swap-remove: moves the last slot into the freed position).
     */
    public void releaseSlot(int slot, RectangleComponent movedRect) {
        if (slot < 0 || slot >= slotCount) return;

        int lastSlot = slotCount - 1;
        if (slot != lastSlot && movedRect != null) {
            // Swap last into freed slot
            System.arraycopy(vertexData, lastSlot * FLOATS_PER_SLOT,
                    vertexData, slot * FLOATS_PER_SLOT, FLOATS_PER_SLOT);
            slotDirty[slot] = true;
            movedRect.properties().updateBulkSlotIndex(slot);
            markSlotDirty(slot);
        }

        slotCount--;
    }

    // --- Vertex writing ---

    /**
     * Writes the full vertex data for a nine-slice rect into its slot.
     * This generates 9 quads (each as 2 triangles = 6 vertices).
     */
    private void writeSlotVertices(int slot, RectangleComponent rect) {
        int offset = slot * FLOATS_PER_SLOT;

        float x = rect.x(), y = rect.y();
        float w = rect.width(), h = rect.height();
        float r = rect.r(), g = rect.g(), b = rect.b(), a = rect.a();

        float il = rect.insetLeft(), ir = rect.insetRight();
        float it = rect.insetTop(), ib = rect.insetBottom();

        // UV region
        float uvx = rect.uvX(), uvy = rect.uvY();
        float uvw = rect.uvW(), uvh = rect.uvH();

        // Compute the 4 x-splits and 4 y-splits
        float x0 = x, x1 = x + il, x2 = x + w - ir, x3 = x + w;
        float y0 = y, y1 = y + it, y2 = y + h - ib, y3 = y + h;

        // UV splits
        float uvIl = (w > 0) ? (il / w) * uvw : 0;
        float uvIr = (w > 0) ? (ir / w) * uvw : 0;
        float uvIt = (h > 0) ? (it / h) * uvh : 0;
        float uvIb = (h > 0) ? (ib / h) * uvh : 0;

        float u0 = uvx, u1 = uvx + uvIl, u2 = uvx + uvw - uvIr, u3 = uvx + uvw;
        float v0 = uvy, v1 = uvy + uvIt, v2 = uvy + uvh - uvIb, v3 = uvy + uvh;

        float[] xs = {x0, x1, x2, x3};
        float[] ys = {y0, y1, y2, y3};
        float[] us = {u0, u1, u2, u3};
        float[] vs = {v0, v1, v2, v3};

        // Generate 9 quads (3x3 grid)
        int vi = offset;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                // Each quad = 2 triangles = 6 vertices
                vi = writeQuad(vertexData, vi,
                        xs[col], ys[row], xs[col + 1], ys[row + 1],
                        us[col], vs[row], us[col + 1], vs[row + 1],
                        r, g, b, a);
            }
        }
    }

    /**
     * Writes a single quad (2 triangles, 6 vertices) into the vertex data array.
     * Returns the new offset after writing.
     */
    private static int writeQuad(float[] data, int offset,
                                  float x0, float y0, float x1, float y1,
                                  float u0, float v0, float u1, float v1,
                                  float r, float g, float b, float a) {
        // Triangle 1: top-left, top-right, bottom-left
        offset = writeVertex(data, offset, x0, y0, u0, v0, r, g, b, a);
        offset = writeVertex(data, offset, x1, y0, u1, v0, r, g, b, a);
        offset = writeVertex(data, offset, x0, y1, u0, v1, r, g, b, a);

        // Triangle 2: top-right, bottom-right, bottom-left
        offset = writeVertex(data, offset, x1, y0, u1, v0, r, g, b, a);
        offset = writeVertex(data, offset, x1, y1, u1, v1, r, g, b, a);
        offset = writeVertex(data, offset, x0, y1, u0, v1, r, g, b, a);

        return offset;
    }

    private static int writeVertex(float[] data, int offset,
                                    float x, float y, float u, float v,
                                    float r, float g, float b, float a) {
        data[offset] = x;
        data[offset + 1] = y;
        data[offset + 2] = u;
        data[offset + 3] = v;
        data[offset + 4] = r;
        data[offset + 5] = g;
        data[offset + 6] = b;
        data[offset + 7] = a;
        return offset + FLOATS_PER_VERTEX;
    }

    // --- Access for GPU upload ---

    /** @return the raw vertex data array. */
    public float[] vertexData() { return vertexData; }

    /** @return the number of active slots. */
    public int slotCount() { return slotCount; }

    /** @return total vertex count (for draw call). */
    public int vertexCount() { return slotCount * VERTICES_PER_RECT; }

    /** @return true if any slot has been modified since last clearDirty(). */
    public boolean isDirty() { return anySlotDirty; }

    /** @return number of dirty slots. */
    public int dirtySlotCount() { return dirtySlotCount; }

    /**
     * Clears all dirty flags. Call after uploading to GPU.
     */
    public void clearDirty() {
        if (!anySlotDirty) return;
        for (int i = 0; i < slotCount; i++) {
            slotDirty[i] = false;
        }
        anySlotDirty = false;
        dirtySlotCount = 0;
    }

    private void markSlotDirty(int slot) {
        if (!slotDirty[slot]) {
            slotDirty[slot] = true;
            dirtySlotCount++;
            anySlotDirty = true;
        }
    }

    private void ensureCapacity(int needed) {
        if (needed <= slotCapacity) return;
        int newCap = Math.max(needed, 16);

        float[] newData = new float[newCap * FLOATS_PER_SLOT];
        boolean[] newDirty = new boolean[newCap];

        if (vertexData != null) {
            System.arraycopy(vertexData, 0, newData, 0, slotCount * FLOATS_PER_SLOT);
            System.arraycopy(slotDirty, 0, newDirty, 0, slotCount);
        }

        vertexData = newData;
        slotDirty = newDirty;
        slotCapacity = newCap;
    }
}
