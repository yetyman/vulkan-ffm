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
    private final Map<Integer, CompiledShader.GeneratedDescriptorSetLayout> layouts;
    private final Map<Integer, VkDescriptorSet> descriptorSets;
    private final VkDescriptorPool descriptorPool;
    private MemorySegment pipelineLayout = MemorySegment.NULL;

    private final List<PushConstant<?>> pushConstants = new ArrayList<>();
    private final List<DescriptorSlot> slots = new ArrayList<>();
    private final Map<String, PushConstant<?>> pushConstantsByName = new HashMap<>();
    private final Map<String, DescriptorSlot> slotsByName = new HashMap<>();

    private ShaderInstance(CompiledShader compiled, VkDevice device) {
        this.compiled = compiled;
        this.device = device;
        this.arena = Arena.ofConfined();
        this.layouts = compiled.getAllDescriptorSetLayouts(device);
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
        return new ShaderInstance(compiled, device);
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
    @SuppressWarnings("unchecked")
    public <T> StorageBufferSlot<T> getStorageBufferSlot(String name, Class<T> type) {
        ShaderLoader.DescriptorBindingInfo info = requireBinding(name,
            SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER);
        int setNumber = findSetForBinding(info);
        StorageBufferSlot<T> slot = new StorageBufferSlot<>(name, setNumber, info.getBinding());
        registerSlot(name, slot);
        return slot;
    }

    /** @return a UniformBufferSlot for the named UBO binding, validated against reflection. */
    @SuppressWarnings("unchecked")
    public <T> UniformBufferSlot<T> getUniformBufferSlot(String name, Class<T> type) {
        ShaderLoader.DescriptorBindingInfo info = requireBinding(name,
            SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
        int setNumber = findSetForBinding(info);
        UniformBufferSlot<T> slot = new UniformBufferSlot<>(name, setNumber, info.getBinding());
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
    public Map<Integer, CompiledShader.GeneratedDescriptorSetLayout> layouts() { return Collections.unmodifiableMap(layouts); }
    public Map<Integer, VkDescriptorSet> descriptorSets() { return Collections.unmodifiableMap(descriptorSets); }

    @Override
    public void close() {
        for (CompiledShader.GeneratedDescriptorSetLayout layout : layouts.values()) layout.close();
        descriptorPool.close();
        arena.close();
    }

    // ---- Private helpers ----

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
        for (Map.Entry<Integer, CompiledShader.GeneratedDescriptorSetLayout> entry : layouts.entrySet()) {
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
        // Use all shader stages for push constants — the pipeline layout defines the actual range
        int stageFlags = VkShaderStageFlagBits.VK_SHADER_STAGE_ALL.value();
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

        if (slot instanceof UniformBufferSlot<?> ubo && ubo.boundBuffer() != null) {
            descriptorSet.updateBuffer(slot.binding(),
                VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER.value(),
                ubo.boundBuffer().handle(), 0, ubo.boundBuffer().size(), flushArena);
        } else if (slot instanceof StorageBufferSlot<?> ssbo && ssbo.boundBuffer() != null) {
            descriptorSet.updateBuffer(slot.binding(),
                VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER.value(),
                ssbo.boundBuffer().handle(), 0, ssbo.boundBuffer().size(), flushArena);
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
