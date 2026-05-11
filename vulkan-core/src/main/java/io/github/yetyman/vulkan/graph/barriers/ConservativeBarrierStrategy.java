package io.github.yetyman.vulkan.graph.barriers;

import io.github.yetyman.vulkan.VkBufferBarrier;
import io.github.yetyman.vulkan.VkImageBarrier;
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

    private static final int ALL_COMMANDS = 0x00010000; // VK_PIPELINE_STAGE_ALL_COMMANDS_BIT
    private static final int ALL_ACCESS = 0x00000060 | 0x00000100 | 0x00000400 | 0x00000800
                                        | 0x00000020 | 0x00000010 | 0x00000004 | 0x00000002;

    @Override
    public void emit(GraphResource resource, ResourceEdge consumer, BarrierBatch batch, Arena arena) {
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
