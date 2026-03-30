# Next Steps

## Buffer System

- CPU-side ring buffering only warranted when data changes every frame
  - Large rarely-updated data: 1 CPU copy + dirty flag + staged upload on change, no CPU ring buffer

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

## Shader System

### Descriptor Pool Lifecycle
- Pool grows on `VK_ERROR_OUT_OF_POOL_MEMORY` — chain of pools, always allocate from tail
- Shader registration: reflect → record descriptor requirements → allocate from current pool → grow if needed
  - No upfront total required; shaders can be registered at any time including runtime
- Shader disposal: decrement live-set count on the owning pool. Pool is NOT destroyed immediately.
- `trimPools()`: destroys any pool where live-set count = 0 and it is not the active allocation pool
  - User calls at natural low-pressure moments (level load complete, loading screen, etc.)
  - All remaining pools destroyed at shutdown
- Default instance count = 1 per shader program; user can override at registration

### Reflection & Validation (at shader load time)
- Warn when shader uses variable-count array bindings but `bindlessDescriptors=false`
- Warn when shader's descriptor set count exceeds `maxBoundDescriptorSets`
- Warn when parameters in the same descriptor set are bound to different update frequencies
  - Suggest reorganizing to frequency-boundary set layout: set 0=global/frame, set 1=per-pass, set 2=per-material, set 3=per-object
  - Frequency mismatch within a set → promote entire set to fastest frequency among its bindings

### Struct Member Reflection — spirv-reflect-bindings additions needed

`spirv_reflect_wrapper.h` is hand-written and currently forward-declares `SpvReflectTypeDescription`
and `SpvReflectBlockVariable` without defining them. Add these definitions and re-run
`generate-spirv-reflect-bindings.bat` to enable struct member mirroring and push constant enumeration:

```c
typedef enum SpvReflectTypeFlags {
    SPV_REFLECT_TYPE_FLAG_UNDEFINED = 0,
    SPV_REFLECT_TYPE_FLAG_VOID      = 0x00000001,
    SPV_REFLECT_TYPE_FLAG_BOOL      = 0x00000002,
    SPV_REFLECT_TYPE_FLAG_INT       = 0x00000004,
    SPV_REFLECT_TYPE_FLAG_FLOAT     = 0x00000008,
    SPV_REFLECT_TYPE_FLAG_VECTOR    = 0x00000100,
    SPV_REFLECT_TYPE_FLAG_MATRIX    = 0x00000200,
    SPV_REFLECT_TYPE_FLAG_STRUCT    = 0x00000800,
    SPV_REFLECT_TYPE_FLAG_ARRAY     = 0x00010000,
} SpvReflectTypeFlags;

typedef struct SpvReflectNumericTraits {
    struct { uint32_t width; uint32_t signedness; } scalar;
    struct { uint32_t component_count; } vector;
    struct { uint32_t column_count; uint32_t row_count; uint32_t stride; } matrix;
} SpvReflectNumericTraits;

typedef struct SpvReflectArrayTraits {
    uint32_t dims_count;
    uint32_t dims[32];
    uint32_t stride;
} SpvReflectArrayTraits;

struct SpvReflectTypeDescription {
    uint32_t id;
    uint32_t op;
    const char* type_name;
    const char* struct_member_name;
    SpvReflectTypeFlags type_flags;
    uint32_t decoration_flags;
    SpvReflectNumericTraits traits;
    uint32_t member_count;
    SpvReflectTypeDescription* members;
};

struct SpvReflectBlockVariable {
    uint32_t spirv_id;
    const char* name;
    uint32_t offset;
    uint32_t absolute_offset;
    uint32_t size;
    uint32_t padded_size;
    uint32_t decoration_flags;
    SpvReflectNumericTraits numeric;
    SpvReflectArrayTraits array;
    uint32_t member_count;
    SpvReflectBlockVariable* members;
    SpvReflectTypeDescription* type_description;
};
```

After

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

### RenderList.executeParallel Implementation
- Main thread allocates one primary command buffer + N secondary command buffers (one per worker)
- Secondary buffers recorded with `VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT`
- `VkCommandBufferInheritanceInfo` must reference the active render pass + framebuffer (or use `VkCommandBufferInheritanceRenderingInfo` for dynamic rendering)
- Workers receive their secondary buffer + thread index, call `ParallelExecutor.execute()`
- Synchronization: `CountDownLatch` or array of `CompletableFuture` — await all workers before `vkCmdExecuteCommands`
- Main thread calls `Vulkan.cmdExecuteCommands(primaryCmdBuf, secondaryCmdBufs[])` after all workers complete
- Thread count should be capped at actual draw call count for the pass — no benefit spinning 8 threads for 2 draws

### Vulkan.java additions needed (already added)
- `cmdExecuteCommands(MemorySegment commandBuffer, int count, MemorySegment commandBuffers)` — already present

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
