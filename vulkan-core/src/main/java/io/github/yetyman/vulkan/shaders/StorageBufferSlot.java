package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.spirv.enums.SpirvReflectDescriptorType;
import io.github.yetyman.vulkan.buffers.ManagedBuffer;

/**
 * Descriptor slot for a storage buffer binding.
 */
public class StorageBufferSlot extends DescriptorSlot {
    private ManagedBuffer boundBuffer;

    StorageBufferSlot(String name, int set, int binding) {
        super(name, set, binding, SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER);
    }

    /** Sets the buffer and marks this slot dirty. */
    public void set(ManagedBuffer buffer) {
        this.boundBuffer = buffer;
        markDirty();
    }

    public ManagedBuffer boundBuffer() { return boundBuffer; }
}
