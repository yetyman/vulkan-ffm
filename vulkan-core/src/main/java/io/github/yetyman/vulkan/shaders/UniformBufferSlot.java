package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.spirv.enums.SpirvReflectDescriptorType;
import io.github.yetyman.vulkan.buffers.ManagedBuffer;

/**
 * Descriptor slot for a uniform buffer binding.
 */
public class UniformBufferSlot extends DescriptorSlot {
    private ManagedBuffer boundBuffer;

    UniformBufferSlot(String name, int set, int binding) {
        super(name, set, binding, SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
    }

    /** Sets the buffer and marks this slot dirty. */
    public void set(ManagedBuffer buffer) {
        this.boundBuffer = buffer;
        markDirty();
    }

    public ManagedBuffer boundBuffer() { return boundBuffer; }
}
