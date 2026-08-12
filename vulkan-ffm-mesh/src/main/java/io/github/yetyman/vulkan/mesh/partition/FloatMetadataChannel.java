package io.github.yetyman.vulkan.mesh.partition;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;

/**
 * A primitive-specialized metadata channel backed by a {@code float[]}. No boxing occurs at
 * any point: set/get take and return raw floats, and bulk transfer is a single
 * {@link MemorySegment#copy} between the backing array and a native segment.
 *
 * <p>Use this for per-partition scalar float metadata: LOD error bounds, projected screen
 * error, cone cutoffs, distance values, blend weights, etc.
 *
 * <p>This implements {@link MetadataStore} so it participates uniformly in
 * {@link io.github.yetyman.vulkan.mesh.consume.GeometryTable} channel attachment and bulk upload.
 *
 * <h2>Thread safety</h2>
 * <p>Not thread-safe. External synchronization required if accessed from multiple threads.
 * Per-thread partition assignment or a shared-nothing design is the expected usage pattern.
 */
public final class FloatMetadataChannel implements MetadataStore {

    private static final int STRIDE = 4;

    private final String name;
    private float[] data;
    private int count;

    /**
     * Creates a channel with the given capacity. All values initialize to 0.0f.
     *
     * @param name     diagnostic name
     * @param capacity number of partitions (elements)
     */
    public FloatMetadataChannel(String name, int capacity) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (capacity < 0) throw new IllegalArgumentException("capacity must be >= 0");
        this.name = name;
        this.data = new float[capacity];
        this.count = capacity;
    }

    // -------------------------------------------------------------------------
    // Primitive accessors (zero boxing)
    // -------------------------------------------------------------------------

    /**
     * Sets the value at the given partition index.
     */
    public void set(int index, float value) {
        data[index] = value;
    }

    /**
     * Gets the value at the given partition index.
     */
    public float get(int index) {
        return data[index];
    }

    /**
     * Bulk-sets a range from a source array.
     *
     * @param srcArray source float array
     * @param srcOffset offset into srcArray
     * @param dstIndex first partition index to write
     * @param length number of elements to copy
     */
    public void setRange(float[] srcArray, int srcOffset, int dstIndex, int length) {
        System.arraycopy(srcArray, srcOffset, data, dstIndex, length);
    }

    /**
     * Bulk-gets a range into a destination array.
     */
    public void getRange(int srcIndex, float[] dstArray, int dstOffset, int length) {
        System.arraycopy(data, srcIndex, dstArray, dstOffset, length);
    }

    /**
     * @return direct reference to the backing array. Mutations are visible immediately.
     * Use for zero-copy access when the caller manages bounds.
     */
    public float[] backingArray() {
        return data;
    }

    /**
     * Fills all elements with the given value.
     */
    public void fill(float value) {
        Arrays.fill(data, 0, count, value);
    }

    /**
     * Resizes the backing array. Existing data is preserved up to min(oldCount, newCount).
     * New elements initialize to 0.0f.
     */
    public void resize(int newCount) {
        if (newCount == count) return;
        data = Arrays.copyOf(data, newCount);
        count = newCount;
    }

    // -------------------------------------------------------------------------
    // MetadataStore implementation
    // -------------------------------------------------------------------------

    @Override
    public String name() { return name; }

    @Override
    public int byteSize() { return STRIDE; }

    @Override
    public int count() { return count; }

    @Override
    public void bulkWriteTo(MemorySegment dst, long dstOffset, int from, int to) {
        int elements = to - from;
        if (elements <= 0) return;
        // Single bulk copy: float[] -> MemorySegment
        MemorySegment.copy(data, from, dst, JAVA_FLOAT_UNALIGNED, dstOffset, elements);
    }

    @Override
    public void bulkReadFrom(MemorySegment src, long srcOffset, int from, int to) {
        int elements = to - from;
        if (elements <= 0) return;
        // Single bulk copy: MemorySegment -> float[]
        MemorySegment.copy(src, JAVA_FLOAT_UNALIGNED, srcOffset, data, from, elements);
    }

    @Override
    public String toString() {
        return "FloatMetadataChannel[" + name + ", count=" + count + "]";
    }
}
