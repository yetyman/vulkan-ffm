package io.github.yetyman.vulkan.mesh.partition;

import io.github.yetyman.vulkan.buffers.GpuLayout;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-partition typed side channels, stored as dense arrays parallel to the partition list so that
 * bulk GPU upload of a metadata channel is a single contiguous copy.
 *
 * <p>Each channel has its own {@link GpuLayout}, which is what makes a metadata channel uploadable
 * without the module knowing what the channel means. A meshlet cone channel and a cluster error
 * channel are the same code path.
 */
public final class PartitionMetadata {

    private final int partitionCount;
    private final Map<MetadataChannel<?>, Object[]> data = new LinkedHashMap<>();
    private final Map<MetadataChannel<?>, MemorySegment> serialized = new LinkedHashMap<>();

    /**
     * @param partitionCount number of partitions this metadata covers (array length)
     */
    public PartitionMetadata(int partitionCount) {
        if (partitionCount < 0) throw new IllegalArgumentException("partitionCount must be >= 0");
        this.partitionCount = partitionCount;
    }

    /**
     * Sets the value for a partition in a channel. Creates the channel's backing array on first use.
     */
    @SuppressWarnings("unchecked")
    public <T> void put(MetadataChannel<T> channel, int partitionIndex, T value) {
        checkIndex(partitionIndex);
        Object[] arr = data.computeIfAbsent(channel, k -> new Object[partitionCount]);
        arr[partitionIndex] = value;
        serialized.remove(channel); // invalidate cached serialization
    }

    /**
     * Gets the value for a partition in a channel, or null if not set.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(MetadataChannel<T> channel, int partitionIndex) {
        checkIndex(partitionIndex);
        Object[] arr = data.get(channel);
        if (arr == null) return null;
        return (T) arr[partitionIndex];
    }

    /**
     * @return true if this metadata has any values for the given channel
     */
    public boolean has(MetadataChannel<?> channel) {
        return data.containsKey(channel);
    }

    /**
     * @return all channels that have been written to
     */
    public Set<MetadataChannel<?>> channels() {
        return Collections.unmodifiableSet(data.keySet());
    }

    /**
     * @return the dense serialized backing store for a channel, suitable for bulk GPU upload.
     * Lazily serialized on first call after a write; subsequent calls return the cached segment
     * until another write invalidates it.
     */
    @SuppressWarnings("unchecked")
    public <T> MemorySegment raw(MetadataChannel<T> channel) {
        MemorySegment cached = serialized.get(channel);
        if (cached != null) return cached;

        GpuLayout<T> layout = channel.layout();
        int stride = layout.byteSize();
        MemorySegment seg = Arena.ofAuto().allocate((long) stride * partitionCount);
        Object[] arr = data.get(channel);
        if (arr != null) {
            for (int i = 0; i < partitionCount; i++) {
                T value = (T) arr[i];
                if (value != null) {
                    layout.writeTo(value, seg, (long) i * stride);
                }
            }
        }
        serialized.put(channel, seg);
        return seg;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= partitionCount)
            throw new IndexOutOfBoundsException("partition index " + index + " out of bounds for count " + partitionCount);
    }
}
