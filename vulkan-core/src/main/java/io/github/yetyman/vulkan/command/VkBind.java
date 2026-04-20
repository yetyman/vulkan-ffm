package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.VulkanFFM;
import io.github.yetyman.vulkan.util.BumpAllocator;

import java.lang.foreign.*;

/**
 * Vulkan binding command wrapper for pipelines, descriptor sets, vertex buffers, and index buffers.
 */
public record VkBind(MemorySegment handle, BindType type, int bindPoint, int firstSet, MemorySegment layout) {

    public enum BindType {PIPELINE, DESCRIPTOR_SETS, VERTEX_BUFFERS, INDEX_BUFFER}

    // Static helpers for pipeline binding
    public static void bindPipeline(VkCommandBuffer cmd, VkPipeline pipeline) {
        bindPipeline(cmd.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), pipeline.handle());
    }

    public static void bindPipeline(MemorySegment cmd, VkPipeline pipeline) {
        bindPipeline(cmd, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), pipeline.handle());
    }

    public static void bindPipeline(VkCommandBuffer cmd, VkComputePipeline pipeline) {
        bindPipeline(cmd.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_COMPUTE.value(), pipeline.handle());
    }

    public static void bindPipeline(MemorySegment cmd, VkComputePipeline pipeline) {
        bindPipeline(cmd, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_COMPUTE.value(), pipeline.handle());
    }

    public static void bindPipeline(VkCommandBuffer cmd, int bindPoint, MemorySegment pipeline) {
        bindPipeline(cmd.handle(), bindPoint, pipeline);
    }

    public static void bindPipeline(MemorySegment cmd, int bindPoint, MemorySegment pipeline) {
        VulkanFFM.vkCmdBindPipeline(cmd, bindPoint, pipeline);
    }

    // Static helpers for vertex buffer binding
    public static void bindVertexBuffers(VkCommandBuffer cmd, int firstBinding, MemorySegment buffer, long offset) {
        bindVertexBuffers(cmd.handle(), firstBinding, buffer, offset);
    }

    public static void bindVertexBuffers(MemorySegment cmd, int firstBinding, MemorySegment buffer, long offset) {
        BumpAllocator ba = BumpAllocator.get();
        ba.push();
        try {
            MemorySegment buffers = ba.alloc(ValueLayout.ADDRESS.byteSize());
            MemorySegment offsets = ba.alloc(ValueLayout.JAVA_LONG.byteSize());
            buffers.set(ValueLayout.ADDRESS, 0, buffer);
            offsets.set(ValueLayout.JAVA_LONG, 0, offset);
            VulkanFFM.vkCmdBindVertexBuffers(cmd, firstBinding, 1, buffers, offsets);
        } finally {
            ba.pop();
        }
    }

    public static void bindVertexBuffers(VkCommandBuffer cmd, int firstBinding, VkBuffer buffer, long offset) {
        bindVertexBuffers(cmd, firstBinding, buffer.handle(), offset);
    }

    public static void bindVertexBuffers(MemorySegment cmd, int firstBinding, VkBuffer buffer, long offset) {
        bindVertexBuffers(cmd, firstBinding, buffer.handle(), offset);
    }

    // Static helpers for multiple vertex buffer binding
    public static void bindVertexBuffers(VkCommandBuffer cmd, int firstBinding, int bindingCount, MemorySegment bufferArray, MemorySegment offsetArray) {
        bindVertexBuffers(cmd.handle(), firstBinding, bindingCount, bufferArray, offsetArray);
    }

    public static void bindVertexBuffers(MemorySegment cmd, int firstBinding, int bindingCount, MemorySegment bufferArray, MemorySegment offsetArray) {
        VulkanFFM.vkCmdBindVertexBuffers(cmd, firstBinding, bindingCount, bufferArray, offsetArray);
    }

    // Static helpers for index buffer binding
    public static void bindIndexBuffer(VkCommandBuffer cmd, MemorySegment buffer, long offset, int indexType) {
        bindIndexBuffer(cmd.handle(), buffer, offset, indexType);
    }

    public static void bindIndexBuffer(MemorySegment cmd, MemorySegment buffer, long offset, int indexType) {
        VulkanFFM.vkCmdBindIndexBuffer(cmd, buffer, offset, indexType);
    }

    public static void bindIndexBuffer(VkCommandBuffer cmd, VkBuffer buffer, long offset, int indexType) {
        bindIndexBuffer(cmd, buffer.handle(), offset, indexType);
    }

    public static void bindIndexBuffer(MemorySegment cmd, VkBuffer buffer, long offset, int indexType) {
        bindIndexBuffer(cmd, buffer.handle(), offset, indexType);
    }

    // Static helpers for descriptor set binding
    public static void bindDescriptorSets(VkCommandBuffer cmd, int pipelineBindPoint, MemorySegment pipelineLayout, int firstSet, MemorySegment descriptorSet) {
        bindDescriptorSets(cmd.handle(), pipelineBindPoint, pipelineLayout, firstSet, descriptorSet);
    }

    public static void bindDescriptorSets(MemorySegment cmd, int pipelineBindPoint, MemorySegment pipelineLayout, int firstSet, MemorySegment descriptorSet) {
        BumpAllocator ba = BumpAllocator.get();
        ba.push();
        try {
            MemorySegment descriptorSets = ba.alloc(ValueLayout.ADDRESS.byteSize());
            descriptorSets.set(ValueLayout.ADDRESS, 0, descriptorSet);
            VulkanFFM.vkCmdBindDescriptorSets(cmd, pipelineBindPoint, pipelineLayout, firstSet, 1, descriptorSets, 0, MemorySegment.NULL);
        } finally {
            ba.pop();
        }
    }

    // Reusable execution methods
    public void execute(VkCommandBuffer cmd) {
        execute(cmd.handle());
    }

    public void execute(MemorySegment cmd) {
        switch (type) {
            case PIPELINE -> bindPipeline(cmd, bindPoint, handle);
            case DESCRIPTOR_SETS -> {
                // For descriptor sets, handle contains the descriptor set array
                VulkanFFM.vkCmdBindDescriptorSets(cmd, bindPoint, layout, firstSet, 1, handle, 0, MemorySegment.NULL);
            }
            case VERTEX_BUFFERS -> {
                BumpAllocator ba = BumpAllocator.get();
                ba.push();
                try {
                    MemorySegment buffers = ba.alloc(ValueLayout.ADDRESS.byteSize());
                    MemorySegment offsets = ba.alloc(ValueLayout.JAVA_LONG.byteSize());
                    buffers.set(ValueLayout.ADDRESS, 0, handle);
                    offsets.set(ValueLayout.JAVA_LONG, 0, layout.address());
                    VulkanFFM.vkCmdBindVertexBuffers(cmd, firstSet, 1, buffers, offsets);
                } finally {
                    ba.pop();
                }
            }
            case INDEX_BUFFER -> {
                // For index buffer, bindPoint contains index type, layout contains offset
                VulkanFFM.vkCmdBindIndexBuffer(cmd, handle, layout.address(), bindPoint);
            }
            default -> throw new UnsupportedOperationException("Bind type not implemented: " + type);
        }
    }

    // Builder for fluent construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MemorySegment handle;
        private BindType type;
        private int bindPoint;
        private int firstSet = 0;
        private MemorySegment layout;

        private Builder() {
        }

        public Builder pipeline(VkPipeline pipeline) {
            this.handle = pipeline.handle();
            this.type = BindType.PIPELINE;
            this.bindPoint = VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value();
            return this;
        }

        public Builder pipeline(VkComputePipeline pipeline) {
            this.handle = pipeline.handle();
            this.type = BindType.PIPELINE;
            this.bindPoint = VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_COMPUTE.value();
            return this;
        }

        public Builder bindPoint(int bindPoint) {
            this.bindPoint = bindPoint;
            return this;
        }

        public Builder firstSet(int firstSet) {
            this.firstSet = firstSet;
            return this;
        }

        public Builder layout(MemorySegment layout) {
            this.layout = layout;
            return this;
        }

        public VkBind build() {
            return new VkBind(handle, type, bindPoint, firstSet, layout);
        }

        public void bind(VkCommandBuffer cmd) {
            build().execute(cmd);
        }

        public void bind(MemorySegment cmd) {
            build().execute(cmd);
        }
    }
}