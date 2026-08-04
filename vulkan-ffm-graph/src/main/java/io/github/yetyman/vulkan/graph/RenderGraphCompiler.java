package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.edges.DependencyEdge;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.edges.TemporalEdge;
import io.github.yetyman.vulkan.graph.memory.AliasingStrategy;
import io.github.yetyman.vulkan.graph.memory.ResourceAlias;
import io.github.yetyman.vulkan.graph.memory.SemaphorePartialOrder;
import io.github.yetyman.vulkan.graph.nodes.NodeType;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.ResourceLifetime;
import io.github.yetyman.vulkan.graph.resources.TemporalResource;
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
import java.util.stream.Collectors;

/**
 * Compiles a render graph declaration into an executable plan.
 * Runs validation, lifetime computation, culling, scheduling, aliasing, and barrier synthesis.
 *
 * Multi-queue aliasing: after scheduling assigns nodes to queues, the compiler builds a
 * partial order from the bucket structure and inter-queue semaphore edges. This partial order
 * is provided to the aliasing strategy so it can correctly determine which transient resources
 * on different queues can safely share memory.
 */
public class RenderGraphCompiler {

    private final SchedulingStrategy schedulingStrategy;
    private final BarrierStrategy barrierStrategy;
    private final AliasingStrategy aliasingStrategy;
    private final TemporalUnroller temporalUnroller;
    private List<DependencyEdge> dependencyEdges = List.of();

    public RenderGraphCompiler(SchedulingStrategy schedulingStrategy,
                               BarrierStrategy barrierStrategy,
                               AliasingStrategy aliasingStrategy) {
        this.schedulingStrategy = schedulingStrategy;
        this.barrierStrategy = barrierStrategy;
        this.aliasingStrategy = aliasingStrategy;
        this.temporalUnroller = new TemporalUnroller();
    }

    /** Sets the manual dependency edges for scheduling */
    public void setDependencyEdges(List<DependencyEdge> edges) {
        this.dependencyEdges = edges != null ? edges : List.of();
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
     * Stages: validate, version, lifetimes, cull, schedule, alias (with partial order).
     */
    public CompiledGraph compile(List<RenderNode> nodes, Map<QueueCapability, QueueAssignment> queues) {
        return compile(nodes, queues, Collections.emptyList());
    }

    /**
     * Full compilation with temporal resource support.
     * Stages: validate, temporal validate, version, lifetimes, cull, schedule, alias.
     */
    public CompiledGraph compile(List<RenderNode> nodes, Map<QueueCapability, QueueAssignment> queues,
                                 List<TemporalResource> temporalResources) {
        // Stage 1: Validate
        validate(nodes);

        // Stage 1b: Validate temporal edges (completeness, no non-temporal cycles)
        if (!temporalResources.isEmpty()) {
            temporalUnroller.validate(nodes, temporalResources);
            temporalUnroller.validateStartingPoints(nodes, temporalResources);
            validateTemporalSingleWriter(nodes, temporalResources);
        }

        // Stage 2: Version persistent resources (resolve feedback edges)
        versionResources(nodes);

        // Stage 4: Cull unreachable passes
        List<RenderNode> activeNodes = cull(nodes);

        // Stage 5-6: Schedule (queue assignment + topological sort)
        List<ExecutionBucket> buckets = schedulingStrategy.schedule(activeNodes, queues, dependencyEdges);

        // Stage 3: Compute lifetimes with queue family information from scheduling
        Map<GraphResource, ResourceLifetime> lifetimes = computeLifetimes(activeNodes, buckets);

        // Stage 7: Build partial order from bucket structure for multi-queue aliasing
        ResourceLifetime.PartialOrder partialOrder = SemaphorePartialOrder.build(buckets, activeNodes);
        if (aliasingStrategy != null) {
            aliasingStrategy.setPartialOrder(partialOrder);
        }

        // Stage 7b: Alias transient resources with non-overlapping lifetimes
        List<ResourceAlias> aliasingGroups = computeAliasing(activeNodes);

        return new CompiledGraph(activeNodes, buckets, lifetimes, aliasingGroups);
    }

    /**
     * Partial recompile: skips validation and versioning, re-runs lifetimes through aliasing.
     * Used after resize when topology is unchanged but resource sizes changed.
     */
    public CompiledGraph recompileFromLifetimes(List<RenderNode> nodes, Map<QueueCapability, QueueAssignment> queues) {
        List<RenderNode> activeNodes = cull(nodes);
        List<ExecutionBucket> buckets = schedulingStrategy.schedule(activeNodes, queues);
        Map<GraphResource, ResourceLifetime> lifetimes = computeLifetimes(activeNodes, buckets);

        ResourceLifetime.PartialOrder partialOrder = SemaphorePartialOrder.build(buckets, activeNodes);
        if (aliasingStrategy != null) {
            aliasingStrategy.setPartialOrder(partialOrder);
        }
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
     * Stage 3: Compute first-write to last-read lifetime intervals for each resource,
     * including queue family information from the scheduled buckets.
     * Also tracks temporal physical slot lifetimes for cross-frame aliasing.
     */
    public Map<GraphResource, ResourceLifetime> computeLifetimes(List<RenderNode> nodes,
                                                                  List<ExecutionBucket> buckets) {
        // Build node -> (passIndex, queueFamily) mapping from buckets
        Map<RenderNode, Integer> nodeToPassIndex = new HashMap<>();
        Map<RenderNode, Integer> nodeToQueueFamily = new HashMap<>();
        int passIndex = 0;
        for (ExecutionBucket bucket : buckets) {
            int queueFamily = bucket.queue().queueFamilyIndex();
            for (RenderNode node : bucket.nodes()) {
                nodeToPassIndex.put(node, passIndex);
                nodeToQueueFamily.put(node, queueFamily);
                passIndex++;
            }
        }

        Map<GraphResource, ResourceLifetime> lifetimes = new LinkedHashMap<>();

        for (RenderNode node : nodes) {
            int idx = nodeToPassIndex.getOrDefault(node, -1);
            int queue = nodeToQueueFamily.getOrDefault(node, -1);
            if (idx < 0) continue; // culled node

            for (ResourceEdge edge : node.writes()) {
                GraphResource res = edge.resource();
                lifetimes.computeIfAbsent(res, k -> res.lifetime()).recordWrite(idx, queue);
            }

            for (ResourceEdge edge : node.reads()) {
                GraphResource res = edge.resource();
                lifetimes.computeIfAbsent(res, k -> res.lifetime()).recordRead(idx, queue);
            }

            // Track temporal physical slot lifetimes for cross-frame aliasing
            for (TemporalEdge te : node.temporalEdges()) {
                if (te.temporalResource().physicalSlots() == null) continue;
                if (te.isReadPrevious()) {
                    GraphResource readSlot = te.temporalResource().previousReadSlot();
                    lifetimes.computeIfAbsent(readSlot, k -> readSlot.lifetime()).recordRead(idx, queue);
                    te.temporalResource().recordUse(idx);
                } else if (te.isWriteCurrent()) {
                    GraphResource writeSlot = te.temporalResource().currentWriteSlot();
                    lifetimes.computeIfAbsent(writeSlot, k -> writeSlot.lifetime()).recordWrite(idx, queue);
                    te.temporalResource().recordUse(idx);
                }
            }
        }

        return lifetimes;
    }

    /**
     * Stage 7: Compute aliasing groups for transient resources.
     * Includes temporal physical slots with their submission-local lifetimes for cross-frame aliasing.
     */
    private List<ResourceAlias> computeAliasing(List<RenderNode> activeNodes) {
        if (aliasingStrategy == null) return Collections.emptyList();

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

        for (RenderNode node : nodes) {
            for (ResourceEdge edge : node.reads()) {
                GraphResource res = edge.resource();
                if (!produced.contains(res) && !res.isImported()) {
                    // Find which nodes write to help diagnose
                    String suggestion = "Ensure another node writes to '" + res.name() +
                        "' before '" + node.name() + "' reads it, or mark it as imported via .imported(\"" +
                        res.name() + "\", resource).";
                    throw new RenderGraphException(
                        "Node '" + node.name() + "' reads resource '" + res.name() +
                        "' which has no producer and is not imported. " + suggestion);
                }
            }
        }

        for (Map.Entry<GraphResource, List<RenderNode>> entry : resourceWriters.entrySet()) {
            List<RenderNode> writers = entry.getValue();
            if (writers.size() > 1) {
                GraphResource res = entry.getKey();
                boolean hasIntermediateReader = resourceReaders.contains(res);
                if (!hasIntermediateReader) {
                    throw new RenderGraphException(
                        "Write-after-write hazard on resource '" + res.name() +
                        "': written by " + writers.stream().map(RenderNode::name)
                            .collect(Collectors.joining(", ")) +
                        " with no intervening read. Add a read edge between the writers, " +
                        "or use separate resources if the writes are independent.");
                }
            }
        }
    }

    /**
     * Validates that each temporal resource has at most one active writer.
     * Multiple writers to the same temporal resource would corrupt the flip state.
     */
    private void validateTemporalSingleWriter(List<RenderNode> nodes, List<TemporalResource> temporalResources) {
        Map<String, List<String>> writersByTemporal = new HashMap<>();
        for (RenderNode node : nodes) {
            if (!node.isActive()) continue;
            for (TemporalEdge te : node.temporalEdges()) {
                if (te.isWriteCurrent()) {
                    writersByTemporal.computeIfAbsent(te.temporalResource().name(), k -> new ArrayList<>())
                        .add(node.name());
                }
            }
        }
        for (var entry : writersByTemporal.entrySet()) {
            if (entry.getValue().size() > 1) {
                throw new RenderGraphException(
                    "Temporal resource '" + entry.getKey() + "' has multiple active writers: " +
                    String.join(", ", entry.getValue()) +
                    ". Each temporal resource must have exactly one writer per frame.");
            }
        }
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
        // Temporal writes are sinks (they persist across frames)
        for (TemporalEdge te : node.temporalEdges()) {
            if (te.isWriteCurrent()) return true;
        }
        return false;
    }
}
