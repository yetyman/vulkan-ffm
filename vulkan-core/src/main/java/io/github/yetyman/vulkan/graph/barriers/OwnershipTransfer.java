package io.github.yetyman.vulkan.graph.barriers;

import io.github.yetyman.vulkan.VkBarrier;

/**
 * Represents a queue ownership transfer that requires a release barrier on the source queue
 * and an acquire barrier on the destination queue. Vulkan mandates that both halves are
 * recorded on their respective queue's command buffer for the transfer to be valid.
 *
 * The release barrier is recorded at the end of the last bucket on the source queue that
 * writes the resource. The acquire barrier is recorded at the start of the first bucket on
 * the destination queue that reads the resource.
 */
public record OwnershipTransfer(
    /** Barrier to record on the source queue's command buffer (release) */
    VkBarrier releaseBarrier,
    /** Barrier to record on the destination queue's command buffer (acquire) */
    VkBarrier acquireBarrier,
    /** Source pipeline stage mask for the release barrier */
    int releaseSrcStage,
    /** Destination pipeline stage mask for the release barrier (TOP_OF_PIPE on release side) */
    int releaseDstStage,
    /** Source pipeline stage mask for the acquire barrier (BOTTOM_OF_PIPE on acquire side) */
    int acquireSrcStage,
    /** Destination pipeline stage mask for the acquire barrier */
    int acquireDstStage,
    /** Source queue family index */
    int srcQueueFamily,
    /** Destination queue family index */
    int dstQueueFamily
) {}
