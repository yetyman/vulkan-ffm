# Generating Vulkan Bindings with jextract

## What is jextract?

jextract is a tool that automatically generates Java FFM bindings from C header files. It will create all the correct struct layouts, function signatures, and constants for Vulkan.

## Installation

1. Download jextract from: https://jdk.java.net/jextract/
2. Extract to a directory (e.g., `C:\jextract`)
3. Add to PATH or use full path

## Generate Vulkan Bindings

Run this command from the project root:

```bash
jextract \
  --output vulkan-core/src/main/java \
  --target-package io.github.yetyman.vulkan.generated \
  --include-dir C:/VulkanSDK/1.4.335.0/Include \
  --header-class-name VulkanFFM \
  C:/VulkanSDK/1.4.335.0/Include/vulkan/vulkan.h
```

This will generate:
- `VulkanFFM.java` - Main class with all Vulkan functions
- All struct classes with correct memory layouts
- All constants and enums

## Using Generated Bindings

```java
import io.github.yetyman.vulkan.generated.*;

// All structs have correct layouts
VkApplicationInfo appInfo = VkApplicationInfo.allocate(arena);
appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO);
appInfo.pApplicationName(arena.allocateUtf8String("MyApp"));

// All functions are available
VulkanFFM.vkCreateInstance(createInfo, null, instancePtr);
```

## Benefits

- ✅ Correct struct layouts (no more crashes!)
- ✅ All Vulkan functions available
- ✅ Type-safe API
- ✅ No manual struct creation needed
- ✅ Automatically updated when Vulkan SDK updates
