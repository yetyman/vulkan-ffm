package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.spirv.enums.SpirvReflectDescriptorType;

/**
 * Tracks a single descriptor binding with a dirty flag.
 * Subclasses hold the specific bound resource and are flushed via
 * vkUpdateDescriptorSets + vkCmdBindDescriptorSets in ShaderInstance.flush().
 */
public abstract class DescriptorSlot {
    private final String name;
    private final int set;
    private final int binding;
    private final SpirvReflectDescriptorType descriptorType;
    private boolean dirty;

    protected DescriptorSlot(String name, int set, int binding, SpirvReflectDescriptorType descriptorType) {
        this.name = name;
        this.set = set;
        this.binding = binding;
        this.descriptorType = descriptorType;
    }

    public String name() {
        return name;
    }

    public int set() {
        return set;
    }

    public int binding() {
        return binding;
    }

    public SpirvReflectDescriptorType descriptorType() {
        return descriptorType;
    }

    public boolean isDirty() {
        return dirty;
    }

    protected void markDirty() {
        dirty = true;
    }

    void clearDirty() {
        dirty = false;
    }
}
