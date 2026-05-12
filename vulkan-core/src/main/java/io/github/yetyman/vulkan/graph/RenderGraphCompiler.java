package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.nodes.NodeType;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.ResourceLifetime;
import io.github.yetyman.vulkan.graph.scheduling.ExecutionBucket;
import io.github.yetyman.vulkan.graph.scheduling.QueueAssignment;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.SchedulingStrategy;
import io.github.yetyman.vulkan.graph.barriers.BarrierStrategy;
import io.github.yetyman.vulkan.graph.memory.AliasingStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles a render graph declaration into an executable plan.
 * Runs validation, lifetime computation, culling, scheduling, barrier synthesis, and aliasing.
 */
public class RenderGraphCompiler {

    private final SchedulingStrategy schedulingStrategy;
    private final BarrierStrategy barrierStrategy;
    private final AliasingStrategy aliasingStrategy;

    public RenderGraphCompiler(SchedulingStrategy schedulingStrategy,
                               BarrierStrategy barrierStrategy,
                               AliasingStrategy aliasingStrategy) {
        this.schedulingStrategy = schedulingStrategy;
        this.barrierStrategy = barrierStrategy;
        this.aliasingStrategy = aliasingStrategy;
    }

    /**
     * Stage 2: Validate feedback edges. Ensures that every feedback edge references a resource
     * that has a writer in the graph (the resource must be produced each frame for the ring
     * to advance). Detects cycles in feedback dependencies.
     */
    public void versionResources(List<RenderNode> nodes) {
        // Collect all resources written in this frame
        Set<GraphResource> produced = new HashSet<>();
        for (RenderNode node : nodes) {
            for (ResourceEdge edge : node.writes()) {
                produced.add(edge.resource());
            }
        }

        // Validate that every feedback edge's resource is also written this frame
        for (RenderNode node : nodes) {
            for (var feedbackEdge : node.feedbackReads()) {
                GraphResource res = feedbackEdge.resource();
                if (!produced.contains(res)) {
                    throw new RenderGraphException(
                        "Node '" + node.name() + "' has feedback read on resource '" + res.name() +
                        "' which is not written in the current frame. " +
                        "Persistent resources must be written each frame for the ring to advance.");
                }
            }
        }
    }

    /**
     * Full compilation from declared nodes to execution plan.
     *
     * stub -- stages 7-8 (memory aliasing + transient allocation) and stage 11 (timestamp
     * insertion) from the plan are not executed here. The aliaser is accepted but never called.
     * Transient resources are not allocated by the compiler. GPU timestamp query insertion
     * is not part of the compilation output. These must be implemented for the graph to
     * manage memory efficiently and provide GPU timing data.
     */
    public CompiledGraph compile(List<RenderNode> nodes, Map<QueueCapability, QueueAssignment> queues) {
        // Stage 1: Validate
        validate(nodes);

        // Stage 2: Version persistent resources (resolve feedback edges)
        versionResources(nodes);

        // Stage 3: Compute lifetimes
        Map<GraphResource, ResourceLifetime> lifetimes = computeLifetimes(nodes);

        // Stage 4: Cull unreachable passes
        List<RenderNode> activeNodes = cull(nodes);

        // Stage 5-6: Schedule (queue assignment + topological sort)
        List<ExecutionBucket> buckets = schedulingStrategy.schedule(activeNodes, queues);

        return new CompiledGraph(activeNodes, buckets, lifetimes);
    }

    /**
     * Stage 1: Validate that all read edges have a corresponding producer (write edge)
     * somewhere in the graph. Detects orphan reads.
     */
    public void validate(List<RenderNode> nodes) {
        Set<GraphResource> produced = new HashSet<>();
        for (RenderNode node : nodes) {
            for (ResourceEdge edge : node.writes()) {
                produced.add(edge.resource());
            }
        }

        for (RenderNode node : nodes) {
            for (ResourceEdge edge : node.reads()) {
                GraphResource res = edge.resource();
                if (!produced.contains(res) && !res.isImported()) {
                    throw new RenderGraphException(
                        "Node '" + node.name() + "' reads resource '" + res.name() +
                        "' which has no producer and is not imported");
                }
            }
        }
    }

    /**
     * Stage 3: Compute first-write to last-read lifetime intervals for each resource.
     */
    public Map<GraphResource, ResourceLifetime> computeLifetimes(List<RenderNode> nodes) {
        Map<GraphResource, ResourceLifetime> lifetimes = new LinkedHashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            RenderNode node = nodes.get(i);

            for (ResourceEdge edge : node.writes()) {
                GraphResource res = edge.resource();
                lifetimes.computeIfAbsent(res, k -> res.lifetime()).recordWrite(i);
            }

            for (ResourceEdge edge : node.reads()) {
                GraphResource res = edge.resource();
                lifetimes.computeIfAbsent(res, k -> res.lifetime()).recordRead(i);
            }
        }

        return lifetimes;
    }

    /**
     * Stage 4: Cull passes with no path to any sink node (present, persistent write).
     * Uses reverse reachability from sink nodes.
     */
    public List<RenderNode> cull(List<RenderNode> nodes) {
        // Build reverse dependency map: resource -> nodes that write it
        Map<GraphResource, List<RenderNode>> producers = new HashMap<>();
        for (RenderNode node : nodes) {
            for (ResourceEdge edge : node.writes()) {
                producers.computeIfAbsent(edge.resource(), k -> new ArrayList<>()).add(node);
            }
        }

        // Find sink nodes
        Set<RenderNode> reachable = new HashSet<>();
        List<RenderNode> worklist = new ArrayList<>();

        for (RenderNode node : nodes) {
            if (!node.isActive()) continue;
            if (isSink(node)) {
                worklist.add(node);
                reachable.add(node);
            }
        }

        // Walk backwards through dependencies
        while (!worklist.isEmpty()) {
            RenderNode current = worklist.remove(worklist.size() - 1);
            for (ResourceEdge readEdge : current.reads()) {
                List<RenderNode> writers = producers.get(readEdge.resource());
                if (writers != null) {
                    for (RenderNode writer : writers) {
                        if (writer.isActive() && reachable.add(writer)) {
                            worklist.add(writer);
                        }
                    }
                }
            }
        }

        // Return only reachable nodes in original order
        List<RenderNode> result = new ArrayList<>();
        for (RenderNode node : nodes) {
            if (reachable.contains(node)) {
                result.add(node);
            }
        }
        return result;
    }

    private boolean isSink(RenderNode node) {
        if (node.type() == NodeType.PRESENT) return true;
        for (ResourceEdge edge : node.writes()) {
            if (!edge.resource().isTransient()) return true;
        }
        return false;
    }
}
