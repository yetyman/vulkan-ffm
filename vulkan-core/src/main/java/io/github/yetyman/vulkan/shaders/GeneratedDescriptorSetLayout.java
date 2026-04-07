package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.vulkan.VkDescriptorSetLayout;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.enums.VkDescriptorType;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;

import java.lang.foreign.Arena;

/**
 * A VkDescriptorSetLayout built from shader reflection data. Owned by its creator (ShaderInstance or DescriptorGroup).
 */
public class GeneratedDescriptorSetLayout implements AutoCloseable {
    private final ShaderLoader.DescriptorSetInfo setInfo;
    private final VkDevice device;
    private final VkShaderStageFlagBits defaultStageFlags;
    private VkDescriptorSetLayout layout;

    GeneratedDescriptorSetLayout(ShaderLoader.DescriptorSetInfo setInfo, VkDevice device, VkShaderStageFlagBits defaultStageFlags) {
        this.setInfo = setInfo;
        this.device = device;
        this.defaultStageFlags = defaultStageFlags;
    }

    /** Creates and caches the VkDescriptorSetLayout. Safe to call multiple times. */
    VkDescriptorSetLayout createLayout(Arena arena) {
        if (layout != null) return layout;
        VkDescriptorSetLayout.Builder builder = VkDescriptorSetLayout.builder().device(device);
        for (ShaderLoader.DescriptorBindingInfo binding : setInfo.getBindings().values()) {
            VkDescriptorType vkType = VkDescriptorType.fromValue(binding.getDescriptorType().value());
            int stageFlags = binding.getStageFlags() != 0 ? binding.getStageFlags() : defaultStageFlags.value();
            builder.binding(binding.getBinding(), vkType.value(), binding.getDescriptorCount(), stageFlags);
        }
        layout = builder.build(arena);
        return layout;
    }

    VkDescriptorSetLayout getLayout() {
        if (layout == null) throw new IllegalStateException("Layout not created yet — call createLayout() first");
        return layout;
    }

    ShaderLoader.DescriptorSetInfo getSetInfo() { return setInfo; }
    int getSetNumber() { return setInfo.getSetNumber(); }

    @Override
    public void close() {
        if (layout != null) {
            layout.close();
            layout = null;
        }
    }
}
