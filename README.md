# Vulkan FFM Wrapper

A Java wrapper for Vulkan using the Foreign Function & Memory (FFM) API introduced in Java 22+.

## Features

- Direct native Vulkan API access using FFM
- Type-safe structure wrappers
- Memory-safe with Arena-based allocation
- Core Vulkan functionality: instances, devices, buffers

## Requirements

- Java 25+
- Vulkan SDK installed
- vulkan-1.dll (Windows) in system PATH

## Usage

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment appInfo = VkApplicationInfo.allocate(
        arena, "MyApp", 1, "NoEngine", 0, Vulkan.VK_API_VERSION_1_0
    );
    
    MemorySegment createInfo = VkInstanceCreateInfo.allocate(
        arena, appInfo, null, null
    );
    
    MemorySegment instancePtr = arena.allocate(ValueLayout.ADDRESS);
    Vulkan.createInstance(createInfo, instancePtr).check();
    
    MemorySegment instance = instancePtr.get(ValueLayout.ADDRESS, 0);
    // Use instance...
    
    Vulkan.destroyInstance(instance);
}
```

## Building

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="io.github.yetyman.vulkan.VulkanExample"
```

## Architecture

- `Vulkan.java` - Main API functions
- `VulkanLibrary.java` - Native library loader
- `Vk*CreateInfo.java` - Structure wrappers
- `VkResult.java` - Error handling
