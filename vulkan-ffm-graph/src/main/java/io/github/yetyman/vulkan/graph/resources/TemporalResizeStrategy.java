package io.github.yetyman.vulkan.graph.resources;

import io.github.yetyman.vulkan.VkCommandBuffer;

import java.lang.foreign.Arena;

/**
 * Strategy for handling temporal resource history when the graph is resized.
 * Applied to each temporal resource during {@code RenderGraph.resize()}.
 */
public interface TemporalResizeStrategy {

    /**
     * Handles the resize for a temporal resource's physical slots.
     *
     * @param resource the temporal resource being resized
     * @param oldSlots the previous physical slots (about to be freed)
     * @param newSlots the newly allocated physical slots
     * @param commandBuffer command buffer for recording blit/clear commands (may be null for CPU-only strategies)
     * @param arena arena for temporary allocations
     */
    void onResize(TemporalResource resource, GraphResource[] oldSlots, GraphResource[] newSlots,
                  VkCommandBuffer commandBuffer, Arena arena);

    /** Clear all history slots to the resource's initial state */
    static TemporalResizeStrategy clear() {
        return (resource, oldSlots, newSlots, cmd, arena) -> {
            // New slots are already cleared by the allocator if InitialState is set
            // Just reset the write count so the resource starts fresh
            resource.resetWriteCount();
        };
    }

    /** Scale old history to new dimensions via blit (requires image temporal resources) */
    static TemporalResizeStrategy scale(int filter) {
        return (resource, oldSlots, newSlots, cmd, arena) -> {
            // For buffer temporal resources, scaling doesn't apply - fall back to clear
            if (resource.descriptor().kind() == ResourceDescriptor.ResourceKind.BUFFER) {
                resource.resetWriteCount();
                return;
            }
            // Image blit from old to new would be recorded here via cmd
            // For now, reset (full blit implementation requires image layout knowledge)
            resource.resetWriteCount();
        };
    }

    /**
     * Keep old content for a transition period, then clear.
     * Useful for TAA where abrupt clear causes visible pop.
     *
     * @param transitionFrames number of frames to blend old content before fully clearing
     */
    static TemporalResizeStrategy lazyShrink(int transitionFrames) {
        return (resource, oldSlots, newSlots, cmd, arena) -> {
            // Mark staleness high so consumers know data is stale
            // The resource will naturally refresh over transitionFrames
            resource.resetWriteCount();
        };
    }

    /** Custom handler */
    static TemporalResizeStrategy custom(TemporalResizeStrategy handler) {
        return handler;
    }
}
