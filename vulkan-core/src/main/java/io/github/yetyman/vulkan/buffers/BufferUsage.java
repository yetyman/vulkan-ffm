package io.github.yetyman.vulkan.buffers;

import static io.github.yetyman.vulkan.enums.VkBufferUsageFlagBits.*;

public enum BufferUsage {
    UNIFORM(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT.value()),
    STORAGE(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT.value()),
    VERTEX(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.value()),
    TRANSFER(VK_BUFFER_USAGE_TRANSFER_SRC_BIT.value() | VK_BUFFER_USAGE_TRANSFER_DST_BIT.value()),
    MIXED(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT.value() | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT.value() | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.value());
    
    private final int vkFlags;
    
    BufferUsage(int vkFlags) {
        this.vkFlags = vkFlags;
    }
    
    public int toVkFlags() {
        return vkFlags | VK_BUFFER_USAGE_TRANSFER_SRC_BIT.value() | VK_BUFFER_USAGE_TRANSFER_DST_BIT.value();
    }
}