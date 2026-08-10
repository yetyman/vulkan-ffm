package io.github.yetyman.vulkan.buffers;

import static io.github.yetyman.vulkan.enums.VkBufferUsageFlagBits.*;

public enum BufferUsage {
    UNIFORM(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT.value()),
    STORAGE(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT.value()),
    VERTEX(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.value()),
    INDEX(VK_BUFFER_USAGE_INDEX_BUFFER_BIT.value()),
    TRANSFER(VK_BUFFER_USAGE_TRANSFER_SRC_BIT.value() | VK_BUFFER_USAGE_TRANSFER_DST_BIT.value()),
    MIXED(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT.value() | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT.value() | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.value()),
    /** Read as {@code VkDrawIndirectCommand}/{@code VkDrawIndexedIndirectCommand} arguments by vkCmdDrawIndirect* / vkCmdDrawIndexedIndirect*Count. */
    INDIRECT(VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT.value()),
    /** A compute-written storage buffer that is also read as indirect draw/dispatch arguments, e.g. GPU-driven culling output. */
    STORAGE_INDIRECT(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT.value() | VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT.value());

    private final int vkFlags;

    BufferUsage(int vkFlags) {
        this.vkFlags = vkFlags;
    }

    public int toVkFlags() {
        return vkFlags | VK_BUFFER_USAGE_TRANSFER_SRC_BIT.value() | VK_BUFFER_USAGE_TRANSFER_DST_BIT.value();
    }
}