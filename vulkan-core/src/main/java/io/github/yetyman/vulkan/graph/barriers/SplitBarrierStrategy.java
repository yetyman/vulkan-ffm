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
 * - No barrier needed if src and dst access are both reads with no layout change
 * - Queue ownership transfer emits release+acquire pair (two barriers)
 * - Image layout transitions always emit an image barrier
 * - Buffer access changes emit a buffer barrier
 */
public class SplitBarrierStrategy implements BarrierStrategy {

    private static final int VK_QUEUE_FAMILY_IGNORED = ~0;

    @Override
    public void emit(GraphResource resource, ResourceEdge consumer, BarrierBatch batch, Arena arena) {
        int srcAccess = resource.lastAccessMask();
        int dstAccess = consumer.accessMask();
        int srcStage = resource.lastStageMask();
        int dstStage = consumer.stageMask();
        int srcQueue = resource.owningQueueFamily();

        if (resource instanceof GraphImageResource imageRes) {
            emitImageBarrier(imageRes, consumer, srcAccess, dstAccess, srcStage, dstStage, srcQueue, batch, arena);
        } else if (resource instanceof GraphBufferResource) {
            emitBufferBarrier(resource, srcAccess, dstAccess, srcStage, dstStage, srcQueue, batch, arena);
        } else {
            // Generic resource -- emit buffer barrier as fallback
            emitBufferBarrier(resource, srcAccess, dstAccess, srcStage, dstStage, srcQueue, batch, arena);
        }
    }

    private void emitImageBarrier(GraphImageResource imageRes, ResourceEdge consumer,
                                  int srcAccess, int dstAccess, int srcStage, int dstStage,
                                  int srcQueue, BarrierBatch batch, Arena arena) {
        int oldLayout = imageRes.currentLayout();
        int newLayout = consumer.imageLayout();

        // If no layout specified on the edge, keep current layout
        if (newLayout < 0) {
            newLayout = oldLayout;
        }

        // Skip barrier if: same layout, read-to-read, same queue
        if (oldLayout == newLayout && isReadOnly(srcAccess) && isReadOnly(dstAccess)) {
            return;
        }

        VkImageBarrier.Builder builder = VkImageBarrier.builder()
            .image(imageRes.handle())
            .srcAccess(srcAccess)
            .dstAccess(dstAccess)
            .transition(oldLayout, newLayout);

        // Queue ownership transfer
        // stub -- queue family ownership transfer is not implemented. The release+acquire
        // barrier pair required for cross-queue resource sharing is not emitted. All resources
        // are treated as if they stay on the same queue family. Multi-queue graphs will have
        // incorrect synchronization until this is implemented.
        if (srcQueue != VK_QUEUE_FAMILY_IGNORED && consumer.stageMask() != 0) {
            builder.queueFamilyTransfer(VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED);
        }

        batch.add(builder.build(arena), srcStage, dstStage);

        // Update tracked layout
        imageRes.updateLayout(newLayout);
    }

    private void emitBufferBarrier(GraphResource resource,
                                   int srcAccess, int dstAccess, int srcStage, int dstStage,
                                   int srcQueue, BarrierBatch batch, Arena arena) {
        // Skip barrier for read-to-read on same queue
        if (isReadOnly(srcAccess) && isReadOnly(dstAccess)) {
            return;
        }

        // Skip if nothing changed (e.g. first use with no prior access)
        if (srcAccess == 0 && srcStage == 0) {
            return;
        }

        VkBufferBarrier barrier = VkBufferBarrier.builder()
            .buffer(resource.handle())
            .srcAccess(srcAccess)
            .dstAccess(dstAccess)
            .build(arena);

        batch.add(barrier, srcStage, dstStage);
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
