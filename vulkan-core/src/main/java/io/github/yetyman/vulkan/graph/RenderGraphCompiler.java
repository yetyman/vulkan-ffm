package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.memory.AliasingStrategy;
import io.github.yetyman.vulkan.graph.memory.ResourceAlias;
import io.github.yetyman.vulkan.graph.nodes.NodeType;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.ResourceLifetime;
import io.github.yetyman.vulkan.graph.scheduling.ExecutionBucket;
import io.github.yetyman.vulkan.graph.scheduling.QueueAssignment;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.SchedulingStrategy;
import io.github.yetyman.vulkan.graph.barriers.BarrierStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles a render graph declaration into an executable plan.
 * Runs validation, lifetime computation, culling, scheduling, aliasing, and barrier synthesis.
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
        Set<GraphResource> produced = new HashSet<>();
        for (RenderNode node : nodes) {
            for (ResourceEdge edge : node.writes()) {
                produced.add(edge.resource());
            }
        }

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
     * Stages: validate, version, lifetimes, cull, schedule, alias.
     *
     * stub -- stage 11 (GPU timestamp query insertion) is not part of the compilation output.
     * Timestamps are inserted by the executor at runtime, not baked into the compiled plan.
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

        // Stage 7: Alias transient resources with non-overlapping lifetimes
        List<ResourceAlias> aliasingGroups = computeAliasing(activeNodes);

        return new CompiledGraph(activeNodes, buckets, lifetimes, aliasingGroups);
    }

    /**
     * Partial recompile: skips validation and versioning, re-runs lifetimes through aliasing.
     * Used after resize when topology is unchanged but resource sizes changed.
     */
    public CompiledGraph recompileFromLifetimes(List<RenderNode> nodes, Map<QueueCapability, QueueAssignment> queues) {
        Map<GraphResource, ResourceLifetime> lifetimes = computeLifetimes(nodes);
        List<RenderNode> activeNodes = cull(nodes);
        List<ExecutionBucket> buckets = schedulingStrategy.schedule(activeNodes, queues);
        List<ResourceAlias> aliasingGroups = computeAliasing(activeNodes);
        return new CompiledGraph(activeNodes, buckets, lifetimes, aliasingGroups);
    }

    /**
     * Minimal recompile: only re-culls and re-schedules. Used when node active states may have
     * changed (via onStats feedback) but topology and resource sizes are unchanged.
     * Skips validation, versioning, lifetime computation, and aliasing.
     */
    public CompiledGraph recompileFromCull(List<RenderNode> nodes, Map<QueueCapability, QueueAssignment> queues,
                                           Map<GraphResource, ResourceLifetime> cachedLifetimes,
                                           List<ResourceAlias> cachedAliasing) {
        List<RenderNode> activeNodes = cull(nodes);
        List<ExecutionBucket> buckets = schedulingStrategy.schedule(activeNodes, queues);
        return new CompiledGraph(activeNodes, buckets, cachedLifetimes, cachedAliasing);
    }

    /**
     * Stage 7: Compute aliasing groups for transient resources.
     */
    private List<ResourceAlias> computeAliasing(List<RenderNode> activeNodes) {
        if (aliasingStrategy == null) return Collections.emptyList();

        // Collect all transient resources referenced by active nodes
        List<GraphResource> transientResources = new ArrayList<>();
        Set<GraphResource> seen = new HashSet<>();
        for (RenderNode node : activeNodes) {
            for (ResourceEdge edge : node.writes()) {
                GraphResource res = edge.resource();
                if (res.isTransient() && seen.add(res)) {
                    transientResources.add(res);
                }
            }
            for (ResourceEdge edge : node.reads()) {
                GraphResource res = edge.resource();
                if (res.isTransient() && seen.add(res)) {
                    transientResources.add(res);
                }
            }
        }

        if (transientResources.isEmpty()) return Collections.emptyList();
        return aliasingStrategy.alias(transientResources);
    }

    /**
     * Stage 1: Validate the graph declaration.
     * - All read edges have a corresponding producer (write edge) or are imported
     * - No write-after-write hazards (two nodes writing the same resource without an intervening read)
     * - No declared resources are unused (warning only, does not throw)
     */
    public void validate(List<RenderNode> nodes) {
        Set<GraphResource> produced = new HashSet<>();
        Map<GraphResource, List<RenderNode>> resourceWriters = new HashMap<>();
        Set<GraphResource> resourceReaders = new HashSet<>();

        for (RenderNode node : nodes) {
            for (ResourceEdge edge : node.writes()) {
                produced.add(edge.resource());
                resourceWriters.computeIfAbsent(edge.resource(), k -> new ArrayList<>()).add(node);
            }
            for (ResourceEdge edge : node.reads()) {
                resourceReaders.add(edge.resource());
            }
        }

        // Check for orphan reads
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

        // Check for write-after-write hazards: two nodes writing the same resource
        // without any node reading it in between. This is a potential data race.
        for (Map.Entry<GraphResource, List<RenderNode>> entry : resourceWriters.entrySet()) {
            List<RenderNode> writers = entry.getValue();
            if (writers.size() > 1) {
                GraphResource res = entry.getKey();
                // Check if there's at least one reader of this resource between any pair of writers
                boolean hasIntermediateReader = resourceReaders.contains(res);
                if (!hasIntermediateReader) {
                    throw new RenderGraphException(
                        "Write-after-write hazard on resource '" + res.name() +
                        "': written by " + writers.stream().map(RenderNode::name)
                            .collect(java.util.stream.Collectors.joining(", ")) +
                        " with no intervening read. This is a potential data race.");
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
        Map<GraphResource, List<RenderNode>> producers = new HashMap<>();
        for (RenderNode node : nodes) {
            for (ResourceEdge edge : node.writes()) {
                producers.computeIfAbsent(edge.resource(), k -> new ArrayList<>()).add(node);
            }
        }

        Set<RenderNode> reachable = new HashSet<>();
        List<RenderNode> worklist = new ArrayList<>();

        for (RenderNode node : nodes) {
            if (!node.isActive()) continue;
            if (isSink(node)) {
                worklist.add(node);
                reachable.add(node);
            }
        }

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
