package io.github.yetyman.vulkan.graph.nodes;

import io.github.yetyman.vulkan.graph.edges.FeedbackEdge;
import io.github.yetyman.vulkan.graph.edges.OptionalEdge;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.edges.SemaphoreEdge;
import io.github.yetyman.vulkan.graph.edges.TemporalEdge;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.ScheduleHint;

import java.util.Collections;
import java.util.List;

/**
 * Base interface for all render graph nodes. Every node declares its resource dependencies
 * and the graph derives all barriers, ordering, and queue assignment from these declarations.
 */
public interface RenderNode {

    /** @return human-readable name for debugging */
    String name();

    /** @return the type of work this node performs */
    NodeType type();

    /** @return resources this node reads */
    List<ResourceEdge> reads();

    /** @return resources this node writes */
    List<ResourceEdge> writes();

    /** @return external semaphores this node must wait on before executing */
    default List<SemaphoreEdge> externalWaits() { return Collections.emptyList(); }

    /** @return external semaphores this node signals after executing */
    default List<SemaphoreEdge> externalSignals() { return Collections.emptyList(); }

    /** @return feedback edges (reads from previous frames) */
    default List<FeedbackEdge> feedbackReads() { return Collections.emptyList(); }

    /** @return temporal edges (cross-frame cycle dependencies on TemporalResources) */
    default List<TemporalEdge> temporalEdges() { return Collections.emptyList(); }

    /**
     * Resources accessed via bindless descriptors. The graph applies conservative barriers
     * to these since it cannot determine exact access patterns statically.
     */
    default List<GraphResource> bindlessReads() { return Collections.emptyList(); }

    /**
     * Optional resource reads with fallback values. If the source's writer is inactive,
     * the fallback is used instead and no barrier is emitted.
     */
    default List<OptionalEdge> optionalReads() { return Collections.emptyList(); }

    /** @return scheduling hint for placement in the execution timeline */
    default ScheduleHint scheduleHint() { return ScheduleHint.NONE; }

    /** @return priority for graceful degradation (lower priority = dropped first under budget pressure) */
    default Priority priority() { return Priority.MEDIUM; }

    /** @return required queue capability */
    default QueueCapability requiredQueue() { return QueueCapability.ANY; }

    /**
     * Records commands for this node. Called during the execute phase.
     */
    void execute(ExecutionContext ctx);

    /**
     * Called after frame N completes with N's timing data.
     * Nodes can use this to adapt their configuration for N+1.
     */
    default void onStats(NodeStats stats) {}

    /**
     * @return true if this node is currently active. Inactive nodes are culled.
     * Nodes can toggle this based on onStats() feedback.
     */
    default boolean isActive() { return true; }
}
