package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
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
        return builder()
            .applicationName(appName)
            .applicationVersion(appVersion)
            .engineName(engineName)
            .engineVersion(engineVersion)
            .extensions(extensions)
            .build(arena);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String applicationName = "VulkanApp";
        private int applicationVersion = 1;
        private String engineName = "NoEngine";
        private int engineVersion = 0;
        private int apiVersion = Vulkan.VK_API_VERSION_1_0;
        private String[] extensions = null;
        private String[] layers = null;
        private int flags = 0;
        
        public Builder applicationName(String name) {
            this.applicationName = name;
            return this;
        }
        
        public Builder applicationVersion(int version) {
            this.applicationVersion = version;
            return this;
        }
        
        public Builder engineName(String name) {
            this.engineName = name;
            return this;
        }
        
        public Builder engineVersion(int version) {
            this.engineVersion = version;
            return this;
        }
        
        public Builder apiVersion(int version) {
            this.apiVersion = version;
            return this;
        }
        
        public Builder extensions(String... extensions) {
            this.extensions = extensions;
            return this;
        }
        
        public Builder layers(String... layers) {
            this.layers = layers;
            return this;
        }
        
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        public VkInstance build(Arena arena) {
            MemorySegment appInfo = VkApplicationInfo.allocate(arena);
            VkApplicationInfo.sType(appInfo, VkStructureType.VK_STRUCTURE_TYPE_APPLICATION_INFO.value());
            VkApplicationInfo.pNext(appInfo, MemorySegment.NULL);
            VkApplicationInfo.pApplicationName(appInfo, arena.allocateFrom(applicationName));
            VkApplicationInfo.applicationVersion(appInfo, applicationVersion);
            VkApplicationInfo.pEngineName(appInfo, arena.allocateFrom(engineName));
            VkApplicationInfo.engineVersion(appInfo, engineVersion);
            VkApplicationInfo.apiVersion(appInfo, apiVersion);
            
            MemorySegment createInfo = VkInstanceCreateInfo.allocate(arena);
            VkInstanceCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO.value());
            VkInstanceCreateInfo.pNext(createInfo, MemorySegment.NULL);
            VkInstanceCreateInfo.flags(createInfo, flags);
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
            
            if (layers != null && layers.length > 0) {
                MemorySegment layerArray = arena.allocate(ValueLayout.ADDRESS, layers.length);
                for (int i = 0; i < layers.length; i++) {
                    layerArray.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(layers[i]));
                }
                VkInstanceCreateInfo.enabledLayerCount(createInfo, layers.length);
                VkInstanceCreateInfo.ppEnabledLayerNames(createInfo, layerArray);
            } else {
                VkInstanceCreateInfo.enabledLayerCount(createInfo, 0);
                VkInstanceCreateInfo.ppEnabledLayerNames(createInfo, MemorySegment.NULL);
            }
            
            MemorySegment instancePtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.createInstance(createInfo, instancePtr).check();
            return new VkInstance(instancePtr.get(ValueLayout.ADDRESS, 0), arena);
        }
    }
    
    public MemorySegment handle() {
        return handle;
    }
    
    @Override
    public void close() {
        Vulkan.destroyInstance(handle);
    }
}