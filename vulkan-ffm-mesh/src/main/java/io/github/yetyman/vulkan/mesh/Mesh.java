package io.github.yetyman.vulkan.mesh;

import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.GpuCompletion;
import io.github.yetyman.vulkan.mesh.consume.GeometryBinding;
import io.github.yetyman.vulkan.mesh.consume.GeometryDrawRange;
import io.github.yetyman.vulkan.mesh.partition.GeometryPartition;
import io.github.yetyman.vulkan.mesh.partition.PartitionSet;
import io.github.yetyman.vulkan.mesh.residency.DedicatedAllocator;
import io.github.yetyman.vulkan.mesh.residency.GeometryAllocator;
import io.github.yetyman.vulkan.mesh.residency.GeometryAllocation;
import io.github.yetyman.vulkan.mesh.residency.TransferBatchExecutor;
import io.github.yetyman.vulkan.mesh.residency.UploadExecutor;
import io.github.yetyman.vulkan.mesh.residency.UploadPlan;
import io.github.yetyman.vulkan.mesh.residency.UploadPlanner;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.IndexStream;

import java.util.List;
import java.util.Optional;

/**
 * Convenience aggregate over a geometry's source, allocation, partitions, and binding.
 *
 * <p>This type is deliberately thin and carries no behavior of its own. Nothing in
 * {@code vulkan-ffm-mesh} depends on it, and every operation available through it is available
 * directly on the underlying types. It exists so that simple cases read simply, and so that sample
 * code has one obvious noun.
 *
 * <p>If you are building a high-performance or unusual pipeline, ignore this class and compose
 * {@link GeometrySource}, {@link GeometryAllocator}, {@link PartitionSet},
 * {@link GeometryBinding}, and the upload planner directly.
 * Doing so is the intended path, not a workaround.
 *
 * <p>This class has no material, no transform, no draw method, and no render-paradigm assumptions,
 * and it must never acquire any.
 */
public final class Mesh implements AutoCloseable {

    private final GeometrySource source;
    private final MeshLayout layout;
    private final GeometryAllocation allocation;
    private final GeometryAllocator allocator;
    private final GeometryBinding binding;
    private final PartitionSet partitions;
    private final AABB bounds;

    private Mesh(GeometrySource source, MeshLayout layout, GeometryAllocation allocation,
                 GeometryAllocator allocator, GeometryBinding binding,
                 PartitionSet partitions, AABB bounds) {
        this.source = source;
        this.layout = layout;
        this.allocation = allocation;
        this.allocator = allocator;
        this.binding = binding;
        this.partitions = partitions;
        this.bounds = bounds;
    }

    public GeometrySource source() { return source; }
    public MeshLayout layout() { return layout; }
    public GeometryAllocation allocation() { return allocation; }
    public GeometryBinding binding() { return binding; }
    public PartitionSet partitions() { return partitions; }
    public AABB bounds() { return bounds; }

    /**
     * @return a draw range covering the entire mesh as a single draw call
     */
    public GeometryDrawRange fullDrawRange() {
        Optional<IndexStream> idx = source.indices();
        if (idx.isPresent()) {
            return GeometryDrawRange.indexed(
                    (int) idx.get().indexCount(), 0, (int) allocation.vertexBase(),
                    source.topology());
        }
        return GeometryDrawRange.nonIndexed(
                (int) source.elementCount(), 0, source.topology());
    }

    @Override
    public void close() {
        allocator.free(allocation);
    }

    /**
     * Builder that does the obvious thing: takes a source, allocates, uploads immediately, and
     * returns a ready-to-draw Mesh.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private GeometrySource source;
        private MeshLayout layout;
        private GeometryAllocator allocator;
        private UploadExecutor executor;
        private VkQueue queue;

        private Builder() {}

        public Builder source(GeometrySource source) { this.source = source; return this; }
        public Builder layout(MeshLayout layout) { this.layout = layout; return this; }
        public Builder allocator(GeometryAllocator allocator) { this.allocator = allocator; return this; }
        public Builder executor(UploadExecutor executor) { this.executor = executor; return this; }
        public Builder queue(VkQueue queue) { this.queue = queue; return this; }

        /**
         * Allocates buffer space, plans the upload, executes it synchronously, and returns the
         * mesh ready for use.
         */
        public Mesh build() {
            if (source == null) throw new IllegalStateException("source required");
            if (allocator == null) throw new IllegalStateException("allocator required");
            if (queue == null) throw new IllegalStateException("queue required");

            if (layout == null) {
                layout = source.nativeLayout().orElseThrow(
                        () -> new IllegalStateException("layout required when source has no native layout"));
            }
            if (executor == null) executor = new TransferBatchExecutor();

            IndexWidth idxWidth = source.indices()
                    .map(idx -> IndexWidth.narrowestFor(source.elementCount()))
                    .orElse(null);
            long indexCount = source.indices().map(IndexStream::indexCount).orElse(0L);

            GeometryAllocation allocation = allocator.allocate(layout, source.elementCount(),
                    idxWidth, indexCount);

            UploadPlan plan = new UploadPlanner().plan(source, layout, allocation, allocator);
            GpuCompletion completion = executor.execute(plan, queue);
            completion.flush();
            completion.await();
            completion.close();

            GeometryBinding binding = new GeometryBinding(layout, allocation, idxWidth);

            GeometryPartition single = new GeometryPartition(
                    "", 0, source.indices().map(idx -> idx.indexCount() / 3).orElse(source.elementCount() / 3),
                    source.elementCount(), source.topology(), source.bounds(), 0, 0);
            PartitionSet partitions = PartitionSet.single(single);

            return new Mesh(source, layout, allocation, allocator, binding, partitions, source.bounds());
        }
    }
}
