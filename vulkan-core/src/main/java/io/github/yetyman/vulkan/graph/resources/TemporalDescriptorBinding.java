package io.github.yetyman.vulkan.graph.resources;

import io.github.yetyman.vulkan.VkDescriptorPool;
import io.github.yetyman.vulkan.VkDescriptorSet;
import io.github.yetyman.vulkan.VkDescriptorSetLayout;
import io.github.yetyman.vulkan.VkDevice;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Optional graph-managed descriptor sets for a TemporalResource.
 *
 * When configured, the graph allocates one descriptor set per physical slot and keeps them
 * bound to the correct buffer/image. Nodes can then call ctx.temporalDescriptorSet("name")
 * to get the pre-bound set for the current frame's read or write slot, eliminating manual
 * descriptor set selection logic.
 *
 * Users who prefer manual descriptor management can skip this entirely and use
 * ctx.temporalReadHandle() / ctx.temporalWriteHandle() instead.
 */
public class TemporalDescriptorBinding implements AutoCloseable {

    private final VkDescriptorPool pool;
    private final VkDescriptorSet[] sets; // one per physical slot
    private final int binding;

    private TemporalDescriptorBinding(VkDescriptorPool pool, VkDescriptorSet[] sets, int binding) {
        this.pool = pool;
        this.sets = sets;
        this.binding = binding;
    }

    /**
     * Creates a temporal descriptor binding for a buffer temporal resource.
     * Allocates one descriptor set per physical slot, each bound to its slot's buffer handle.
     *
     * @param device the logical device
     * @param layout the descriptor set layout (must have a storage buffer at the given binding)
     * @param binding the binding index within the layout
     * @param temporalResource the temporal resource (must have physical slots allocated)
     * @param arena arena for allocation (should be graph-lifetime)
     * @return the binding, or null if physical slots are not allocated
     */
    public static TemporalDescriptorBinding createForBuffer(
            VkDevice device, VkDescriptorSetLayout layout, int binding,
            TemporalResource temporalResource, Arena arena) {
        GraphResource[] slots = temporalResource.physicalSlots();
        if (slots == null) return null;

        int count = slots.length;
        VkDescriptorPool pool = VkDescriptorPool.builder()
            .device(device)
            .maxSets(count)
            .storageBuffers(count)
            .build(arena);

        VkDescriptorSet[] sets = new VkDescriptorSet[count];
        for (int i = 0; i < count; i++) {
            sets[i] = pool.allocateDescriptorSet(layout);
            try (Arena tmp = Arena.ofConfined()) {
                // VK_DESCRIPTOR_TYPE_STORAGE_BUFFER = 7
                sets[i].bindBuffer(binding, 7, slots[i].handle(), 0, -1, tmp);
            }
        }

        return new TemporalDescriptorBinding(pool, sets, binding);
    }

    /**
     * Creates a paired temporal descriptor binding for a compute shader that reads from one
     * slot and writes to another in the same descriptor set.
     *
     * Allocates N sets (one per flip state). Each set has:
     * - readBinding bound to the read slot for that flip state
     * - writeBinding bound to the write slot for that flip state
     *
     * Use with ctx.temporalReadDescriptorSet() to get the correct paired set for the current frame.
     *
     * @param device the logical device
     * @param layout the descriptor set layout (must have storage buffers at both bindings)
     * @param readBinding the binding index for the read buffer
     * @param writeBinding the binding index for the write buffer
     * @param temporalResource the temporal resource (must have physical slots allocated)
     * @param arena arena for allocation (should be graph-lifetime)
     * @return the binding, or null if physical slots are not allocated
     */
    public static TemporalDescriptorBinding createPairedForBuffer(
            VkDevice device, VkDescriptorSetLayout layout, int readBinding, int writeBinding,
            TemporalResource temporalResource, Arena arena) {
        GraphResource[] slots = temporalResource.physicalSlots();
        if (slots == null) return null;

        int count = slots.length;
        // Need one set per flip state: set[i] has read=slot[(i-1+count)%count], write=slot[i]
        VkDescriptorPool pool = VkDescriptorPool.builder()
            .device(device)
            .maxSets(count)
            .storageBuffers(count * 2)
            .build(arena);

        VkDescriptorSet[] sets = new VkDescriptorSet[count];
        for (int i = 0; i < count; i++) {
            sets[i] = pool.allocateDescriptorSet(layout);
            int readSlotIdx = (i - 1 + count) % count;
            int writeSlotIdx = i;
            try (Arena tmp = Arena.ofConfined()) {
                sets[i].bindBuffer(readBinding, 7, slots[readSlotIdx].handle(), 0, -1, tmp);
                sets[i].bindBuffer(writeBinding, 7, slots[writeSlotIdx].handle(), 0, -1, tmp);
            }
        }

        return new TemporalDescriptorBinding(pool, sets, readBinding);
    }

    /**
     * Returns the descriptor set bound to the given physical slot index.
     */
    public VkDescriptorSet setForSlot(int slotIndex) {
        return sets[slotIndex];
    }

    /**
     * Returns the descriptor set for the read slot (previous frame's write).
     *
     * @param writeCount the temporal resource's current write count
     * @param bufferCount the temporal resource's buffer count
     */
    public VkDescriptorSet readSet(int writeCount, int bufferCount) {
        int readIdx = (writeCount - 1 + bufferCount) % bufferCount;
        return sets[readIdx];
    }

    /**
     * Returns the descriptor set for the write slot (current frame's write target).
     *
     * @param writeCount the temporal resource's current write count
     * @param bufferCount the temporal resource's buffer count
     */
    public VkDescriptorSet writeSet(int writeCount, int bufferCount) {
        int writeIdx = writeCount % bufferCount;
        return sets[writeIdx];
    }

    /** @return the binding index these sets are configured for */
    public int binding() { return binding; }

    @Override
    public void close() {
        if (pool != null) pool.close();
    }
}
