package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.barriers.BarrierBatch;
import io.github.yetyman.vulkan.graph.barriers.SplitBarrierStrategy;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.feedback.AdaptiveFeedbackHandler;
import io.github.yetyman.vulkan.graph.feedback.FrameStats;
import io.github.yetyman.vulkan.graph.memory.LifetimeAliasingStrategy;
import io.github.yetyman.vulkan.graph.nodes.ComputePassNode;
import io.github.yetyman.vulkan.graph.nodes.ExecutionContext;
import io.github.yetyman.vulkan.graph.nodes.GraphicsPassNode;
import io.github.yetyman.vulkan.graph.nodes.NodeStats;
import io.github.yetyman.vulkan.graph.nodes.PresentNode;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.scheduling.ExecutionBucket;
import io.github.yetyman.vulkan.graph.scheduling.ListSchedulingStrategy;
import io.github.yetyman.vulkan.graph.scheduling.QueueAssignment;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.ScheduleHint;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test: builds a multi-node graph, compiles it, executes it,
 * and verifies execution order, barrier emission, resource state transitions, and
 * feedback propagation.
 */
class RenderGraphIntegrationTest {

    // Access/stage constants
    private static final int SHADER_WRITE = 0x00000040;
    private static final int SHADER_READ = 0x00000020;
    private static final int COLOR_WRITE = 0x00000100;
    private static final int TRANSFER_WRITE = 0x00000800;
    private static final int COMPUTE_STAGE = 0x00000800;
    private static final int FRAGMENT_STAGE = 0x00000080;
    private static final int COLOR_OUTPUT_STAGE = 0x00000400;
    private static final int TRANSFER_STAGE = 0x00001000;
    private static final int BOTTOM_OF_PIPE = 0x00002000;

    @Test
    void fullPipeline_executesInCorrectOrder() {
        // Build a simple deferred pipeline: gbuffer -> lighting -> present
        GraphResource gbufferColor = TestResources.transientBuffer("gbuffer-color");
        GraphResource hdrColor = TestResources.transientBuffer("hdr-color");
        GraphResource swapchain = TestResources.importedBuffer("swapchain");

        List<String> executionOrder = new ArrayList<>();

        RenderNode gbufferPass = GraphicsPassNode.builder()
            .name("gbuffer")
            .writes(ResourceEdge.write(gbufferColor, COLOR_WRITE, COLOR_OUTPUT_STAGE))
            .execute(ctx -> executionOrder.add("gbuffer"))
            .build();

        RenderNode lightingPass = GraphicsPassNode.builder()
            .name("lighting")
            .reads(ResourceEdge.read(gbufferColor, SHADER_READ, FRAGMENT_STAGE))
            .writes(ResourceEdge.write(hdrColor, COLOR_WRITE, COLOR_OUTPUT_STAGE))
            .execute(ctx -> executionOrder.add("lighting"))
            .build();

        RenderNode tonemapPass = GraphicsPassNode.builder()
            .name("tonemap")
            .reads(ResourceEdge.read(hdrColor, SHADER_READ, FRAGMENT_STAGE))
            .writes(ResourceEdge.write(swapchain, COLOR_WRITE, COLOR_OUTPUT_STAGE))
            .execute(ctx -> executionOrder.add("tonemap"))
            .build();

        RenderNode present = PresentNode.of(swapchain, MemorySegment.NULL);

        // Compile
        RenderGraphCompiler compiler = new RenderGraphCompiler(
            new ListSchedulingStrategy(), new SplitBarrierStrategy(), new LifetimeAliasingStrategy());
        CompiledGraph compiled = compiler.compile(
            List.of(gbufferPass, lightingPass, tonemapPass, present),
            Map.of(QueueCapability.GRAPHICS, new QueueAssignment(MemorySegment.NULL, 0, QueueCapability.GRAPHICS)));

        // Execute (no real command buffer needed -- nodes just record to our list)
        RenderGraphExecutor executor = new RenderGraphExecutor(null, new SplitBarrierStrategy());
        try (Arena arena = Arena.ofConfined()) {
            executor.execute(compiled, null, arena, 0, 0, null);
        }

        // Verify execution order respects dependencies
        assertEquals(List.of("gbuffer", "lighting", "tonemap"), executionOrder);
    }

    @Test
    void resourceState_updatedAfterExecution() {
        GraphResource buf = TestResources.transientBuffer("buf");
        GraphResource out = TestResources.importedBuffer("out");

        RenderNode writer = ComputePassNode.builder()
            .name("writer")
            .writes(ResourceEdge.write(buf, SHADER_WRITE, COMPUTE_STAGE))
            .execute(ctx -> {})
            .build();
        RenderNode reader = ComputePassNode.builder()
            .name("reader")
            .reads(ResourceEdge.read(buf, SHADER_READ, COMPUTE_STAGE))
            .writes(ResourceEdge.write(out, SHADER_WRITE, COMPUTE_STAGE))
            .execute(ctx -> {})
            .build();

        RenderGraphCompiler compiler = new RenderGraphCompiler(
            new ListSchedulingStrategy(), new SplitBarrierStrategy(), null);
        CompiledGraph compiled = compiler.compile(List.of(writer, reader),
            Map.of(QueueCapability.COMPUTE, new QueueAssignment(MemorySegment.NULL, 1, QueueCapability.COMPUTE)));

        RenderGraphExecutor executor = new RenderGraphExecutor(null, new SplitBarrierStrategy());
        try (Arena arena = Arena.ofConfined()) {
            executor.execute(compiled, null, arena, 0, 0, null);
        }

        // After execution, buf should have writer's access/stage/queue
        assertEquals(SHADER_WRITE, buf.lastAccessMask());
        assertEquals(COMPUTE_STAGE, buf.lastStageMask());
        assertEquals(1, buf.owningQueueFamily());
    }

    @Test
    void barrierEmission_writeToReadEmitsBarrier() {
        // Manually test that the strategy emits a barrier between write and read
        GraphResource buf = TestResources.transientBuffer("buf");
        buf.updateState(SHADER_WRITE, COMPUTE_STAGE, 0);

        SplitBarrierStrategy strategy = new SplitBarrierStrategy();
        try (Arena arena = Arena.ofConfined()) {
            BarrierBatch batch = new BarrierBatch();
            strategy.emit(buf, ResourceEdge.read(buf, SHADER_READ, FRAGMENT_STAGE), batch, arena);

            assertFalse(batch.isEmpty());
            assertEquals(COMPUTE_STAGE, batch.srcStageMask());
            assertEquals(FRAGMENT_STAGE, batch.dstStageMask());
        }
    }

    @Test
    void culling_removesUnreachableNodes() {
        GraphResource used = TestResources.transientBuffer("used");
        GraphResource unused = TestResources.transientBuffer("unused");
        GraphResource swapchain = TestResources.importedBuffer("swapchain");

        RenderNode usedPass = GraphicsPassNode.builder()
            .name("used-pass")
            .writes(ResourceEdge.write(used, COLOR_WRITE, COLOR_OUTPUT_STAGE))
            .execute(ctx -> {})
            .build();
        RenderNode unusedPass = GraphicsPassNode.builder()
            .name("unused-pass")
            .writes(ResourceEdge.write(unused, COLOR_WRITE, COLOR_OUTPUT_STAGE))
            .execute(ctx -> {})
            .build();
        RenderNode finalPass = GraphicsPassNode.builder()
            .name("final")
            .reads(ResourceEdge.read(used, SHADER_READ, FRAGMENT_STAGE))
            .writes(ResourceEdge.write(swapchain, COLOR_WRITE, COLOR_OUTPUT_STAGE))
            .execute(ctx -> {})
            .build();
        RenderNode present = PresentNode.of(swapchain, MemorySegment.NULL);

        RenderGraphCompiler compiler = new RenderGraphCompiler(
            new ListSchedulingStrategy(), null, null);
        CompiledGraph compiled = compiler.compile(
            List.of(usedPass, unusedPass, finalPass, present),
            Map.of(QueueCapability.GRAPHICS, new QueueAssignment(MemorySegment.NULL, 0, QueueCapability.GRAPHICS)));

        // unused-pass should be culled
        List<String> activeNames = compiled.activeNodes().stream().map(RenderNode::name).toList();
        assertTrue(activeNames.contains("used-pass"));
        assertTrue(activeNames.contains("final"));
        assertTrue(activeNames.contains("present"));
        assertFalse(activeNames.contains("unused-pass"));
    }

    @Test
    void feedbackLoop_statsReachNodes() {
        GraphResource buf = TestResources.transientBuffer("buf");
        GraphResource out = TestResources.importedBuffer("out");
        AtomicInteger statsReceived = new AtomicInteger(0);

        ComputePassNode node = ComputePassNode.builder()
            .name("adaptive")
            .writes(ResourceEdge.write(buf, SHADER_WRITE, COMPUTE_STAGE))
            .execute(ctx -> {})
            .onStats(stats -> statsReceived.incrementAndGet())
            .build();
        RenderNode sink = ComputePassNode.builder()
            .name("sink")
            .reads(ResourceEdge.read(buf, SHADER_READ, COMPUTE_STAGE))
            .writes(ResourceEdge.write(out, SHADER_WRITE, COMPUTE_STAGE))
            .execute(ctx -> {})
            .build();

        RenderGraphCompiler compiler = new RenderGraphCompiler(
            new ListSchedulingStrategy(), null, null);
        CompiledGraph compiled = compiler.compile(List.of(node, sink),
            Map.of(QueueCapability.COMPUTE, new QueueAssignment(MemorySegment.NULL, 0, QueueCapability.COMPUTE)));

        // Simulate stats delivery
        FrameStats stats = new FrameStats(0, 1_000_000, 100_000, Map.of(
            "adaptive", new NodeStats(1_000_000, 100_000, 0)
        ));

        // Distribute stats to nodes (as RenderGraph.onFrameComplete does)
        for (RenderNode n : compiled.activeNodes()) {
            var ns = stats.forNode(n.name());
            if (ns != null) n.onStats(ns);
        }

        assertEquals(1, statsReceived.get());
    }

    @Test
    void parallelNodes_scheduledInSameBucket() {
        // Two independent compute passes should land in the same bucket
        GraphResource a = TestResources.transientBuffer("a");
        GraphResource b = TestResources.transientBuffer("b");
        GraphResource out = TestResources.importedBuffer("out");

        RenderNode passA = ComputePassNode.builder()
            .name("A")
            .writes(ResourceEdge.write(a, SHADER_WRITE, COMPUTE_STAGE))
            .execute(ctx -> {})
            .build();
        RenderNode passB = ComputePassNode.builder()
            .name("B")
            .writes(ResourceEdge.write(b, SHADER_WRITE, COMPUTE_STAGE))
            .execute(ctx -> {})
            .build();
        RenderNode merge = ComputePassNode.builder()
            .name("merge")
            .reads(ResourceEdge.read(a, SHADER_READ, COMPUTE_STAGE))
            .reads(ResourceEdge.read(b, SHADER_READ, COMPUTE_STAGE))
            .writes(ResourceEdge.write(out, SHADER_WRITE, COMPUTE_STAGE))
            .execute(ctx -> {})
            .build();

        RenderGraphCompiler compiler = new RenderGraphCompiler(
            new ListSchedulingStrategy(), null, null);
        CompiledGraph compiled = compiler.compile(List.of(passA, passB, merge),
            Map.of(QueueCapability.COMPUTE, new QueueAssignment(MemorySegment.NULL, 0, QueueCapability.COMPUTE)));

        // A and B should be in bucket 0, merge in bucket 1
        List<ExecutionBucket> buckets = compiled.executionBuckets();
        assertEquals(2, buckets.size());
        assertEquals(2, buckets.get(0).nodes().size());
        assertEquals(1, buckets.get(1).nodes().size());
        assertEquals("merge", buckets.get(1).nodes().get(0).name());
    }

    @Test
    void cpuTimingCollected() {
        GraphResource out = TestResources.importedBuffer("out");

        RenderNode slowNode = ComputePassNode.builder()
            .name("slow")
            .writes(ResourceEdge.write(out, SHADER_WRITE, COMPUTE_STAGE))
            .execute(ctx -> {
                // Simulate some CPU work
                long start = System.nanoTime();
                while (System.nanoTime() - start < 100_000) { /* spin 0.1ms */ }
            })
            .build();

        RenderGraphCompiler compiler = new RenderGraphCompiler(
            new ListSchedulingStrategy(), null, null);
        CompiledGraph compiled = compiler.compile(List.of(slowNode),
            Map.of(QueueCapability.COMPUTE, new QueueAssignment(MemorySegment.NULL, 0, QueueCapability.COMPUTE)));

        RenderGraphExecutor executor = new RenderGraphExecutor(null, null);
        Map<String, Long> cpuTimes;
        try (Arena arena = Arena.ofConfined()) {
            cpuTimes = executor.execute(compiled, null, arena, 0, 0, null);
        }

        assertTrue(cpuTimes.containsKey("slow"));
        assertTrue(cpuTimes.get("slow") >= 50_000, "Should measure at least 50us of CPU time");
    }
}
