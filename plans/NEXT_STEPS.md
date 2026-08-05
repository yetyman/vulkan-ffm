# Next Steps

## Known Bug — TransferBatch Fence Threading Violation

Discovered while regression-testing the buffer system (see `docs/design/buffers/README.md` for the
current architecture; not introduced by the composition-over-inheritance refactor described there —
confirmed by code inspection that `TransferBatch`/`BatchTransferCompletion`/`TransferCompletion`
fence handling is structurally unchanged from before that refactor).

`TransferCompletion.onComplete(callback)` spawns a virtual thread that calls `await()` ->
`BatchTransferCompletion.await()` -> `VkFenceOps.wait(fence.device(), fence, ...)` on the shared
`VkFence` with no synchronization. If the main thread concurrently triggers another wait/flush on
the same fence (e.g. the next test's synchronous read forces a flush before the virtual thread's
wait call returns), validation layers report `UNASSIGNED-Threading-MultipleThreads-Read` on
`vkWaitForFences` — Vulkan spec forbids concurrent `vkWaitForFences` calls on the same `VkFence`
from multiple threads. Reproduced on NVIDIA in `BufferExample`'s `DEVICE_LOCAL writeAsync+onComplete`
test; did not reproduce on AMD in the same run (timing-dependent).

Fix needs a lock (or per-completion fence) around fence wait/status calls in
`BatchTransferCompletion`, scoped to whichever `TransferBatch`/fence-pool design ends up being used
long-term. Not fixed yet — out of scope for the buffer strategy refactor.

---

## BumpAllocator — Thread-Local Native Bump Allocator

For all build methods in Vulkan wrappers, intermediate structs (VkBufferCreateInfo, VkMemoryAllocateInfo, etc.) only need to live until the Vulkan call returns. Currently these use `Arena.ofConfined()` which has cleanup tracking overhead. A thread-local bump allocator eliminates this overhead.

### Location
`vulkan-core/src/main/java/io/github/yetyman/vulkan/util/BumpAllocator.java`

### Design
```java
public final class BumpAllocator {
    private static final int BLOCK_SIZE = 64 * 1024;
    private static final ThreadLocal<BumpAllocator> INSTANCE = ThreadLocal.withInitial(BumpAllocator::new);
    
    private final MemorySegment block = Arena.global().allocate(BLOCK_SIZE, 8);
    private final int[] offsetStack = new int[8]; // supports 8 levels of nesting
    private int offset = 0;
    private int stackDepth = 0;
    
    public static BumpAllocator get() { return INSTANCE.get(); }
    public void push() { offsetStack[stackDepth++] = offset; }
    public void pop()  { offset = offsetStack[--stackDepth]; }
    public MemorySegment alloc(long size, long align) { ... } // rounds up to align, bumps offset
    // overflow fallback: if size exceeds remaining block, allocate from Arena.ofConfined() with trace log
}
```

### Usage in build methods
```java
public VkBuffer build(Arena arena) {
    BumpAllocator ba = BumpAllocator.get();
    ba.push();
    try {
        MemorySegment bufferInfo = ba.alloc(VkBufferCreateInfo.sizeof(), 8);
        // fill struct, call Vulkan, return wrapper
    } finally {
        ba.pop();
    }
}
```

---

## FFM Downcall Optimization — Device-Level Proc-Addr Function Handles

`VkDevice` functions loaded via `vkGetDeviceProcAddr` (e.g. `vkCmdBeginRendering`, timeline semaphore ops) are stored as instance `MethodHandle` fields on `VkDevice`. The JIT cannot constant-fold an instance field load, so `invokeExact` through these handles carries indirect-call overhead that jextract-generated `static final` handles do not. Profiling of the render-graph hot path confirms `VkDevice.cmdBeginRendering` shows up as a real cost.

This is a general FFM/JIT performance concern independent of critical-native linkage — it applies to any per-instance-loaded function pointer, whether or not it is ever made critical.

The correct long-term fix is **per-device bytecode generation**: at device creation time, use ASM or ByteBuddy to emit a class with `static final MethodHandle` fields bound to the resolved function pointer addresses. This matches jextract performance exactly — the JIT sees a class-level constant and can inline through the downcall. The generated class is discarded when the device is closed.

This is a future concern, not a present one — non-trivial to implement correctly (class generation/loading/unloading lifecycle tied to device lifecycle) and should be deferred until profiling shows it matters at scale beyond what's already been observed.

---

## Runtime Configuration — Native Linkage & Validation Layer Control

Currently, critical-native linkage decisions are fixed at build/generation time (see the vulkan-bindings critical-natives injector) or chosen explicitly per call site (see `VulkanFFMCritical`'s opt-in `*Critical` methods and `disableCriticalOverrides` panic switch). There is no runtime system-property surface for controlling this, and no integration with validation-layer state.

Wanted, not yet built:
- System properties (e.g. `vulkan.critical`, `vulkan.validation`) read once at an appropriate initialization point to express user/deployment intent for native linkage aggressiveness.
- A more complete model tying validation-layer enablement to native linkage decisions automatically — today, enabling validation layers does not automatically call `VulkanFFMCritical.disableCriticalOverrides(true)` or otherwise adjust linkage; a user who enables validation must remember to also flip the panic switch (or avoid opting into `*Critical` call sites) themselves.
- Consider whether this belongs on `Vulkan`, `VulkanContext`, or a dedicated small config/policy class, and whether it should be static (JVM-wide) or per-`VkDevice`/per-`VulkanContext`.

---

## Math Package

`helpers-core/src/main/java/io/github/yetyman/vulkan/math/`

Advancement
-a flyweight/cursor view type that reads/writes fields directly from a `TypedVkBuffer`-backed mapped region at a stride offset, without materializing a Java object per element (needed for iterating large counts, e.g. 10k transforms, without 10k allocations). Design not yet started.

---

## Spatial Structures (own module, future)

- **Uniform spatial grid** — 2D and 3D variants. Fixed cell size, each cell holds list of occupant indices. O(1) average hit test. Used for broad-phase collision and cursor hit testing at scale.
- **GPU-resident BVH** — LBVH build via Morton code sort (compute shader), bottom-up refit (compute shader). Stored in storage buffer. Traversable from any shader stage via ray query or manual traversal. Separate from Vulkan AS — this is a custom structure for non-ray spatial queries.
- **HZB occlusion culling** — two-phase render (phase 1: draw last-frame-visible, build max-depth mip pyramid via compute; phase 2: test all objects against HZB, draw newly visible). Max-depth mip means test is: `object.minDepth > HZB.sample(projectedRect)` → cull. Never incorrectly culls, may miss some culls. One frame latency, self-healing.
- **Frustum culling compute pass** — test object AABBs against 6 frustum planes in compute, write surviving instance IDs to indirect draw buffer.
- **3D ring buffer** — fixed-size 3D slot array for chunk/region streaming. World coordinate → slot index via `Math.floorMod`. Shift operation evicts/loads only the slab of slots that changed. O(radius²) per unit of player movement.

---

## Compute Utilities (future)

`vulkan-core/src/main/java/io/github/yetyman/vulkan/compute/`

- **Generic mip generation** — single-dispatch hierarchical reduction using wave intrinsics (`subgroupShuffleDown` or equivalent). Reusable for texture mip chains, HZB max-depth pyramid, prefix sums. One compute dispatch instead of one per mip level. Requires `VK_KHR_shader_subgroup_extended_types` or Vulkan 1.1 subgroup support.

---

## BLAS / TLAS Wrappers (future, after ray query capability is added)

See Ray Tracing section below for Phase 1 (ray query) prerequisites. BLAS/TLAS wrappers build on top of that.

- `VkBlas` — wraps VkAccelerationStructureKHR for bottom-level geometry. Builder takes VkBuffer (vertices + indices). Supports `rebuild()` (full SAH rebuild) and `refit()` (topology-preserving bounds update). Double-buffer pattern: hold two VkBlas instances and swap per frame for dynamic geometry.
- `VkTlas` — wraps VkAccelerationStructureKHR for top-level scene. Takes list of (VkBlas, transform matrix, instanceId). Maintains instance buffer internally. `rebuild()` called per frame when transforms change (cheap — just bounding boxes of BLASes).
- `VkScratchBuffer` — pooled scratch memory for AS builds. Size queried via `vkGetAccelerationStructureBuildSizesKHR`. One pool sized for the largest build, reused across sequential builds. Larger-than-minimum size is fine.
- Proxy mesh pattern: for Nanite-style or highly detailed meshes, build BLAS from a simplified LOD proxy (not full detail). Ray tracing doesn't need full geometric detail; a shadow/reflection ray hitting a simplified mesh is visually identical.
- BLAS refit constraint: vertex buffer indices must not change between build and refit. Vertex positions can move; topology cannot. Moving a vertex buffer for defragmentation is safe as long as the whole buffer moves atomically and the BLAS device address is updated before the next refit.

---

## Bindless Descriptors

- Add `VulkanCapabilities.bindlessDescriptors` flag
- Check via `vkGetPhysicalDeviceFeatures2` + `VkPhysicalDeviceDescriptorIndexingFeatures` at capability init
  - Required sub-features: `descriptorBindingPartiallyBound`, `descriptorBindingVariableDescriptorCount`,
    `runtimeDescriptorArray`, `descriptorBindingUpdateUnusedWhilePending`
  - Extension `VK_EXT_descriptor_indexing` on Vulkan 1.1; core on 1.2+ (check version OR extension name)
- Enable features in `VkDeviceCreateInfo` pNext chain when available
- When bindless enabled, pool must be created with `VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT`
- Bindless bindings require three flags per binding:
  `VARIABLE_DESCRIPTOR_COUNT + PARTIALLY_BOUND + UPDATE_AFTER_BIND`
- Bindless is additive — non-bindless shaders and bindings are unaffected

## Debug Utils / Validation Layer Integration

### VkDebugMessenger (lives on VkInstance)
- `VkDebugMessenger` wrapper class — owns a `VkDebugUtilsMessengerEXT` handle
- Created against `VkInstance` via `vkCreateDebugUtilsMessengerEXT` (instance-level extension function, loaded via `vkGetInstanceProcAddr`)
- `VkInstance` loads the function pointer in its constructor (same pattern as `VkDevice` timeline semaphore loading)
- `VkInstance.createDebugMessenger(callback)` factory — returns `VkDebugMessenger`, registered for cleanup on `VkInstance.close()`
- Callback signature: `(severity, messageType, callbackData) -> boolean` where severity maps to `Logger` levels
- Default callback routes to `Logger.error/warn/info/debug` based on `VK_DEBUG_UTILS_MESSAGE_SEVERITY_*` flags
- `VkInstance.Builder.enableDebugMessenger()` convenience — creates messenger immediately after instance creation
- Destroy order: `vkDestroyDebugUtilsMessengerEXT` before `vkDestroyInstance`

### Object Naming (lives on VkDevice)
- `VkDevice.setObjectName(MemorySegment handle, int objectType, String name)` — calls `vkSetDebugUtilsObjectNameEXT`
- `objectType` values from `VkObjectType` enum (already generated in bindings)
- Convenience overloads: `setObjectName(VkBuffer, String)`, `setObjectName(VkImage, String)`, etc.
- No-op when `VK_EXT_debug_utils` is not available (check `VulkanCapabilities`)
- Add `debugUtils` flag to `VulkanCapabilities`

### Command Buffer Labels
- `Vulkan.cmdBeginDebugLabel(commandBuffer, name, color)` / `cmdEndDebugLabel(commandBuffer)`
- `Vulkan.cmdInsertDebugLabel(commandBuffer, name, color)` for single-point markers
- Queue labels: `Vulkan.queueBeginDebugLabel` / `queueEndDebugLabel` / `queueInsertDebugLabel`
- All no-ops when extension unavailable

---

## Ray Tracing

### Phase 1: Ray Query (recommended starting point)
- Requires: `VK_KHR_acceleration_structure`, `VK_KHR_ray_query`, `VK_KHR_deferred_host_operations`
- Add `rayQuery` and `accelerationStructure` flags to `VulkanCapabilities`
- Enable features via `VkPhysicalDeviceRayQueryFeaturesKHR` + `VkPhysicalDeviceAccelerationStructureFeaturesKHR` in device pNext chain
- `VkAccelerationStructure` wrapper:
  - BLAS builder: takes `VkBuffer` of geometry (vertex/index), calls `vkGetAccelerationStructureBuildSizesKHR`, allocates scratch + AS buffers, calls `vkCmdBuildAccelerationStructuresKHR`
  - TLAS builder: takes array of `VkAccelerationStructureInstanceKHR` (transform + BLAS device address), same build pattern
  - Compaction: query compacted size via `VkQueryPool`, copy with `vkCmdCopyAccelerationStructureKHR`
  - `close()` calls `vkDestroyAccelerationStructureKHR` + frees backing buffer
- TLAS bound as descriptor type `VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR`
- Add `accelerationStructure(int binding, VkAccelerationStructure tlas)` to `DescriptorGroup.Builder`
- Ray queries usable from any shader stage — no new pipeline type needed
- Add `Vulkan.java` entries: `createAccelerationStructureKHR`, `destroyAccelerationStructureKHR`, `cmdBuildAccelerationStructuresKHR`, `getAccelerationStructureBuildSizesKHR`, `getAccelerationStructureDeviceAddressKHR`, `cmdCopyAccelerationStructureKHR`

### Phase 2: Ray Tracing Pipeline
- Requires Phase 1 plus `VK_KHR_ray_tracing_pipeline`, `VK_KHR_spirv_1_4`, `VK_KHR_shader_float_controls`
- `VkRayTracingPipeline` wrapper:
  - Shader groups: ray gen, miss, closest hit, any hit, intersection, callable
  - `VkRayTracingShaderGroupCreateInfoKHR` array built from group descriptors
  - Shader Binding Table (SBT): single buffer divided into ray-gen/miss/hit/callable regions
  - SBT alignment: `shaderGroupHandleSize` and `shaderGroupBaseAlignment` from `VkPhysicalDeviceRayTracingPipelinePropertiesKHR`
  - `vkGetRayTracingShaderGroupHandlesKHR` to retrieve handles, then upload to SBT buffer
- `Vulkan.cmdTraceRaysKHR(commandBuffer, raygenSBT, missSBT, hitSBT, callableSBT, width, height, depth)`
- Ray tracing pipelines do NOT use render passes — `vkCmdTraceRaysKHR` is outside any render pass
- Orthogonal to dynamic rendering — compose by tracing into a storage image, then compositing in a graphics/dynamic-rendering pass
- Add `rayTracingPipeline` flag to `VulkanCapabilities`

---

## Multi-threaded Command Recording

### Threading Model
- Fixed thread pool (not virtual threads — recording is CPU-bound work)
- Default thread count: `Runtime.getRuntime().availableProcessors() / 2`, minimum 1
- Thread count configurable at runtime via `RenderList.setRecordingThreads(int)`
- Do NOT spin up threads per frame — pool is created once and reused
- Each worker thread owns a `VkCommandPool` (via `VkCommandPoolRegistry`) and a pre-allocated secondary `VkCommandBuffer` per pass slot
- Secondary buffers reset at start of each frame, not reallocated

### What still needs building
- `RenderList` parallel pass executor wiring (replace stub loop with actual thread pool dispatch)
- Per-thread secondary command buffer pre-allocation in `RenderList`
- `RenderList.setRecordingThreads(int)` runtime configuration method
- Integration with dynamic rendering inheritance info once dynamic rendering is in place

---

## Scene / Shader Graph

### Concept
A scene graph layer sits above `RenderList` and owns the mapping from logical scene objects to draw calls.
It does not replace `RenderList` — it feeds it.

### Natural inputs already available
- `ShaderInstance` — owns push constants, descriptor slots, pipeline layout
- `DescriptorGroup` — bundles layout + pool + set + buffer bindings
- `VulkanMesh` — vertex/index buffers with vertex format
- `VkPipeline` — built from shader instances with reflection-inferred push constant ranges

### What a scene graph would add
- `Material` — owns a `VkPipeline` + one or more `ShaderInstance` + `DescriptorGroup` per set
  - `material.bind(commandBuffer)` — binds pipeline, flushes shader instances, binds descriptor sets
- `Drawable` — owns a `VulkanMesh` + `Material` + per-object push constant values
  - `drawable.draw(commandBuffer, arena)` — binds mesh, sets push constants, issues draw call
- `Scene` — list of `Drawable` objects, sorted by material to minimize pipeline switches
  - `scene.buildPassExecutor()` — returns a `RenderList.GraphicsExecutor` lambda that iterates drawables
- Frustum culling integrates here: `scene.buildPassExecutor(frustum)` filters drawables before recording

### Relationship to RenderList
```java
renderList.graphicsPass("geometry")
    .write("sceneColor", COLOR_ATTACHMENT)
    .write("sceneDepth", DEPTH_ATTACHMENT)
    .execute(scene.buildPassExecutor(camera.frustum()))
```
- `RenderList` owns: attachment setup, begin/end, barrier placement
- Scene graph owns: pipeline selection, descriptor binding, draw call generation
- Neither layer knows about the other's internals

---

## Shared Timeout / Timeline System

A single dedicated thread draining a priority queue of `(triggerTimeNanos, Runnable)` pairs via `LockSupport.parkNanos` to the next deadline. Intended to replace ad-hoc `ScheduledExecutorService` usage and provide sub-millisecond precision for timeout-driven state transitions.

### Current stubs waiting on this
- `MouseState.moving` — should be set `true` on position update, `false` after a configurable idle timeout
- `MouseState.dragging` — derived from `moving && any button DOWN`; needs `moving` to be correct first
- `MouseState.scrolling` — same pattern as `moving` but for scroll events

### Planned scope
- Wall-clock timeline: `schedule(delayNanos, Runnable)` → returns a cancellable handle
- Tick-driven timeline (future): advanced explicitly by simulation loop, not wall-clock
- Lives in `helpers-core`, no Vulkan dependency
- `StateRegistry` listeners schedule into it naturally for timeout-driven derived state
