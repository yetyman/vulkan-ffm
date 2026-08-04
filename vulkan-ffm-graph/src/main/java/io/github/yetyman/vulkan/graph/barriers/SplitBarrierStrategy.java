package io.github.yetyman.vulkan.graph.barriers;

import io.github.yetyman.vulkan.VkBufferBarrier;
import io.github.yetyman.vulkan.VkImageBarrier;
import io.github.yetyman.vulkan.enums.VkAccessFlagBits;
import io.github.yetyman.vulkan.enums.VkPipelineStageFlagBits;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.resources.GraphBufferResource;
import io.github.yetyman.vulkan.graph.resources.GraphImageResource;
import io.github.yetyman.vulkan.graph.resources.GraphResource;

import java.lang.foreign.SegmentAllocator;

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

    private static final int WRITE_BITS =
        VkAccessFlagBits.VK_ACCESS_SHADER_WRITE_BIT.value()
        | VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value()
        | VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value()
        | VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT.value()
        | VkAccessFlagBits.VK_ACCESS_HOST_WRITE_BIT.value();

    @Override
    public void emit(GraphResource resource, ResourceEdge consumer, int consumerQueueFamily,
                     BarrierBatch batch, SegmentAllocator allocator) {
        int srcAccess = resource.lastAccessMask();
        int dstAccess = consumer.accessMask();
        int srcStage = resource.lastStageMask();
        int dstStage = consumer.stageMask();
        int srcQueue = resource.owningQueueFamily();
        int dstQueue = consumerQueueFamily;

        boolean crossQueue = needsOwnershipTransfer(srcQueue, dstQueue);

        if (resource instanceof GraphImageResource imageRes) {
            emitImageBarrier(imageRes, consumer, srcAccess, dstAccess, srcStage, dstStage,
                srcQueue, dstQueue, crossQueue, batch, allocator);
        } else if (resource instanceof GraphBufferResource) {
            emitBufferBarrier(resource, srcAccess, dstAccess, srcStage, dstStage,
                srcQueue, dstQueue, crossQueue, batch, allocator);
        } else {
            emitBufferBarrier(resource, srcAccess, dstAccess, srcStage, dstStage,
                srcQueue, dstQueue, crossQueue, batch, allocator);
        }
    }

    private void emitImageBarrier(GraphImageResource imageRes, ResourceEdge consumer,
                                  int srcAccess, int dstAccess, int srcStage, int dstStage,
                                  int srcQueue, int dstQueue, boolean crossQueue,
                                  BarrierBatch batch, SegmentAllocator allocator) {
        int oldLayout = imageRes.currentLayout();
        int newLayout = consumer.imageLayout();

        if (newLayout < 0) {
            newLayout = oldLayout;
        }

        // Skip barrier if: same layout, read-to-read, same queue
        if (!crossQueue && oldLayout == newLayout && isReadOnly(srcAccess) && isReadOnly(dstAccess)) {
            return;
        }

        if (crossQueue) {
            VkImageBarrier releaseBarrier = VkImageBarrier.builder()
                .image(imageRes.handle())
                .srcAccess(srcAccess)
                .dstAccess(0)
                .transition(oldLayout, newLayout)
                .queueFamilyTransfer(srcQueue, dstQueue)
                .build(allocator);

            VkImageBarrier acquireBarrier = VkImageBarrier.builder()
                .image(imageRes.handle())
                .srcAccess(0)
                .dstAccess(dstAccess)
                .transition(oldLayout, newLayout)
                .queueFamilyTransfer(srcQueue, dstQueue)
                .build(allocator);

            batch.addOwnershipTransfer(new OwnershipTransfer(
                releaseBarrier, acquireBarrier,
                srcStage,
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT.value(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value(),
                dstStage,
                srcQueue, dstQueue
            ));
        } else {
            VkImageBarrier barrier = VkImageBarrier.builder()
                .image(imageRes.handle())
                .srcAccess(srcAccess)
                .dstAccess(dstAccess)
                .transition(oldLayout, newLayout)
                .queueFamilyTransfer(VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED)
                .build(allocator);

            batch.add(barrier, srcStage, dstStage);
        }

        imageRes.updateLayout(newLayout);
    }

    private void emitBufferBarrier(GraphResource resource,
                                   int srcAccess, int dstAccess, int srcStage, int dstStage,
                                   int srcQueue, int dstQueue, boolean crossQueue,
                                   BarrierBatch batch, SegmentAllocator allocator) {
        if (!crossQueue && isReadOnly(srcAccess) && isReadOnly(dstAccess)) {
            return;
        }

        if (!crossQueue && srcAccess == 0 && srcStage == 0) {
            return;
        }

        if (crossQueue) {
            VkBufferBarrier releaseBarrier = VkBufferBarrier.builder()
                .buffer(resource.handle())
                .srcAccess(srcAccess)
                .dstAccess(0)
                .queueFamilyTransfer(srcQueue, dstQueue)
                .build(allocator);

            VkBufferBarrier acquireBarrier = VkBufferBarrier.builder()
                .buffer(resource.handle())
                .srcAccess(0)
                .dstAccess(dstAccess)
                .queueFamilyTransfer(srcQueue, dstQueue)
                .build(allocator);

            batch.addOwnershipTransfer(new OwnershipTransfer(
                releaseBarrier, acquireBarrier,
                srcStage,
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT.value(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value(),
                dstStage,
                srcQueue, dstQueue
            ));
        } else {
            VkBufferBarrier barrier = VkBufferBarrier.builder()
                .buffer(resource.handle())
                .srcAccess(srcAccess)
                .dstAccess(dstAccess)
                .build(allocator);

            batch.add(barrier, srcStage, dstStage);
        }
    }

    private boolean needsOwnershipTransfer(int srcQueue, int dstQueue) {
        if (srcQueue == VK_QUEUE_FAMILY_IGNORED || dstQueue == VK_QUEUE_FAMILY_IGNORED) {
            return false;
        }
        return srcQueue != dstQueue;
    }

    private boolean isReadOnly(int accessMask) {
        return (accessMask & WRITE_BITS) == 0;
    }
}
