package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferFactory;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The simplest allocator: one {@link IBuffer} per vertex stream and one for indices, per geometry.
 * Most debuggable, fine for a handful of large meshes, bad for thousands because each geometry adds
 * a buffer bind.
 *
 * <p>Uses {@link BufferFactory} to create each buffer, so the underlying memory strategy
 * (device-local, mapped, ReBAR) is inherited from the factory's configuration.
 */
public final class DedicatedAllocator implements GeometryAllocator {

    private final VkDevice device;
    private final VkQueue transferQueue;
    private final MemoryStrategy strategy;
    private final List<DedicatedAllocation> liveAllocations = new ArrayList<>();

    /**
     * @param device        the device to allocate buffers on
     * @param transferQueue the queue used for transfer operations
     * @param strategy      the memory strategy for all allocated buffers
     */
    public DedicatedAllocator(VkDevice device, VkQueue transferQueue, MemoryStrategy strategy) {
        if (device == null) throw new IllegalArgumentException("device required");
        if (transferQueue == null) throw new IllegalArgumentException("transferQueue required");
        if (strategy == null) throw new IllegalArgumentException("strategy required");
        this.device = device;
        this.transferQueue = transferQueue;
        this.strategy = strategy;
    }

    @Override
    public GeometryAllocation allocate(MeshLayout layout, long vertexCount,
                                       IndexWidth indexWidth, long indexCount) {
        int streamCount = layout.streamCount();
        DeviceRange[] vertexRanges = new DeviceRange[streamCount];
        for (int s = 0; s < streamCount; s++) {
            long size = layout.strideOf(s) * vertexCount;
            if (size <= 0) continue;
            IBuffer buf = BufferFactory.create(strategy, null, size,
                    BufferUsage.VERTEX, device, transferQueue);
            vertexRanges[s] = new DeviceRange(buf, 0, size, layout.strideOf(s));
        }

        DeviceRange idxRange = null;
        if (indexWidth != null && indexCount > 0) {
            long idxSize = (long) indexWidth.byteSize() * indexCount;
            IBuffer idxBuf = BufferFactory.create(strategy, null, idxSize,
                    BufferUsage.STORAGE, device, transferQueue);
            idxRange = new DeviceRange(idxBuf, 0, idxSize, indexWidth.byteSize());
        }

        DedicatedAllocation alloc = new DedicatedAllocation(vertexRanges, idxRange);
        liveAllocations.add(alloc);
        return alloc;
    }

    @Override
    public void free(GeometryAllocation allocation) {
        if (allocation instanceof DedicatedAllocation da) {
            liveAllocations.remove(da);
            da.close();
        }
    }

    @Override
    public IndexBaseMode indexBaseMode() {
        return IndexBaseMode.RELATIVE_WITH_DRAW_OFFSET;
    }

    @Override
    public void close() {
        for (DedicatedAllocation a : liveAllocations) a.close();
        liveAllocations.clear();
    }

    /**
     * A dedicated allocation: owns its buffers and closes them on free.
     */
    static final class DedicatedAllocation implements GeometryAllocation {
        private final DeviceRange[] vertexRanges;
        private final DeviceRange indexRange;

        DedicatedAllocation(DeviceRange[] vertexRanges, DeviceRange indexRange) {
            this.vertexRanges = vertexRanges;
            this.indexRange = indexRange;
        }

        @Override
        public DeviceRange vertexRange(int streamId) {
            if (streamId < 0 || streamId >= vertexRanges.length || vertexRanges[streamId] == null)
                throw new IndexOutOfBoundsException("no vertex range for stream " + streamId);
            return vertexRanges[streamId];
        }

        @Override
        public Optional<DeviceRange> indexRange() {
            return Optional.ofNullable(indexRange);
        }

        @Override
        public long vertexBase() {
            return 0;
        }

        @Override
        public long indexBase() {
            return 0;
        }

        void close() {
            for (DeviceRange r : vertexRanges) {
                if (r != null) r.buffer().close();
            }
            if (indexRange != null) indexRange.buffer().close();
        }
    }
}
