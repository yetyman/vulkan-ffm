package io.github.yetyman.vulkan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static io.github.yetyman.vulkan.generated.VulkanFFM.vkGetDeviceQueue;

public record VkQueue(MemorySegment handle, int familyIndex) {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private VkDevice device;
        private int queueFamilyIndex;
        private int queueIndex = 0;
        
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        public Builder familyIndex(int queueFamilyIndex) {
            this.queueFamilyIndex = queueFamilyIndex;
            return this;
        }
        
        public Builder queueIndex(int queueIndex) {
            this.queueIndex = queueIndex;
            return this;
        }
        
        public VkQueue build(Arena arena) {
            MemorySegment queuePtr = arena.allocate(ValueLayout.ADDRESS);
            vkGetDeviceQueue(device.handle(), queueFamilyIndex, queueIndex, queuePtr);
            MemorySegment queueHandle = queuePtr.get(ValueLayout.ADDRESS, 0);
            return new VkQueue(queueHandle, queueFamilyIndex);
        }
    }
}
