# Vulkan FFM Wrapper

A Java wrapper for Vulkan using the Foreign Function & Memory (FFM) API introduced in Java 22+.

## Features

- Direct native Vulkan API access using FFM
- Type-safe structure wrappers
- Memory-safe with Arena-based allocation
- Core Vulkan functionality: instances, devices, buffers

## System Requirements

### Core Requirements
- **Java 25+** with Foreign Function & Memory (FFM) API support
- **Vulkan Runtime** - graphics API for GPU acceleration
- **Maven 3.6+** - for building the project

### Platform-Specific Installation

#### Windows
1. **Vulkan Runtime**
   - Usually pre-installed with modern GPU drivers
   - If missing: Download from [LunarG Vulkan SDK](https://vulkan.lunarg.com/sdk/home#windows)
   - Verify installation: `where vulkan-1.dll`

2. **Java 25+**
   - Download from [OpenJDK](https://jdk.java.net/) or [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)
   - Verify: `java --version`

#### Linux (Ubuntu/Debian)
```bash
# Vulkan runtime and development libraries
sudo apt update
sudo apt install vulkan-tools libvulkan-dev

# Verify installation
vulkaninfo
```

#### macOS
```bash
# Install via Homebrew
brew install molten-vk  # Vulkan compatibility layer for Metal

# Verify installation
vulkaninfo
```

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

## Project Modules

This project uses a multi-module Maven structure for clean separation of concerns:

### Core Modules

#### `vulkan-bindings/`
- **Purpose**: Auto-generated FFM bindings for Vulkan API
- **Generated from**: Vulkan C headers using jextract tool
- **Contains**: Low-level native function bindings, constants, and structures
- **Build**: Run `generate-vulkan-bindings.bat` to regenerate

#### `glfw-bindings/`
- **Purpose**: Auto-generated FFM bindings for GLFW windowing library
- **Generated from**: GLFW C headers using jextract tool
- **Contains**: Window management, input handling, and context creation
- **Features**: Bundled native libraries (no separate GLFW installation needed)
- **Build**: Run `generate-glfw-bindings.bat` to regenerate

#### `vulkan-core/`
- **Purpose**: High-level Java wrapper and utilities
- **Dependencies**: vulkan-bindings
- **Contains**: 
  - Type-safe structure builders (`VkApplicationInfo`, `VkInstanceCreateInfo`)
  - Memory management utilities with Arena integration
  - Error handling and result checking (`VkResult`)
  - Resource lifecycle management

#### `sample-app/`
- **Purpose**: Example applications and tutorials
- **Dependencies**: vulkan-core, glfw-bindings, jgltf-model
- **Contains**: 
  - Basic triangle rendering example
  - GLFW window integration
  - GLTF model loading demonstration
- **Main class**: `io.github.yetyman.vulkan.sample.complex.TriangleApp`

### Build Profiles

- **Default**: Builds `vulkan-core` and `sample-app` (uses pre-built bindings)
- **with-bindings**: Includes `vulkan-bindings` and `glfw-bindings` for full rebuild

### Architecture Benefits

- **Separation**: Generated bindings isolated from hand-written code
- **Maintainability**: Core wrapper can evolve independently of bindings
- **Flexibility**: Easy to regenerate bindings for new Vulkan versions
- **Distribution**: Core module can be used without sample applications
