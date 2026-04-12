package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.spirv.enums.SpirvReflectDescriptorType;
import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.VkDescriptorType;
import io.github.yetyman.vulkan.enums.VkImageLayout;
import io.github.yetyman.vulkan.enums.VkPipelineBindPoint;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime instance of a compiled shader. Owns descriptor sets and all parameter slots.
 * Wraps a CompiledShader and provides typed slot factory methods validated against reflection.
 */
public class ShaderInstance implements AutoCloseable {
    private final CompiledShader compiled;
    private final VkDevice device;
    private final Arena arena;
    private final Map<Integer, GeneratedDescriptorSetLayout> layouts;
    private final Map<Integer, VkDescriptorSet> descriptorSets;
    private final VkDescriptorPool descriptorPool;
    private MemorySegment pipelineLayout = MemorySegment.NULL;

    private final List<PushConstant<?>> pushConstants = new ArrayList<>();
    private final List<DescriptorSlot> slots = new ArrayList<>();
    private final Map<String, PushConstant<?>> pushConstantsByName = new HashMap<>();
    private final Map<String, DescriptorSlot> slotsByName = new HashMap<>();

    /** Specialization constant values keyed by name. Stored for pipeline builder access via buildSpecializationInfo(). */
    private final Map<String, Object> specializationValues;
    /** Preprocessor defines used to compile this instance's variant. */
    private final Map<String, String> defines;

    // ---- Builder ----

    /** @return a new Builder for configuring a ShaderInstance. */
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private CompiledShader compiled;
        private VkDevice device;
        private Map<String, String> defines = Map.of();
        private Map<String, Object> specializationValues = Map.of();

        private Builder() {}

        /** Sets the compiled shader to instantiate. */
        public Builder of(CompiledShader compiled) { this.compiled = compiled; return this; }
        /** Sets the device. */
        public Builder device(VkDevice device) { this.device = device; return this; }
        /** Sets preprocessor defines (only meaningful if compiling from source). */
        public Builder defines(Map<String, String> defines) { this.defines = defines; return this; }
        /** Sets a single specialization constant override. */
        public Builder specialize(String name, Object value) {
            Map<String, Object> m = new HashMap<>(specializationValues);
            m.put(name, value);
            this.specializationValues = m;
            return this;
        }
        /** Sets all specialization constant overrides at once. */
        public Builder specializationValues(Map<String, Object> values) { this.specializationValues = values; return this; }

        public ShaderInstance build() {
            if (compiled == null) throw new IllegalStateException("compiled shader not set");
            if (device == null)   throw new IllegalStateException("device not set");
            validateSpecializationValues(compiled.getReflection(), specializationValues);
            return new ShaderInstance(compiled, device, defines, specializationValues);
        }
    }

    private ShaderInstance(CompiledShader compiled, VkDevice device,
                           Map<String, String> defines, Map<String, Object> specializationValues) {
        this.compiled = compiled;
        this.device = device;
        this.defines = Collections.unmodifiableMap(new HashMap<>(defines));
        this.specializationValues = Collections.unmodifiableMap(new HashMap<>(specializationValues));
        this.arena = Arena.ofConfined();
        this.layouts = buildLayouts();
        this.descriptorPool = buildPool();
        this.descriptorSets = allocateDescriptorSets();
    }

    /** Compiles the shader at the given resource path and creates a ShaderInstance. */
    public static ShaderInstance from(String resourcePath) {
        throw new UnsupportedOperationException("ShaderInstance.from() requires a VkDevice — use from(resourcePath, device)");
    }

    /** @return a new ShaderInstance for the given shader resource path and device. */
    public static ShaderInstance from(String resourcePath, VkDevice device) {
        CompiledShader compiled = ShaderLoader.compileShader(resourcePath);
        return new ShaderInstance(compiled, device, Map.of(), Map.of());
    }

    /** @return a new ShaderInstance wrapping an already-compiled shader. */
    public static ShaderInstance from(CompiledShader compiled, VkDevice device) {
        return new ShaderInstance(compiled, device, Map.of(), Map.of());
    }

    /** @return a new ShaderInstance wrapping an already-compiled shader with specialization constants. */
    public static ShaderInstance from(CompiledShader compiled, VkDevice device, Map<String, Object> specializationValues) {
        validateSpecializationValues(compiled.getReflection(), specializationValues);
        return new ShaderInstance(compiled, device, Map.of(), specializationValues);
    }

    /**
     * @return a new ShaderInstance compiled with the given preprocessor defines.
     * Each unique define set produces a distinct compiled variant.
     */
    public static ShaderInstance from(String resourcePath, VkDevice device, Map<String, String> defines) {
        CompiledShader compiled = ShaderLoader.compileShader(resourcePath, defines);
        return new ShaderInstance(compiled, device, defines, Map.of());
    }

    /**
     * @return a new ShaderInstance with Vulkan specialization constants injected at pipeline creation time.
     * Values are stored and exposed via buildSpecializationInfo(Arena) for use in pipeline builders.
     */
    public static ShaderInstance from(String resourcePath, VkDevice device, Map<String, String> defines,
                                      Map<String, Object> specializationValues) {
        CompiledShader compiled = ShaderLoader.compileShader(resourcePath, defines);
        validateSpecializationValues(compiled.getReflection(), specializationValues);
        return new ShaderInstance(compiled, device, defines, specializationValues);
    }

    private static void validateSpecializationValues(ShaderLoader.ShaderReflection reflection,
                                                     Map<String, Object> values) {
        for (String name : values.keySet()) {
            if (reflection.getSpecializationConstant(name) == null)
                throw new IllegalArgumentException("Shader has no specialization constant named '" + name + "'");
        }
    }

    /**
     * Sets the pipeline layout handle used for push constant and descriptor bind commands.
     * Must be called before flush() if the shader has push constants or descriptor sets.
     */
    public ShaderInstance pipelineLayout(MemorySegment pipelineLayout) {
        this.pipelineLayout = pipelineLayout;
        return this;
    }

    // ---- Slot factory methods ----

    /** @return a PushConstant for the named push constant member, validated against reflection. */
    @SuppressWarnings("unchecked")
    public <T> PushConstant<T> getPushConstant(String name, Class<T> type) {
        ShaderLoader.StructMemberInfo member = compiled.getReflection().getPushConstantMember(name);
        if (member == null)
            throw new IllegalArgumentException("Shader has no push constant member named '" + name + "'");
        PushConstant<T> pc = new PushConstant<>(name, member.offset(), member.size());
        pushConstants.add(pc);
        pushConstantsByName.put(name, pc);
        return pc;
    }

    /** @return a StorageBufferSlot for the named SSBO binding, validated against reflection. */
    public StorageBufferSlot getStorageBufferSlot(String name) {
        ShaderLoader.DescriptorBindingInfo info = requireBinding(name,
            SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER);
        int setNumber = findSetForBinding(info);
        StorageBufferSlot slot = new StorageBufferSlot(name, setNumber, info.getBinding());
        registerSlot(name, slot);
        return slot;
    }

    /** @return a UniformBufferSlot for the named UBO binding, validated against reflection. */
    public UniformBufferSlot getUniformBufferSlot(String name) {
        ShaderLoader.DescriptorBindingInfo info = requireBinding(name,
            SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
        int setNumber = findSetForBinding(info);
        UniformBufferSlot slot = new UniformBufferSlot(name, setNumber, info.getBinding());
        registerSlot(name, slot);
        return slot;
    }

    /** @return a TextureSlot for the named combined image sampler binding, validated against reflection. */
    public TextureSlot getTextureSlot(String name) {
        ShaderLoader.DescriptorBindingInfo info = requireBinding(name,
            SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        int setNumber = findSetForBinding(info);
        TextureSlot slot = new TextureSlot(name, setNumber, info.getBinding());
        registerSlot(name, slot);
        return slot;
    }

    // ---- Flush ----

    /**
     * Writes all dirty push constants and descriptor slots into the command buffer.
     * Clears dirty flags after writing.
     */
    public void flush(VkCommandBuffer commandBuffer) {
        flush(commandBuffer.handle());
    }

    public void flush(MemorySegment commandBuffer) {
        try (Arena flushArena = Arena.ofConfined()) {
            for (PushConstant<?> pc : pushConstants) {
                if (!pc.isDirty()) continue;
                writePushConstant(commandBuffer, pc, flushArena);
                pc.clearDirty();
            }
            for (DescriptorSlot slot : slots) {
                if (!slot.isDirty()) continue;
                writeDescriptorSlot(slot, flushArena);
                slot.clearDirty();
            }
            bindDescriptorSets(commandBuffer, flushArena);
        }
    }

    // ---- Accessors ----

    public CompiledShader compiled() { return compiled; }
    public VkDevice device() { return device; }
    public io.github.yetyman.shaderc.enums.ShadercShaderKind shaderKind() { return compiled.getShaderKind(); }
    public Map<Integer, GeneratedDescriptorSetLayout> layouts() { return Collections.unmodifiableMap(layouts); }
    public Map<Integer, VkDescriptorSet> descriptorSets() { return Collections.unmodifiableMap(descriptorSets); }

    /** @return the VkDescriptorSetLayout handle for the given set number, for use in pipeline builders. */
    public MemorySegment layoutHandle(int setNumber) {
        GeneratedDescriptorSetLayout layout = layouts.get(setNumber);
        if (layout == null) throw new IllegalArgumentException("No layout for set " + setNumber);
        return layout.getLayout().handle();
    }
    /** @return the preprocessor defines this instance was compiled with. */
    public Map<String, String> defines() { return defines; }
    /** @return the specialization constant values set at construction, keyed by name. */
    public Map<String, Object> specializationValues() { return specializationValues; }

    /** @return the descriptor binding info for the named binding, or null if not found. */
    public ShaderLoader.DescriptorBindingInfo getBindingByName(String name) {
        return compiled.getReflection().getBindingByName(name);
    }

    /** @return the push constant member info for the named member, or null if not found. */
    public ShaderLoader.StructMemberInfo getPushConstantMember(String name) {
        return compiled.getReflection().getPushConstantMember(name);
    }

    /** @return the specialization constant info for the named constant, or null if not found. */
    public ShaderLoader.SpecializationConstantInfo getSpecializationConstantInfo(String name) {
        return compiled.getReflection().getSpecializationConstant(name);
    }

    /**
     * @return the value of the named specialization constant as set at construction,
     * or the reflected default if not overridden, or null if the constant doesn't exist.
     */
    public Object getSpecializationConstant(String name) {
        if (specializationValues.containsKey(name)) return specializationValues.get(name);
        ShaderLoader.SpecializationConstantInfo info = compiled.getReflection().getSpecializationConstant(name);
        if (info == null) return null;
        if (info.isBool())  return info.defaultBool();
        if (info.isFloat()) return info.defaultFloat();
        return info.defaultInt();
    }

    /**
     * Builds a VkSpecializationInfo native struct for use in pipeline creation.
     * The returned segment is allocated from the given arena and valid for its lifetime.
     * Returns MemorySegment.NULL if there are no specialization constants in this shader.
     */
    public MemorySegment buildSpecializationInfo(Arena specArena) {
        List<ShaderLoader.SpecializationConstantInfo> constants = compiled.getReflection().getSpecializationConstants();
        if (constants.isEmpty()) return MemorySegment.NULL;

        int count = constants.size();
        // VkSpecializationMapEntry: uint32_t constantID, uint32_t offset, size_t size — 16 bytes each
        long entrySize = 16L;
        MemorySegment mapEntries = specArena.allocate(entrySize * count);
        MemorySegment data = specArena.allocate(ValueLayout.JAVA_INT, count); // all spec constants are 32-bit

        for (int i = 0; i < count; i++) {
            ShaderLoader.SpecializationConstantInfo info = constants.get(i);
            Object value = specializationValues.getOrDefault(info.name(),
                info.isBool() ? info.defaultBool() : info.isFloat() ? info.defaultFloat() : info.defaultInt());
            int bits;
            if (value instanceof Boolean b)  bits = b ? 1 : 0;
            else if (value instanceof Float f) bits = Float.floatToRawIntBits(f);
            else if (value instanceof Integer iv) bits = iv;
            else bits = info.defaultValueBits();

            data.setAtIndex(ValueLayout.JAVA_INT, i, bits);

            long entryOffset = entrySize * i;
            mapEntries.set(ValueLayout.JAVA_INT,  entryOffset,      info.constantId());   // constantID
            mapEntries.set(ValueLayout.JAVA_INT,  entryOffset + 4,  (int)(i * 4L));       // offset into data
            mapEntries.set(ValueLayout.JAVA_LONG, entryOffset + 8,  4L);                  // size
        }

        // VkSpecializationInfo: uint32_t mapEntryCount, VkSpecializationMapEntry* pMapEntries,
        //                       size_t dataSize, void* pData — 32 bytes
        MemorySegment specInfo = specArena.allocate(32);
        specInfo.set(ValueLayout.JAVA_INT,     0,  count);
        specInfo.set(ValueLayout.ADDRESS,      8,  mapEntries);
        specInfo.set(ValueLayout.JAVA_LONG,    16, (long)(count * 4));
        specInfo.set(ValueLayout.ADDRESS,      24, data);
        return specInfo;
    }

    @Override
    public void close() {
        for (GeneratedDescriptorSetLayout layout : layouts.values()) layout.close();
        descriptorPool.close();
        arena.close();
    }

    // ---- Private helpers ----

    private Map<Integer, GeneratedDescriptorSetLayout> buildLayouts() {
        Map<Integer, GeneratedDescriptorSetLayout> result = new HashMap<>();
        for (int setNumber : compiled.getReflection().getSetNumbers()) {
            ShaderLoader.DescriptorSetInfo setInfo = compiled.getReflection().getDescriptorSet(setNumber);
            result.put(setNumber, new GeneratedDescriptorSetLayout(setInfo, device, compiled.getVkShaderStage()));
        }
        return result;
    }

    private ShaderLoader.DescriptorBindingInfo requireBinding(String name, SpirvReflectDescriptorType expectedType) {
        ShaderLoader.DescriptorBindingInfo info = compiled.getReflection().getBindingByName(name);
        if (info == null)
            throw new IllegalArgumentException("Shader has no descriptor binding named '" + name + "'");
        if (!info.getDescriptorType().equals(expectedType))
            throw new IllegalArgumentException("Binding '" + name + "' is " + info.getDescriptorType() + ", expected " + expectedType);
        return info;
    }

    private int findSetForBinding(ShaderLoader.DescriptorBindingInfo info) {
        for (Map.Entry<Integer, ShaderLoader.DescriptorSetInfo> entry : compiled.getReflection().getDescriptorSets().entrySet()) {
            if (entry.getValue().getBindings().containsValue(info)) return entry.getKey();
        }
        return 0;
    }

    private void registerSlot(String name, DescriptorSlot slot) {
        slots.add(slot);
        slotsByName.put(name, slot);
    }

    private VkDescriptorPool buildPool() {
        Map<Integer, Integer> typeCounts = new HashMap<>();
        for (ShaderLoader.DescriptorSetInfo setInfo : compiled.getReflection().getDescriptorSets().values()) {
            for (ShaderLoader.DescriptorBindingInfo b : setInfo.getBindings().values()) {
                int typeVal = b.getDescriptorType().value();
                typeCounts.merge(typeVal, b.getDescriptorCount(), Integer::sum);
            }
        }
        if (typeCounts.isEmpty()) {
            return VkDescriptorPool.builder().device(device).maxSets(1)
                .poolSize(VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER.value(), 1)
                .build(arena);
        }
        VkDescriptorPool.Builder builder = VkDescriptorPool.builder().device(device)
            .maxSets(Math.max(1, compiled.getReflection().getSetNumbers().size()));
        for (Map.Entry<Integer, Integer> e : typeCounts.entrySet()) {
            builder.poolSize(e.getKey(), e.getValue());
        }
        return builder.build(arena);
    }

    private Map<Integer, VkDescriptorSet> allocateDescriptorSets() {
        Map<Integer, VkDescriptorSet> sets = new HashMap<>();
        for (Map.Entry<Integer, GeneratedDescriptorSetLayout> entry : layouts.entrySet()) {
            VkDescriptorSetLayout layout = entry.getValue().createLayout(arena);
            VkDescriptorSet set = descriptorPool.allocateDescriptorSet(layout);
            sets.put(entry.getKey(), set);
        }
        return sets;
    }

    private void writePushConstant(MemorySegment commandBuffer, PushConstant<?> pc, Arena flushArena) {
        if (pipelineLayout.equals(MemorySegment.NULL)) return;
        Object value = pc.pendingValue();
        if (value == null) return;
        MemorySegment data = serializePushConstant(value, pc.size(), flushArena);
        if (data == null) return;
        int stageFlags = compiled.getShaderStageFlags();
        Vulkan.cmdPushConstants(commandBuffer, pipelineLayout, stageFlags, pc.offset(), pc.size(), data);
    }

    private MemorySegment serializePushConstant(Object value, int size, Arena arena) {
        if (value instanceof Float f) {
            MemorySegment seg = arena.allocate(ValueLayout.JAVA_FLOAT);
            seg.set(ValueLayout.JAVA_FLOAT, 0, f);
            return seg;
        }
        if (value instanceof Integer i) {
            MemorySegment seg = arena.allocate(ValueLayout.JAVA_INT);
            seg.set(ValueLayout.JAVA_INT, 0, i);
            return seg;
        }
        if (value instanceof Long l) {
            MemorySegment seg = arena.allocate(ValueLayout.JAVA_LONG);
            seg.set(ValueLayout.JAVA_LONG, 0, l);
            return seg;
        }
        if (value instanceof float[] fa) {
            MemorySegment seg = arena.allocate(ValueLayout.JAVA_FLOAT, fa.length);
            for (int i = 0; i < fa.length; i++) seg.setAtIndex(ValueLayout.JAVA_FLOAT, i, fa[i]);
            return seg;
        }
        if (value instanceof MemorySegment ms) {
            return ms;
        }
        // Unknown type — caller is responsible for providing a MemorySegment for complex types
        return null;
    }

    private void writeDescriptorSlot(DescriptorSlot slot, Arena flushArena) {
        VkDescriptorSet descriptorSet = descriptorSets.get(slot.set());
        if (descriptorSet == null) return;

        if (slot instanceof UniformBufferSlot ubo && ubo.buffer() != null) {
            descriptorSet.updateBuffer(slot.binding(),
                VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER.value(),
                ubo.buffer().handle(), 0, ubo.buffer().size(), flushArena);
        } else if (slot instanceof StorageBufferSlot ssbo && ssbo.buffer() != null) {
            descriptorSet.updateBuffer(slot.binding(),
                VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER.value(),
                ssbo.buffer().handle(), 0, ssbo.buffer().size(), flushArena);
        } else if (slot instanceof TextureSlot tex && tex.boundImageView() != null && tex.boundSampler() != null) {
            descriptorSet.updateImageSampler(slot.binding(),
                tex.boundSampler().handle(), tex.boundImageView().handle(),
                VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), flushArena);
        }
    }

    private void bindDescriptorSets(MemorySegment commandBuffer, Arena flushArena) {
        if (pipelineLayout.equals(MemorySegment.NULL) || descriptorSets.isEmpty()) return;
        for (Map.Entry<Integer, VkDescriptorSet> entry : descriptorSets.entrySet()) {
            entry.getValue().bind(commandBuffer,
                VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(),
                pipelineLayout, entry.getKey(), flushArena);
        }
    }
}
