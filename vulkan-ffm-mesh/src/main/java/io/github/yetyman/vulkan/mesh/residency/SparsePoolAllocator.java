package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferFactory;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.buffers.SparseCapable;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A sparse-bound virtual pool allocator: address space is reserved up front via
 * {@link MemoryStrategy#SPARSE} buffers, but physical pages are only committed for geometry
 * that is currently resident. Eviction decommits pages, releasing physical memory while keeping
 * virtual addresses stable.
 *
 * <p>This is the streaming and virtual geometry allocator. It combines the single-bind advantage
 * of {@link PoolAllocator} (all geometry shares one buffer per stream) with demand-paged
 * physical memory: only resident partitions consume device memory. The virtual address space
 * can be vastly larger than physical memory, enabling out-of-core geometry streaming.</p>
 *
 * <p>Allocation is bump-based (same as {@code PoolAllocator}). Physical pages covering a range
 * are committed on upload and decommitted on eviction. Since virtual addresses never move,
 * the {@link io.github.yetyman.vulkan.mesh.consume.GeometryTable} entries for evicted partitions
 * remain valid (the GPU reads zeroes from decommitted pages on devices with sparse residency
 * guarantees).</p>
 *
 * <p><b>Requirements:</b></p>
 * <ul>
 *   <li>Device must support {@code sparseBinding} (always required for sparse buffers)</li>
 *   <li>For zero-on-read guarantees on decommitted pages, the device must also support
 *       {@code sparseResidencyBuffer}. Without it, reads from decommitted regions are
 *       undefined (usually garbage, not a crash).</li>
 * </ul>
 *
 * <p>This class implements the {@link GeometryAllocator} interface identically to
 * {@link PoolAllocator} -- the Phase 5 falsification test applies: no code above
 * {@code GeometryAllocator} should need to change when swapping allocator implementations.</p>
 */
public final class SparsePoolAllocator implements GeometryAllocator {

    private final IBuffer[] vertexPools;
    private final IBuffer indexPool;
    private final long[] vertexStrides;
    private final long[] vertexHighWaterMark;
    private long indexHighWaterMark;
    private final int indexByteSize;
    private final IndexBaseMode indexBaseMode;
    private final List<SparseAllocation> liveAllocations = new ArrayList<>();

    /**
     * @param device            the device (must support sparse binding)
     * @param sparseQueue       a queue supporting {@code VK_QUEUE_SPARSE_BINDING_BIT}
     * @param layout            the layout all geometry in this pool will use
     * @param maxVertices       maximum total vertices the virtual address space can hold
     * @param maxIndices        maximum total indices (0 if not indexed)
     * @param indexWidth        index width (null if not indexed)
     * @param indexBaseMode     how indices reference vertices in the shared pool
     */
    public SparsePoolAllocator(VkDevice device, VkQueue sparseQueue,
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
            vertexPools[s] = BufferFactory.create(MemoryStrategy.SPARSE, MemoryStrategy.DEVICE_LOCAL,
                    poolSize, BufferUsage.VERTEX, device, sparseQueue);
        }

        if (indexWidth != null && maxIndices > 0) {
            indexByteSize = indexWidth.byteSize();
            long idxPoolSize = (long) indexByteSize * maxIndices;
            indexPool = BufferFactory.create(MemoryStrategy.SPARSE, MemoryStrategy.DEVICE_LOCAL,
                    idxPoolSize, BufferUsage.INDEX, device, sparseQueue);
        } else {
            indexByteSize = 0;
            indexPool = null;
        }
    }

    @Override
    public GeometryAllocation allocate(MeshLayout layout, long vertexCount,
                                       IndexWidth indexWidth, long indexCount) {
        // Bump-allocate from each stream pool (same logic as PoolAllocator)
        long[] vertexOffsets = new long[vertexPools.length];
        long vertexBase = -1;
        for (int s = 0; s < vertexPools.length; s++) {
            long offset = vertexHighWaterMark[s];
            long size = vertexStrides[s] * vertexCount;
            if (offset + size > vertexPools[s].size()) {
                throw new IllegalStateException("Sparse pool vertex buffer for stream " + s
                        + " virtual address space exhausted: need " + (offset + size)
                        + " bytes, have " + vertexPools[s].size());
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
                throw new IllegalStateException("Sparse pool index buffer virtual address space exhausted");
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

        SparseAllocation alloc = new SparseAllocation(vertexRanges, idxRange,
                vertexBase >= 0 ? vertexBase : 0, indexBase);
        liveAllocations.add(alloc);
        return alloc;
    }

    /**
     * Commits physical pages for all buffer ranges of the given allocation.
     * Call this before uploading data to the allocation. Pages already committed are left
     * untouched.
     *
     * @param allocation the allocation whose pages to commit
     */
    public void commitPages(GeometryAllocation allocation) {
        if (!(allocation instanceof SparseAllocation sa)) {
            throw new IllegalArgumentException("Allocation did not come from this SparsePoolAllocator");
        }
        for (DeviceRange range : sa.vertexRanges) {
            if (range.buffer() instanceof SparseCapable sparse) {
                sparse.commitPages(range.offset(), range.size());
            }
        }
        if (sa.indexRange != null && sa.indexRange.buffer() instanceof SparseCapable sparse) {
            sparse.commitPages(sa.indexRange.offset(), sa.indexRange.size());
        }
    }

    /**
     * Decommits physical pages for all buffer ranges of the given allocation.
     * Call this when evicting geometry: the virtual addresses remain stable but physical memory
     * is released. On devices with sparse residency support, GPU reads from decommitted pages
     * return zero.
     *
     * @param allocation the allocation whose pages to decommit
     */
    public void decommitPages(GeometryAllocation allocation) {
        if (!(allocation instanceof SparseAllocation sa)) {
            throw new IllegalArgumentException("Allocation did not come from this SparsePoolAllocator");
        }
        for (DeviceRange range : sa.vertexRanges) {
            if (range.buffer() instanceof SparseCapable sparse) {
                sparse.decommitPages(range.offset(), range.size());
            }
        }
        if (sa.indexRange != null && sa.indexRange.buffer() instanceof SparseCapable sparse) {
            sparse.decommitPages(sa.indexRange.offset(), sa.indexRange.size());
        }
    }

    /**
     * @return true if all pages covering the given allocation are committed
     */
    public boolean isCommitted(GeometryAllocation allocation) {
        if (!(allocation instanceof SparseAllocation sa)) return false;
        for (DeviceRange range : sa.vertexRanges) {
            if (range.buffer() instanceof SparseCapable sparse) {
                if (!sparse.isCommitted(range.offset(), range.size())) return false;
            }
        }
        if (sa.indexRange != null && sa.indexRange.buffer() instanceof SparseCapable sparse) {
            if (!sparse.isCommitted(sa.indexRange.offset(), sa.indexRange.size())) return false;
        }
        return true;
    }

    @Override
    public void free(GeometryAllocation allocation) {
        // Decommit pages and remove from live list.
        // Virtual address space is not reclaimed (bump allocator).
        decommitPages(allocation);
        liveAllocations.remove(allocation);
    }

    @Override
    public IndexBaseMode indexBaseMode() {
        return indexBaseMode;
    }

    /**
     * @return the shared sparse vertex buffer for the given stream
     */
    public IBuffer vertexPool(int streamId) {
        return vertexPools[streamId];
    }

    /**
     * @return the shared sparse index buffer, or null if not indexed
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

    /**
     * @return the sparse page size for the vertex pool of stream 0, or -1 if the buffer is not
     *         sparse-capable (should not happen if constructed correctly)
     */
    public long pageSize() {
        if (vertexPools[0] instanceof SparseCapable sparse) {
            return sparse.pageSize();
        }
        return -1;
    }

    @Override
    public void close() {
        liveAllocations.clear();
        for (IBuffer pool : vertexPools) pool.close();
        if (indexPool != null) indexPool.close();
    }

    static final class SparseAllocation implements GeometryAllocation {
        private final DeviceRange[] vertexRanges;
        private final DeviceRange indexRange;
        private final long vertexBase;
        private final long indexBase;

        SparseAllocation(DeviceRange[] vertexRanges, DeviceRange indexRange,
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
