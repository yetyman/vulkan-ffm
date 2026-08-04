package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.nodes.ComputePassNode;
import io.github.yetyman.vulkan.graph.nodes.GraphicsPassNode;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.scheduling.ExecutionBucket;
import io.github.yetyman.vulkan.graph.scheduling.ListSchedulingStrategy;
import io.github.yetyman.vulkan.graph.scheduling.QueueAssignment;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.ScheduleHint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ListSchedulingStrategyTest {

    private ListSchedulingStrategy strategy;
    private Map<QueueCapability, QueueAssignment> queues;

    @BeforeEach
    void setUp() {
        strategy = new ListSchedulingStrategy();
        queues = Map.of(
            QueueCapability.GRAPHICS, new QueueAssignment(MemorySegment.NULL, 0, QueueCapability.GRAPHICS),
            QueueCapability.COMPUTE, new QueueAssignment(MemorySegment.NULL, 1, QueueCapability.COMPUTE),
            QueueCapability.TRANSFER, new QueueAssignment(MemorySegment.NULL, 2, QueueCapability.TRANSFER)
        );
    }

    @Test
    void schedule_linearChainProducesSequentialBuckets() {
        GraphResource a = TestResources.transientBuffer("a");
        GraphResource b = TestResources.transientBuffer("b");

        RenderNode pass1 = ComputePassNode.builder()
            .name("pass1")
            .writes(ResourceEdge.write(a, 0x20, 0x800))
            .execute(ctx -> {})
            .build();
        RenderNode pass2 = ComputePassNode.builder()
            .name("pass2")
            .reads(ResourceEdge.read(a, 0x20, 0x800))
            .writes(ResourceEdge.write(b, 0x20, 0x800))
            .execute(ctx -> {})
            .build();

        List<ExecutionBucket> buckets = strategy.schedule(List.of(pass1, pass2), queues);

        // pass2 depends on pass1, so they must be in separate buckets
        assertEquals(2, buckets.size());
        assertTrue(buckets.get(0).nodes().contains(pass1));
        assertTrue(buckets.get(1).nodes().contains(pass2));
    }

    @Test
    void schedule_independentNodesCanShareBucket() {
        GraphResource a = TestResources.transientBuffer("a");
        GraphResource b = TestResources.transientBuffer("b");

        RenderNode pass1 = ComputePassNode.builder()
            .name("pass1")
            .writes(ResourceEdge.write(a, 0x20, 0x800))
            .execute(ctx -> {})
            .build();
        RenderNode pass2 = ComputePassNode.builder()
            .name("pass2")
            .writes(ResourceEdge.write(b, 0x20, 0x800))
            .execute(ctx -> {})
            .build();

        List<ExecutionBucket> buckets = strategy.schedule(List.of(pass1, pass2), queues);

        // No dependency between them -- they can be in the same bucket
        assertEquals(1, buckets.size());
        assertEquals(2, buckets.get(0).nodes().size());
    }

    @Test
    void schedule_respectsTopologicalOrder() {
        // C reads from both A and B
        GraphResource ra = TestResources.transientBuffer("ra");
        GraphResource rb = TestResources.transientBuffer("rb");
        GraphResource rc = TestResources.transientBuffer("rc");

        RenderNode a = ComputePassNode.builder()
            .name("A")
            .writes(ResourceEdge.write(ra, 0x20, 0x800))
            .execute(ctx -> {})
            .build();
        RenderNode b = ComputePassNode.builder()
            .name("B")
            .writes(ResourceEdge.write(rb, 0x20, 0x800))
            .execute(ctx -> {})
            .build();
        RenderNode c = ComputePassNode.builder()
            .name("C")
            .reads(ResourceEdge.read(ra, 0x20, 0x800))
            .reads(ResourceEdge.read(rb, 0x20, 0x800))
            .writes(ResourceEdge.write(rc, 0x20, 0x800))
            .execute(ctx -> {})
            .build();

        List<ExecutionBucket> buckets = strategy.schedule(List.of(a, b, c), queues);

        // A and B are independent (bucket 1), C depends on both (bucket 2)
        assertEquals(2, buckets.size());
        assertTrue(buckets.get(0).nodes().contains(a));
        assertTrue(buckets.get(0).nodes().contains(b));
        assertTrue(buckets.get(1).nodes().contains(c));
    }

    @Test
    void schedule_detectsCycle() {
        GraphResource ra = TestResources.transientBuffer("ra");
        GraphResource rb = TestResources.transientBuffer("rb");

        // A writes ra, reads rb; B writes rb, reads ra -> cycle
        RenderNode a = ComputePassNode.builder()
            .name("A")
            .reads(ResourceEdge.read(rb, 0x20, 0x800))
            .writes(ResourceEdge.write(ra, 0x20, 0x800))
            .execute(ctx -> {})
            .build();
        RenderNode b = ComputePassNode.builder()
            .name("B")
            .reads(ResourceEdge.read(ra, 0x20, 0x800))
            .writes(ResourceEdge.write(rb, 0x20, 0x800))
            .execute(ctx -> {})
            .build();

        assertThrows(RenderGraphException.class,
            () -> strategy.schedule(List.of(a, b), queues));
    }

    @Test
    void schedule_earlyHintComesFirst() {
        GraphResource a = TestResources.transientBuffer("a");
        GraphResource b = TestResources.transientBuffer("b");

        RenderNode late = ComputePassNode.builder()
            .name("late")
            .scheduleHint(ScheduleHint.LATE)
            .writes(ResourceEdge.write(a, 0x20, 0x800))
            .execute(ctx -> {})
            .build();
        RenderNode early = ComputePassNode.builder()
            .name("early")
            .scheduleHint(ScheduleHint.EARLY)
            .writes(ResourceEdge.write(b, 0x20, 0x800))
            .execute(ctx -> {})
            .build();

        List<ExecutionBucket> buckets = strategy.schedule(List.of(late, early), queues);

        // Both independent, so same bucket, but the sort should place early first
        // Since they're in the same bucket the order within the bucket reflects topo sort order
        List<RenderNode> allNodes = buckets.stream()
            .flatMap(bucket -> bucket.nodes().stream())
            .toList();
        assertTrue(allNodes.indexOf(early) < allNodes.indexOf(late),
            "EARLY hint should be scheduled before LATE");
    }

    @Test
    void schedule_assignsCorrectQueueCapability() {
        GraphResource a = TestResources.transientBuffer("a");

        RenderNode graphics = GraphicsPassNode.builder()
            .name("draw")
            .writes(ResourceEdge.write(a, 0x100, 0x400))
            .execute(ctx -> {})
            .build();

        List<ExecutionBucket> buckets = strategy.schedule(List.of(graphics), queues);

        assertEquals(1, buckets.size());
        assertEquals(QueueCapability.GRAPHICS, buckets.get(0).queue().capability());
    }
}
