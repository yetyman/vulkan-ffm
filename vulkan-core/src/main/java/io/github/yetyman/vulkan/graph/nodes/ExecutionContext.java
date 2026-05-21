package io.github.yetyman.vulkan.graph.nodes;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDescriptorSet;
import io.github.yetyman.vulkan.graph.feedback.FrameStats;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.scheduling.QueueAssignment;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Context provided to nodes during the execute phase.
 */
public interface ExecutionContext {

    /** @return the command buffer to record into */
    VkCommandBuffer commandBuffer();

    /** @return arena scoped to this frame (freed after submit) */
    Arena frameArena();

    /** @return which frame-in-flight slot (0..framesInFlight-1) */
    int frameIndex();

    /** @return monotonic frame counter */
    long frameGeneration();

    /** @return the queue this node is executing on */
    QueueAssignment queue();

    /** @return frame N-1's stats for inline adaptation (may be null on first frame) */
    FrameStats previousStats();

    /**
     * Returns the physical resource handle for a temporal read (previous frame's write slot).
     * The graph resolves which physical slot to read based on the temporal resource's flip state.
     *
     * @param temporalResourceName the name of the temporal resource
     * @return the handle of the physical resource to read from, or NULL if not available
     */
    default MemorySegment temporalReadHandle(String temporalResourceName) { return MemorySegment.NULL; }

    /**
     * Returns the physical resource handle for a temporal write (current frame's write slot).
     * The graph resolves which physical slot to write based on the temporal resource's flip state.
     *
     * @param temporalResourceName the name of the temporal resource
     * @return the handle of the physical resource to write to, or NULL if not available
     */
    default MemorySegment temporalWriteHandle(String temporalResourceName) { return MemorySegment.NULL; }

    /**
     * Returns the physical GraphResource for a temporal read (previous frame's write slot).
     *
     * @param temporalResourceName the name of the temporal resource
     * @return the GraphResource to read from, or null if not available
     */
    default GraphResource temporalRead(String temporalResourceName) { return null; }

    /**
     * Returns the physical GraphResource for a temporal write (current frame's write slot).
     *
     * @param temporalResourceName the name of the temporal resource
     * @return the GraphResource to write to, or null if not available
     */
    default GraphResource temporalWrite(String temporalResourceName) { return null; }

    /**
     * Returns a pre-bound descriptor set for the temporal resource's read slot (previous frame's data).
     * Only available if the temporal resource was configured with a descriptor layout via the graph.
     * Returns null if the user is managing descriptors manually.
     *
     * @param temporalResourceName the name of the temporal resource
     * @return the descriptor set bound to the read slot, or null if not graph-managed
     */
    default VkDescriptorSet temporalReadDescriptorSet(String temporalResourceName) { return null; }

    /**
     * Returns a pre-bound descriptor set for the temporal resource's write slot (current frame's target).
     * Only available if the temporal resource was configured with a descriptor layout via the graph.
     * Returns null if the user is managing descriptors manually.
     *
     * @param temporalResourceName the name of the temporal resource
     * @return the descriptor set bound to the write slot, or null if not graph-managed
     */
    default VkDescriptorSet temporalWriteDescriptorSet(String temporalResourceName) { return null; }

    /**
     * Returns the current handle of an imported resource.
     * For swapchain images, this is the handle that was most recently bound via rebind().
     *
     * @param importedResourceName the name of the imported resource
     * @return the handle, or NULL if not found
     */
    default MemorySegment importedHandle(String importedResourceName) { return MemorySegment.NULL; }

    /**
     * Returns how many submissions have passed since a temporal resource was last written.
     * 0 means it was written this frame. Useful for multi-rate rendering where a resource
     * may not be updated every frame.
     *
     * @param temporalResourceName the name of the temporal resource
     * @return submissions since last write, or -1 if not found
     */
    default int temporalStaleness(String temporalResourceName) { return -1; }
}
