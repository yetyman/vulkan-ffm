package io.github.yetyman.vulkan.mesh.partition;

import io.github.yetyman.vulkan.buffers.GpuCodec;
import io.github.yetyman.vulkan.buffers.GpuLayout;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;

/**
 * A metadata channel for compound mutable types (Vec3, Mat4, AABB, etc.) that holds its own
 * data and implements {@link MetadataStore} for uniform bulk transfer.
 *
 * <p>The backing store is {@code Object[]} with elements cast to T. Bulk write iterates the
 * array calling {@link GpuLayout#writeTo} per element. Bulk read calls {@link GpuLayout#readFrom}
 * per element (mutating existing objects in place) or, if the layout is a {@link GpuCodec},
 * calls {@link GpuCodec#decode} to create new instances.
 *
 * <p>For per-partition scalar primitives (float, int), use {@link FloatMetadataChannel} or
 * {@link IntMetadataChannel} instead — they avoid boxing entirely and do bulk transfer as a
 * single memory copy.
 *
 * @param <T> the type of value stored per partition
 */
public final class TypedMetadataChannel<T> implements MetadataStore {

    private final String name;
    private final GpuLayout<T> layout;
    private Object[] data;
    private int count;

    /**
     * Creates a typed channel with the given capacity. All values initialize to null.
     *
     * @param name     diagnostic name
     * @param layout   GPU serialization layout for one element
     * @param capacity number of partitions (elements)
     */
    public TypedMetadataChannel(String name, GpuLayout<T> layout, int capacity) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (layout == null) throw new IllegalArgumentException("layout required");
        if (capacity < 0) throw new IllegalArgumentException("capacity must be >= 0");
        this.name = name;
        this.layout = layout;
        this.data = new Object[capacity];
        this.count = capacity;
    }

    // -------------------------------------------------------------------------
    // Typed accessors
    // -------------------------------------------------------------------------

    /**
     * Sets the value at the given partition index.
     */
    public void set(int index, T value) {
        data[index] = value;
    }

    /**
     * Gets the value at the given partition index, or null if not set.
     */
    @SuppressWarnings("unchecked")
    public T get(int index) {
        return (T) data[index];
    }

    /**
     * @return the GpuLayout used for per-element serialization
     */
    public GpuLayout<T> layout() {
        return layout;
    }

    /**
     * Fills all elements with the given value (reference copy, not deep clone).
     */
    public void fill(T value) {
        Arrays.fill(data, 0, count, value);
    }

    /**
     * Resizes the backing array. Existing data is preserved up to min(oldCount, newCount).
     * New elements initialize to null.
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
    public int byteSize() { return layout.byteSize(); }

    @Override
    public int count() { return count; }

    @Override
    @SuppressWarnings("unchecked")
    public void bulkWriteTo(MemorySegment dst, long dstOffset, int from, int to) {
        int stride = layout.byteSize();
        long offset = dstOffset;
        for (int i = from; i < to; i++) {
            T value = (T) data[i];
            if (value != null) {
                layout.writeTo(value, dst, offset);
            }
            offset += stride;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void bulkReadFrom(MemorySegment src, long srcOffset, int from, int to) {
        int stride = layout.byteSize();
        long offset = srcOffset;

        if (layout instanceof GpuCodec<T> codec) {
            // Immutable T: decode creates new instances
            for (int i = from; i < to; i++) {
                data[i] = codec.decode(src, offset);
                offset += stride;
            }
        } else {
            // Mutable T: readFrom mutates in place
            for (int i = from; i < to; i++) {
                T value = (T) data[i];
                if (value != null) {
                    layout.readFrom(value, src, offset);
                }
                // If null, we can't read into nothing — skip. Caller must pre-populate
                // with empty instances if they want bulk readback into a typed channel.
                offset += stride;
            }
        }
    }

    @Override
    public String toString() {
        return "TypedMetadataChannel[" + name + ", count=" + count + "]";
    }
}
