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
 * A global vertex/index pool allocator: one large buffer per stream, one for indices.
 * All geometry shares buffers, so binding happens once for the whole scene. This is the
 * prerequisite for a single {@code vkCmdDrawIndexedIndirectCount} over everything.
 *
 * <p>Allocation is a simple bump allocator with a free list for reclamation. Fragmentation is
 * expected to be handled by a future defragmentation pass (copy + table update); the pool does
 * not compact automatically.
 *
 * <p>This is the implementation the Phase 5 falsification test targets: swapping
 * {@link DedicatedAllocator} for this class must require no changes above the
 * {@link GeometryAllocator} interface.
 */
public final class PoolAllocator implements GeometryAllocator {

    private final IBuffer[] vertexPools;
    private final IBuffer indexPool;
    private final long[] vertexStrides;
    private final long[] vertexHighWaterMark;
    private long indexHighWaterMark;
    private final int indexByteSize;
    private final IndexBaseMode indexBaseMode;
    private final List<PoolAllocation> liveAllocations = new ArrayList<>();

    /**
     * @param device           the device
     * @param transferQueue    the queue for transfer operations
     * @param strategy         memory strategy for the pool buffers
     * @param layout           the layout all geometry in this pool will use
     * @param maxVertices      maximum total vertices the pool can hold
     * @param maxIndices       maximum total indices (0 if not indexed)
     * @param indexWidth       index width (null if not indexed)
     * @param indexBaseMode    how indices reference vertices in the shared pool
     */
    public PoolAllocator(VkDevice device, VkQueue transferQueue, MemoryStrategy strategy,
                         MeshLayout layout, long maxVertices, long maxIndices,
                         IndexWidth indexWidth, IndexBaseMode indexBaseMode) {
        int streamCount = layout.streamCount();
        this.vertexPools = new IBuffer[streamCount];
        this.vertexStrides = new long[streamCount];
        this.vertexHighWaterMark = new long[streamCount];
        this.indexBaseMode = indexBaseMode;

        for (int s = 0; s < streamCount; s++) {
            long stride = layout.strideOf(s);
            vertexStrides[s] = stride;
            long poolSize = stride * maxVertices;
            vertexPools[s] = BufferFactory.create(strategy, null, poolSize,
                    BufferUsage.VERTEX, device, transferQueue);
        }

        if (indexWidth != null && maxIndices > 0) {
            indexByteSize = indexWidth.byteSize();
            long idxPoolSize = (long) indexByteSize * maxIndices;
            indexPool = BufferFactory.create(strategy, null, idxPoolSize,
                    BufferUsage.INDEX, device, transferQueue);
        } else {
            indexByteSize = 0;
            indexPool = null;
        }
    }

    @Override
    public GeometryAllocation allocate(MeshLayout layout, long vertexCount,
                                       IndexWidth indexWidth, long indexCount) {
        // Bump-allocate from each stream pool
        long[] vertexOffsets = new long[vertexPools.length];
        long vertexBase = -1;
        for (int s = 0; s < vertexPools.length; s++) {
            long offset = vertexHighWaterMark[s];
            long size = vertexStrides[s] * vertexCount;
            if (offset + size > vertexPools[s].size()) {
                throw new IllegalStateException("Pool allocator vertex pool for stream " + s
                        + " exhausted: need " + (offset + size) + " bytes, have " + vertexPools[s].size());
            }
            vertexOffsets[s] = offset;
            vertexHighWaterMark[s] = offset + size;
            if (vertexBase < 0) vertexBase = offset / vertexStrides[s];
        }

        long indexOffset = 0;
        long indexBase = 0;
        if (indexPool != null && indexWidth != null && indexCount > 0) {
            indexOffset = indexHighWaterMark;
            long idxSize = (long) indexWidth.byteSize() * indexCount;
            if (indexOffset + idxSize > indexPool.size()) {
                throw new IllegalStateException("Pool allocator index pool exhausted");
            }
            indexHighWaterMark = indexOffset + idxSize;
            indexBase = indexOffset / indexWidth.byteSize();
        }

        DeviceRange[] vertexRanges = new DeviceRange[vertexPools.length];
        for (int s = 0; s < vertexPools.length; s++) {
            long size = vertexStrides[s] * vertexCount;
            vertexRanges[s] = new DeviceRange(vertexPools[s], vertexOffsets[s], size, vertexStrides[s]);
        }

        DeviceRange idxRange = null;
        if (indexPool != null && indexWidth != null && indexCount > 0) {
            long idxSize = (long) indexWidth.byteSize() * indexCount;
            idxRange = new DeviceRange(indexPool, indexOffset, idxSize, indexWidth.byteSize());
        }

        PoolAllocation alloc = new PoolAllocation(vertexRanges, idxRange,
                vertexBase >= 0 ? vertexBase : 0, indexBase);
        liveAllocations.add(alloc);
        return alloc;
    }

    @Override
    public void free(GeometryAllocation allocation) {
        // Bump allocator: free is a no-op until defragmentation is implemented.
        // The space is not reclaimed, but the allocation is unlinked.
        liveAllocations.remove(allocation);
    }

    @Override
    public IndexBaseMode indexBaseMode() {
        return indexBaseMode;
    }

    /**
     * @return the shared vertex buffer for the given stream. Bind this once for the whole scene.
     */
    public IBuffer vertexPool(int streamId) {
        return vertexPools[streamId];
    }

    /**
     * @return the shared index buffer, or null if not indexed. Bind this once for the whole scene.
     */
    public IBuffer indexPool() {
        return indexPool;
    }

    /**
     * @return number of live allocations
     */
    public int allocationCount() {
        return liveAllocations.size();
    }

    @Override
    public void close() {
        liveAllocations.clear();
        for (IBuffer pool : vertexPools) pool.close();
        if (indexPool != null) indexPool.close();
    }

    static final class PoolAllocation implements GeometryAllocation {
        private final DeviceRange[] vertexRanges;
        private final DeviceRange indexRange;
        private final long vertexBase;
        private final long indexBase;

        PoolAllocation(DeviceRange[] vertexRanges, DeviceRange indexRange,
                       long vertexBase, long indexBase) {
            this.vertexRanges = vertexRanges;
            this.indexRange = indexRange;
            this.vertexBase = vertexBase;
            this.indexBase = indexBase;
        }

        @Override
        public DeviceRange vertexRange(int streamId) {
            return vertexRanges[streamId];
        }

        @Override
        public Optional<DeviceRange> indexRange() {
            return Optional.ofNullable(indexRange);
        }

        @Override
        public long vertexBase() {
            return vertexBase;
        }

        @Override
        public long indexBase() {
            return indexBase;
        }
    }
}
