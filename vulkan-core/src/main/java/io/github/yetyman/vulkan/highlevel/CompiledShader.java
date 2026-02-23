package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.VkDescriptorSetLayout;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkShaderModule;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.shaderc.enums.*;
import io.github.yetyman.spirv.enums.SpirvReflectDescriptorType;
import java.lang.foreign.Arena;
import java.util.*;

import static io.github.yetyman.vulkan.enums.VkDescriptorType.*;

/**
 * A compiled shader with SPIR-V bytecode, reflection data, and descriptor set layout generation.
 */
public class CompiledShader implements AutoCloseable {
    private static final Map<Integer, VkShaderStageFlagBits> SHADER_KIND_TO_STAGE;
    static {
        Map<Integer, VkShaderStageFlagBits> m = new HashMap<>();
        m.put(ShadercShaderKind.shaderc_vertex_shader.value(),        VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT);
        m.put(ShadercShaderKind.shaderc_fragment_shader.value(),      VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT);
        m.put(ShadercShaderKind.shaderc_compute_shader.value(),       VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT);
        m.put(ShadercShaderKind.shaderc_geometry_shader.value(),      VkShaderStageFlagBits.VK_SHADER_STAGE_GEOMETRY_BIT);
        m.put(ShadercShaderKind.shaderc_tess_control_shader.value(),  VkShaderStageFlagBits.VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT);
        m.put(ShadercShaderKind.shaderc_tess_evaluation_shader.value(), VkShaderStageFlagBits.VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT);
        SHADER_KIND_TO_STAGE = Collections.unmodifiableMap(m);
    }

    private final byte[] spirv;
    private final ShaderLoader.ShaderReflection reflection;
    private final ShadercShaderKind shaderKind;
    private final Map<Integer, GeneratedDescriptorSetLayout> generatedLayouts = new HashMap<>();

    public CompiledShader(byte[] spirv, ShaderLoader.ShaderReflection reflection, ShadercShaderKind shaderKind) {
        this.spirv = spirv;
        this.reflection = reflection;
        this.shaderKind = shaderKind;
    }

    public byte[] getSpirV() { return spirv.clone(); }
    public ShaderLoader.ShaderReflection getReflection() { return reflection; }
    public ShadercShaderKind getShaderKind() { return shaderKind; }

    /** Creates a VkShaderModule from the compiled SPIR-V */
    public VkShaderModule createShaderModule(VkDevice device, Arena arena) {
        return VkShaderModule.create(arena, device, spirv);
    }

    /** Gets or creates a descriptor set layout for the specified set number */
    public GeneratedDescriptorSetLayout getDescriptorSetLayout(int setNumber, VkDevice device) {
        return generatedLayouts.computeIfAbsent(setNumber, set -> {
            ShaderLoader.DescriptorSetInfo setInfo = reflection.getDescriptorSet(set);
            if (setInfo == null) {
                throw new IllegalArgumentException("Shader does not have descriptor set " + set);
            }
            return new GeneratedDescriptorSetLayout(setInfo, device, getVkShaderStage());
        });
    }

    /** Gets all descriptor set layouts used by this shader */
    public Map<Integer, GeneratedDescriptorSetLayout> getAllDescriptorSetLayouts(VkDevice device) {
        Map<Integer, GeneratedDescriptorSetLayout> layouts = new HashMap<>();
        for (int setNumber : reflection.getSetNumbers()) {
            layouts.put(setNumber, getDescriptorSetLayout(setNumber, device));
        }
        return layouts;
    }

    @Override
    public void close() {
        for (GeneratedDescriptorSetLayout layout : generatedLayouts.values()) {
            layout.close();
        }
        generatedLayouts.clear();
    }

    private VkShaderStageFlagBits getVkShaderStage() {
        return SHADER_KIND_TO_STAGE.getOrDefault(shaderKind.value(), VkShaderStageFlagBits.VK_SHADER_STAGE_ALL);
    }

    /**
     * A descriptor set layout generated from shader reflection with methods to create descriptor sets.
     */
    public static class GeneratedDescriptorSetLayout implements AutoCloseable {
        private final ShaderLoader.DescriptorSetInfo setInfo;
        private final VkDevice device;
        private final VkShaderStageFlagBits defaultStageFlags;
        private VkDescriptorSetLayout layout;

        public GeneratedDescriptorSetLayout(ShaderLoader.DescriptorSetInfo setInfo, VkDevice device, VkShaderStageFlagBits defaultStageFlags) {
            this.setInfo = setInfo;
            this.device = device;
            this.defaultStageFlags = defaultStageFlags;
        }

        /** Creates the VkDescriptorSetLayout */
        public VkDescriptorSetLayout createLayout(Arena arena) {
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

        /** Gets the existing layout or throws if not created yet */
        public VkDescriptorSetLayout getLayout() {
            if (layout == null) throw new IllegalStateException("Layout not created yet - call createLayout() first");
            return layout;
        }

        /** Adds a manual binding that wasn't in the shader reflection */
        public GeneratedDescriptorSetLayout addBinding(int binding, VkDescriptorType type, VkShaderStageFlagBits stages, int count) {
            if (layout != null) throw new IllegalStateException("Cannot add bindings after layout is created");
            SpirvReflectDescriptorType spirvType = SpirvReflectDescriptorType.fromValue(type.value());
            setInfo.addBinding(binding, new ShaderLoader.DescriptorBindingInfo(binding, spirvType, count, stages.value()));
            return this;
        }

        /** Adds a uniform buffer binding */
        public GeneratedDescriptorSetLayout addUniformBuffer(int binding, VkShaderStageFlagBits stages) {
            return addBinding(binding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, stages, 1);
        }

        /** Adds a combined image sampler binding */
        public GeneratedDescriptorSetLayout addTexture(int binding, VkShaderStageFlagBits stages) {
            return addBinding(binding, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, stages, 1);
        }

        /** Adds a storage buffer binding */
        public GeneratedDescriptorSetLayout addStorageBuffer(int binding, VkShaderStageFlagBits stages) {
            return addBinding(binding, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, stages, 1);
        }

        @Override
        public void close() {
            if (layout != null) {
                layout.close();
                layout = null;
            }
        }

        public ShaderLoader.DescriptorSetInfo getSetInfo() { return setInfo; }
        public int getSetNumber() { return setInfo.getSetNumber(); }
    }
}
