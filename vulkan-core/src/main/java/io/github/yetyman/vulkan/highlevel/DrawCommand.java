package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.Vulkan;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Unified abstraction for all Vulkan draw commands.
 * Supports direct, indexed, indirect, multi-draw, and mesh shader variants.
 */
public abstract class DrawCommand {
    
    /**
     * Execute this draw command on the given command buffer.
     */
    public abstract void execute(MemorySegment commandBuffer);
    
    // Factory methods for different draw types
    
    /**
     * Create a direct draw command (no index buffer).
     */
    public static DirectDraw direct(int vertexCount, int instanceCount) {
        return new DirectDraw(vertexCount, instanceCount, 0, 0);
    }
    
    /**
     * Create an indexed draw command.
     */
    public static IndexedDraw indexed(int indexCount, int instanceCount) {
        return new IndexedDraw(indexCount, instanceCount, 0, 0, 0);
    }
    
    /**
     * Create an indirect draw command (GPU reads parameters from buffer).
     */
    public static IndirectDraw indirect(MemorySegment buffer, int drawCount) {
        return new IndirectDraw(buffer, 0, drawCount, 16); // 16 = sizeof(VkDrawIndirectCommand)
    }
    
    /**
     * Create an indexed indirect draw command.
     */
    public static IndexedIndirectDraw indexedIndirect(MemorySegment buffer, int drawCount) {
        return new IndexedIndirectDraw(buffer, 0, drawCount, 20); // 20 = sizeof(VkDrawIndexedIndirectCommand)
    }
    
    /**
     * Create a multi-draw command (multiple draws in one call).
     */
    public static MultiDraw multi(DrawInfo[] draws) {
        return new MultiDraw(draws);
    }
    
    /**
     * Create a mesh shader draw command.
     */
    public static MeshDraw mesh(int groupCountX) {
        return new MeshDraw(groupCountX, 1, 1);
    }
    
    // Implementation classes
    
    public static class DirectDraw extends DrawCommand {
        private final int vertexCount, instanceCount, firstVertex, firstInstance;
        
        private DirectDraw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
            this.vertexCount = vertexCount;
            this.instanceCount = instanceCount;
            this.firstVertex = firstVertex;
            this.firstInstance = firstInstance;
        }
        
        public DirectDraw firstVertex(int firstVertex) {
            return new DirectDraw(vertexCount, instanceCount, firstVertex, firstInstance);
        }
        
        public DirectDraw firstInstance(int firstInstance) {
            return new DirectDraw(vertexCount, instanceCount, firstVertex, firstInstance);
        }
        
        @Override
        public void execute(MemorySegment commandBuffer) {
            Vulkan.cmdDraw(commandBuffer, vertexCount, instanceCount, firstVertex, firstInstance);
        }
    }
    
    public static class IndexedDraw extends DrawCommand {
        private final int indexCount, instanceCount, firstIndex, vertexOffset, firstInstance;
        
        private IndexedDraw(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
            this.indexCount = indexCount;
            this.instanceCount = instanceCount;
            this.firstIndex = firstIndex;
            this.vertexOffset = vertexOffset;
            this.firstInstance = firstInstance;
        }
        
        public IndexedDraw firstIndex(int firstIndex) {
            return new IndexedDraw(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
        }
        
        public IndexedDraw vertexOffset(int vertexOffset) {
            return new IndexedDraw(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
        }
        
        public IndexedDraw firstInstance(int firstInstance) {
            return new IndexedDraw(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
        }
        
        @Override
        public void execute(MemorySegment commandBuffer) {
            Vulkan.cmdDrawIndexed(commandBuffer, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
        }
    }
    
    public static class IndirectDraw extends DrawCommand {
        private final MemorySegment buffer;
        private final long offset;
        private final int drawCount, stride;
        
        private IndirectDraw(MemorySegment buffer, long offset, int drawCount, int stride) {
            this.buffer = buffer;
            this.offset = offset;
            this.drawCount = drawCount;
            this.stride = stride;
        }
        
        public IndirectDraw offset(long offset) {
            return new IndirectDraw(buffer, offset, drawCount, stride);
        }
        
        public IndirectDraw stride(int stride) {
            return new IndirectDraw(buffer, offset, drawCount, stride);
        }
        
        @Override
        public void execute(MemorySegment commandBuffer) {
            Vulkan.cmdDrawIndirect(commandBuffer, buffer, offset, drawCount, stride);
        }
    }
    
    public static class IndexedIndirectDraw extends DrawCommand {
        private final MemorySegment buffer;
        private final long offset;
        private final int drawCount, stride;
        
        private IndexedIndirectDraw(MemorySegment buffer, long offset, int drawCount, int stride) {
            this.buffer = buffer;
            this.offset = offset;
            this.drawCount = drawCount;
            this.stride = stride;
        }
        
        public IndexedIndirectDraw offset(long offset) {
            return new IndexedIndirectDraw(buffer, offset, drawCount, stride);
        }
        
        public IndexedIndirectDraw stride(int stride) {
            return new IndexedIndirectDraw(buffer, offset, drawCount, stride);
        }
        
        @Override
        public void execute(MemorySegment commandBuffer) {
            Vulkan.cmdDrawIndexedIndirect(commandBuffer, buffer, offset, drawCount, stride);
        }
    }
    
    public static class MultiDraw extends DrawCommand {
        private final DrawInfo[] draws;
        
        private MultiDraw(DrawInfo[] draws) {
            this.draws = draws;
        }
        
        @Override
        public void execute(MemorySegment commandBuffer) {
            if (VulkanCapabilities.multiDraw) {
                // Use extension if available
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment drawsBuffer = arena.allocate(ValueLayout.JAVA_INT, draws.length * 4);
                    for (int i = 0; i < draws.length; i++) {
                        DrawInfo draw = draws[i];
                        int offset = i * 4;
                        drawsBuffer.setAtIndex(ValueLayout.JAVA_INT, offset, draw.vertexCount);
                        drawsBuffer.setAtIndex(ValueLayout.JAVA_INT, offset + 1, draw.instanceCount);
                        drawsBuffer.setAtIndex(ValueLayout.JAVA_INT, offset + 2, draw.firstVertex);
                        drawsBuffer.setAtIndex(ValueLayout.JAVA_INT, offset + 3, draw.firstInstance);
                    }
                    
                    //actual VK_EXT_multi_draw binding
                    io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdDrawMultiEXT(commandBuffer, draws.length, drawsBuffer, 0, 0, 16);
                    
                    // Fallback
//                    for (DrawInfo draw : draws) {
//                        Vulkan.cmdDraw(commandBuffer, draw.vertexCount, draw.instanceCount,
//                                     draw.firstVertex, draw.firstInstance);
//                    }
                }
            } else {
                // Fallback to individual draws
                for (DrawInfo draw : draws) {
                    Vulkan.cmdDraw(commandBuffer, draw.vertexCount, draw.instanceCount, 
                                 draw.firstVertex, draw.firstInstance);
                }
            }
        }
    }
    
    public static class MeshDraw extends DrawCommand {
        private final int groupCountX, groupCountY, groupCountZ;
        
        private MeshDraw(int groupCountX, int groupCountY, int groupCountZ) {
            this.groupCountX = groupCountX;
            this.groupCountY = groupCountY;
            this.groupCountZ = groupCountZ;
        }
        
        public MeshDraw groupCountY(int groupCountY) {
            return new MeshDraw(groupCountX, groupCountY, groupCountZ);
        }
        
        public MeshDraw groupCountZ(int groupCountZ) {
            return new MeshDraw(groupCountX, groupCountY, groupCountZ);
        }
        
        @Override
        public void execute(MemorySegment commandBuffer) {
            if (VulkanCapabilities.meshShaders) {
                io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdDrawMeshTasksEXT(commandBuffer, groupCountX, groupCountY, groupCountZ);
            } else {
                throw new UnsupportedOperationException("Mesh shaders not supported on this device");
            }
        }
    }
    
    /**
     * Information for a single draw in a multi-draw command.
     */
    public static class DrawInfo {
        public final int vertexCount, instanceCount, firstVertex, firstInstance;
        
        public DrawInfo(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
            this.vertexCount = vertexCount;
            this.instanceCount = instanceCount;
            this.firstVertex = firstVertex;
            this.firstInstance = firstInstance;
        }
        
        public static DrawInfo create(int vertexCount, int instanceCount) {
            return new DrawInfo(vertexCount, instanceCount, 0, 0);
        }
    }
}