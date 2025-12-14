package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VkInstance implements AutoCloseable {
    private final MemorySegment handle;
    private final Arena arena;
    
    private VkInstance(MemorySegment handle, Arena arena) {
        this.handle = handle;
        this.arena = arena;
    }
    
    public static VkInstance create(Arena arena, String appName, int appVersion, String engineName, int engineVersion, String[] extensions) {
        MemorySegment appInfo = VkApplicationInfo.allocate(arena);
        VkApplicationInfo.sType(appInfo, VkStructureType.VK_STRUCTURE_TYPE_APPLICATION_INFO);
        VkApplicationInfo.pNext(appInfo, MemorySegment.NULL);
        VkApplicationInfo.pApplicationName(appInfo, arena.allocateFrom(appName));
        VkApplicationInfo.applicationVersion(appInfo, appVersion);
        VkApplicationInfo.pEngineName(appInfo, arena.allocateFrom(engineName));
        VkApplicationInfo.engineVersion(appInfo, engineVersion);
        VkApplicationInfo.apiVersion(appInfo, Vulkan.VK_API_VERSION_1_0);
        
        MemorySegment createInfo = VkInstanceCreateInfo.allocate(arena);
        VkInstanceCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
        VkInstanceCreateInfo.pNext(createInfo, MemorySegment.NULL);
        VkInstanceCreateInfo.flags(createInfo, 0);
        VkInstanceCreateInfo.pApplicationInfo(createInfo, appInfo);
        
        if (extensions != null && extensions.length > 0) {
            MemorySegment extArray = arena.allocate(ValueLayout.ADDRESS, extensions.length);
            for (int i = 0; i < extensions.length; i++) {
                extArray.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(extensions[i]));
            }
            VkInstanceCreateInfo.enabledExtensionCount(createInfo, extensions.length);
            VkInstanceCreateInfo.ppEnabledExtensionNames(createInfo, extArray);
        } else {
            VkInstanceCreateInfo.enabledExtensionCount(createInfo, 0);
            VkInstanceCreateInfo.ppEnabledExtensionNames(createInfo, MemorySegment.NULL);
        }
        
        VkInstanceCreateInfo.enabledLayerCount(createInfo, 0);
        VkInstanceCreateInfo.ppEnabledLayerNames(createInfo, MemorySegment.NULL);
        
        MemorySegment instancePtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.createInstance(createInfo, instancePtr).check();
        return new VkInstance(instancePtr.get(ValueLayout.ADDRESS, 0), arena);
    }
    
    public MemorySegment handle() {
        return handle;
    }
    
    @Override
    public void close() {
        Vulkan.destroyInstance(handle);
    }
}