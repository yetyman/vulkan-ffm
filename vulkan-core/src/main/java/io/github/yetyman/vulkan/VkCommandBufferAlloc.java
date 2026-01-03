package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VkCommandBufferAlloc {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private VkDevice device;
        private MemorySegment commandPool;
        private int level = VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        private int count = 1;
        
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        public Builder commandPool(MemorySegment commandPool) {
            this.commandPool = commandPool;
            return this;
        }
        
        public Builder primary() {
            this.level = VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
            return this;
        }
        
        public Builder secondary() {
            this.level = VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_SECONDARY;
            return this;
        }
        
        public Builder count(int count) {
            this.count = count;
            return this;
        }
        
        public MemorySegment[] allocate(Arena arena) {
            MemorySegment allocInfo = VkCommandBufferAllocateInfo.allocate(arena);
            VkCommandBufferAllocateInfo.sType(allocInfo, VkStructureType.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            VkCommandBufferAllocateInfo.pNext(allocInfo, MemorySegment.NULL);
            VkCommandBufferAllocateInfo.commandPool(allocInfo, commandPool);
            VkCommandBufferAllocateInfo.level(allocInfo, level);
            VkCommandBufferAllocateInfo.commandBufferCount(allocInfo, count);
            
            MemorySegment commandBuffersArray = arena.allocate(ValueLayout.ADDRESS, count);
            Vulkan.allocateCommandBuffers(device.handle(), allocInfo, commandBuffersArray).check();
            
            MemorySegment[] result = new MemorySegment[count];
            for (int i = 0; i < count; i++) {
                result[i] = commandBuffersArray.getAtIndex(ValueLayout.ADDRESS, i);
            }
            return result;
        }
    }
}