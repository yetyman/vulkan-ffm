package io.github.yetyman.vulkan.graph.resources;

import io.github.yetyman.vulkan.enums.VkBufferUsageFlagBits;

/**
 * Descriptor for a graph-managed buffer resource. Captures the parameters needed to
 * allocate the buffer without actually allocating it.
 */
public class BufferDesc {

    private final long size;
    private final int usage;
    private final int memoryProperties; // VkMemoryPropertyFlags (0 = device-local default)

    private BufferDesc(long size, int usage, int memoryProperties) {
        this.size = size;
        this.usage = usage;
        this.memoryProperties = memoryProperties;
    }

    /** Storage buffer */
    public static BufferDesc storage(long size) {
        return new BufferDesc(size,
            VkBufferUsageFlagBits.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT.value()
                | VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_DST_BIT.value()
                | VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_SRC_BIT.value(), 0);
    }

    /** Uniform buffer */
    public static BufferDesc uniform(long size) {
        return new BufferDesc(size,
            VkBufferUsageFlagBits.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT.value()
                | VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_DST_BIT.value(), 0);
    }

    /** Custom usage */
    public static BufferDesc custom(long size, int usage) {
        return new BufferDesc(size, usage, 0);
    }

    /** Custom usage with memory property hints */
    public static BufferDesc custom(long size, int usage, int memoryProperties) {
        return new BufferDesc(size, usage, memoryProperties);
    }

    /** @return a copy with new size (for resize) */
    public BufferDesc withSize(long newSize) {
        return new BufferDesc(newSize, usage, memoryProperties);
    }

    public long size() { return size; }
    public int usage() { return usage; }
    public int memoryProperties() { return memoryProperties; }

    /** @return memory size aligned to 256 bytes */
    public long alignedSize() {
        return (size + 255) & ~255L;
    }
}
