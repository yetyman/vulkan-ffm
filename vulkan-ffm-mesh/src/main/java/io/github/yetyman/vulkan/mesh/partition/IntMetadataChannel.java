package io.github.yetyman.vulkan.mesh.partition;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

/**
 * A primitive-specialized metadata channel backed by an {@code int[]}. No boxing occurs at
 * any point: set/get take and return raw ints, and bulk transfer is a single
 * {@link MemorySegment#copy} between the backing array and a native segment.
 *
 * <p>Use this for per-partition integer metadata: LOD node indices, LOD levels, group IDs,
 * material indices, flags, cluster connectivity indices, etc.
 *
 * <p>This implements {@link MetadataStore} so it participates uniformly in
 * {@link io.github.yetyman.vulkan.mesh.consume.GeometryTable} channel attachment and bulk upload.
 *
 * <h2>Thread safety</h2>
 * <p>Not thread-safe. External synchronization required if accessed from multiple threads.
 */
public final class IntMetadataChannel implements MetadataStore {

    private static final int STRIDE = 4;

    private final String name;
    private int[] data;
    private int count;

    /**
     * Creates a channel with the given capacity. All values initialize to 0.
     *
     * @param name     diagnostic name
     * @param capacity number of partitions (elements)
     */
    public IntMetadataChannel(String name, int capacity) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (capacity < 0) throw new IllegalArgumentException("capacity must be >= 0");
        this.name = name;
        this.data = new int[capacity];
        this.count = capacity;
    }

    // -------------------------------------------------------------------------
    // Primitive accessors (zero boxing)
    // -------------------------------------------------------------------------

    /**
     * Sets the value at the given partition index.
     */
    public void set(int index, int value) {
        data[index] = value;
    }

    /**
     * Gets the value at the given partition index.
     */
    public int get(int index) {
        return data[index];
    }

    /**
     * Bulk-sets a range from a source array.
     */
    public void setRange(int[] srcArray, int srcOffset, int dstIndex, int length) {
        System.arraycopy(srcArray, srcOffset, data, dstIndex, length);
    }

    /**
     * Bulk-gets a range into a destination array.
     */
    public void getRange(int srcIndex, int[] dstArray, int dstOffset, int length) {
        System.arraycopy(data, srcIndex, dstArray, dstOffset, length);
    }

    /**
     * @return direct reference to the backing array. Mutations are visible immediately.
     */
    public int[] backingArray() {
        return data;
    }

    /**
     * Fills all elements with the given value.
     */
    public void fill(int value) {
        Arrays.fill(data, 0, count, value);
    }

    /**
     * Resizes the backing array. Existing data is preserved up to min(oldCount, newCount).
     * New elements initialize to 0.
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
        MemorySegment.copy(data, from, dst, JAVA_INT_UNALIGNED, dstOffset, elements);
    }

    @Override
    public void bulkReadFrom(MemorySegment src, long srcOffset, int from, int to) {
        int elements = to - from;
        if (elements <= 0) return;
        MemorySegment.copy(src, JAVA_INT_UNALIGNED, srcOffset, data, from, elements);
    }

    @Override
    public String toString() {
        return "IntMetadataChannel[" + name + ", count=" + count + "]";
    }
}
