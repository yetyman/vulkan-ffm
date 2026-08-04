package io.github.yetyman.vulkan.graph.memory;

import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.ResourceLifetime;
import io.github.yetyman.vulkan.graph.scheduling.ExecutionBucket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives a partial order from the execution bucket structure and inter-queue semaphore edges.
 *
 * The ordering model:
 * - Within a single queue, pass indices define a total order (earlier index = earlier execution).
 * - Across queues, ordering is established only by semaphore edges. A bucket on queue A that
 *   precedes a bucket on queue B (with a semaphore between them) establishes that all passes
 *   in A happen-before all passes in B.
 * - If two passes are on different queues with no semaphore chain connecting them, they have
 *   no ordering relationship and must be treated as potentially concurrent.
 *
 * Implementation: builds a reachability matrix from the bucket dependency graph. Two passes
 * have a happens-before relationship if there exists a directed path of semaphore edges from
 * the bucket containing pass A to the bucket containing pass B.
 */
public class SemaphorePartialOrder implements ResourceLifetime.PartialOrder {

    // Maps (passIndex, queueFamily) -> bucket index
    private final Map<Long, Integer> passToBucket;
    // Transitive reachability: reachable[i][j] means bucket i happens-before bucket j
    private final boolean[][] reachable;

    private SemaphorePartialOrder(Map<Long, Integer> passToBucket, boolean[][] reachable) {
        this.passToBucket = passToBucket;
        this.reachable = reachable;
    }

    /**
     * Builds the partial order from execution buckets and their inter-queue dependencies.
     *
     * @param buckets the ordered execution buckets from the compiled graph
     * @param nodes all active nodes (used to map pass indices to buckets)
     * @return the partial order
     */
    public static SemaphorePartialOrder build(List<ExecutionBucket> buckets, List<RenderNode> nodes) {
        int bucketCount = buckets.size();

        // Map each node (by its index in the active node list) to its bucket
        Map<RenderNode, Integer> nodeToBucket = new HashMap<>();
        for (int b = 0; b < bucketCount; b++) {
            for (RenderNode node : buckets.get(b).nodes()) {
                nodeToBucket.put(node, b);
            }
        }

        // Map (passIndex, queueFamily) -> bucket index
        Map<Long, Integer> passToBucket = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            RenderNode node = nodes.get(i);
            Integer bucketIdx = nodeToBucket.get(node);
            if (bucketIdx != null) {
                int queueFamily = buckets.get(bucketIdx).queue().queueFamilyIndex();
                passToBucket.put(key(i, queueFamily), bucketIdx);
            }
        }

        // Build adjacency: bucket i -> bucket j if j depends on i (cross-queue edge)
        // Within the same queue, sequential buckets have implicit ordering.
        // Cross-queue ordering comes from resource dependencies that cross queue boundaries.
        boolean[][] adjacent = new boolean[bucketCount][bucketCount];

        // Sequential ordering within the same queue
        for (int i = 0; i < bucketCount - 1; i++) {
            int queueI = buckets.get(i).queue().queueFamilyIndex();
            for (int j = i + 1; j < bucketCount; j++) {
                int queueJ = buckets.get(j).queue().queueFamilyIndex();
                if (queueI == queueJ) {
                    // Same queue: i happens-before j (sequential submission)
                    adjacent[i][j] = true;
                    break; // Only the immediately next bucket on the same queue
                }
            }
        }

        // Cross-queue ordering: if bucket j on queue B reads a resource written by bucket i on queue A,
        // and i != j's queue, then there's a semaphore edge i -> j
        for (int j = 0; j < bucketCount; j++) {
            int queueJ = buckets.get(j).queue().queueFamilyIndex();
            for (RenderNode reader : buckets.get(j).nodes()) {
                for (var readEdge : reader.reads()) {
                    // Find the writer bucket
                    for (int i = 0; i < j; i++) {
                        int queueI = buckets.get(i).queue().queueFamilyIndex();
                        if (queueI == queueJ) continue; // same queue, already handled
                        for (RenderNode writer : buckets.get(i).nodes()) {
                            for (var writeEdge : writer.writes()) {
                                if (writeEdge.resource() == readEdge.resource()) {
                                    adjacent[i][j] = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Compute transitive closure (Floyd-Warshall)
        boolean[][] reachable = new boolean[bucketCount][bucketCount];
        for (int i = 0; i < bucketCount; i++) {
            System.arraycopy(adjacent[i], 0, reachable[i], 0, bucketCount);
        }
        for (int k = 0; k < bucketCount; k++) {
            for (int i = 0; i < bucketCount; i++) {
                if (!reachable[i][k]) continue;
                for (int j = 0; j < bucketCount; j++) {
                    if (reachable[k][j]) {
                        reachable[i][j] = true;
                    }
                }
            }
        }

        return new SemaphorePartialOrder(passToBucket, reachable);
    }

    /**
     * A trivial partial order that only recognizes same-queue ordering.
     * Used as a fallback when bucket information is not yet available.
     */
    public static ResourceLifetime.PartialOrder sameQueueOnly() {
        return (passA, queueA, passB, queueB) -> {
            if (queueA < 0 || queueB < 0) return passA < passB;
            if (queueA == queueB) return passA < passB;
            return false; // Different queues with no known ordering
        };
    }

    /**
     * A partial order for transient resources that uses submission order as the ordering
     * guarantee. Since all transient resources are dead by frame end, and the CPU submits
     * buckets in order, a resource last-read in an earlier bucket is guaranteed to be
     * finished before a resource first-written in a later bucket -- even across queues.
     *
     * This is safe because:
     * - The executor submits queues in bucket order with timeline semaphores between them
     * - A later bucket's GPU work cannot begin until its submission, which happens after
     *   earlier buckets are submitted
     * - Transient resources don't survive across frames, so frame-boundary races are impossible
     *
     * Use this for transient-vs-transient aliasing. For persistent resources that survive
     * across frames, use the strict semaphore-based partial order instead.
     */
    public static ResourceLifetime.PartialOrder submissionOrder() {
        return (passA, queueA, passB, queueB) -> passA < passB;
    }

    @Override
    public boolean happensBefore(int passA, int queueA, int passB, int queueB) {
        if (queueA < 0 || queueB < 0) {
            // Unknown queue: fall back to linear ordering
            return passA < passB;
        }

        if (queueA == queueB) {
            // Same queue: linear order
            return passA < passB;
        }

        // Different queues: look up bucket indices and check reachability
        Integer bucketA = passToBucket.get(key(passA, queueA));
        Integer bucketB = passToBucket.get(key(passB, queueB));

        if (bucketA == null || bucketB == null) {
            return false; // Unknown passes -- no ordering
        }

        if (bucketA >= reachable.length || bucketB >= reachable.length) {
            return false;
        }

        return reachable[bucketA][bucketB];
    }

    private static long key(int passIndex, int queueFamily) {
        return ((long) queueFamily << 32) | (passIndex & 0xFFFFFFFFL);
    }
}
