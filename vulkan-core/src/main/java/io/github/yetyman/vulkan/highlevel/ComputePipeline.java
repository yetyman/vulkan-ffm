package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.*;

/**
 * Wrapper for Vulkan compute pipeline with automatic resource management.
 * Handles compute shader execution and workgroup dispatch calculations.
 * 
 * Example usage:
 * <pre>{@code
 * // Create compute pipeline
 * byte[] shader = ShaderLoader.load("/shaders/particle_update.comp").compile();
 * VkComputePipeline pipeline = VkComputePipeline.builder()
 *     .device(device)
 *     .computeShader(shader)
 *     .descriptorSetLayouts(descriptorSetLayout)
 *     .pushConstantRange(VK_SHADER_STAGE_COMPUTE_BIT, 0, 16)
 *     .build(arena);
 * 
 * // Dispatch compute work
 * pipeline.bindDescriptorSets(commandBuffer, 0, descriptorSet);
 * pipeline.pushConstants(commandBuffer, 0, constantData);
 * pipeline.dispatchWorkItems(commandBuffer, 1024, 1, 1); // Process 1024 particles
 * }</pre>
 */
public class ComputePipeline implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment layout;
    private final VkDevice device;
    private final int[] workgroupSize;
    
    private ComputePipeline(MemorySegment handle, MemorySegment layout, VkDevice device, int[] workgroupSize) {
        this.handle = handle;
        this.layout = layout;
        this.device = device;
        this.workgroupSize = workgroupSize;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkPipeline handle */
    public MemorySegment handle() { return handle; }
    
    /** @return the VkPipelineLayout handle */
    public MemorySegment layout() { return layout; }
    
    /** @return the local workgroup size [x, y, z] */
    public int[] workgroupSize() { return workgroupSize.clone(); }
    
    /**
     * Calculates optimal dispatch size for given work dimensions.
     */
    public int[] calculateDispatchSize(int workX, int workY, int workZ) {
        return new int[] {
            (workX + workgroupSize[0] - 1) / workgroupSize[0],
            (workY + workgroupSize[1] - 1) / workgroupSize[1],
            (workZ + workgroupSize[2] - 1) / workgroupSize[2]
        };
    }
    
    /**
     * Records a compute dispatch command to the command buffer.
     */
    public void dispatch(MemorySegment commandBuffer, int groupCountX, int groupCountY, int groupCountZ) {
        Vulkan.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_COMPUTE.value(), handle);
        Vulkan.cmdDispatch(commandBuffer, groupCountX, groupCountY, groupCountZ);
    }
    
    /**
     * Records a compute dispatch command with automatic workgroup calculation.
     */
    public void dispatchWorkItems(MemorySegment commandBuffer, int workX, int workY, int workZ) {
        int[] dispatchSize = calculateDispatchSize(workX, workY, workZ);
        dispatch(commandBuffer, dispatchSize[0], dispatchSize[1], dispatchSize[2]);
    }
    
    /**
     * Records descriptor set binding for compute pipeline.
     */
    public void bindDescriptorSets(MemorySegment commandBuffer, int firstSet, MemorySegment... descriptorSets) {
        MemorySegment setsArray = Arena.ofAuto().allocate(ValueLayout.ADDRESS, descriptorSets.length);
        for (int i = 0; i < descriptorSets.length; i++) {
            setsArray.setAtIndex(ValueLayout.ADDRESS, i, descriptorSets[i]);
        }
        
        Vulkan.cmdBindDescriptorSets(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_COMPUTE.value(),
            layout, firstSet, descriptorSets.length, setsArray, 0, MemorySegment.NULL);
    }
    
    /**
     * Records push constants for compute pipeline.
     */
    public void pushConstants(MemorySegment commandBuffer, int offset, MemorySegment data) {
        Vulkan.cmdPushConstants(commandBuffer, layout, VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value(),
            offset, (int)data.byteSize(), data);
    }
    
    @Override
    public void close() {
        Vulkan.destroyPipeline(device.handle(), handle);
        Vulkan.destroyPipelineLayout(device.handle(), layout);
    }
    
    public static class Builder {
        private VkDevice device;
        private byte[] computeShader;
        private MemorySegment[] descriptorSetLayouts;
        private List<PushConstantRange> pushConstantRanges = new ArrayList<>();
        private int flags = 0;
        private MemorySegment basePipeline = MemorySegment.NULL;
        private int basePipelineIndex = -1;
        
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        public Builder computeShader(byte[] shader) {
            this.computeShader = shader;
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
        
        public ComputePipeline build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (computeShader == null) throw new IllegalStateException("compute shader not set");
            
            // Create shader module
            VkShaderModule shaderModule = VkShaderModule.create(arena, device, computeShader);
            
            try {
                // Extract workgroup size from shader (simplified - assumes default 1,1,1)
                int[] workgroupSize = {1, 1, 1}; // TODO: Extract from SPIR-V reflection
                
                // Create pipeline layout
                MemorySegment pipelineLayoutInfo = VkPipelineLayoutCreateInfo.allocate(arena);
                VkPipelineLayoutCreateInfo.sType(pipelineLayoutInfo, VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO.value());
                
                if (descriptorSetLayouts != null && descriptorSetLayouts.length > 0) {
                    MemorySegment layoutsArray = arena.allocate(ValueLayout.ADDRESS, descriptorSetLayouts.length);
                    for (int i = 0; i < descriptorSetLayouts.length; i++) {
                        layoutsArray.setAtIndex(ValueLayout.ADDRESS, i, descriptorSetLayouts[i]);
                    }
                    VkPipelineLayoutCreateInfo.setLayoutCount(pipelineLayoutInfo, descriptorSetLayouts.length);
                    VkPipelineLayoutCreateInfo.pSetLayouts(pipelineLayoutInfo, layoutsArray);
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
                    VkPipelineLayoutCreateInfo.pushConstantRangeCount(pipelineLayoutInfo, pushConstantRanges.size());
                    VkPipelineLayoutCreateInfo.pPushConstantRanges(pipelineLayoutInfo, rangesArray);
                }
                
                MemorySegment pipelineLayoutPtr = arena.allocate(ValueLayout.ADDRESS);
                Vulkan.createPipelineLayout(device.handle(), pipelineLayoutInfo, pipelineLayoutPtr).check();
                MemorySegment pipelineLayout = pipelineLayoutPtr.get(ValueLayout.ADDRESS, 0);
                
                // Create compute pipeline
                MemorySegment pipelineInfo = VkComputePipelineCreateInfo.allocate(arena);
                VkComputePipelineCreateInfo.sType(pipelineInfo, VkStructureType.VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO.value());
                VkComputePipelineCreateInfo.flags(pipelineInfo, flags);
                
                // Shader stage
                MemorySegment stage = VkComputePipelineCreateInfo.stage(pipelineInfo);
                VkPipelineShaderStageCreateInfo.sType(stage, VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO.value());
                VkPipelineShaderStageCreateInfo.stage(stage, VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value());
                VkPipelineShaderStageCreateInfo.module(stage, shaderModule.handle());
                VkPipelineShaderStageCreateInfo.pName(stage, arena.allocateFrom("main"));
                
                VkComputePipelineCreateInfo.layout(pipelineInfo, pipelineLayout);
                VkComputePipelineCreateInfo.basePipelineHandle(pipelineInfo, basePipeline);
                VkComputePipelineCreateInfo.basePipelineIndex(pipelineInfo, basePipelineIndex);
                
                MemorySegment pipelinePtr = arena.allocate(ValueLayout.ADDRESS);
                Vulkan.createComputePipelines(device.handle(), MemorySegment.NULL, 1, pipelineInfo, pipelinePtr).check();
                
                return new ComputePipeline(pipelinePtr.get(ValueLayout.ADDRESS, 0), pipelineLayout, device, workgroupSize);
            } finally {
                shaderModule.close();
            }
        }
        
        private record PushConstantRange(int stageFlags, int offset, int size) {}
    }
}