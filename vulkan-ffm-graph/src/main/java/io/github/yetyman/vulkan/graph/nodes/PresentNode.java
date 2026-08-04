package io.github.yetyman.vulkan.graph.nodes;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.edges.SemaphoreEdge;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.ScheduleHint;

import java.lang.foreign.MemorySegment;
import java.util.Collections;
import java.util.List;

/**
 * Terminal sink node that presents a swapchain image. The graph uses this to determine
 * which nodes are reachable (non-reachable nodes are culled).
 */
public class PresentNode implements RenderNode {

    private final GraphResource swapchainImage;
    private final ResourceEdge readEdge;
    private final SemaphoreEdge signalSemaphore;

    private PresentNode(GraphResource swapchainImage, ResourceEdge readEdge, SemaphoreEdge signalSemaphore) {
        this.swapchainImage = swapchainImage;
        this.readEdge = readEdge;
        this.signalSemaphore = signalSemaphore;
    }

    /**
     * Creates a present node that reads the swapchain image and signals a semaphore when done.
     */
    public static PresentNode of(GraphResource swapchainImage, MemorySegment renderFinishedSemaphore) {
        // Present reads the image in PRESENT_SRC layout
        ResourceEdge edge = ResourceEdge.readImage(
            swapchainImage,
            0, // no access mask needed for present
            0x00002000, // VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT
            2  // VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
        );
        SemaphoreEdge signal = SemaphoreEdge.binary(
            renderFinishedSemaphore,
            0x00002000 // VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT
        );
        return new PresentNode(swapchainImage, edge, signal);
    }

    @Override public String name() { return "present"; }
    @Override public NodeType type() { return NodeType.PRESENT; }
    @Override public List<ResourceEdge> reads() { return List.of(readEdge); }
    @Override public List<ResourceEdge> writes() { return Collections.emptyList(); }
    @Override public List<SemaphoreEdge> externalSignals() { return List.of(signalSemaphore); }
    @Override public ScheduleHint scheduleHint() { return ScheduleHint.LATE; }
    @Override public QueueCapability requiredQueue() { return QueueCapability.GRAPHICS; }

    /** @return the swapchain image resource this node presents */
    public GraphResource swapchainImage() { return swapchainImage; }

    @Override
    public void execute(ExecutionContext ctx) {
        // Present is handled externally by the graph after command submission.
        // This node exists only for dependency tracking and cull reachability.
    }
}
