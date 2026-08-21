package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.CpuObservability;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.buffers.MirrorCapable;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.nodes.CpuWorkNode;
import io.github.yetyman.vulkan.graph.nodes.NodeType;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.VkBufferGraphResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Graph analysis pass that detects deferred mirrored buffers and synthesizes flush/readDiff
 * nodes at the appropriate scheduling points.
 *
 * <p>This is an additive optimization — the graph works without it (manual flush calls), but
 * when enabled, it automatically inserts:
 * <ul>
 *   <li>A {@code flushDirty} transfer between CPU writer nodes and the first GPU reader</li>
 *   <li>A {@code readDiff} transfer between GPU writer nodes and the first CPU reader</li>
 * </ul>
 *
 * <p>Buffers marked with {@link VkBufferGraphResource#isManualFlush()} are skipped.
 */
public final class MirrorFlushPass {

    private MirrorFlushPass() {}

    /**
     * Identifies buffers in the node list that need automatic flush/readDiff nodes inserted.
     *
     * @param nodes the render graph's node list (before or after compilation)
     * @return analysis result describing which buffers need flush/readDiff and between which nodes
     */
    public static MirrorFlushAnalysis analyze(List<RenderNode> nodes) {
        // Track which mirrored buffers are written by CPU nodes and read by GPU nodes
        Map<GraphResource, List<RenderNode>> cpuWriters = new HashMap<>();
        Map<GraphResource, List<RenderNode>> gpuReaders = new HashMap<>();
        Map<GraphResource, List<RenderNode>> gpuWriters = new HashMap<>();
        Map<GraphResource, List<RenderNode>> cpuReaders = new HashMap<>();

        for (RenderNode node : nodes) {
            boolean isCpu = node.type() == NodeType.CPU_WORK;
            boolean isGpu = node.type() == NodeType.GRAPHICS || node.type() == NodeType.COMPUTE
                    || node.type() == NodeType.TRANSFER;

            for (ResourceEdge edge : node.writes()) {
                GraphResource res = edge.resource();
                if (!isMirroredDeferred(res)) continue;
                if (isCpu) cpuWriters.computeIfAbsent(res, k -> new ArrayList<>()).add(node);
                if (isGpu) gpuWriters.computeIfAbsent(res, k -> new ArrayList<>()).add(node);
            }

            for (ResourceEdge edge : node.reads()) {
                GraphResource res = edge.resource();
                if (!isMirroredDeferred(res)) continue;
                if (isGpu) gpuReaders.computeIfAbsent(res, k -> new ArrayList<>()).add(node);
                if (isCpu) cpuReaders.computeIfAbsent(res, k -> new ArrayList<>()).add(node);
            }
        }

        // CPU writes -> GPU reads: need flushDirty between them
        List<FlushAction> flushActions = new ArrayList<>();
        for (Map.Entry<GraphResource, List<RenderNode>> entry : cpuWriters.entrySet()) {
            GraphResource res = entry.getKey();
            if (isManualFlush(res)) continue;
            List<RenderNode> readers = gpuReaders.get(res);
            if (readers != null && !readers.isEmpty()) {
                flushActions.add(new FlushAction(res, entry.getValue(), readers, FlushDirection.CPU_TO_GPU));
            }
        }

        // GPU writes -> CPU reads: need readDiff between them
        for (Map.Entry<GraphResource, List<RenderNode>> entry : gpuWriters.entrySet()) {
            GraphResource res = entry.getKey();
            if (isManualFlush(res)) continue;
            List<RenderNode> readers = cpuReaders.get(res);
            if (readers != null && !readers.isEmpty()) {
                flushActions.add(new FlushAction(res, entry.getValue(), readers, FlushDirection.GPU_TO_CPU));
            }
        }

        return new MirrorFlushAnalysis(flushActions);
    }

    /**
     * Generates CpuWorkNodes that perform the flush/readDiff operations identified by analysis.
     * These nodes can be added to the graph before compilation — they will be scheduled
     * between the writers and readers by normal dependency resolution.
     *
     * @param analysis the result of {@link #analyze(List)}
     * @param queue    the transfer queue to use for flush operations
     * @return list of CpuWorkNodes to add to the graph
     */
    public static List<CpuWorkNode> generateFlushNodes(MirrorFlushAnalysis analysis, VkQueue queue) {
        List<CpuWorkNode> nodes = new ArrayList<>();
        for (FlushAction action : analysis.actions()) {
            GraphResource res = action.resource();
            String name = action.direction() == FlushDirection.CPU_TO_GPU
                    ? "auto_flush_" + res.name()
                    : "auto_readDiff_" + res.name();

            CpuWorkNode.Builder builder = CpuWorkNode.builder().name(name);

            // The flush node reads from the resource (it needs the CPU-side dirty state)
            // and writes to it (it modifies the GPU-side or mirror-side state)
            if (action.direction() == FlushDirection.CPU_TO_GPU) {
                builder.reads(ResourceEdge.read(res, 0x00000001, 0x00001000)); // HOST_READ, TRANSFER
                builder.writes(ResourceEdge.write(res, 0x00000002, 0x00001000)); // HOST_WRITE, TRANSFER
                builder.does(ctx -> {
                    IBuffer buf = ((VkBufferGraphResource) res).managedBuffer();
                    if (buf instanceof ManagedBuffer managed) {
                        managed.flushDirty(queue);
                    }
                });
            } else {
                builder.reads(ResourceEdge.read(res, 0x00000001, 0x00001000));
                builder.writes(ResourceEdge.write(res, 0x00000002, 0x00001000));
                builder.does(ctx -> {
                    IBuffer buf = ((VkBufferGraphResource) res).managedBuffer();
                    if (buf instanceof ManagedBuffer managed) {
                        managed.readDiff(queue);
                    }
                });
            }

            nodes.add(builder.build());
        }
        return nodes;
    }

    private static boolean isMirroredDeferred(GraphResource res) {
        if (!(res instanceof VkBufferGraphResource bufRes)) return false;
        IBuffer buf = bufRes.managedBuffer();
        if (!(buf instanceof ManagedBuffer managed)) return false;
        return managed.observability().isMirrored() && managed.isDeferred();
    }

    private static boolean isManualFlush(GraphResource res) {
        if (!(res instanceof VkBufferGraphResource bufRes)) return false;
        return bufRes.isManualFlush();
    }

    // -------------------------------------------------------------------------
    // Result types
    // -------------------------------------------------------------------------

    /** Direction of the flush operation. */
    public enum FlushDirection {
        /** CPU wrote, GPU needs to read: flushDirty (mirror -> primary) */
        CPU_TO_GPU,
        /** GPU wrote, CPU needs to read: readDiff (primary -> mirror) */
        GPU_TO_CPU
    }

    /** A single identified flush action. */
    public record FlushAction(
            GraphResource resource,
            List<RenderNode> writers,
            List<RenderNode> readers,
            FlushDirection direction
    ) {}

    /** Result of analyzing a node list for required mirror flushes. */
    public record MirrorFlushAnalysis(List<FlushAction> actions) {
        /** @return true if no flush actions are needed */
        public boolean isEmpty() { return actions.isEmpty(); }

        /** @return number of flush actions identified */
        public int size() { return actions.size(); }
    }
}
