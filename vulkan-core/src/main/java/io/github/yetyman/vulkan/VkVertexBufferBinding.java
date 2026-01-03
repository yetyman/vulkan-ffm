package io.github.yetyman.vulkan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstraction for vertex buffer binding operations
 */
public class VkVertexBufferBinding {
    private final List<MemorySegment> buffers = new ArrayList<>();
    private final List<Long> offsets = new ArrayList<>();
    
    private VkVertexBufferBinding() {}
    
    public static VkVertexBufferBinding create() {
        return new VkVertexBufferBinding();
    }
    
    public VkVertexBufferBinding buffer(MemorySegment buffer) {
        return buffer(buffer, 0L);
    }
    
    public VkVertexBufferBinding buffer(MemorySegment buffer, long offset) {
        buffers.add(buffer);
        offsets.add(offset);
        return this;
    }
    
    public void bind(MemorySegment commandBuffer, int firstBinding, Arena arena) {
        if (buffers.isEmpty()) return;
        
        MemorySegment bufferArray = arena.allocate(ValueLayout.ADDRESS, buffers.size());
        MemorySegment offsetArray = arena.allocate(ValueLayout.JAVA_LONG, offsets.size());
        
        for (int i = 0; i < buffers.size(); i++) {
            bufferArray.setAtIndex(ValueLayout.ADDRESS, i, buffers.get(i));
            offsetArray.setAtIndex(ValueLayout.JAVA_LONG, i, offsets.get(i));
        }
        
        Vulkan.cmdBindVertexBuffers(commandBuffer, firstBinding, buffers.size(), bufferArray, offsetArray);
    }
}