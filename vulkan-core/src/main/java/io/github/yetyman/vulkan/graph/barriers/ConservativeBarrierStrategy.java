package io.github.yetyman.vulkan.graph.barriers;

import io.github.yetyman.vulkan.VkBufferBarrier;
import io.github.yetyman.vulkan.VkImageBarrier;
import io.github.yetyman.vulkan.enums.VkAccessFlagBits;
import io.github.yetyman.vulkan.enums.VkPipelineStageFlagBits;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.resources.GraphImageResource;
import io.github.yetyman.vulkan.graph.resources.GraphResource;

import java.lang.foreign.Arena;

/**
 * Conservative barrier strategy for bindless resources and unknown access patterns.
 * Always emits a full barrier (all access, all stages) to guarantee correctness
 * at the cost of potential GPU stalls.
 */
public class ConservativeBarrierStrategy implements BarrierStrategy {

    private static final int ALL_COMMANDS = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT.value();
    private static final int ALL_ACCESS =
        VkAccessFlagBits.VK_ACCESS_SHADER_WRITE_BIT.value()
        | VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value()
        | VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value()
        | VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value()
        | VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT.value()
        | VkAccessFlagBits.VK_ACCESS_TRANSFER_READ_BIT.value()
        | VkAccessFlagBits.VK_ACCESS_UNIFORM_READ_BIT.value()
        | VkAccessFlagBits.VK_ACCESS_INPUT_ATTACHMENT_READ_BIT.value();

    @Override
    public void emit(GraphResource resource, ResourceEdge consumer, int consumerQueueFamily,
                     BarrierBatch batch, Arena arena) {
        if (resource instanceof GraphImageResource imageRes) {
            int newLayout = consumer.imageLayout() >= 0 ? consumer.imageLayout() : imageRes.currentLayout();
            VkImageBarrier barrier = VkImageBarrier.builder()
                .image(resource.handle())
                .srcAccess(ALL_ACCESS)
                .dstAccess(ALL_ACCESS)
                .transition(imageRes.currentLayout(), newLayout)
                .build(arena);
            batch.add(barrier, ALL_COMMANDS, ALL_COMMANDS);
            imageRes.updateLayout(newLayout);
        } else {
            VkBufferBarrier barrier = VkBufferBarrier.builder()
                .buffer(resource.handle())
                .srcAccess(ALL_ACCESS)
                .dstAccess(ALL_ACCESS)
                .build(arena);
            batch.add(barrier, ALL_COMMANDS, ALL_COMMANDS);
        }
    }
}
