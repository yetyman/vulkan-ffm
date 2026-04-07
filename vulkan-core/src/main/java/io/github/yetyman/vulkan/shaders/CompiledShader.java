package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.vulkan.VkDescriptorSetLayout;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkShaderModule;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.shaderc.enums.*;

import java.lang.foreign.Arena;
import java.util.*;

/**
 * A compiled shader: SPIR-V bytecode plus reflection data. Device-agnostic and immutable.
 */
public class CompiledShader {
    static final Map<ShadercShaderKind, VkShaderStageFlagBits> SHADER_KIND_TO_STAGE;
    static {
        SHADER_KIND_TO_STAGE = Map.of(
                ShadercShaderKind.shaderc_vertex_shader, VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT, 
                ShadercShaderKind.shaderc_fragment_shader, VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT, 
                ShadercShaderKind.shaderc_compute_shader, VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT, 
                ShadercShaderKind.shaderc_geometry_shader, VkShaderStageFlagBits.VK_SHADER_STAGE_GEOMETRY_BIT, 
                ShadercShaderKind.shaderc_tess_control_shader, VkShaderStageFlagBits.VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT, 
                ShadercShaderKind.shaderc_tess_evaluation_shader, VkShaderStageFlagBits.VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT
        );
    }

    private final byte[] spirv;
    private final ShaderLoader.ShaderReflection reflection;
    private final ShadercShaderKind shaderKind;
    private final String name;
    private final Map<String, String> defines;

    public CompiledShader(byte[] spirv, ShaderLoader.ShaderReflection reflection, ShadercShaderKind shaderKind) {
        this(spirv, reflection, shaderKind, null, Map.of());
    }

    public CompiledShader(byte[] spirv, ShaderLoader.ShaderReflection reflection, ShadercShaderKind shaderKind, String name) {
        this(spirv, reflection, shaderKind, name, Map.of());
    }

    public CompiledShader(byte[] spirv, ShaderLoader.ShaderReflection reflection, ShadercShaderKind shaderKind, String name, Map<String, String> defines) {
        this.spirv = spirv;
        this.reflection = reflection;
        this.shaderKind = shaderKind;
        this.name = name;
        this.defines = Collections.unmodifiableMap(new HashMap<>(defines));
    }

    public byte[] getSpirV() { return spirv.clone(); }
    public ShaderLoader.ShaderReflection getReflection() { return reflection; }
    public ShadercShaderKind getShaderKind() { return shaderKind; }
    /** @return the VkShaderStageFlagBits int value for this shader's stage. */
    public int getShaderStageFlags() { return getVkShaderStage().value(); }
    /** @return the logical name set at build time, or null if not set. */
    public String getName() { return name; }
    /** @return the preprocessor defines this shader was compiled with, empty if none. */
    public Map<String, String> getDefines() { return defines; }

    /** @return a new ShaderInstance for this compiled shader on the given device. */
    public ShaderInstance createInstance(VkDevice device) {
        return ShaderInstance.builder().of(this).device(device).build();
    }

    /** @return a Builder pre-configured with this compiled shader. */
    public ShaderInstance.Builder instanceBuilder(VkDevice device) {
        return ShaderInstance.builder().of(this).device(device);
    }

    /** Creates a VkShaderModule from the compiled SPIR-V. */
    public VkShaderModule createShaderModule(VkDevice device, Arena arena) {
        return VkShaderModule.create(arena, device, spirv);
    }

    /**
     * Creates a new VkDescriptorSetLayout for the given device and set number.
     */
    public VkDescriptorSetLayout getLayout(VkDevice device, int setNumber, Arena arena) {
        ShaderLoader.DescriptorSetInfo setInfo = reflection.getDescriptorSet(setNumber);
        if (setInfo == null) throw new IllegalArgumentException("Shader has no descriptor set " + setNumber);
        return new GeneratedDescriptorSetLayout(setInfo, device, getVkShaderStage()).createLayout(arena);
    }

    /** @return a DescriptorGroup builder pre-wired with this shader's reflected layout (set 0). */
    public DescriptorGroup.Builder descriptorGroup(VkDevice device) {
        return descriptorGroup(device, 0);
    }

    /** @return a DescriptorGroup builder pre-wired with this shader's reflected layout for the given set. */
    public DescriptorGroup.Builder descriptorGroup(VkDevice device, int setNumber) {
        return DescriptorGroup.builder().device(device).reflection(this, setNumber);
    }

    public VkShaderStageFlagBits getVkShaderStage() {
        return SHADER_KIND_TO_STAGE.getOrDefault(shaderKind, VkShaderStageFlagBits.VK_SHADER_STAGE_ALL);
    }
}
