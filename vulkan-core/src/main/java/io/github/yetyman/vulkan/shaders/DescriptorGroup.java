package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.enums.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bundles a descriptor set layout, pool, and allocated set with buffer bindings.
 * Infers pool sizes from the layout automatically.
 *
 * <pre>{@code
 * try (DescriptorGroup group = DescriptorGroup.builder()
 *         .device(device)
 *         .storageBuffer(0, inputBuf)
 *         .storageBuffer(1, outputBuf)
 *         .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
 *         .build(arena)) {
 *     // group.layout(), group.set() ready to use
 * }
 * }</pre>
 */
public class DescriptorGroup implements AutoCloseable {
    private final VkDescriptorSetLayout layout;
    private final VkDescriptorPool pool;
    private final VkDescriptorSet set;
    private final boolean ownsLayout;

    private DescriptorGroup(VkDescriptorSetLayout layout, VkDescriptorPool pool, VkDescriptorSet set, boolean ownsLayout) {
        this.layout = layout;
        this.pool = pool;
        this.set = set;
        this.ownsLayout = ownsLayout;
    }

    public VkDescriptorSetLayout layout() {
        return layout;
    }

    public VkDescriptorPool pool() {
        return pool;
    }

    public VkDescriptorSet set() {
        return set;
    }

    /**
     * Shortcut: layout handle for pipeline builders.
     */
    public MemorySegment layoutHandle() {
        return layout.handle();
    }

    @Override
    public void close() {
        pool.close();
        if (ownsLayout) layout.close();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private VkDevice device;
        private int defaultStageFlags = VkShaderStageFlagBits.VK_SHADER_STAGE_ALL.value();
        private final List<BindingEntry> bindings = new ArrayList<>();
        private VkDescriptorSetLayout prebuiltLayout;
        private CompiledShader reflectionSource;
        private int reflectionSetNumber;
        private boolean ownsLayout = true;

        private Builder() {
        }

        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }

        /**
         * Sets default stage flags for all bindings that don't specify their own.
         */
        public Builder stageFlags(int flags) {
            this.defaultStageFlags = flags;
            return this;
        }

        /**
         * Uses a reflected descriptor set layout from a CompiledShader (set 0).
         */
        public Builder reflection(CompiledShader compiled) {
            return reflection(compiled, 0);
        }

        /**
         * Uses a reflected descriptor set layout from a CompiledShader for the given set number.
         */
        public Builder reflection(CompiledShader compiled, int setNumber) {
            ShaderLoader.DescriptorSetInfo setInfo = compiled.getReflection().getDescriptorSet(setNumber);
            if (setInfo == null) throw new IllegalArgumentException("Shader has no descriptor set " + setNumber);
            for (var binding : setInfo.getBindings().values()) {
                VkDescriptorType vkType = VkDescriptorType.fromValue(binding.getDescriptorType().value());
                bindings.add(new BindingEntry(binding.getName(), binding.getBinding(), vkType,
                        binding.getStageFlags(), binding.getDescriptorCount(), null, null));
            }
            this.reflectionSource = compiled;
            this.reflectionSetNumber = setNumber;
            this.ownsLayout = false;
            return this;
        }

        /**
         * Uses a pre-built descriptor set layout (caller retains ownership).
         */
        public Builder layout(VkDescriptorSetLayout layout) {
            this.prebuiltLayout = layout;
            this.ownsLayout = false;
            return this;
        }

        /**
         * Adds a storage buffer binding and immediately binds the buffer.
         */
        public Builder storageBuffer(int binding, ManagedBuffer buffer) {
            return bufferBinding(null, binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, -1, 1, buffer);
        }

        /**
         * Adds a storage buffer binding with explicit stage flags.
         */
        public Builder storageBuffer(int binding, ManagedBuffer buffer, int stageFlags) {
            return bufferBinding(null, binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, stageFlags, 1, buffer);
        }

        /**
         * Adds a uniform buffer binding and immediately binds the buffer.
         */
        public Builder uniformBuffer(int binding, ManagedBuffer buffer) {
            return bufferBinding(null, binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, -1, 1, buffer);
        }

        /**
         * Adds a uniform buffer binding with explicit stage flags.
         */
        public Builder uniformBuffer(int binding, ManagedBuffer buffer, int stageFlags) {
            return bufferBinding(null, binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, stageFlags, 1, buffer);
        }

        /**
         * Binds a buffer by shader variable name, inferring binding index and type from reflection. Only works with reflection().
         */
        public Builder buffer(String name, ManagedBuffer buffer) {
            for (int i = 0; i < bindings.size(); i++) {
                if (name.equals(bindings.get(i).name)) {
                    BindingEntry old = bindings.get(i);
                    bindings.set(i, new BindingEntry(old.name, old.binding, old.type, old.stageFlags, old.descriptorCount, buffer, null));
                    return this;
                }
            }
            throw new IllegalArgumentException("No reflected binding named '" + name + "' — use storageBuffer()/uniformBuffer() for manual layouts");
        }

        /**
         * Binds a buffer to a binding index, inferring descriptor type from reflection. Only works with reflection().
         */
        public Builder buffer(int binding, ManagedBuffer buffer) {
            for (int i = 0; i < bindings.size(); i++) {
                if (bindings.get(i).binding == binding) {
                    BindingEntry old = bindings.get(i);
                    bindings.set(i, new BindingEntry(old.name, old.binding, old.type, old.stageFlags, old.descriptorCount, buffer, null));
                    return this;
                }
            }
            throw new IllegalArgumentException("No reflected binding at index " + binding + " — use storageBuffer()/uniformBuffer() for non-reflected layouts");
        }

        private Builder bufferBinding(String name, int binding, VkDescriptorType type, int stageFlags, int descriptorCount, ManagedBuffer buffer) {
            for (int i = 0; i < bindings.size(); i++) {
                if (bindings.get(i).binding == binding) {
                    BindingEntry old = bindings.get(i);
                    bindings.set(i, new BindingEntry(
                            old.name != null ? old.name : name, binding, old.type,
                            stageFlags >= 0 ? stageFlags : old.stageFlags, old.descriptorCount, buffer, null));
                    return this;
                }
            }
            bindings.add(new BindingEntry(name, binding, type, stageFlags, descriptorCount, buffer, null));
            return this;
        }

        /**
         * Adds a combined image sampler binding.
         */
        public Builder combinedImageSampler(int binding, ImageBinding imageBinding) {
            bindings.add(new BindingEntry(null, binding,
                    VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, -1, 1, null, imageBinding));
            return this;
        }

        /**
         * Adds a raw binding with no resource attached (for manual update later).
         */
        public Builder binding(int bindingIndex, VkDescriptorType type) {
            bindings.add(new BindingEntry(null, bindingIndex, type, -1, 1, null, null));
            return this;
        }

        public DescriptorGroup build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");

            // Resolve layout
            VkDescriptorSetLayout layout;
            if (reflectionSource != null) {
                layout = reflectionSource.getLayout(device, reflectionSetNumber, arena);
            } else if (prebuiltLayout != null) {
                layout = prebuiltLayout;
            } else {
                if (bindings.isEmpty()) throw new IllegalStateException("no bindings defined");
                VkDescriptorSetLayout.Builder layoutBuilder = VkDescriptorSetLayout.builder().device(device);
                for (BindingEntry e : bindings) {
                    int stageFlags = e.stageFlags >= 0 ? e.stageFlags : defaultStageFlags;
                    layoutBuilder.binding(e.binding, e.type.value(), e.descriptorCount, stageFlags);
                }
                layout = layoutBuilder.build(arena);
                ownsLayout = true;
            }

            // Infer pool sizes from bindings
            Map<Integer, Integer> typeCounts = new HashMap<>();
            for (BindingEntry e : bindings) {
                typeCounts.merge(e.type.value(), 1, Integer::sum);
            }
            if (typeCounts.isEmpty()) {
                typeCounts.put(VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER.value(), 1);
            }

            // Build pool
            VkDescriptorPool.Builder poolBuilder = VkDescriptorPool.builder().device(device).maxSets(1);
            for (var entry : typeCounts.entrySet()) {
                poolBuilder.poolSize(entry.getKey(), entry.getValue());
            }
            VkDescriptorPool pool = poolBuilder.build(arena);

            // Allocate set
            VkDescriptorSet set = pool.allocateDescriptorSet(layout);

            // Bind buffers
            for (BindingEntry e : bindings) {
                if (e.buffer != null) {
                    set.updateBuffer(e.binding, e.type.value(),
                            e.buffer.handle(), 0, e.buffer.size(), arena);
                } else if (e.imageBinding != null) {
                    set.updateImageSampler(e.binding, e.imageBinding.sampler(),
                            e.imageBinding.imageView(), e.imageBinding.imageLayout(), arena);
                }
            }

            return new DescriptorGroup(layout, pool, set, ownsLayout);
        }

        private record BindingEntry(String name, int binding, VkDescriptorType type, int stageFlags,
                                    int descriptorCount, ManagedBuffer buffer, ImageBinding imageBinding) {
        }
    }

    /**
     * Image binding info for combined image samplers.
     */
    public record ImageBinding(MemorySegment sampler, MemorySegment imageView, int imageLayout) {
    }
}
