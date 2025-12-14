package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VkShaderModule implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    
    private VkShaderModule(MemorySegment handle, MemorySegment device) {
        this.handle = handle;
        this.device = device;
    }
    
    public static VkShaderModule create(Arena arena, MemorySegment device, byte[] code) {
        MemorySegment codeSegment = arena.allocate(code.length);
        MemorySegment.copy(code, 0, codeSegment, ValueLayout.JAVA_BYTE, 0, code.length);
        
        MemorySegment createInfo = VkShaderModuleCreateInfo.allocate(arena);
        VkShaderModuleCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
        VkShaderModuleCreateInfo.pNext(createInfo, MemorySegment.NULL);
        VkShaderModuleCreateInfo.flags(createInfo, 0);
        VkShaderModuleCreateInfo.codeSize(createInfo, code.length);
        VkShaderModuleCreateInfo.pCode(createInfo, codeSegment);
        
        MemorySegment shaderModulePtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.createShaderModule(device, createInfo, shaderModulePtr).check();
        return new VkShaderModule(shaderModulePtr.get(ValueLayout.ADDRESS, 0), device);
    }
    
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        VulkanExtensions.destroyShaderModule(device, handle);
    }
}