package io.github.yetyman.vulkan;

import java.lang.foreign.*;

public class VkPhysicalDeviceOps {
    
    public static EnumerateBuilder enumerate(MemorySegment instance) {
        return new EnumerateBuilder(instance);
    }
    
    public static class EnumerateBuilder {
        private final MemorySegment instance;
        
        private EnumerateBuilder(MemorySegment instance) {
            this.instance = instance;
        }
        
        public MemorySegment[] execute(Arena arena) {
            MemorySegment deviceCount = arena.allocate(ValueLayout.JAVA_INT);
            Vulkan.enumeratePhysicalDevices(instance, deviceCount, MemorySegment.NULL).check();
            int count = deviceCount.get(ValueLayout.JAVA_INT, 0);
            
            if (count == 0) {
                return new MemorySegment[0];
            }
            
            MemorySegment devices = arena.allocate(ValueLayout.ADDRESS, count);
            Vulkan.enumeratePhysicalDevices(instance, deviceCount, devices).check();
            
            MemorySegment[] result = new MemorySegment[count];
            for (int i = 0; i < count; i++) {
                result[i] = devices.getAtIndex(ValueLayout.ADDRESS, i);
            }
            return result;
        }
        
        public MemorySegment first(Arena arena) {
            MemorySegment[] devices = execute(arena);
            if (devices.length == 0) {
                throw new RuntimeException("No Vulkan devices found");
            }
            return devices[0];
        }
    }
}