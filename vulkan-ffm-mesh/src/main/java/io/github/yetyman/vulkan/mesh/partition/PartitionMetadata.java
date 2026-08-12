package io.github.yetyman.vulkan.mesh.partition;

import io.github.yetyman.vulkan.buffers.GpuLayout;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

/**
 * Per-partition metadata registry: the single source of truth for all per-partition side-channel
 * data associated with one partition set.
 *
 * <p>Three storage tiers, each O(1) lookup by channel key ID:
 * <ul>
 *   <li><b>Float channels</b> ({@link FloatChannelKey}): backed by {@code float[]} per key.
 *       Zero boxing, bulk transfer is a single memcpy.</li>
 *   <li><b>Int channels</b> ({@link IntChannelKey}): backed by {@code int[]} per key.
 *       Same guarantees.</li>
 *   <li><b>Typed channels</b> ({@link MetadataChannel}): backed by {@code Object[]} per key.
 *       Per-element serialization via {@link GpuLayout}. For compound mutable types.</li>
 * </ul>
 *
 * <h2>Sharing guarantee</h2>
 * <p>Two subsystems using the same key on the same {@code PartitionMetadata} instance are
 * guaranteed to read and write the same backing array. No silent divergence is possible.
 *
 * <h2>Hot-path access</h2>
 * <p>For per-frame reads, callers should grab the backing array reference once via
 * {@link #floatArray(FloatChannelKey)} or {@link #intArray(IntChannelKey)} and access it directly.
 * The registry lookup itself is O(1) (array index), but holding the reference avoids even that.
 *
 * <h2>Bulk GPU transfer</h2>
 * <p>{@link #bulkWriteFloat}, {@link #bulkWriteInt}, and {@link #rawTyped} produce GPU-ready
 * segments. For primitive channels, this is a single {@link MemorySegment#copy}. For typed
 * channels, this is a per-element {@link GpuLayout#writeTo} loop.
 *
 * <h2>Lifecycle</h2>
 * <p>One {@code PartitionMetadata} per {@link PartitionSet}. When the partition set is discarded,
 * the metadata goes with it. Keys survive forever (static finals).
 */
public final class PartitionMetadata {

    private final int partitionCount;

    // Primitive tiers: indexed by key.id(). Grow lazily.
    private float[][] floatChannels;
    private int[][] intChannels;

    // Typed tier: indexed by MetadataChannel identity. Uses a list because typed channel keys
    // don't carry sequential IDs (they predate this design). If typed channels gain IDs later,
    // this can become an array too.
    private final ArrayList<TypedEntry<?>> typedChannels = new ArrayList<>();

    /**
     * @param partitionCount number of partitions this metadata covers (all arrays have this length)
     */
    public PartitionMetadata(int partitionCount) {
        if (partitionCount < 0) throw new IllegalArgumentException("partitionCount must be >= 0");
        this.partitionCount = partitionCount;
        // Start with room for a few channels; grows on demand
        this.floatChannels = new float[4][];
        this.intChannels = new int[4][];
    }

    /** @return number of partitions all channels are sized to */
    public int partitionCount() {
        return partitionCount;
    }

    // =========================================================================
    // Float channels
    // =========================================================================

    /**
     * Ensures a float channel exists for the given key. Allocates the backing {@code float[]}
     * on first access, initialized to 0.0f.
     *
     * @return the backing array (length = partitionCount). Caller may hold this reference.
     */
    public float[] floatChannel(FloatChannelKey key) {
        ensureFloatCapacity(key.id());
        float[] arr = floatChannels[key.id()];
        if (arr == null) {
            arr = new float[partitionCount];
            floatChannels[key.id()] = arr;
        }
        return arr;
    }

    /**
     * @return the backing array if the channel has been initialized, or null if never accessed.
     * Use for optional reads without forcing allocation.
     */
    public float[] floatArrayOrNull(FloatChannelKey key) {
        if (key.id() >= floatChannels.length) return null;
        return floatChannels[key.id()];
    }

    /**
     * Sets one value. Ensures the channel exists.
     */
    public void setFloat(FloatChannelKey key, int partitionIndex, float value) {
        floatChannel(key)[partitionIndex] = value;
    }

    /**
     * Gets one value. Returns 0.0f if the channel has never been written.
     */
    public float getFloat(FloatChannelKey key, int partitionIndex) {
        float[] arr = floatArrayOrNull(key);
        return arr != null ? arr[partitionIndex] : 0.0f;
    }

    /**
     * @return true if the float channel has been initialized (has a backing array)
     */
    public boolean hasFloat(FloatChannelKey key) {
        return floatArrayOrNull(key) != null;
    }

    /**
     * Bulk-writes the float channel into a segment. Single memcpy, zero per-element cost.
     *
     * @param key       the channel to write
     * @param dst       destination segment
     * @param dstOffset byte offset into dst
     * @param from      first partition index (inclusive)
     * @param to        last partition index (exclusive)
     */
    public void bulkWriteFloat(FloatChannelKey key, MemorySegment dst, long dstOffset, int from, int to) {
        float[] arr = floatArrayOrNull(key);
        if (arr == null) return;
        int elements = to - from;
        if (elements <= 0) return;
        MemorySegment.copy(arr, from, dst, JAVA_FLOAT_UNALIGNED, dstOffset, elements);
    }

    /**
     * Bulk-reads from a segment into the float channel. Single memcpy.
     */
    public void bulkReadFloat(FloatChannelKey key, MemorySegment src, long srcOffset, int from, int to) {
        float[] arr = floatChannel(key);
        int elements = to - from;
        if (elements <= 0) return;
        MemorySegment.copy(src, JAVA_FLOAT_UNALIGNED, srcOffset, arr, from, elements);
    }

    /**
     * @return a MetadataStore view over this float channel, for use with GeometryTable attachment.
     * The view does not copy data; it delegates to the backing array in this registry.
     */
    public MetadataStore floatStore(FloatChannelKey key) {
        float[] arr = floatChannel(key);
        return new MetadataStore() {
            @Override public String name() { return key.name(); }
            @Override public int byteSize() { return 4; }
            @Override public int count() { return partitionCount; }
            @Override public void bulkWriteTo(MemorySegment dst, long dstOffset, int from, int to) {
                int elements = to - from;
                if (elements <= 0) return;
                MemorySegment.copy(arr, from, dst, JAVA_FLOAT_UNALIGNED, dstOffset, elements);
            }
            @Override public void bulkReadFrom(MemorySegment src, long srcOffset, int from, int to) {
                int elements = to - from;
                if (elements <= 0) return;
                MemorySegment.copy(src, JAVA_FLOAT_UNALIGNED, srcOffset, arr, from, elements);
            }
        };
    }

    // =========================================================================
    // Int channels
    // =========================================================================

    /**
     * Ensures an int channel exists for the given key. Allocates the backing {@code int[]}
     * on first access, initialized to 0.
     *
     * @return the backing array (length = partitionCount). Caller may hold this reference.
     */
    public int[] intChannel(IntChannelKey key) {
        ensureIntCapacity(key.id());
        int[] arr = intChannels[key.id()];
        if (arr == null) {
            arr = new int[partitionCount];
            intChannels[key.id()] = arr;
        }
        return arr;
    }

    /**
     * @return the backing array if the channel has been initialized, or null if never accessed.
     */
    public int[] intArrayOrNull(IntChannelKey key) {
        if (key.id() >= intChannels.length) return null;
        return intChannels[key.id()];
    }

    /**
     * Sets one value. Ensures the channel exists.
     */
    public void setInt(IntChannelKey key, int partitionIndex, int value) {
        intChannel(key)[partitionIndex] = value;
    }

    /**
     * Gets one value. Returns 0 if the channel has never been written.
     */
    public int getInt(IntChannelKey key, int partitionIndex) {
        int[] arr = intArrayOrNull(key);
        return arr != null ? arr[partitionIndex] : 0;
    }

    /**
     * @return true if the int channel has been initialized (has a backing array)
     */
    public boolean hasInt(IntChannelKey key) {
        return intArrayOrNull(key) != null;
    }

    /**
     * Bulk-writes the int channel into a segment. Single memcpy, zero per-element cost.
     */
    public void bulkWriteInt(IntChannelKey key, MemorySegment dst, long dstOffset, int from, int to) {
        int[] arr = intArrayOrNull(key);
        if (arr == null) return;
        int elements = to - from;
        if (elements <= 0) return;
        MemorySegment.copy(arr, from, dst, JAVA_INT_UNALIGNED, dstOffset, elements);
    }

    /**
     * Bulk-reads from a segment into the int channel. Single memcpy.
     */
    public void bulkReadInt(IntChannelKey key, MemorySegment src, long srcOffset, int from, int to) {
        int[] arr = intChannel(key);
        int elements = to - from;
        if (elements <= 0) return;
        MemorySegment.copy(src, JAVA_INT_UNALIGNED, srcOffset, arr, from, elements);
    }

    /**
     * @return a MetadataStore view over this int channel, for use with GeometryTable attachment.
     */
    public MetadataStore intStore(IntChannelKey key) {
        int[] arr = intChannel(key);
        return new MetadataStore() {
            @Override public String name() { return key.name(); }
            @Override public int byteSize() { return 4; }
            @Override public int count() { return partitionCount; }
            @Override public void bulkWriteTo(MemorySegment dst, long dstOffset, int from, int to) {
                int elements = to - from;
                if (elements <= 0) return;
                MemorySegment.copy(arr, from, dst, JAVA_INT_UNALIGNED, dstOffset, elements);
            }
            @Override public void bulkReadFrom(MemorySegment src, long srcOffset, int from, int to) {
                int elements = to - from;
                if (elements <= 0) return;
                MemorySegment.copy(src, JAVA_INT_UNALIGNED, srcOffset, arr, from, elements);
            }
        };
    }

    // =========================================================================
    // Typed channels (compound mutable types: Vec3, AABB, etc.)
    // =========================================================================

    /**
     * Sets a typed value. Creates the channel's backing array on first use.
     */
    @SuppressWarnings("unchecked")
    public <T> void put(MetadataChannel<T> channel, int partitionIndex, T value) {
        checkIndex(partitionIndex);
        TypedEntry<T> entry = findOrCreateTyped(channel);
        entry.data[partitionIndex] = value;
    }

    /**
     * Gets a typed value, or null if not set.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(MetadataChannel<T> channel, int partitionIndex) {
        checkIndex(partitionIndex);
        TypedEntry<T> entry = findTyped(channel);
        if (entry == null) return null;
        return (T) entry.data[partitionIndex];
    }

    /**
     * @return true if the typed channel has been initialized
     */
    public boolean has(MetadataChannel<?> channel) {
        return findTyped(channel) != null;
    }

    /**
     * @return all typed channels that have been written to
     */
    public List<MetadataChannel<?>> typedChannels() {
        List<MetadataChannel<?>> result = new ArrayList<>(typedChannels.size());
        for (TypedEntry<?> e : typedChannels) result.add(e.key);
        return Collections.unmodifiableList(result);
    }

    /**
     * Serializes a typed channel into a dense GPU-ready segment. Per-element writeTo loop.
     */
    @SuppressWarnings("unchecked")
    public <T> MemorySegment rawTyped(MetadataChannel<T> channel) {
        TypedEntry<T> entry = findTyped(channel);
        if (entry == null) return MemorySegment.NULL;

        GpuLayout<T> layout = channel.layout();
        int stride = layout.byteSize();
        MemorySegment seg = Arena.ofAuto().allocate((long) stride * partitionCount);
        for (int i = 0; i < partitionCount; i++) {
            T value = (T) entry.data[i];
            if (value != null) {
                layout.writeTo(value, seg, (long) i * stride);
            }
        }
        return seg;
    }

    /**
     * @return a MetadataStore view over this typed channel.
     */
    @SuppressWarnings("unchecked")
    public <T> MetadataStore typedStore(MetadataChannel<T> channel) {
        TypedEntry<T> entry = findOrCreateTyped(channel);
        GpuLayout<T> layout = channel.layout();
        return new MetadataStore() {
            @Override public String name() { return channel.name(); }
            @Override public int byteSize() { return layout.byteSize(); }
            @Override public int count() { return partitionCount; }
            @Override public void bulkWriteTo(MemorySegment dst, long dstOffset, int from, int to) {
                int stride = layout.byteSize();
                long offset = dstOffset;
                for (int i = from; i < to; i++) {
                    T value = (T) entry.data[i];
                    if (value != null) layout.writeTo(value, dst, offset);
                    offset += stride;
                }
            }
            @Override public void bulkReadFrom(MemorySegment src, long srcOffset, int from, int to) {
                int stride = layout.byteSize();
                long offset = srcOffset;
                for (int i = from; i < to; i++) {
                    T value = (T) entry.data[i];
                    if (value != null) layout.readFrom(value, src, offset);
                    offset += stride;
                }
            }
        };
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private void ensureFloatCapacity(int id) {
        if (id >= floatChannels.length) {
            floatChannels = Arrays.copyOf(floatChannels, Math.max(id + 1, floatChannels.length * 2));
        }
    }

    private void ensureIntCapacity(int id) {
        if (id >= intChannels.length) {
            intChannels = Arrays.copyOf(intChannels, Math.max(id + 1, intChannels.length * 2));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> TypedEntry<T> findTyped(MetadataChannel<T> channel) {
        for (TypedEntry<?> e : typedChannels) {
            if (e.key == channel) return (TypedEntry<T>) e;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> TypedEntry<T> findOrCreateTyped(MetadataChannel<T> channel) {
        TypedEntry<T> entry = findTyped(channel);
        if (entry == null) {
            entry = new TypedEntry<>(channel, new Object[partitionCount]);
            typedChannels.add(entry);
        }
        return entry;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= partitionCount)
            throw new IndexOutOfBoundsException("partition index " + index + " out of bounds for count " + partitionCount);
    }

    private record TypedEntry<T>(MetadataChannel<T> key, Object[] data) {}
}
