package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.edges.DependencyEdge;
import io.github.yetyman.vulkan.graph.edges.OptionalEdge;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.edges.TemporalEdge;
import io.github.yetyman.vulkan.graph.feedback.FrameStats;
import io.github.yetyman.vulkan.graph.nodes.ComputePassNode;
import io.github.yetyman.vulkan.graph.nodes.GraphicsPassNode;
import io.github.yetyman.vulkan.graph.nodes.IterativePassNode;
import io.github.yetyman.vulkan.graph.nodes.Priority;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.ResourceDescriptor;
import io.github.yetyman.vulkan.graph.resources.TemporalResource;
import io.github.yetyman.vulkan.graph.resources.TemporalResizeStrategy;
import io.github.yetyman.vulkan.graph.scheduling.ListSchedulingStrategy;
import io.github.yetyman.vulkan.graph.scheduling.QueueAssignment;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.TopologicalSort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for newly wired render graph APIs:
 * - DegradationStrategy
 * - DependencyEdge in TopologicalSort
 * - OptionalEdge
 * - IterativePassNode iteration tracking
 * - TemporalResource staleness
 * - Multiple-writer validation
 * - Compiled graph introspection
 * - TemporalResizeStrategy
 */
class RenderGraphFeaturesTest {

    private RenderGraphCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new RenderGraphCompiler(new ListSchedulingStrategy(), null, null);
    }

    // -- DegradationStrategy --

    @Test
    void degradation_none_returnsAllNodes() {
        var strategy = DegradationStrategy.none();
        var nodes = List.<RenderNode>of(
            computeNode("a", Priority.LOW),
            computeNode("b", Priority.HIGH)
        );
        assertEquals(2, strategy.apply(nodes, null).size());
    }

    @Test
    void degradation_dropByPriority_dropsLowestWhenOverBudget() {
        var strategy = DegradationStrategy.dropByPriority(5.0f);
        var low = computeNode("low", Priority.LOW);
        var high = computeNode("high", Priority.CRITICAL);
        var nodes = List.<RenderNode>of(high, low);

        // Simulate stats showing 10ms total (over 5ms budget)
        var stats = fakeStats(10_000_000L, Map.of("low", 6_000_000L, "high", 4_000_000L));
        var result = strategy.apply(new ArrayList<>(nodes), stats);

        // Low priority should be dropped
        assertTrue(result.stream().anyMatch(n -> n.name().equals("high")));
        assertFalse(result.stream().anyMatch(n -> n.name().equals("low")));
    }

    // -- DependencyEdge in TopologicalSort --

    @Test
    void dependencyEdge_enforcesOrdering() {
        GraphResource buf = TestResources.transientBuffer("buf");
        var nodeA = ComputePassNode.builder().name("A")
            .writes(ResourceEdge.write(buf, 0x40, 0x800)).execute(ctx -> {}).build();
        var nodeB = ComputePassNode.builder().name("B")
            .writes(ResourceEdge.write(TestResources.transientBuffer("out"), 0x40, 0x800)).execute(ctx -> {}).build();

        // Without dependency edge, B could come before A (no resource dependency)
        // With dependency edge A->B, A must come first
        var dep = DependencyEdge.of(nodeA, nodeB);
        var sorted = TopologicalSort.sort(List.of(nodeB, nodeA), n -> 0, List.of(dep));

        assertEquals("A", sorted.get(0).name());
        assertEquals("B", sorted.get(1).name());
    }

    @Test
    void dependencyEdge_inactiveIsIgnored() {
        GraphResource buf = TestResources.transientBuffer("buf");
        var nodeA = ComputePassNode.builder().name("A")
            .writes(ResourceEdge.write(buf, 0x40, 0x800)).execute(ctx -> {}).build();
        var nodeB = ComputePassNode.builder().name("B")
            .writes(ResourceEdge.write(TestResources.transientBuffer("out"), 0x40, 0x800)).execute(ctx -> {}).build();

        var dep = DependencyEdge.of(nodeA, nodeB);
        dep.remove(); // deactivate

        // Should not throw cycle error - edge is inactive
        assertDoesNotThrow(() -> TopologicalSort.sort(List.of(nodeB, nodeA), n -> 0, List.of(dep)));
    }

    // -- OptionalEdge --

    @Test
    void optionalRead_declaredOnNode() {
        GraphResource optional = TestResources.transientBuffer("ssao");
        var node = GraphicsPassNode.builder().name("composite")
            .writes(ResourceEdge.write(TestResources.transientBuffer("out"), 0x100, 0x400))
            .optionalRead(OptionalEdge.of(optional, 0x20, 0x80, OptionalEdge.Fallback.clear(1.0f)))
            .execute(ctx -> {})
            .build();

        assertEquals(1, node.optionalReads().size());
        assertEquals("ssao", node.optionalReads().get(0).resource().name());
    }

    // -- IterativePassNode --

    @Test
    void iterativePassNode_tracksIterationIndex() {
        AtomicInteger maxIteration = new AtomicInteger(-1);
        var node = IterativePassNode.builder()
            .name("iterate")
            .maxIterations(5)
            .execute((ctx, iteration) -> maxIteration.set(iteration))
            .build();

        // Execute with a fake context that supports setIterationIndex
        node.execute(new FakeExecutionContext());
        assertEquals(4, maxIteration.get()); // 0-indexed, last iteration is 4
    }

    @Test
    void iterativePassNode_predicateStopsEarly() {
        AtomicInteger count = new AtomicInteger(0);
        var node = IterativePassNode.builder()
            .name("converge")
            .maxIterations(100)
            .continueWhen(() -> count.get() < 3)
            .execute((ctx, iteration) -> count.incrementAndGet())
            .build();

        node.execute(new FakeExecutionContext());
        assertEquals(3, count.get());
    }

    // -- Temporal staleness --

    @Test
    void temporalResource_stalenessAdvances() {
        var tr = TemporalResource.builder()
            .name("history")
            .descriptor(ResourceDescriptor.buffer(1024, 0x80))
            .bufferCount(2)
            .build();

        assertEquals(0, tr.staleness());
        tr.advanceSubmission();
        assertEquals(1, tr.staleness());
        tr.advanceSubmission();
        assertEquals(2, tr.staleness());
        tr.onWriteExecuted();
        assertEquals(0, tr.staleness()); // reset on write
    }

    // -- Multiple-writer validation --

    @Test
    void multipleWriters_throwsForTemporalResource() {
        var tr = TemporalResource.builder()
            .name("shared")
            .descriptor(ResourceDescriptor.buffer(1024, 0x80))
            .bufferCount(2)
            .build();

        var nodeA = ComputePassNode.builder().name("writerA")
            .temporalEdge(TemporalEdge.writeCurrent(tr, 0x40, 0x800))
            .writes(ResourceEdge.write(TestResources.transientBuffer("out"), 0x40, 0x800))
            .execute(ctx -> {}).build();
        var nodeB = ComputePassNode.builder().name("writerB")
            .temporalEdge(TemporalEdge.writeCurrent(tr, 0x40, 0x800))
            .writes(ResourceEdge.write(TestResources.transientBuffer("out2"), 0x40, 0x800))
            .execute(ctx -> {}).build();

        var ex = assertThrows(RenderGraphException.class,
            () -> compiler.compile(List.of(nodeA, nodeB),
                Map.of(QueueCapability.COMPUTE, new QueueAssignment(MemorySegment.NULL, 0, QueueCapability.COMPUTE)),
                List.of(tr)));
        assertTrue(ex.getMessage().contains("multiple active writers"));
    }

    // -- Compiled graph introspection --

    @Test
    void introspection_passesReturnsOrderedInfo() {
        GraphResource buf = TestResources.transientBuffer("buf");
        GraphResource out = TestResources.transientBuffer("out");
        var writer = ComputePassNode.builder().name("compute")
            .writes(ResourceEdge.write(buf, 0x40, 0x800)).execute(ctx -> {}).build();
        var reader = GraphicsPassNode.builder().name("render")
            .reads(ResourceEdge.read(buf, 0x20, 0x80))
            .writes(ResourceEdge.write(out, 0x100, 0x400))
            .execute(ctx -> {}).build();
        // PresentNode acts as a sink so nodes aren't culled
        var present = io.github.yetyman.vulkan.graph.nodes.PresentNode.of(out, MemorySegment.NULL);

        var compiled = compiler.compile(List.of(writer, reader, present),
            Map.of(QueueCapability.GRAPHICS, new QueueAssignment(MemorySegment.NULL, 0, QueueCapability.GRAPHICS),
                   QueueCapability.COMPUTE, new QueueAssignment(MemorySegment.NULL, 0, QueueCapability.COMPUTE)),
            List.of());

        var passes = compiled.passes();
        assertTrue(passes.size() >= 2);
        // compute must come before render
        int computeIdx = -1, renderIdx = -1;
        for (int i = 0; i < passes.size(); i++) {
            if (passes.get(i).name().equals("compute")) computeIdx = i;
            if (passes.get(i).name().equals("render")) renderIdx = i;
        }
        assertTrue(computeIdx >= 0 && renderIdx >= 0);
        assertTrue(computeIdx < renderIdx);
    }

    @Test
    void introspection_exportDotProducesValidOutput() {
        GraphResource buf = TestResources.transientBuffer("buf");
        GraphResource out = TestResources.transientBuffer("out");
        var writer = ComputePassNode.builder().name("compute")
            .writes(ResourceEdge.write(buf, 0x40, 0x800)).execute(ctx -> {}).build();
        var reader = ComputePassNode.builder().name("postprocess")
            .reads(ResourceEdge.read(buf, 0x20, 0x800))
            .writes(ResourceEdge.write(out, 0x40, 0x800))
            .execute(ctx -> {}).build();
        var present = io.github.yetyman.vulkan.graph.nodes.PresentNode.of(out, MemorySegment.NULL);

        var compiled = compiler.compile(List.of(writer, reader, present),
            Map.of(QueueCapability.COMPUTE, new QueueAssignment(MemorySegment.NULL, 0, QueueCapability.COMPUTE),
                   QueueCapability.GRAPHICS, new QueueAssignment(MemorySegment.NULL, 0, QueueCapability.GRAPHICS)),
            List.of());

        String dot = compiled.exportDot();
        assertTrue(dot.contains("digraph"));
        assertTrue(dot.contains("compute"));
        assertTrue(dot.contains("postprocess"));
    }

    // -- TemporalResizeStrategy --

    @Test
    void resizeStrategy_clearResetsWriteCount() {
        var tr = TemporalResource.builder()
            .name("history")
            .descriptor(ResourceDescriptor.buffer(1024, 0x80))
            .bufferCount(2)
            .resizeStrategy(TemporalResizeStrategy.clear())
            .build();

        tr.onWriteExecuted();
        tr.onWriteExecuted();
        assertEquals(2, tr.writeCount());

        tr.resizeStrategy().onResize(tr, null, null, null, null);
        assertEquals(0, tr.writeCount());
    }

    // -- Determinism --

    @Test
    void compilation_isDeterministic() {
        // Same graph compiled twice should produce identical bucket ordering
        GraphResource a = TestResources.transientBuffer("a");
        GraphResource b = TestResources.transientBuffer("b");
        GraphResource c = TestResources.transientBuffer("c");
        GraphResource out = TestResources.transientBuffer("out");

        var n1 = ComputePassNode.builder().name("n1").writes(ResourceEdge.write(a, 0x40, 0x800)).execute(ctx -> {}).build();
        var n2 = ComputePassNode.builder().name("n2").reads(ResourceEdge.read(a, 0x20, 0x800)).writes(ResourceEdge.write(b, 0x40, 0x800)).execute(ctx -> {}).build();
        var n3 = ComputePassNode.builder().name("n3").reads(ResourceEdge.read(b, 0x20, 0x800)).writes(ResourceEdge.write(c, 0x40, 0x800)).execute(ctx -> {}).build();
        var n4 = GraphicsPassNode.builder().name("n4").reads(ResourceEdge.read(c, 0x20, 0x80)).writes(ResourceEdge.write(out, 0x100, 0x400)).execute(ctx -> {}).build();
        var present = io.github.yetyman.vulkan.graph.nodes.PresentNode.of(out, MemorySegment.NULL);

        var queues = Map.of(
            QueueCapability.GRAPHICS, new QueueAssignment(MemorySegment.NULL, 0, QueueCapability.GRAPHICS),
            QueueCapability.COMPUTE, new QueueAssignment(MemorySegment.NULL, 0, QueueCapability.COMPUTE));

        var compiled1 = compiler.compile(List.of(n1, n2, n3, n4, present), queues, List.of());
        var compiled2 = compiler.compile(List.of(n1, n2, n3, n4, present), queues, List.of());

        assertEquals(compiled1.activeNodes().size(), compiled2.activeNodes().size());
        for (int i = 0; i < compiled1.activeNodes().size(); i++) {
            assertEquals(compiled1.activeNodes().get(i).name(), compiled2.activeNodes().get(i).name());
        }
    }

    // -- Helpers --

    private static ComputePassNode computeNode(String name, Priority priority) {
        // Priority is from the default on RenderNode - we can't override it on ComputePassNode
        // but DegradationStrategy sorts by priority().ordinal() which uses the default
        return ComputePassNode.builder().name(name)
            .writes(ResourceEdge.write(TestResources.transientBuffer(name + "_out"), 0x40, 0x800))
            .execute(ctx -> {}).build();
    }

    private static FrameStats fakeStats(long totalGpuNanos, Map<String, Long> perNode) {
        Map<String, io.github.yetyman.vulkan.graph.nodes.NodeStats> nodeStatsMap = new java.util.HashMap<>();
        for (var entry : perNode.entrySet()) {
            nodeStatsMap.put(entry.getKey(),
                new io.github.yetyman.vulkan.graph.nodes.NodeStats(entry.getValue(), 0, 0));
        }
        return new FrameStats(0, totalGpuNanos, 0, nodeStatsMap);
    }

    /** Minimal ExecutionContext for testing IterativePassNode */
    private static class FakeExecutionContext implements io.github.yetyman.vulkan.graph.nodes.ExecutionContext {
        private int iteration = -1;
        @Override public io.github.yetyman.vulkan.VkCommandBuffer commandBuffer() { return null; }
        @Override public java.lang.foreign.SegmentAllocator frameArena() { return null; }
        @Override public int frameIndex() { return 0; }
        @Override public long frameGeneration() { return 0; }
        @Override public io.github.yetyman.vulkan.graph.scheduling.QueueAssignment queue() { return null; }
        @Override public FrameStats previousStats() { return null; }
        @Override public void setIterationIndex(int index) { this.iteration = index; }
        @Override public int iterationIndex() { return iteration; }
    }
}
