package io.github.yetyman.vulkan.graph.barriers;

import io.github.yetyman.vulkan.VkBarrier;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.scheduling.ExecutionBucket;

import java.lang.foreign.Arena;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-computed barriers for a compiled graph. Allocated once at compile time into a
 * long-lived arena. The executor uses these cached barriers each frame instead of
 * re-computing them via the BarrierStrategy, achieving zero per-frame barrier allocation.
 *
 * Barriers are keyed by node name. Each node has a pre-built BarrierBatch containing
 * all same-queue barriers and ownership transfers needed before that node executes.
 *
 * Invalidation: cached barriers become invalid when resource state diverges from the
 * expected initial state (e.g. after a resize or topology change). The graph recompiles
 * in that case, which rebuilds the cache.
 *
 * Limitation: this assumes resource state at the start of each frame is deterministic
 * (same as it was at compile time). This is true for transient resources (always start
 * fresh) and for persistent resources with known ring state. Imported resources with
 * externally-mutated state may require per-frame barrier recomputation -- those nodes
 * are marked as uncacheable.
 */
public class CachedBarriers {

    private final Map<String, BarrierBatch> nodeBarriers;
    private final boolean valid;

    private CachedBarriers(Map<String, BarrierBatch> nodeBarriers) {
        this.nodeBarriers = nodeBarriers;
        this.valid = true;
    }

    /**
     * Pre-computes barriers for all nodes in the compiled graph.
     * Simulates the resource state transitions that would occur during execution
     * and records the barriers that would be emitted at each node.
     *
     * @param buckets the execution buckets from compilation
     * @param barrierStrategy the strategy to use for barrier synthesis
     * @param arena long-lived arena for barrier struct allocation (must outlive the compiled graph)
     * @return the cached barriers
     */
    public static CachedBarriers compile(List<ExecutionBucket> buckets,
                                         BarrierStrategy barrierStrategy,
                                         Arena arena) {
        if (barrierStrategy == null) {
            return new CachedBarriers(Map.of());
        }

        Map<String, BarrierBatch> result = new HashMap<>();

        for (ExecutionBucket bucket : buckets) {
            int queueFamily = bucket.queue().queueFamilyIndex();

            for (RenderNode node : bucket.nodes()) {
                BarrierBatch batch = new BarrierBatch();

                // Emit barriers for reads
                List<ResourceEdge> reads = node.reads();
                for (int i = 0, size = reads.size(); i < size; i++) {
                    ResourceEdge edge = reads.get(i);
                    GraphResource resource = edge.resource();
                    // Skip imported resources -- their state may change externally
                    if (resource.isImported()) continue;
                    barrierStrategy.emit(resource, edge, queueFamily, batch, arena);
                }

                // Emit barriers for writes (WAW hazards)
                List<ResourceEdge> writes = node.writes();
                for (int i = 0, size = writes.size(); i < size; i++) {
                    ResourceEdge edge = writes.get(i);
                    GraphResource resource = edge.resource();
                    if (resource.isImported()) continue;
                    if (resource.lastAccessMask() != 0 || resource.lastStageMask() != 0) {
                        barrierStrategy.emit(resource, edge, queueFamily, batch, arena);
                    }
                }

                // Bindless conservative barriers
                List<GraphResource> bindless = node.bindlessReads();
                for (int i = 0, size = bindless.size(); i < size; i++) {
                    GraphResource res = bindless.get(i);
                    if (res.isImported()) continue;
                    if (res.lastAccessMask() != 0 || res.lastStageMask() != 0) {
                        new ConservativeBarrierStrategy().emit(res,
                            ResourceEdge.read(res, 0x00000020, 0x00010000),
                            queueFamily, batch, arena);
                    }
                }

                // Simulate state update (so subsequent nodes see correct state)
                for (int i = 0, size = writes.size(); i < size; i++) {
                    ResourceEdge edge = writes.get(i);
                    GraphResource resource = edge.resource();
                    resource.updateState(edge.accessMask(), edge.stageMask(), queueFamily);
                    if (edge.imageLayout() >= 0 && resource instanceof io.github.yetyman.vulkan.graph.resources.GraphImageResource imgRes) {
                        imgRes.updateLayout(edge.imageLayout());
                    }
                }

                if (!batch.isEmpty()) {
                    result.put(node.name(), batch);
                }
            }
        }

        return new CachedBarriers(result);
    }

    /**
     * Returns the pre-computed barrier batch for a node, or null if no barriers are needed.
     */
    public BarrierBatch forNode(String nodeName) {
        return nodeBarriers.get(nodeName);
    }

    /** @return true if this cache has any barriers */
    public boolean hasBarriers() { return !nodeBarriers.isEmpty(); }

    /** @return true if this cache is valid (not invalidated by state change) */
    public boolean isValid() { return valid; }
}
