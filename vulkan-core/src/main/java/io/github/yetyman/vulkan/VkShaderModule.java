package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VkShaderModule implements AutoCloseable {
    private final MemorySegment handle;
    private final VkDevice device;
    
    private VkShaderModule(MemorySegment handle, VkDevice device) {
        this.handle = handle;
        this.device = device;
    }
    
    public static VkShaderModule create(Arena arena, VkDevice device, byte[] code) {
        MemorySegment codeSegment = arena.allocate(code.length);
        MemorySegment.copy(code, 0, codeSegment, ValueLayout.JAVA_BYTE, 0, code.length);
        
        MemorySegment createInfo = VkShaderModuleCreateInfo.allocate(arena);
        VkShaderModuleCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
        VkShaderModuleCreateInfo.pNext(createInfo, MemorySegment.NULL);
        VkShaderModuleCreateInfo.flags(createInfo, 0);
        VkShaderModuleCreateInfo.codeSize(createInfo, code.length);
        VkShaderModuleCreateInfo.pCode(createInfo, codeSegment);
        
        MemorySegment shaderModulePtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.createShaderModule(device.handle(), createInfo, shaderModulePtr).check();
        return new VkShaderModule(shaderModulePtr.get(ValueLayout.ADDRESS, 0), device);
    }
    
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        Vulkan.destroyShaderModule(device.handle(), handle);
    }
}