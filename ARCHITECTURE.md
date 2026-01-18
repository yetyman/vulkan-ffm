# Vulkan FFM Architecture

## Current State

This is a **mature, multi-module Vulkan wrapper** with complete rendering capabilities:

### ✅ Complete Infrastructure
- **Multi-module Maven structure** with clean separation of concerns
- **Auto-generated FFM bindings** for both Vulkan and GLFW
- **High-level application framework** eliminating boilerplate
- **Complete rendering pipeline** with triangle and GLTF model support
- **Advanced features**: threading, anti-aliasing, input handling, resource management

### ✅ Working Applications
- **SimpleTriangleApp**: Basic triangle rendering with ~50 lines of application code
- **ComplexTriangleApp**: Advanced rendering with threading, AA, GLTF models, input controls
- **Resource management**: Automatic cleanup, memory pools, sync objects
- **Performance monitoring**: FPS tracking, frame time analysis

## Multi-Module Architecture

```
VulkanFFM/                    # Root project (multi-module Maven)
├── vulkan-bindings/          # Auto-generated Vulkan FFM bindings
│   ├── generate-vulkan-bindings.bat
│   ├── generate-vulkan-win32-bindings.bat
│   ├── vulkan_win32_wrapper.h
│   └── src/main/java/io/github/yetyman/vulkan/generated/
│
├── glfw-bindings/            # Auto-generated GLFW FFM bindings
│   ├── generate-glfw-bindings.bat
│   ├── glfw3_wrapper.h
│   ├── src/main/resources/natives/  # Bundled GLFW DLLs
│   └── src/main/java/io/github/yetyman/glfw/generated/
│
├── vulkan-core/              # High-level Vulkan wrapper
│   ├── src/main/java/io/github/yetyman/vulkan/
│   │   ├── highlevel/        # Application framework
│   │   │   ├── VulkanApplication.java     # Base app class
│   │   │   ├── VulkanContext.java         # Vulkan state management
│   │   │   ├── BaseRenderer.java          # Renderer base class
│   │   │   ├── VulkanCapabilities.java    # Device capability queries
│   │   │   ├── VkMemoryAllocator.java     # Memory management
│   │   │   ├── VkResourcePool.java        # Resource pooling
│   │   │   └── ShaderLoader.java          # Shader compilation
│   │   ├── Vk*.java          # Type-safe structure wrappers
│   │   ├── Vulkan.java       # Core Vulkan functions
│   │   └── VulkanLibrary.java # Native library loading
│   └── src/test/             # Unit tests
│
└── sample-app/               # Example applications
    ├── src/main/java/io/github/yetyman/vulkan/sample/
    │   ├── simple/
    │   │   ├── SimpleTriangleApp.java     # Basic triangle (50 lines)
    │   │   └── SimpleRenderer.java        # Simple rendering
    │   ├── complex/
    │   │   ├── ComplexTriangleApp.java    # Advanced features
    │   │   ├── threading/ThreadedRenderer.java
    │   │   ├── models/         # GLTF model loading
    │   │   └── postprocessing/ # Anti-aliasing effects
    │   └── windowing/
    │       ├── GLFWWindowSystem.java      # Window management
    │       └── GLFWInputSystem.java       # Input handling
    ├── src/main/resources/
    │   ├── shaders/            # GLSL shaders
    │   └── sample-models/      # GLTF test models
    └── dependency-reduced-pom.xml  # Shaded JAR for distribution
```

## Design Philosophy

### Layered Abstraction
- **Generated bindings**: Zero-overhead FFM calls to native APIs
- **Core wrappers**: Type-safe Java structures with memory management
- **High-level framework**: Application lifecycle, resource management, utilities
- **Sample applications**: Complete examples showing best practices

### Key Features
- **Memory safety**: Arena-based allocation with automatic cleanup
- **Performance**: Direct FFM calls, resource pooling, multi-threading
- **Extensibility**: Easy to add new Vulkan functions and features
- **Maintainability**: Clean separation between generated and hand-written code

## Build Profiles

### Default Profile
```bash
mvn clean install          # Builds vulkan-core + sample-app
mvn exec:java -pl sample-app  # Runs ComplexTriangleApp
```

### With Bindings Profile
```bash
mvn clean install -Pwith-bindings  # Includes binding generation
```

### Binding Regeneration
```bash
# Regenerate Vulkan bindings
cd vulkan-bindings
generate-vulkan-bindings.bat

# Regenerate GLFW bindings  
cd glfw-bindings
generate-glfw-bindings.bat
```

## Application Examples

### Simple Triangle (50 lines)
```java
public class SimpleTriangleApp extends VulkanApplication {
    private SimpleRenderer renderer;
    
    public SimpleTriangleApp() {
        super("Simple Triangle", 800, 600, new GLFWWindowSystem(), new GLFWInputSystem());
    }
    
    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());
        renderer = new SimpleRenderer(vulkanContext().arena(), vulkanContext().device(),
                                    vulkanContext().graphicsQueue(), surface(), 800, 600);
        renderer.init(vulkanContext().physicalDevice(), vulkanContext().graphicsQueueFamily());
    }
    
    @Override
    protected void render() {
        renderer.drawFrame();
    }
    
    // ... cleanup methods
}
```

### Complex Features
- **Multi-threading**: Parallel command buffer recording
- **Anti-aliasing**: Adaptive edge detection and smoothing
- **GLTF loading**: Complete 3D model pipeline
- **Input system**: Configurable key bindings
- **Performance monitoring**: FPS and frame time tracking

## System Requirements

- **Java 25+** with FFM API
- **Vulkan 1.0+** runtime
- **Maven 3.6+**
- **Windows**: Vulkan drivers (usually included with GPU drivers)
- **Linux**: `vulkan-tools libvulkan-dev`
- **macOS**: MoltenVK for Vulkan-on-Metal

## Architecture Benefits

### Separation of Concerns
- Generated bindings isolated from application code
- Core wrapper evolves independently of native APIs
- Sample applications demonstrate best practices
- Easy to update for new Vulkan versions

### Developer Experience
- **Minimal boilerplate**: VulkanApplication handles 90% of setup
- **Type safety**: Compile-time validation of Vulkan usage
- **Memory safety**: Automatic resource cleanup with Arena
- **Performance**: Zero-overhead FFM, resource pooling, multi-threading
- **Debugging**: Comprehensive logging and validation layers

This architecture successfully delivers on the original goals: meaningful abstraction with minimal overhead, extensible foundation, and working triangle rendering - now expanded into a complete Vulkan application framework.