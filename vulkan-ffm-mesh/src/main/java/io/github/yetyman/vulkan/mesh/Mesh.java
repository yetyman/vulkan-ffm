package io.github.yetyman.vulkan.mesh;

import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.GpuCompletion;
import io.github.yetyman.vulkan.mesh.consume.GeometryBinding;
import io.github.yetyman.vulkan.mesh.consume.GeometryDrawRange;
import io.github.yetyman.vulkan.mesh.partition.GeometryPartition;
import io.github.yetyman.vulkan.mesh.partition.PartitionSet;
import io.github.yetyman.vulkan.mesh.partition.SinglePartition;
import io.github.yetyman.vulkan.mesh.residency.GeometryAllocation;
import io.github.yetyman.vulkan.mesh.residency.GeometryAllocator;
import io.github.yetyman.vulkan.mesh.residency.TransferBatchExecutor;
import io.github.yetyman.vulkan.mesh.residency.UploadExecutor;
import io.github.yetyman.vulkan.mesh.residency.UploadPlan;
import io.github.yetyman.vulkan.mesh.residency.UploadPlanner;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.IndexStream;
import io.github.yetyman.vulkan.mesh.source.MutableGeometrySource;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Convenience aggregate over a geometry's source, allocation, partitions, and binding.
 *
 * <p>This type is deliberately thin and carries no behavior of its own beyond wiring. Nothing in
 * {@code vulkan-ffm-mesh} depends on it, and every operation available through it is available
 * directly on the underlying types. It exists so that simple cases read simply, and so that sample
 * code has one obvious noun.
 *
 * <p>If you are building a high-performance or unusual pipeline, ignore this class and compose
 * {@link GeometrySource}, {@link GeometryAllocator}, {@link PartitionSet}, {@link GeometryBinding},
 * and {@link UploadPlanner} directly. Doing so is the intended path, not a workaround.
 *
 * <p>This class has no material, no transform, no draw method, and no render-paradigm assumptions,
 * and it must never acquire any.
 *
 * <h2>Capacity versus live count</h2>
 * An allocation is sized to a <em>capacity</em>, while the mesh tracks how many elements are
 * currently live. Growing within capacity is an upload plus a count change; exceeding it requires a
 * new allocation. Draw ranges and table records derive from the live counts, not the capacity.
 *
 * <h2>Mutation</h2>
 * {@link #update} re-uploads changed attributes over an element window without reallocating.
 * {@link #updateDirty} does the same driven by a {@link MutableGeometrySource}'s own dirty tracking.
 * Both return a {@link GpuCompletion} rather than blocking, so the caller decides whether to await
 * it or have the consuming submission wait on it. Overwriting a range the GPU is still reading is
 * undefined; either sequence the returned completion before the read, or allocate per frame in
 * flight.
 */
public final class Mesh implements AutoCloseable {

    private final GeometrySource source;
    private final MeshLayout layout;
    private final GeometryAllocation allocation;
    private final GeometryAllocator allocator;   // null when the allocation is external
    private final GeometryBinding binding;
    private final IndexWidth indexWidth;
    private final PrimitiveTopology topology;

    private PartitionSet partitions;
    private AABB bounds;
    private long vertexCount;
    private long indexCount;
    private final long vertexCapacity;
    private final long indexCapacity;

    private Mesh(Builder b, GeometryAllocation allocation, GeometryBinding binding,
                 PartitionSet partitions) {
        this.source = b.source;
        this.layout = b.layout;
        this.allocation = allocation;
        this.allocator = b.ownsAllocation ? b.allocator : null;
        this.binding = binding;
        this.indexWidth = b.indexWidth;
        this.topology = b.topology;
        this.partitions = partitions;
        this.bounds = b.bounds;
        this.vertexCount = b.vertexCount;
        this.indexCount = b.indexCount;
        this.vertexCapacity = b.vertexCapacity;
        this.indexCapacity = b.indexCapacity;
    }

    /**
     * @return the source this mesh was uploaded from, or null when built over an external allocation
     */
    public GeometrySource source() { return source; }

    public MeshLayout layout() { return layout; }
    public GeometryAllocation allocation() { return allocation; }
    public GeometryBinding binding() { return binding; }
    public PartitionSet partitions() { return partitions; }
    public AABB bounds() { return bounds; }
    public PrimitiveTopology topology() { return topology; }
    public IndexWidth indexWidth() { return indexWidth; }

    /** @return live vertex count, which may be less than {@link #vertexCapacity()} */
    public long vertexCount() { return vertexCount; }

    /** @return live index count, which may be less than {@link #indexCapacity()} */
    public long indexCount() { return indexCount; }

    /** @return the vertex capacity the allocation was sized for */
    public long vertexCapacity() { return vertexCapacity; }

    /** @return the index capacity the allocation was sized for */
    public long indexCapacity() { return indexCapacity; }

    /** @return true if this mesh owns and will free its allocation on close */
    public boolean ownsAllocation() { return allocator != null; }

    /**
     * @return a draw range covering the whole mesh as one call, using the current live counts
     */
    public GeometryDrawRange fullDrawRange() {
        if (indexCount > 0 && binding.indexBufferHandle().isPresent()) {
            return GeometryDrawRange.indexed((int) indexCount, 0,
                    (int) allocation.vertexBase(), topology);
        }
        return GeometryDrawRange.nonIndexed((int) vertexCount, 0, topology);
    }

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    /**
     * Re-uploads the given attributes over the given element window.
     *
     * <p>No reallocation occurs; the window must lie within the current capacity. Attributes sharing
     * a stream with attributes not listed here force a read-modify-write, so keep mutable attributes
     * in their own stream.
     *
     * @return completion for the upload; never blocks
     */
    public GpuCompletion update(Set<AttributeSemantic> semantics, ElementWindow window,
                                UploadExecutor executor, VkQueue queue) {
        if (source == null)
            throw new IllegalStateException("mesh has no source to update from; it wraps an external allocation");
        ElementWindow clamped = window.clampTo(vertexCapacity);
        if (clamped.isEmpty() || semantics.isEmpty()) return GpuCompletion.completed();

        UploadPlan plan = new UploadPlanner()
                .planUpdate(source, layout, allocation, semantics, clamped);
        return executor.execute(plan, queue);
    }

    /**
     * Re-uploads whatever a {@link MutableGeometrySource} reports as dirty, then clears its dirty
     * flags. Returns an already-complete token when nothing changed.
     *
     * <p>Dirty windows are unioned per stream so each stream commits once, which is cheaper than one
     * upload per attribute even when the union is slightly wider than the true edits.
     */
    public GpuCompletion updateDirty(UploadExecutor executor, VkQueue queue) {
        if (!(source instanceof MutableGeometrySource mutable))
            throw new IllegalStateException("source does not implement MutableGeometrySource; "
                    + "pass an explicit window to update(...) instead");

        Set<AttributeSemantic> dirty = new LinkedHashSet<>();
        ElementWindow combined = ElementWindow.empty();
        for (AttributeSemantic sem : layout.semantics()) {
            if (!mutable.available().contains(sem)) continue;
            if (!mutable.isDirty(sem)) continue;
            dirty.add(sem);
            combined = combined.union(mutable.dirtyWindow(sem));
        }

        if (dirty.isEmpty()) return GpuCompletion.completed();

        GpuCompletion completion = update(dirty, combined, executor, queue);
        for (AttributeSemantic sem : dirty) mutable.clearDirty(sem);
        return completion;
    }

    /**
     * Updates the live element counts after the underlying data has grown or shrunk within
     * capacity, so draw ranges and table records reflect the new size.
     *
     * @throws IllegalArgumentException if either count exceeds its capacity
     */
    public void setLiveCounts(long vertexCount, long indexCount) {
        if (vertexCount < 0 || vertexCount > vertexCapacity)
            throw new IllegalArgumentException("vertexCount " + vertexCount
                    + " exceeds capacity " + vertexCapacity);
        if (indexCount < 0 || indexCount > indexCapacity)
            throw new IllegalArgumentException("indexCount " + indexCount
                    + " exceeds capacity " + indexCapacity);
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
    }

    /**
     * Replaces the partition set, for geometry whose submesh structure changed.
     */
    public void setPartitions(PartitionSet partitions) {
        this.partitions = partitions;
    }

    /**
     * Replaces the bounds, for geometry that deformed beyond its original extent.
     */
    public void setBounds(AABB bounds) {
        this.bounds = bounds;
    }

    /**
     * @return true if {@code vertexCount}/{@code indexCount} would fit without reallocating
     */
    public boolean fitsWithinCapacity(long vertexCount, long indexCount) {
        return vertexCount <= vertexCapacity && indexCount <= indexCapacity;
    }

    @Override
    public void close() {
        if (allocator != null) allocator.free(allocation);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder with two modes.
     *
     * <p><b>Upload mode</b> (supply {@code source} + {@code allocator}): allocates, plans, uploads
     * synchronously, and returns a mesh that owns its allocation.
     *
     * <p><b>Adopt mode</b> (supply {@code allocation}): builds a mesh over buffers somebody else
     * owns and wrote, with no upload. This is how compute-generated, simulated, or externally
     * imported geometry enters the system. The mesh does not free what it did not allocate.
     */
    public static final class Builder {
        private GeometrySource source;
        private MeshLayout layout;
        private GeometryAllocator allocator;
        private UploadExecutor executor;
        private VkQueue queue;
        private GeometryAllocation externalAllocation;

        private long vertexCapacity = -1;
        private long indexCapacity = -1;
        private long vertexCount = -1;
        private long indexCount = -1;
        private IndexWidth indexWidth;
        private PrimitiveTopology topology;
        private AABB bounds;
        private PartitionSet partitions;
        private boolean ownsAllocation = true;
        private boolean awaitUpload = true;

        private Builder() {}

        public Builder source(GeometrySource source) { this.source = source; return this; }
        public Builder layout(MeshLayout layout) { this.layout = layout; return this; }
        public Builder allocator(GeometryAllocator allocator) { this.allocator = allocator; return this; }
        public Builder executor(UploadExecutor executor) { this.executor = executor; return this; }
        public Builder queue(VkQueue queue) { this.queue = queue; return this; }

        /**
         * Adopts an existing allocation instead of allocating and uploading. The mesh will not free
         * it. Requires {@code layout}, and requires the count/topology/bounds fields when no
         * {@code source} is supplied to infer them from.
         */
        public Builder allocation(GeometryAllocation allocation) {
            this.externalAllocation = allocation;
            this.ownsAllocation = false;
            return this;
        }

        /**
         * Reserves room for more vertices than the source currently has, so later growth is an
         * upload rather than a reallocation. Defaults to the source's vertex count.
         */
        public Builder vertexCapacity(long capacity) { this.vertexCapacity = capacity; return this; }

        /**
         * Reserves room for more indices than the source currently has. Defaults to the source's
         * index count.
         */
        public Builder indexCapacity(long capacity) { this.indexCapacity = capacity; return this; }

        /** Sets the live vertex count. Defaults to the source's count, or the capacity in adopt mode. */
        public Builder vertexCount(long count) { this.vertexCount = count; return this; }

        /** Sets the live index count. */
        public Builder indexCount(long count) { this.indexCount = count; return this; }

        /** Sets the index width. Inferred from the source when available. */
        public Builder indexWidth(IndexWidth width) { this.indexWidth = width; return this; }

        /** Sets the topology. Inferred from the source when available. */
        public Builder topology(PrimitiveTopology topology) { this.topology = topology; return this; }

        /** Sets the bounds. Inferred from the source when available. */
        public Builder bounds(AABB bounds) { this.bounds = bounds; return this; }

        /** Supplies a partition set instead of deriving a single whole-mesh partition. */
        public Builder partitions(PartitionSet partitions) { this.partitions = partitions; return this; }

        /**
         * When false, {@code build()} returns as soon as the upload is recorded and submitted rather
         * than waiting for the GPU. The caller must then sequence the work itself. Defaults to true.
         */
        public Builder awaitUpload(boolean await) { this.awaitUpload = await; return this; }

        public Mesh build() {
            if (externalAllocation != null) return buildAdopting();
            return buildUploading();
        }

        private Mesh buildUploading() {
            if (source == null) throw new IllegalStateException("source required (or supply allocation(...))");
            if (allocator == null) throw new IllegalStateException("allocator required");
            if (queue == null) throw new IllegalStateException("queue required");

            if (layout == null) {
                layout = source.nativeLayout().orElseThrow(() -> new IllegalStateException(
                        "layout required when the source has no native layout"));
            }
            if (executor == null) executor = new TransferBatchExecutor();

            Optional<IndexStream> idx = source.indices();
            if (indexWidth == null) indexWidth = idx.map(IndexStream::sourceWidth).orElse(null);
            if (topology == null) topology = source.topology();
            if (bounds == null) bounds = source.bounds();

            long srcVertices = source.elementCount();
            long srcIndices = idx.map(IndexStream::indexCount).orElse(0L);
            if (vertexCount < 0) vertexCount = srcVertices;
            if (indexCount < 0) indexCount = srcIndices;
            if (vertexCapacity < 0) vertexCapacity = Math.max(srcVertices, vertexCount);
            if (indexCapacity < 0) indexCapacity = Math.max(srcIndices, indexCount);

            GeometryAllocation alloc = allocator.allocate(layout, vertexCapacity,
                    indexWidth, indexCapacity);

            UploadPlan plan = new UploadPlanner().plan(source, layout, alloc, allocator);
            GpuCompletion completion = executor.execute(plan, queue);
            completion.flush();
            if (awaitUpload) {
                completion.await();
                completion.close();
            }

            GeometryBinding binding = new GeometryBinding(layout, alloc, indexWidth);
            PartitionSet parts = partitions != null
                    ? partitions : SinglePartition.INSTANCE.partition(source);

            ownsAllocation = true;
            return new Mesh(this, alloc, binding, parts);
        }

        private Mesh buildAdopting() {
            if (layout == null) throw new IllegalStateException("layout required in adopt mode");

            if (source != null) {
                Optional<IndexStream> idx = source.indices();
                if (indexWidth == null) indexWidth = idx.map(IndexStream::sourceWidth).orElse(null);
                if (topology == null) topology = source.topology();
                if (bounds == null) bounds = source.bounds();
                if (vertexCount < 0) vertexCount = source.elementCount();
                if (indexCount < 0) indexCount = idx.map(IndexStream::indexCount).orElse(0L);
            }

            if (topology == null) topology = PrimitiveTopology.TRIANGLE_LIST;
            if (bounds == null) throw new IllegalStateException(
                    "bounds required in adopt mode when no source is supplied");
            if (vertexCount < 0) throw new IllegalStateException(
                    "vertexCount required in adopt mode when no source is supplied");
            if (indexCount < 0) indexCount = 0;
            if (vertexCapacity < 0) vertexCapacity = vertexCount;
            if (indexCapacity < 0) indexCapacity = indexCount;

            GeometryBinding binding = new GeometryBinding(layout, externalAllocation, indexWidth);

            PartitionSet parts = partitions;
            if (parts == null) {
                int ipp = topology.indicesPerPrimitive();
                long primitives = ipp > 0 && indexCount > 0 ? indexCount / ipp : vertexCount;
                parts = PartitionSet.single(new GeometryPartition(
                        "", 0, primitives, vertexCount, topology, bounds, 0, 0));
            }

            ownsAllocation = false;
            return new Mesh(this, externalAllocation, binding, parts);
        }
    }
}
