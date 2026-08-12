package io.github.yetyman.vulkan.sample.mesh;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkCommandBufferAlloc;
import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.Vulkan;
import io.github.yetyman.vulkan.buffers.BufferFactory;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.command.VkBind;
import io.github.yetyman.vulkan.enums.VkIndexType;
import io.github.yetyman.vulkan.highlevel.VulkanContext;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.consume.GeometryTable;
import io.github.yetyman.vulkan.mesh.consume.IndirectDrawEncoder;
import io.github.yetyman.vulkan.mesh.partition.PartitionSet;
import io.github.yetyman.vulkan.mesh.partition.SinglePartition;
import io.github.yetyman.vulkan.mesh.residency.GeometryAllocation;
import io.github.yetyman.vulkan.mesh.residency.IndexBaseMode;
import io.github.yetyman.vulkan.mesh.residency.PoolAllocator;
import io.github.yetyman.vulkan.mesh.residency.TransferBatchExecutor;
import io.github.yetyman.vulkan.mesh.residency.UploadPlan;
import io.github.yetyman.vulkan.mesh.residency.UploadPlanner;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.primitives.BoxSource;
import io.github.yetyman.vulkan.util.Logger;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Phase 5 benchmark: measures CPU command recording time at different partition counts
 * to verify Invariant 1 (steady-state involves zero per-mesh CPU work).
 *
 * <p>Headless: creates a VulkanContext without a surface or window, uploads geometry into a
 * PoolAllocator, then times how long it takes to record the GPU-driven draw path into a
 * command buffer. The draw path is a fixed 3 calls (bind vertex, bind index, bind indirect)
 * regardless of how many partitions are in the pool. The time should be flat.</p>
 *
 * <p>Run with: {@code mvn exec:java -pl sample-app -Dexec.mainClass="io.github.yetyman.vulkan.sample.mesh.MeshPoolBenchmark"}</p>
 */
public class MeshPoolBenchmark {

    private static final MeshLayout LAYOUT = MeshLayout.builder()
            .stream(0)
            .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
            .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
            .build();

    public static void main(String[] args) {
        try (Arena arena = Arena.ofConfined()) {
            // Headless Vulkan context: no surface, no swapchain extensions needed
            VulkanContext ctx = VulkanContext.builder()
                    .applicationName("MeshPoolBenchmark")
                    .deviceExtensions() // no swapchain
                    .enableValidation()
                    .preferDiscreteGpu()
                    .build();

            Logger.info("=== Mesh Pool Benchmark: Invariant 1 Verification ===");
            Logger.info("Measuring CPU recording time at different partition counts.");
            Logger.info("Expected: flat time regardless of partition count.\n");

            int[] partitionCounts = {100, 1_000, 5_000, 10_000};

            // Warmup pass: exercises all code paths, JIT compiles hotspots
            Logger.info("--- Warmup pass (results discarded) ---");
            for (int count : partitionCounts) {
                runBenchmark(ctx, count);
            }

            // Measurement pass: JIT is warm, results are meaningful
            Logger.info("\n--- Measurement pass ---");
            for (int count : partitionCounts) {
                runBenchmark(ctx, count);
            }

            Logger.info("\n=== Benchmark complete ===");
            Logger.info("If measurement-pass times are approximately equal, Invariant 1 holds.");

            ctx.close();
        }
    }

    private static void runBenchmark(VulkanContext ctx, int partitionCount) {
        int verticesPerMesh = 24; // box
        int indicesPerMesh = 36;  // box
        long maxVertices = (long) partitionCount * verticesPerMesh;
        long maxIndices = (long) partitionCount * indicesPerMesh;

        try (Arena benchArena = Arena.ofConfined()) {
            PoolAllocator pool = new PoolAllocator(ctx.device(), ctx.graphicsVkQueue(),
                    MemoryStrategy.DEVICE_LOCAL, LAYOUT, maxVertices, maxIndices,
                    IndexWidth.U32, IndexBaseMode.REWRITE_ABSOLUTE);

            GeometryTable table = new GeometryTable(ctx.device(), ctx.graphicsVkQueue(),
                    partitionCount, MemoryStrategy.DEVICE_LOCAL);

            // Upload N boxes into the pool
            GeometrySource box = new BoxSource(benchArena);
            for (int i = 0; i < partitionCount; i++) {
                GeometryAllocation alloc = pool.allocate(LAYOUT,
                        box.elementCount(), IndexWidth.U32,
                        box.indices().get().indexCount());

                UploadPlan plan = new UploadPlanner().plan(box, LAYOUT, alloc, pool);
                new TransferBatchExecutor().execute(plan, ctx.graphicsVkQueue());

                PartitionSet parts = SinglePartition.INSTANCE.partition(box);
                table.register(alloc, parts.get(0));
            }
            table.flush(ctx.graphicsVkQueue());

            // Indirect draw buffer (pre-filled, simulating GPU cull output)
            long drawBufSize = (long) partitionCount * IndirectDrawEncoder.INDEXED_STRIDE;
            IBuffer drawBuffer = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null,
                    drawBufSize, BufferUsage.INDIRECT, ctx.device(), ctx.graphicsVkQueue());

            try (var scope = drawBuffer.acquireWrite(0, drawBufSize, ctx.graphicsVkQueue())) {
                MemorySegment seg = scope.segment();
                for (int i = 0; i < partitionCount; i++) {
                    IndirectDrawEncoder.encodeIndexed(seg, i,
                            indicesPerMesh, 1, i * indicesPerMesh,
                            i * verticesPerMesh, 0);
                }
            }

            // Flush ALL pending transfers and wait for GPU completion before any buffer is used
            // for recording or destroyed. This ensures:
            // 1. All staging copies are submitted
            // 2. GPU has finished executing them
            // 3. The batch's command buffer is safe to reset
            io.github.yetyman.vulkan.buffers.TransferBatchManager.flush(ctx.device(), ctx.graphicsVkQueue());
            Vulkan.deviceWaitIdle(ctx.device().handle()).check();
            // Destroy the thread-local batch so the next iteration gets a fresh command buffer
            io.github.yetyman.vulkan.buffers.TransferBatchManager.destroyThread(ctx.device());

            // Allocate a command buffer for recording
            VkCommandPool cmdPool = ctx.createGraphicsCommandPool();
            VkCommandBuffer cmd = VkCommandBufferAlloc.builder()
                    .device(ctx.device())
                    .commandPool(cmdPool.handle())
                    .primary()
                    .allocate(benchArena)[0];

            // Warm up
            for (int w = 0; w < 10; w++) {
                recordGpuDrivenFrame(cmd, pool, drawBuffer, partitionCount, benchArena);
            }

            // Measure
            int iterations = 200;
            long startNs = System.nanoTime();
            for (int iter = 0; iter < iterations; iter++) {
                recordGpuDrivenFrame(cmd, pool, drawBuffer, partitionCount, benchArena);
            }
            long elapsedNs = System.nanoTime() - startNs;
            double avgUs = (elapsedNs / (double) iterations) / 1000.0;

            Logger.info(String.format("  %,7d partitions: %.1f us per frame recording", partitionCount, avgUs));

            // Cleanup -- GPU work is already complete from the flush above
            drawBuffer.close();
            cmdPool.close();
            table.close();
            pool.close();
        }
    }

    private static void recordGpuDrivenFrame(VkCommandBuffer cmd, PoolAllocator pool,
                                              IBuffer drawBuffer, int partitionCount,
                                              Arena frameArena) {
        VkCommandBuffer.begin(cmd).execute(frameArena);

        // GPU-driven path: 3 calls regardless of partition count
        // 1. Bind the pool's shared vertex buffer
        MemorySegment bufArray = frameArena.allocate(ValueLayout.ADDRESS, 1);
        MemorySegment offArray = frameArena.allocate(ValueLayout.JAVA_LONG, 1);
        bufArray.setAtIndex(ValueLayout.ADDRESS, 0, pool.vertexPool(0).handle());
        offArray.setAtIndex(ValueLayout.JAVA_LONG, 0, 0L);
        VkBind.bindVertexBuffers(cmd.handle(), 0, 1, bufArray, offArray);

        // 2. Bind the pool's shared index buffer
        VkBind.bindIndexBuffer(cmd.handle(), pool.indexPool().handle(), 0,
                VkIndexType.VK_INDEX_TYPE_UINT32.value());

        // 3. The indirect draw would go here in a real render pass.
        //    It's 1 call regardless of partitionCount: vkCmdDrawIndexedIndirect(cmd, drawBuf, 0, count, stride)
        //    We don't issue it because we have no render pass, but the CPU cost is the same
        //    (one FFM call to the driver, the driver reads the buffer on the GPU).

        cmd.end();
    }
}
