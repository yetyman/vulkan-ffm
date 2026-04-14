# Next Steps

## Buffer System Refactor — Composition over Inheritance

The current buffer system uses inheritance (`MappedBuffer extends AbstractBuffer`, etc.) which forces fixed combinations of allocation and IO behavior and causes unnecessary copies when a strategy could avoid them (e.g. ReBar writing directly, ring buffer aliasing). Replace with a composable structure.

### Target shape
```java
public class ManagedBuffer implements AutoCloseable {
    private final AllocationStrategy allocation; // where memory lives, how it is mapped
    private final IoStrategy io;                 // how data moves between CPU and GPU
    private final VkBuffer handle;
}
```

### AllocationStrategy interface
Responsible for: which VkDeviceMemory heap, suballocation vs dedicated, persistent mapping, memory property flags.
Implementations:
- `DirectAllocationStrategy` — one VkDeviceMemory per buffer (current behavior)
- `SuballocatedAllocationStrategy` — backed by a suballocator pool (VMA or custom)
- `ReBarAllocationStrategy` — DEVICE_LOCAL | HOST_VISIBLE when available

### IoStrategy interface
Responsible for: given data to write/read, what is the optimal transfer path.
Implementations:
- `MappedIoStrategy` — persistent CPU map, direct memcpy, no staging
- `StagingIoStrategy` — write to staging buffer, vkCmdCopyBuffer to device-local
- `RingIoStrategy` — N-slot ring wrapping another IoStrategy, one slot per frame in flight
- `DirectIoStrategy` — ReBar direct write, no staging, no persistent map

### Composition examples. just some ideas
```java
// host-visible mapped buffer (current MappedBuffer)
new ManagedBuffer(AllocationStrategy.Direct(HOST_VISIBLE | HOST_COHERENT), IoStrategy.MAPPED, ...)

// device-local with staging (current DeviceLocalBuffer)
new ManagedBuffer(AllocationStrategy.Direct(DEVICE_LOCAL), IoStrategy.Staging(queue), ...)

// suballocated device-local with staging
new ManagedBuffer(AllocationStrategy.Suballoc(SuballocationType.XXXXXX), IoStrategy.Staging(queue), ...)

// ReBar direct write
new ManagedBuffer(AllocationStrategy.ReBar, IoStrategy.DIRECT, ...)

// ring-buffered mapped (current RingBuffer)
new ManagedBuffer(AllocationStrategy.Direct(HOST_VISIBLE), IoStrategy.RingBuffered(3, RenderSync.with(AtomicInteger x, IoStrategy.MAPPED), ...)
```
This composition organization is pending on actual needs to fit in to the architecture. We'll try to do it this way and see what fits. we will NOT be getting rid of the buffer strategy selector.

### VMA integration
VMA backs `SuballocationStrategy` only. It is not used for direct allocations unless explicitly chosen. Lives in `buffers/vma/` subpackage. 

### Migration path
- Keep `ManagedBuffer` interface stable (write/read/writeAsync/handle/size/close)
- Replace SuballocationStrategy class hierarchy with strategy composition
- `TypedVkBuffer`, `FloatVkBuffer`, etc. unchanged — they wrap `ManagedBuffer` interface

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

## Critical Natives — FFM Performance Optimization

FFM `MethodHandle` invocations for Vulkan calls carry safepoint-check overhead. For hot-path per-frame calls this is measurable. `Linker.Option.critical(false)` removes the safepoint check, reducing call overhead significantly.

### Rules for critical native eligibility
```
Use critical if ALL:
  - No validation layers enabled (release mode only)
  - No callbacks (not vkCreate*DebugUtils*, not blocking waits)
  - Expected duration < ~10 microseconds
  - Called in hot path (per-frame or per-draw)

Never critical:
  - vkWaitForFences, vkAcquireNextImageKHR (blocking)
  - vkCreateDebugUtilsMessengerEXT (callback)
  - vkAllocateMemory, vkCreateBuffer (allocating, slow)
  - Any call that may trigger validation layer upcalls
```

### Implementation approach
Maintain three categorization files alongside the generated bindings:
- `always_critical.txt` — hot path, never has callbacks (vkCmdDraw, vkCmdBindPipeline, vkCmdPushConstants, vkCmdSetViewport, vkCmdSetScissor, vkCmdDispatch, vkCmdBindDescriptorSets, vkCmdDrawIndexed, vkCmdDrawIndirect)
- `conditional_critical.txt` — critical in release, not in debug/validation
- `never_critical.txt` — blocking or has callbacks

A transformation script patches the generated VulkanFFM handle initialization to add `Linker.Option.critical(false)` based on these lists. Gated by system property:
```java
private static final boolean CRITICAL =
    Boolean.getBoolean("vulkan.critical") && !Boolean.getBoolean("vulkan.validation");
```
Selected once at class load time — no runtime branching.

### ZGC interaction
With ZGC Generational (recommended GC for this codebase), critical natives are safe. ZGC's safepoints are already sub-millisecond; a 10-microsecond critical native delaying one is irrelevant. ZGC's load barriers operate in Java code, not at safepoints, and are unaffected by critical natives. See README for full ZGC notes.

**Upcalls remain unsafe for critical natives regardless of GC.** The upcall restriction is a JVM constraint unrelated to GC behavior. Timeline semaphore callbacks and validation layer callbacks must never be invoked from a critical native call path.

---

## Math Package

`vulkan-core/src/main/java/io/github/yetyman/vulkan/math/`

GPU-upload-oriented math types. focused on types needed for transforms, camera matrices, and shader uploads.

### Quaternion
Represents a pure rotation (no translation). Unit quaternion = 3x3 rotation matrix equivalent.
```java
Quaternion.fromAxisAngle(float ax, float ay, float az, float angle)
Quaternion.fromEuler(float x, float y, float z)
Quaternion.identity()
q.multiply(Quaternion other)       // compose rotations
q.slerp(Quaternion other, float t) // shortest-path interpolation
q.normalize()
q.conjugate()                      // inverse for unit quaternions
q.toMatrix4f()                     // for GPU upload
q.rotate(float x, float y, float z) // apply rotation to a vector
```

### DualQuaternion
Encodes rotation + translation as q_real + ε·q_dual. Blending multiple dual quaternions produces correct rigid transforms (no candy-wrapper artifact). Non-uniform scale cannot be encoded — handle scale separately.
```java
DualQuaternion.fromRotationTranslation(Quaternion r, float tx, float ty, float tz)
DualQuaternion.identity()
dq.blend(DualQuaternion other, float weight) // for skinning accumulation
dq.normalize()
dq.toMatrix4f()                              // for GPU upload
dq.extractTranslation()                      // float[3]
dq.extractRotation()                         // Quaternion
```

### Matrix4f
Standard 4x4 float matrix. Column-major to match GLSL/Vulkan convention.
```java
Matrix4f.identity()
Matrix4f.perspective(float fovY, float aspect, float near, float far)
Matrix4f.lookAt(float[] eye, float[] center, float[] up)
m.multiply(Matrix4f other)
m.toMemorySegment(Arena arena)  // direct GPU upload
```

---

## Spatial Structures (own module, future)

Not yet. Likely deserves its own Maven module. Planned contents:

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
