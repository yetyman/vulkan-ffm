# Vulkan Sample App Abstraction Plan

## Project Goals
- **vulkan-core**: Universal, unbiased Vulkan abstractions
- **sample-app**: Biased abstractions for high-speed, high-bandwidth rendering
- **Target**: Ultra-smooth renderer handling diverse model data

## Current Issues
- Massive code duplication between simple/complex TriangleApp classes
- ThreadedRenderer (1000+ lines) with mixed responsibilities
- Scattered resource management across BufferManager, LODRenderer, etc.
- Manual memory/lifecycle management throughout
- Hardcoded constants and tightly coupled components

## Abstraction Priority Order

### 1. Application Framework (HIGH PRIORITY)
**Current**: Duplicated window/Vulkan setup in both TriangleApp classes
**Target**: Template method pattern with VulkanApplication base

```java
// Usage goal:
class MyApp extends VulkanApplication {
    MyApp() { super("Title", 800, 600); }
    protected Renderer createRenderer() { return new SimpleRenderer(); }
    protected void onRender() { renderer.drawFrame(); }
}
```

**Key Decisions**:
- Extend existing VulkanApplication, don't rewrite
- Move ALL window/surface/context setup to base class
- Provide hooks for renderer creation and frame logic
- Handle resize/cleanup automatically

### 2. Renderer Architecture (HIGH PRIORITY)
**Current**: ThreadedRenderer (complex) vs Renderer (simple) - 90% duplication
**Target**: Layered composition system

```java
// Usage goals:
Renderer simple = new SimpleRenderer(context);
Renderer threaded = new ThreadedRenderer(new ModelRenderer(context));
Renderer adaptive = new AdaptiveRenderer(new ThreadedRenderer(new ModelRenderer(context)));
```

**Layers**:
- `BaseRenderer` - core pipeline (render pass, command buffers, sync)
- `SimpleRenderer` - basic triangle rendering
- `ModelRenderer` - LOD/instancing capabilities  
- `ThreadedRenderer` - threading wrapper (decorates any renderer)
- `AdaptiveRenderer` - performance adaptation wrapper

**Key Decisions**:
- Use composition over inheritance
- Each layer adds ONE capability
- Threading should be transparent wrapper
- Maintain existing performance characteristics

### 3. Resource Management (MEDIUM PRIORITY)
**Current**: BufferManager, BufferPool, StagingSystem scattered
**Target**: Unified resource lifecycle

```java
// Usage goal:
ResourceManager resources = new ResourceManager(context);
Buffer vertices = resources.createBuffer(VERTEX, data);
// Auto-cleanup on context close
```

**Components**:
- `ResourceManager` - unified GPU resource lifecycle
- `BufferAllocator` - simplified allocation interface
- `GeometryManager` - vertex/index buffer specialization

**Key Decisions**:
- Single point of resource creation/destruction
- Automatic cleanup on context close
- Pool management hidden from user
- Maintain existing buffer pool performance

### 4. Model System Refactor (MEDIUM PRIORITY)
**Current**: LODRenderer with 500+ lines, mixed responsibilities
**Target**: Separated concerns

```java
// Usage goal:
ModelLoader loader = new ModelLoader();
LODSystem lod = new LODSystem();
BatchRenderer batch = new BatchRenderer(context);

CompletableFuture<Model> model = loader.loadGLTF(path);
batch.addInstance(model, transform);
batch.render(camera);
```

**Components**:
- `ModelLoader` - async GLTF loading only
- `LODSystem` - distance-based LOD selection only  
- `BatchRenderer` - instanced rendering only
- `GeometryStreamer` - background streaming only

**Key Decisions**:
- Separate loading from rendering from LOD logic
- Async loading with CompletableFuture
- Streaming should be invisible to user
- Maintain existing LOD performance

### 5. Configuration System (LOW PRIORITY)
**Current**: Hardcoded constants everywhere
**Target**: Centralized configuration

```java
// Usage goal:
RenderSettings settings = RenderSettings.defaults()
    .maxFramesInFlight(3)
    .adaptiveAA(true)
    .threadingMode(ADAPTIVE);
```

## Implementation Guidelines

### Code Style Principles
1. **Minimal API Surface**: Each class should have <10 public methods
2. **Single Responsibility**: Each class does ONE thing well
3. **Composition Over Inheritance**: Prefer wrapping over extending
4. **Fail Fast**: Validate inputs immediately, throw clear exceptions
5. **Resource Safety**: All GPU resources auto-cleanup via try-with-resources

### Performance Constraints
- **Zero Allocation**: No allocations in render loops
- **Cache Friendly**: Batch operations, minimize state changes
- **Thread Safe**: All public APIs must be thread-safe
- **Async Loading**: Never block render thread for I/O

### Vulkan-Specific Rules
- **Arena Management**: Each abstraction manages its own Arena lifecycle
- **Command Buffer Reuse**: Pre-record where possible, minimize recording
- **Synchronization**: Hide all semaphores/fences from user
- **Memory Types**: Automatically select optimal memory types

### Migration Strategy
1. **Start with VulkanApplication**: Provides immediate value, low risk
2. **Extract BaseRenderer**: Common pipeline code from existing renderers
3. **Layer ThreadedRenderer**: Wrap BaseRenderer with threading
4. **Refactor LODRenderer**: Split into focused components
5. **Add ResourceManager**: Gradually migrate buffer management

### Success Metrics
- **Lines of Code**: 50% reduction in sample app
- **Duplication**: <5% code duplication between renderers
- **Performance**: No regression in frame times
- **Usability**: New renderer in <20 lines of code

## File Locations
- **vulkan-core**: Universal abstractions (VulkanApplication, BaseRenderer, ResourceManager)
- **sample-app**: Biased abstractions (ModelRenderer, LODSystem, BatchRenderer)
- **Migration**: Keep existing classes during transition, deprecate gradually

## Risk Mitigation
- **Performance**: Benchmark before/after each abstraction
- **Complexity**: Each abstraction must reduce total system complexity
- **Compatibility**: Maintain existing public APIs during migration
- **Testing**: Create simple test cases for each abstraction layer

## Implementation Progress
- **VulkanApplication Framework**: ✅ **MOVED TO VULKAN-CORE**
  - Enhanced with Config class for universal use
  - InputManager system moved to vulkan-core
  - Sample apps updated to use vulkan-core classes
  - Eliminated ~200 lines of duplicated boilerplate
- **BaseRenderer**: ✅ **CREATED IN VULKAN-CORE**
  - Extracted common pipeline code (swapchain, render pass, sync)
  - NewSimpleRenderer: 80 lines vs 200+ original (60% reduction)
  - Template method pattern for easy extension
- **Resource Management**: ⏳ Pending
- **Model System**: ⏳ Pending
- **Configuration**: ⏳ Pending