package io.github.yetyman.vulkan.graph.resources;

import io.github.yetyman.vulkan.enums.VkBufferUsageFlagBits;

/**
 * Descriptor for a graph-managed buffer resource. Captures the parameters needed to
 * allocate the buffer without actually allocating it.
 */
public class BufferDesc {

    private final long size;
    private final int usage;

    private BufferDesc(long size, int usage) {
        this.size = size;
        this.usage = usage;
    }

    /** Storage buffer */
    public static BufferDesc storage(long size) {
        return new BufferDesc(size,
            VkBufferUsageFlagBits.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT.value()
                | VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_DST_BIT.value()
                | VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_SRC_BIT.value());
    }

    /** Uniform buffer */
    public static BufferDesc uniform(long size) {
        return new BufferDesc(size,
            VkBufferUsageFlagBits.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT.value()
                | VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_DST_BIT.value());
    }

    /** Custom usage */
    public static BufferDesc custom(long size, int usage) {
        return new BufferDesc(size, usage);
    }

    /** @return a copy with new size (for resize) */
    public BufferDesc withSize(long newSize) {
        return new BufferDesc(newSize, usage);
    }

    public long size() { return size; }
    public int usage() { return usage; }

    /** @return memory size aligned to 256 bytes */
    public long alignedSize() {
        return (size + 255) & ~255L;
    }
}
