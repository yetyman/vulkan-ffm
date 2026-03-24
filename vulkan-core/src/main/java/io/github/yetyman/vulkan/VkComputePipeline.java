package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import io.github.yetyman.vulkan.highlevel.VkTransientCommandBuffer;
import java.lang.foreign.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Wrapper for Vulkan compute pipeline with automatic resource management.
 * A compute pipeline binds a single compute shader stage and its pipeline layout.
 *
 * Example usage:
 * <pre>{@code
 * try (VkComputePipeline pipeline = VkComputePipeline.builder()
 *         .device(device)
 *         .computeShader(spirvBytes)
 *         .descriptorSetLayouts(descriptorSetLayout.handle())
 *         .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value(), 0, 16)
 *         .build(arena)) {
 *     // bind and dispatch...
 * }
 * }</pre>
 */
public class VkComputePipeline implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment layout;
    private final VkDevice device;
    private final int pushConstantStageFlags;

    private VkComputePipeline(MemorySegment handle, MemorySegment layout, VkDevice device, int pushConstantStageFlags) {
        this.handle = handle;
        this.layout = layout;
        this.device = device;
        this.pushConstantStageFlags = pushConstantStageFlags;
    }

    public MemorySegment handle() { return handle; }

    public MemorySegment layout() { return layout; }

    public VkDevice device() { return device; }

    public static Builder builder() { return new Builder(); }

    /** Binds this compute pipeline to a command buffer. */
    public void bind(MemorySegment commandBuffer) {
        Vulkan.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_COMPUTE.value(), handle);
    }

    /** Dispatches compute work groups. */
    public static void dispatch(MemorySegment commandBuffer, int groupCountX, int groupCountY, int groupCountZ) {
        Vulkan.cmdDispatch(commandBuffer, groupCountX, groupCountY, groupCountZ);
    }

    /** Pushes an int push constant at the given byte offset. */
    public void pushInt(MemorySegment commandBuffer, int offset, int value) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment data = a.allocate(ValueLayout.JAVA_INT);
            data.set(ValueLayout.JAVA_INT, 0, value);
            Vulkan.cmdPushConstants(commandBuffer, layout, pushConstantStageFlags, offset, 4, data);
        }
    }

    /** Pushes a float push constant at the given byte offset. */
    public void pushFloat(MemorySegment commandBuffer, int offset, float value) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment data = a.allocate(ValueLayout.JAVA_FLOAT);
            data.set(ValueLayout.JAVA_FLOAT, 0, value);
            Vulkan.cmdPushConstants(commandBuffer, layout, pushConstantStageFlags, offset, 4, data);
        }
    }

    /** Pushes raw bytes as push constants at the given byte offset. */
    public void pushConstants(MemorySegment commandBuffer, int offset, MemorySegment data, int size) {
        Vulkan.cmdPushConstants(commandBuffer, layout, pushConstantStageFlags, offset, size, data);
    }

    /**
     * Records bind + optional descriptor set bind + optional push constants + dispatch into a
     * transient command buffer, submits, and waits for completion.
     */
    public void dispatchAndWait(VkQueue queue, VkDescriptorSet descriptorSet, int groupCountX, int groupCountY, int groupCountZ) {
        VkCommandPool cmdPool = device.getOrCreateCommandPool(queue.familyIndex());
        try (Arena a = Arena.ofConfined()) {
            VkTransientCommandBuffer tcb = VkTransientCommandBuffer.begin(cmdPool, queue.handle(), a);
            bind(tcb.handle());
            if (descriptorSet != null) {
                descriptorSet.bind(tcb.handle(), this, 0, a);
            }
            dispatch(tcb.handle(), groupCountX, groupCountY, groupCountZ);
            tcb.submitAndWait();
            tcb.close();
        }
    }

    /** Convenience: dispatch with only X groups. */
    public void dispatchAndWait(VkQueue queue, VkDescriptorSet descriptorSet, int groupCountX) {
        dispatchAndWait(queue, descriptorSet, groupCountX, 1, 1);
    }

    @Override
    public void close() {
        Vulkan.destroyPipeline(device.handle(), handle);
        Vulkan.destroyPipelineLayout(device.handle(), layout);
    }

    public static class Builder {
        private VkDevice device;
        private byte[] shaderCode;
        private String entryPoint = "main";
        private int flags = 0;
        private MemorySegment basePipeline = MemorySegment.NULL;
        private int basePipelineIndex = -1;
        private MemorySegment pipelineCache = MemorySegment.NULL;

        // Pipeline layout
        private MemorySegment[] descriptorSetLayouts = null;
        private final List<PushConstantRange> pushConstantRanges = new ArrayList<>();

        // Specialization constants
        private final Map<Integer, Object> specializationConstants = new LinkedHashMap<>();

        private Builder() {}

        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }

        public Builder computeShader(byte[] spirv) {
            this.shaderCode = spirv;
            return this;
        }

        public Builder entryPoint(String entryPoint) {
            this.entryPoint = entryPoint;
            return this;
        }

        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }

        public Builder basePipeline(MemorySegment pipeline) {
            this.basePipeline = pipeline;
            return this;
        }

        public Builder basePipelineIndex(int index) {
            this.basePipelineIndex = index;
            return this;
        }

        public Builder pipelineCache(MemorySegment cache) {
            this.pipelineCache = cache;
            return this;
        }

        public Builder descriptorSetLayouts(MemorySegment... layouts) {
            this.descriptorSetLayouts = layouts;
            return this;
        }

        public Builder pushConstantRange(int stageFlags, int offset, int size) {
            pushConstantRanges.add(new PushConstantRange(stageFlags, offset, size));
            return this;
        }

        /** Sets a boolean specialization constant (VkBool32 — 4 bytes). */
        public Builder specialize(int constantId, boolean value) {
            specializationConstants.put(constantId, value);
            return this;
        }

        /** Sets an int specialization constant. */
        public Builder specialize(int constantId, int value) {
            specializationConstants.put(constantId, value);
            return this;
        }

        /** Sets a float specialization constant. */
        public Builder specialize(int constantId, float value) {
            specializationConstants.put(constantId, value);
            return this;
        }

        /** Sets a long specialization constant (uint64). */
        public Builder specialize(int constantId, long value) {
            specializationConstants.put(constantId, value);
            return this;
        }

        /** Sets a double specialization constant. */
        public Builder specialize(int constantId, double value) {
            specializationConstants.put(constantId, value);
            return this;
        }

        public VkComputePipeline build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (shaderCode == null) throw new IllegalStateException("compute shader not set");

            VkShaderModule shaderModule = VkShaderModule.create(arena, device, shaderCode);
            try {
                // Shader stage
                MemorySegment stageInfo = VkPipelineShaderStageCreateInfo.allocate(arena);
                VkPipelineShaderStageCreateInfo.sType(stageInfo, VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO.value());
                VkPipelineShaderStageCreateInfo.stage(stageInfo, VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value());
                VkPipelineShaderStageCreateInfo.module(stageInfo, shaderModule.handle());
                VkPipelineShaderStageCreateInfo.pName(stageInfo, arena.allocateFrom(entryPoint));

                if (!specializationConstants.isEmpty()) {
                    VkPipelineShaderStageCreateInfo.pSpecializationInfo(stageInfo, buildSpecializationInfo(arena));
                }

                // Pipeline layout
                MemorySegment layoutInfo = VkPipelineLayoutCreateInfo.allocate(arena);
                VkPipelineLayoutCreateInfo.sType(layoutInfo, VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO.value());

                if (descriptorSetLayouts != null && descriptorSetLayouts.length > 0) {
                    MemorySegment layoutsArray = arena.allocate(ValueLayout.ADDRESS, descriptorSetLayouts.length);
                    for (int i = 0; i < descriptorSetLayouts.length; i++) {
                        layoutsArray.setAtIndex(ValueLayout.ADDRESS, i, descriptorSetLayouts[i]);
                    }
                    VkPipelineLayoutCreateInfo.setLayoutCount(layoutInfo, descriptorSetLayouts.length);
                    VkPipelineLayoutCreateInfo.pSetLayouts(layoutInfo, layoutsArray);
                }

                if (!pushConstantRanges.isEmpty()) {
                    MemorySegment rangesArray = arena.allocate(VkPushConstantRange.layout(), pushConstantRanges.size());
                    for (int i = 0; i < pushConstantRanges.size(); i++) {
                        PushConstantRange range = pushConstantRanges.get(i);
                        MemorySegment rangeStruct = rangesArray.asSlice(i * VkPushConstantRange.layout().byteSize(), VkPushConstantRange.layout());
                        VkPushConstantRange.stageFlags(rangeStruct, range.stageFlags());
                        VkPushConstantRange.offset(rangeStruct, range.offset());
                        VkPushConstantRange.size(rangeStruct, range.size());
                    }
                    VkPipelineLayoutCreateInfo.pushConstantRangeCount(layoutInfo, pushConstantRanges.size());
                    VkPipelineLayoutCreateInfo.pPushConstantRanges(layoutInfo, rangesArray);
                }

                MemorySegment pipelineLayoutPtr = arena.allocate(ValueLayout.ADDRESS);
                Vulkan.createPipelineLayout(device.handle(), layoutInfo, pipelineLayoutPtr).check();
                MemorySegment pipelineLayout = pipelineLayoutPtr.get(ValueLayout.ADDRESS, 0);

                // Compute pipeline
                MemorySegment createInfo = VkComputePipelineCreateInfo.allocate(arena);
                VkComputePipelineCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO.value());
                VkComputePipelineCreateInfo.pNext(createInfo, MemorySegment.NULL);
                VkComputePipelineCreateInfo.flags(createInfo, flags);

                // stage is an inline struct — copy into the createInfo's stage field
                MemorySegment stageDst = VkComputePipelineCreateInfo.stage(createInfo);
                MemorySegment.copy(stageInfo, 0, stageDst, 0, VkPipelineShaderStageCreateInfo.layout().byteSize());

                VkComputePipelineCreateInfo.layout(createInfo, pipelineLayout);
                VkComputePipelineCreateInfo.basePipelineHandle(createInfo, basePipeline);
                VkComputePipelineCreateInfo.basePipelineIndex(createInfo, basePipelineIndex);

                MemorySegment pipelinePtr = arena.allocate(ValueLayout.ADDRESS);
                Vulkan.createComputePipelines(device.handle(), pipelineCache, 1, createInfo, pipelinePtr).check();

                return new VkComputePipeline(pipelinePtr.get(ValueLayout.ADDRESS, 0), pipelineLayout, device,
                    pushConstantRanges.isEmpty() ? VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value()
                        : pushConstantRanges.getFirst().stageFlags());
            } finally {
                shaderModule.close();
            }
        }

        private MemorySegment buildSpecializationInfo(Arena arena) {
            // Each constant is 4 bytes (bool/int/float) or 8 bytes (long/double)
            int entryCount = specializationConstants.size();
            int dataSize = 0;
            List<SpecEntry> entries = new ArrayList<>();

            for (var entry : specializationConstants.entrySet()) {
                int size = sizeOf(entry.getValue());
                entries.add(new SpecEntry(entry.getKey(), dataSize, size, entry.getValue()));
                dataSize += size;
            }

            MemorySegment mapEntries = arena.allocate(VkSpecializationMapEntry.layout(), entryCount);
            ByteBuffer data = ByteBuffer.allocate(dataSize).order(ByteOrder.nativeOrder());

            for (int i = 0; i < entries.size(); i++) {
                SpecEntry e = entries.get(i);
                MemorySegment mapEntry = mapEntries.asSlice(
                    i * VkSpecializationMapEntry.layout().byteSize(),
                    VkSpecializationMapEntry.layout());
                VkSpecializationMapEntry.constantID(mapEntry, e.constantId);
                VkSpecializationMapEntry.offset(mapEntry, e.offset);
                VkSpecializationMapEntry.size(mapEntry, e.size);

                switch (e.value) {
                    case Boolean b -> data.putInt(b ? 1 : 0);
                    case Integer v -> data.putInt(v);
                    case Float v -> data.putFloat(v);
                    case Long v -> data.putLong(v);
                    case Double v -> data.putDouble(v);
                    default -> throw new IllegalArgumentException("Unsupported spec constant type: " + e.value.getClass());
                }
            }

            data.flip();
            MemorySegment dataSegment = arena.allocate(dataSize);
            MemorySegment.copy(data.array(), 0, dataSegment, ValueLayout.JAVA_BYTE, 0, dataSize);

            MemorySegment specInfo = VkSpecializationInfo.allocate(arena);
            VkSpecializationInfo.mapEntryCount(specInfo, entryCount);
            VkSpecializationInfo.pMapEntries(specInfo, mapEntries);
            VkSpecializationInfo.dataSize(specInfo, dataSize);
            VkSpecializationInfo.pData(specInfo, dataSegment);

            return specInfo;
        }

        private static int sizeOf(Object value) {
            return switch (value) {
                case Boolean _ -> 4; // VkBool32
                case Integer _ -> 4;
                case Float _ -> 4;
                case Long _ -> 8;
                case Double _ -> 8;
                default -> throw new IllegalArgumentException("Unsupported spec constant type: " + value.getClass());
            };
        }

        private record SpecEntry(int constantId, int offset, int size, Object value) {}
        private record PushConstantRange(int stageFlags, int offset, int size) {}
    }
}
