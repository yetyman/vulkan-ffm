# Product Overview

## Purpose
VulkanFFM is a Java wrapper for the Vulkan graphics API using the Foreign Function & Memory (FFM) API introduced in Java 22+. It provides type-safe, memory-safe access to Vulkan without JNI, enabling high-performance GPU programming directly from Java.

## Value Proposition
- Zero-overhead native Vulkan calls via FFM (no JNI overhead)
- Arena-based memory management for automatic, safe resource cleanup
- Layered abstraction: from raw bindings up to a full application framework
- Runtime GLSL→SPIR-V compilation + reflection-driven shader binding
- Complete rendering pipeline with minimal boilerplate (~50 lines for a triangle app)

## Key Features
- Auto-generated FFM bindings for Vulkan, GLFW, shaderc, and SPIRV-Reflect (via jextract)
- Type-safe structure wrappers (VkDevice, VkBuffer, VkImage, VkComputePipeline, etc.)
- Builder pattern for all major Vulkan object creation
- **Shader system**: runtime GLSL/HLSL compilation, SPIR-V reflection, typed code generation
  - `ShaderLoader` compiles GLSL→SPIR-V via shaderc with preprocessor defines and optimization
  - `ShaderReflection` introspects descriptor sets, push constants, specialization constants
  - `ShaderGenerator` emits typed Java wrapper classes with push constant fields, descriptor slots, BufferWritable records
  - `ShaderInstance` provides runtime dirty-tracked parameter binding with flush()
  - Specialization constants supported at both shader instance and pipeline builder level
- **Buffer strategy system**: pluggable memory strategies (mapped, device-local, mirrored, ReBAR, ring, sparse, suballocator)
  - `TypedVkBuffer<T>` for typed array views with optional CPU mirror
  - Primitive typed buffers: `FloatVkBuffer`, `IntVkBuffer`, `LongVkBuffer`, `ShortVkBuffer`, `DoubleVkBuffer`
  - `TransferBatch` batches GPU copies with auto-flush at 64MB threshold
  - `MirroredObservability` for zero-cost CPU reads of device-local data via the observability strategy axis
- **Compute pipeline**: `VkComputePipeline` with specialization constants, push constants, dispatchAndWait
- **Timeline semaphores**: `VkTimelineSemaphore` with CPU signal/await, submit builder integration
- **Descriptor groups**: `DescriptorGroup` bundles layout + pool + set + bindings in one builder call
- **Dynamic rendering**: `VkRendering` builder for Vulkan 1.3 dynamic rendering (no VkRenderPass/VkFramebuffer needed); auto-enabled by `VulkanContext.Builder`
- **Sparse image**: `VkSparseImage` with on-demand tile commit/decommit for virtual texturing
- **Decoupled loop system**: `LoopDriver` (uncapped/fixedRate/fixedRateCatchUp/vsync) + `LoopThread` + `TimingStrategy` (none/profiled/budgeted); composed by `GraphicsLoop`
- **TransientCommandBuffer**: RAII one-shot command buffer with copyBuffer, copyBufferToImage, copyImageToBuffer, copyImage, transitionImageLayout, memoryBarrier
- **CommandManager**: thread-safe command pool management with optional per-thread pools
- **Node tree system**: hierarchical component composition with DI, typed events, traversal views, and dirty tracking
  - `Tree`/`Node`/`Component` with full lifecycle state machine
  - Dependency injection with claim styles, lookup scopes, fallback policies
  - `TraversalView<C>` with O(1) operations and incremental dirty tracking for GPU-friendly bulk iteration
  - `EventType` identity tokens with typed handlers, zero-alloc capture/bubble dispatch
  - `PropertyNotifier` for enum-keyed per-instance change notification
- **UILayer composition**: stackable rendering/input subsystems with capture/bubble input dispatch
  - `UILayer` interface for self-contained rendering subsystems
  - `UIComposite` orchestrator with order-sorted layers, lifecycle management
  - Two-phase input dispatch (capture top-down, bubble bottom-up) with cross-layer context annotation
  - `AssetRegistry` typed service locator for shared platform resources
- **Mesh system** (`vulkan-ffm-mesh`): layered mesh architecture separating vocabulary, sources, partitioning, residency, and consumption
  - Layer 0: `AttributeSemantic`, `AttributeFormat`, `MeshLayout` -- pure data types, no GPU
  - Layer 1: `GeometrySource`, `AttributeStream`, `IndexStream` -- residency-agnostic producers
  - Layer 2: `GeometryPartition`, `PartitionSet`, metadata channels -- topology and partitioning
  - Layer 3: `GeometryAllocator`, `UploadPlanner`, `ResidencyTracker` -- allocation and upload
  - Layer 4: `GeometryBinding`, `GeometryDrawRange`, `IndirectDrawEncoder`, `GeometryTable` -- work description
  - Layer 5: `LodSelector`, `RepresentationGraph`, `RefinementStream` -- LOD selection
  - Optimized processing in sibling module: `QemSimplifier`, `OptimizedMeshletBuilder`
- High-level application framework (VulkanApplication, VulkanContext, GraphicsRenderer, GraphicsLoop)
- Resource pooling, sync management (SyncManager, SemaphoreManager, FencePool)
- Declarative render graph (`RenderList`) for multi-pass rendering
- GLTF model loading, multi-threading, anti-aliasing in sample apps
- Input handling system (GLFW-backed)
- Win32 surface support for Windows

## Target Users
- Java developers building GPU-accelerated applications
- Developers who want Vulkan access without C/C++
- Projects needing a modern, FFM-based alternative to LWJGL

## Use Cases
- Real-time 3D rendering applications
- GPU compute workloads from Java (via VkComputePipeline)
- Learning Vulkan concepts with Java tooling
- Building game engines or visualization tools in Java
