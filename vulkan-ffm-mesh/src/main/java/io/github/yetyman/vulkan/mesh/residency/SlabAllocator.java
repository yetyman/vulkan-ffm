package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.buffers.SuballocatorBuffer;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A fixed-slot slab allocator: all geometries are the same maximum size, allocated from a
 * pre-sized {@link SuballocatorBuffer} per stream. O(1) allocate and free.
 *
 * <p>Best for many geometries of similar or identical size (instanced meshes, particle billboards,
 * fixed-resolution terrain tiles). Wasteful when geometries vary significantly in size because each
 * slot reserves the maximum.
 *
 * <p>Each stream gets its own slab sized to {@code maxGeometries * slotSize}. The slot size per
 * stream is {@code maxVertexCount * stride}. Indices get a separate slab sized to
 * {@code maxGeometries * maxIndexCount * indexWidth}.
 */
public final class SlabAllocator implements GeometryAllocator {

    private final SuballocatorBuffer[] vertexSlabs;
    private final SuballocatorBuffer indexSlab;
    private final long[] vertexSlotSizes;
    private final long indexSlotSize;
    private final int streamCount;
    private final List<SlabAllocation> liveAllocations = new ArrayList<>();

    /**
     * @param device          the device
     * @param transferQueue   the transfer queue
     * @param strategy        memory strategy for the backing buffers
     * @param layout          the layout all geometries will be uploaded in
     * @param maxVertexCount  maximum vertices per geometry (determines slot size)
     * @param maxIndexCount   maximum indices per geometry (0 if not indexed)
     * @param indexWidth      index width (null if not indexed)
     * @param maxGeometries   number of slots to pre-allocate
     */
    public SlabAllocator(VkDevice device, VkQueue transferQueue, MemoryStrategy strategy,
                         MeshLayout layout, long maxVertexCount,
                         long maxIndexCount, IndexWidth indexWidth, int maxGeometries) {
        if (maxGeometries <= 0) throw new IllegalArgumentException("maxGeometries must be > 0");
        this.streamCount = layout.streamCount();
        this.vertexSlabs = new SuballocatorBuffer[streamCount];
        this.vertexSlotSizes = new long[streamCount];

        for (int s = 0; s < streamCount; s++) {
            long slotSize = layout.strideOf(s) * maxVertexCount;
            vertexSlotSizes[s] = slotSize;
            long totalSize = slotSize * maxGeometries;
            vertexSlabs[s] = new SuballocatorBuffer(device, totalSize, BufferUsage.VERTEX,
                    slotSize, strategy, transferQueue);
        }

        if (indexWidth != null && maxIndexCount > 0) {
            indexSlotSize = (long) indexWidth.byteSize() * maxIndexCount;
            long totalIndexSize = indexSlotSize * maxGeometries;
            indexSlab = new SuballocatorBuffer(device, totalIndexSize, BufferUsage.STORAGE,
                    indexSlotSize, strategy, transferQueue);
        } else {
            indexSlotSize = 0;
            indexSlab = null;
        }
    }

    @Override
    public GeometryAllocation allocate(MeshLayout layout, long vertexCount,
                                       IndexWidth indexWidth, long indexCount) {
        SuballocatorBuffer.Suballocation[] vertexSubs = new SuballocatorBuffer.Suballocation[streamCount];
        for (int s = 0; s < streamCount; s++) {
            vertexSubs[s] = vertexSlabs[s].allocate();
            if (vertexSubs[s] == null) {
                // Roll back any already-allocated slots
                for (int r = 0; r < s; r++) vertexSubs[r].close();
                throw new IllegalStateException("Slab allocator out of vertex slots in stream " + s);
            }
        }

        SuballocatorBuffer.Suballocation indexSub = null;
        if (indexSlab != null && indexWidth != null && indexCount > 0) {
            indexSub = indexSlab.allocate();
            if (indexSub == null) {
                for (var sub : vertexSubs) sub.close();
                throw new IllegalStateException("Slab allocator out of index slots");
            }
        }

        SlabAllocation alloc = new SlabAllocation(vertexSubs, indexSub, vertexSlotSizes, indexSlotSize);
        liveAllocations.add(alloc);
        return alloc;
    }

    @Override
    public void free(GeometryAllocation allocation) {
        if (allocation instanceof SlabAllocation sa) {
            liveAllocations.remove(sa);
            sa.release();
        }
    }

    @Override
    public IndexBaseMode indexBaseMode() {
        return IndexBaseMode.RELATIVE_WITH_DRAW_OFFSET;
    }

    @Override
    public void close() {
        for (SlabAllocation a : liveAllocations) a.release();
        liveAllocations.clear();
        for (SuballocatorBuffer slab : vertexSlabs) slab.close();
        if (indexSlab != null) indexSlab.close();
    }

    static final class SlabAllocation implements GeometryAllocation {
        private final SuballocatorBuffer.Suballocation[] vertexSubs;
        private final SuballocatorBuffer.Suballocation indexSub;
        private final long[] vertexSlotSizes;
        private final long indexSlotSize;

        SlabAllocation(SuballocatorBuffer.Suballocation[] vertexSubs,
                       SuballocatorBuffer.Suballocation indexSub,
                       long[] vertexSlotSizes, long indexSlotSize) {
            this.vertexSubs = vertexSubs;
            this.indexSub = indexSub;
            this.vertexSlotSizes = vertexSlotSizes;
            this.indexSlotSize = indexSlotSize;
        }

        @Override
        public DeviceRange vertexRange(int streamId) {
            if (streamId < 0 || streamId >= vertexSubs.length)
                throw new IndexOutOfBoundsException("no vertex range for stream " + streamId);
            SuballocatorBuffer.Suballocation sub = vertexSubs[streamId];
            return new DeviceRange(sub, 0, vertexSlotSizes[streamId], 0);
        }

        @Override
        public Optional<DeviceRange> indexRange() {
            if (indexSub == null) return Optional.empty();
            return Optional.of(new DeviceRange(indexSub, 0, indexSlotSize, 0));
        }

        @Override
        public long vertexBase() {
            return 0;
        }

        @Override
        public long indexBase() {
            return 0;
        }

        void release() {
            for (var sub : vertexSubs) sub.close();
            if (indexSub != null) indexSub.close();
        }
    }
}
