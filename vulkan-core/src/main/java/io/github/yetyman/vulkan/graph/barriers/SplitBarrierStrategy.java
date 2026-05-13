package io.github.yetyman.vulkan.graph.barriers;

import io.github.yetyman.vulkan.VkBufferBarrier;
import io.github.yetyman.vulkan.VkImageBarrier;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.resources.GraphBufferResource;
import io.github.yetyman.vulkan.graph.resources.GraphImageResource;
import io.github.yetyman.vulkan.graph.resources.GraphResource;

import java.lang.foreign.Arena;

/**
 * Default barrier strategy. Emits the minimal barrier needed to transition a resource
 * from its current tracked state to the state required by the consuming edge.
 *
 * Rules:
 * - No barrier needed if src and dst access are both reads with no layout change and same queue
 * - Queue ownership transfer emits a release+acquire barrier pair via OwnershipTransfer.
 *   The release barrier is recorded on the source queue's command buffer with
 *   srcAccessMask set and dstAccessMask=0 (the release side does not need to make memory
 *   available to the destination yet). The acquire barrier is recorded on the destination
 *   queue's command buffer with srcAccessMask=0 and dstAccessMask set (the acquire side
 *   makes memory available to the destination's access type).
 * - Image layout transitions always emit an image barrier
 * - Buffer access changes emit a buffer barrier
 */
public class SplitBarrierStrategy implements BarrierStrategy {

    private static final int VK_QUEUE_FAMILY_IGNORED = ~0;

    // Pipeline stage constants for ownership transfer halves
    private static final int VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT = 0x00000001;
    private static final int VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT = 0x00002000;

    /**
     * The destination queue family index for the consuming edge. Set by the executor before
     * calling emit() so the strategy knows whether a queue ownership transfer is needed.
     * If not set (remains VK_QUEUE_FAMILY_IGNORED), no ownership transfer is emitted.
     */
    private int consumerQueueFamily = VK_QUEUE_FAMILY_IGNORED;

    /**
     * Sets the consumer's queue family index. The executor must call this before emit()
     * for each node so the strategy can detect cross-queue transitions.
     */
    public void setConsumerQueueFamily(int queueFamily) {
        this.consumerQueueFamily = queueFamily;
    }

    @Override
    public void emit(GraphResource resource, ResourceEdge consumer, BarrierBatch batch, Arena arena) {
        int srcAccess = resource.lastAccessMask();
        int dstAccess = consumer.accessMask();
        int srcStage = resource.lastStageMask();
        int dstStage = consumer.stageMask();
        int srcQueue = resource.owningQueueFamily();
        int dstQueue = consumerQueueFamily;

        // Determine if this is a cross-queue transition
        boolean crossQueue = needsOwnershipTransfer(srcQueue, dstQueue);

        if (resource instanceof GraphImageResource imageRes) {
            emitImageBarrier(imageRes, consumer, srcAccess, dstAccess, srcStage, dstStage,
                srcQueue, dstQueue, crossQueue, batch, arena);
        } else if (resource instanceof GraphBufferResource) {
            emitBufferBarrier(resource, srcAccess, dstAccess, srcStage, dstStage,
                srcQueue, dstQueue, crossQueue, batch, arena);
        } else {
            // Generic resource -- emit buffer barrier as fallback
            emitBufferBarrier(resource, srcAccess, dstAccess, srcStage, dstStage,
                srcQueue, dstQueue, crossQueue, batch, arena);
        }
    }

    private void emitImageBarrier(GraphImageResource imageRes, ResourceEdge consumer,
                                  int srcAccess, int dstAccess, int srcStage, int dstStage,
                                  int srcQueue, int dstQueue, boolean crossQueue,
                                  BarrierBatch batch, Arena arena) {
        int oldLayout = imageRes.currentLayout();
        int newLayout = consumer.imageLayout();

        // If no layout specified on the edge, keep current layout
        if (newLayout < 0) {
            newLayout = oldLayout;
        }

        // Skip barrier if: same layout, read-to-read, same queue (no ownership transfer)
        if (!crossQueue && oldLayout == newLayout && isReadOnly(srcAccess) && isReadOnly(dstAccess)) {
            return;
        }

        if (crossQueue) {
            // Emit release+acquire pair as an OwnershipTransfer
            // Release barrier: on source queue, srcAccess -> 0, old layout -> new layout,
            //   srcQueueFamily=src, dstQueueFamily=dst
            VkImageBarrier releaseBarrier = VkImageBarrier.builder()
                .image(imageRes.handle())
                .srcAccess(srcAccess)
                .dstAccess(0)  // release side: no dst access needed
                .transition(oldLayout, newLayout)
                .queueFamilyTransfer(srcQueue, dstQueue)
                .build(arena);

            // Acquire barrier: on destination queue, 0 -> dstAccess, old layout -> new layout,
            //   srcQueueFamily=src, dstQueueFamily=dst
            VkImageBarrier acquireBarrier = VkImageBarrier.builder()
                .image(imageRes.handle())
                .srcAccess(0)  // acquire side: no src access needed
                .dstAccess(dstAccess)
                .transition(oldLayout, newLayout)
                .queueFamilyTransfer(srcQueue, dstQueue)
                .build(arena);

            batch.addOwnershipTransfer(new OwnershipTransfer(
                releaseBarrier, acquireBarrier,
                srcStage, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,  // release: src stage -> bottom
                VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, dstStage,     // acquire: top -> dst stage
                srcQueue, dstQueue
            ));
        } else {
            // Same-queue barrier
            VkImageBarrier barrier = VkImageBarrier.builder()
                .image(imageRes.handle())
                .srcAccess(srcAccess)
                .dstAccess(dstAccess)
                .transition(oldLayout, newLayout)
                .queueFamilyTransfer(VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED)
                .build(arena);

            batch.add(barrier, srcStage, dstStage);
        }

        // Update tracked layout
        imageRes.updateLayout(newLayout);
    }

    private void emitBufferBarrier(GraphResource resource,
                                   int srcAccess, int dstAccess, int srcStage, int dstStage,
                                   int srcQueue, int dstQueue, boolean crossQueue,
                                   BarrierBatch batch, Arena arena) {
        // Skip barrier for read-to-read on same queue with no ownership transfer
        if (!crossQueue && isReadOnly(srcAccess) && isReadOnly(dstAccess)) {
            return;
        }

        // Skip if nothing changed (e.g. first use with no prior access) and no ownership transfer
        if (!crossQueue && srcAccess == 0 && srcStage == 0) {
            return;
        }

        if (crossQueue) {
            // Release barrier: on source queue
            VkBufferBarrier releaseBarrier = VkBufferBarrier.builder()
                .buffer(resource.handle())
                .srcAccess(srcAccess)
                .dstAccess(0)
                .queueFamilyTransfer(srcQueue, dstQueue)
                .build(arena);

            // Acquire barrier: on destination queue
            VkBufferBarrier acquireBarrier = VkBufferBarrier.builder()
                .buffer(resource.handle())
                .srcAccess(0)
                .dstAccess(dstAccess)
                .queueFamilyTransfer(srcQueue, dstQueue)
                .build(arena);

            batch.addOwnershipTransfer(new OwnershipTransfer(
                releaseBarrier, acquireBarrier,
                srcStage, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, dstStage,
                srcQueue, dstQueue
            ));
        } else {
            VkBufferBarrier barrier = VkBufferBarrier.builder()
                .buffer(resource.handle())
                .srcAccess(srcAccess)
                .dstAccess(dstAccess)
                .build(arena);

            batch.add(barrier, srcStage, dstStage);
        }
    }

    /**
     * Returns true if a queue ownership transfer is needed between srcQueue and dstQueue.
     * Transfer is needed when both are valid queue family indices and they differ.
     */
    private boolean needsOwnershipTransfer(int srcQueue, int dstQueue) {
        if (srcQueue == VK_QUEUE_FAMILY_IGNORED || dstQueue == VK_QUEUE_FAMILY_IGNORED) {
            return false;
        }
        return srcQueue != dstQueue;
    }

    /**
     * Returns true if the access mask contains only read operations.
     * Write bits: SHADER_WRITE (0x40), COLOR_ATTACHMENT_WRITE (0x100),
     * DEPTH_STENCIL_WRITE (0x400), TRANSFER_WRITE (0x800), HOST_WRITE (0x4000)
     */
    private boolean isReadOnly(int accessMask) {
        int writeBits = 0x00000040  // VK_ACCESS_SHADER_WRITE_BIT
                      | 0x00000100  // VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                      | 0x00000400  // VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                      | 0x00000800  // VK_ACCESS_TRANSFER_WRITE_BIT
                      | 0x00004000; // VK_ACCESS_HOST_WRITE_BIT
        return (accessMask & writeBits) == 0;
    }
}
