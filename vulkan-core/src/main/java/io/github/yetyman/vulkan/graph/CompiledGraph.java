package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.memory.ResourceAlias;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.ResourceLifetime;
import io.github.yetyman.vulkan.graph.scheduling.ExecutionBucket;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The output of render graph compilation. Contains the ordered execution plan,
 * active nodes, computed resource lifetimes, and aliasing groups.
 */
public class CompiledGraph {

    private final List<RenderNode> activeNodes;
    private final List<ExecutionBucket> executionBuckets;
    private final Map<GraphResource, ResourceLifetime> lifetimes;
    private final List<ResourceAlias> aliasingGroups;

    public CompiledGraph(List<RenderNode> activeNodes,
                         List<ExecutionBucket> executionBuckets,
                         Map<GraphResource, ResourceLifetime> lifetimes,
                         List<ResourceAlias> aliasingGroups) {
        this.activeNodes = Collections.unmodifiableList(activeNodes);
        this.executionBuckets = Collections.unmodifiableList(executionBuckets);
        this.lifetimes = Collections.unmodifiableMap(lifetimes);
        this.aliasingGroups = aliasingGroups != null ? Collections.unmodifiableList(aliasingGroups) : Collections.emptyList();
    }

    /** @return nodes that survived culling, in execution order */
    public List<RenderNode> activeNodes() { return activeNodes; }

    /** @return ordered execution buckets (groups of parallel nodes) */
    public List<ExecutionBucket> executionBuckets() { return executionBuckets; }

    /** @return computed resource lifetimes */
    public Map<GraphResource, ResourceLifetime> lifetimes() { return lifetimes; }

    /** @return aliasing groups for transient resources with non-overlapping lifetimes */
    public List<ResourceAlias> aliasingGroups() { return aliasingGroups; }
}
