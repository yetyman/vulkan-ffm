package io.github.yetyman.vulkan.mesh.residency;

import java.util.List;

/**
 * An immutable, executable description of the work needed to upload one geometry (or a window of
 * one) into its allocated buffer ranges.
 *
 * <p>Pure data: no execution logic, no side effects, no Vulkan calls. An {@link UploadExecutor}
 * consumes this. The four hint fields carry enough information for an external scheduler (a render
 * graph, a custom submit path) to do barrier insertion, queue assignment, ownership transfer,
 * priority scheduling, and degradation, without the mesh module importing a single graph type.
 *
 * @param ops            the ordered list of copy/transcode operations
 * @param dstAccessMask  VkAccessFlags the destination will be read with (e.g. VERTEX_ATTRIBUTE_READ)
 * @param dstStageMask   VkPipelineStageFlags of the first consumer (e.g. VERTEX_INPUT)
 * @param preferredQueue preferred queue class; the executor resolves to a concrete queue
 * @param priority       scheduling priority hint
 * @param deferrable     whether this plan may be split across frames or dropped under budget pressure
 */
public record UploadPlan(
        List<UploadOp> ops,
        int dstAccessMask,
        int dstStageMask,
        QueueClass preferredQueue,
        Priority priority,
        boolean deferrable
) {
    public UploadPlan {
        ops = List.copyOf(ops);
    }
}
