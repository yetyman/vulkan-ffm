package io.github.yetyman.vulkan.win32;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.win32.VkWin32SurfaceCreateInfoKHR;
import java.lang.foreign.*;

public class VkWin32Surface {
    
    public static Builder builder(MemorySegment instance) {
        return new Builder(instance);
    }
    
    public static class Builder {
        private final MemorySegment instance;
        private MemorySegment hinstance = MemorySegment.NULL;
        private long hwnd;
        private int flags = 0;
        
        private Builder(MemorySegment instance) {
            this.instance = instance;
        }
        
        public Builder hinstance(MemorySegment hinstance) {
            this.hinstance = hinstance;
            return this;
        }
        
        public Builder hwnd(long hwnd) {
            this.hwnd = hwnd;
            return this;
        }
        
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        public MemorySegment build(Arena arena) {
            MemorySegment surfaceCreateInfo = VkWin32SurfaceCreateInfoKHR.allocate(arena);
            VkWin32SurfaceCreateInfoKHR.sType(surfaceCreateInfo, VkStructureType.VK_STRUCTURE_TYPE_WIN32_SURFACE_CREATE_INFO_KHR.value());
            VkWin32SurfaceCreateInfoKHR.pNext(surfaceCreateInfo, MemorySegment.NULL);
            VkWin32SurfaceCreateInfoKHR.flags(surfaceCreateInfo, flags);
            VkWin32SurfaceCreateInfoKHR.hinstance(surfaceCreateInfo, hinstance);
            VkWin32SurfaceCreateInfoKHR.hwnd(surfaceCreateInfo, MemorySegment.ofAddress(hwnd));
            
            MemorySegment surfacePtr = arena.allocate(ValueLayout.ADDRESS);
            VulkanWin32.createWin32Surface(instance, surfaceCreateInfo, surfacePtr).check();
            return surfacePtr.get(ValueLayout.ADDRESS, 0);
        }
    }
}