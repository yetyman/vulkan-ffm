package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.spirv.enums.SpirvReflectDescriptorType;
import io.github.yetyman.vulkan.VkImageView;
import io.github.yetyman.vulkan.VkSampler;

/**
 * Descriptor slot for a combined image sampler binding.
 */
public class TextureSlot extends DescriptorSlot {
    private VkImageView boundImageView;
    private VkSampler boundSampler;

    TextureSlot(String name, int set, int binding) {
        super(name, set, binding, SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
    }

    /** Sets the image view and sampler, marks this slot dirty. */
    public void set(VkImageView imageView, VkSampler sampler) {
        this.boundImageView = imageView;
        this.boundSampler = sampler;
        markDirty();
    }

    public VkImageView boundImageView() { return boundImageView; }
    public VkSampler boundSampler() { return boundSampler; }
}
