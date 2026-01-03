package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Wrapper for Vulkan sampler (VkSampler) with fluent builder.
 */
public class VkSampler implements AutoCloseable {
    private final MemorySegment handle;
    private final VkDevice device;
    
    private VkSampler(MemorySegment handle, VkDevice device) {
        this.handle = handle;
        this.device = device;
    }
    
    /** @return a new builder for configuring sampler creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkSampler handle */
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        Vulkan.destroySampler(device.handle(), handle);
    }
    
    public static class Builder {
        private VkDevice device;
        private int magFilter = VkFilter.VK_FILTER_LINEAR;
        private int minFilter = VkFilter.VK_FILTER_LINEAR;
        private int mipmapMode = VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR;
        private int addressModeU = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT;
        private int addressModeV = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT;
        private int addressModeW = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT;
        private float mipLodBias = 0.0f;
        private boolean anisotropyEnable = false;
        private float maxAnisotropy = 1.0f;
        private boolean compareEnable = false;
        private int compareOp = VkCompareOp.VK_COMPARE_OP_ALWAYS;
        private float minLod = 0.0f;
        private float maxLod = 0.0f;
        private int borderColor = VkBorderColor.VK_BORDER_COLOR_INT_OPAQUE_BLACK;
        private boolean unnormalizedCoordinates = false;
        
        private Builder() {}
        
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        public Builder nearest() {
            this.magFilter = VkFilter.VK_FILTER_NEAREST;
            this.minFilter = VkFilter.VK_FILTER_NEAREST;
            return this;
        }
        
        public Builder linear() {
            this.magFilter = VkFilter.VK_FILTER_LINEAR;
            this.minFilter = VkFilter.VK_FILTER_LINEAR;
            return this;
        }
        
        public Builder clampToEdge() {
            this.addressModeU = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            this.addressModeV = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            this.addressModeW = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            return this;
        }
        
        public Builder repeat() {
            this.addressModeU = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT;
            this.addressModeV = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT;
            this.addressModeW = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT;
            return this;
        }
        
        public Builder anisotropy(float maxAnisotropy) {
            this.anisotropyEnable = true;
            this.maxAnisotropy = maxAnisotropy;
            return this;
        }
        
        public VkSampler build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            
            MemorySegment samplerInfo = VkSamplerCreateInfo.allocate(arena);
            VkSamplerCreateInfo.sType(samplerInfo, VkStructureType.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);
            VkSamplerCreateInfo.magFilter(samplerInfo, magFilter);
            VkSamplerCreateInfo.minFilter(samplerInfo, minFilter);
            VkSamplerCreateInfo.addressModeU(samplerInfo, addressModeU);
            VkSamplerCreateInfo.addressModeV(samplerInfo, addressModeV);
            VkSamplerCreateInfo.addressModeW(samplerInfo, addressModeW);
            VkSamplerCreateInfo.anisotropyEnable(samplerInfo, anisotropyEnable ? 1 : 0);
            VkSamplerCreateInfo.maxAnisotropy(samplerInfo, maxAnisotropy);
            VkSamplerCreateInfo.borderColor(samplerInfo, borderColor);
            VkSamplerCreateInfo.unnormalizedCoordinates(samplerInfo, unnormalizedCoordinates ? 1 : 0);
            VkSamplerCreateInfo.compareEnable(samplerInfo, compareEnable ? 1 : 0);
            VkSamplerCreateInfo.compareOp(samplerInfo, compareOp);
            VkSamplerCreateInfo.mipmapMode(samplerInfo, mipmapMode);
            VkSamplerCreateInfo.mipLodBias(samplerInfo, mipLodBias);
            VkSamplerCreateInfo.minLod(samplerInfo, minLod);
            VkSamplerCreateInfo.maxLod(samplerInfo, maxLod);
            
            MemorySegment samplerPtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.createSampler(device.handle(), samplerInfo, samplerPtr).check();
            return new VkSampler(samplerPtr.get(ValueLayout.ADDRESS, 0), device);
        }
    }
}