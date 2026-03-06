# Next Steps

## Buffer System

- `BufferStrategySelection` add recommended `BufferUsage` (UBO vs SSBO) as output field
- Add `rotates` boolean to selection logic, derived from whether `RING_BUFFER` strategy was chosen
  - UBO recommended only when: `rotates=false`, size ≤ SMALL, gpuWrite=NEVER
  - 3x-size single-buffer branch is the primary case where `rotates=false` is meaningful
- `RingBuffer` constructor parameter for layout strategy:
  - `SEPARATE_BUFFERS` — N separate allocations, current impl
  - `SINGLE_OFFSET_BUFFER` — one Nx allocation, frame region selected by offset
- Single-offset branch uses `STORAGE_BUFFER_DYNAMIC` / `UNIFORM_BUFFER_DYNAMIC` descriptor type
- Prefer dynamic descriptor offset over push constant offset when `VulkanCapabilities` confirms support
  - Dynamic offset: same `vkCmdBindDescriptorSets` call, cheaper driver path, better prefetch visibility
  - Push constant offset: one extra ALU op in shader, driver has less visibility — fallback only
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

### Parameter Binding Model
Java-side connections between shader parameters and data sources. The binding mechanism
(push constant / dynamic offset / descriptor update) is chosen internally from metadata — not user-visible.

```java
class ModelVertShader {

  // ShaderInstance is the runtime handle — wraps CompiledShader, owns descriptor sets and slots
  final ShaderInstance shader = ShaderInstance.from("model.vert");

  // Set 0
  public final PushConstant<Long> time = shader.getPushConstant("time", Long.class);
  public final PushConstant<Matrix> perspective = shader.getPushConstant("perspective", Matrix.class);
  public final StorageBufferSlot<ModelData> models = shader.getStorageBufferSlot("models", ModelData.class);
  public final UniformBufferSlot<Double> outlineThickness = shader.getUniformBufferSlot("outlineThickness", Double.class);

  // Set 1
  public final TextureSlot tex = shader.getTextureSlot("tex");

  // generated to mirror a struct from the shader file
  record ModelData(...) implements BufferWritable {}
}
```

example usage
```java
ModelVertShader modelVert = new ModelVertShader();
modelVert.time.set(System.nanoTime());
modelVert.perspective.set(defaultPerspective);
loadingModels.onComplete(modelBuffer -> modelVert.models.set(modelBuffer));
modelVert.shader.flush(commandBuffer); // writes all dirty slots to the command buffer
// cmdDraw deferred — needs draw command builder; stub in generated class for now
```

- Binding name validated against reflected shader at construction time; throws if not found
- Dirty flag cleared on `flush()` — async upload concerns handled externally via TransferCompletion

### Class Hierarchy

- `PushConstant<T>` — name, offset, size from reflection; dirty flag + pending value;
  flush via `vkCmdPushConstants`; no descriptor involvement
- `DescriptorSlot` (abstract) — name, set, binding, dirty flag, last-bound object reference;
  flush via `vkUpdateDescriptorSets` + `vkCmdBindDescriptorSets`
  - `UniformBufferSlot<T>` — holds `ManagedBuffer`; descriptor type `UNIFORM_BUFFER`
  - `StorageBufferSlot<T>` — holds `ManagedBuffer`; descriptor type `STORAGE_BUFFER`
  - `TextureSlot` — holds `VkImageView` + `VkSampler`; descriptor type `COMBINED_IMAGE_SAMPLER`
- `ShaderInstance` — runtime instance wrapping `CompiledShader`
  - Owns: `VkDescriptorSet` per set, descriptor pool allocation, maps of name→slot
  - Factory methods: `getPushConstant(name, type)`, `getStorageBufferSlot(name, type)`,
    `getUniformBufferSlot(name, type)`, `getTextureSlot(name)` — register and validate against reflection
  - `flush(VkCommandBuffer)` — iterates all dirty `PushConstant`s and `DescriptorSlot`s,
    writes commands, clears dirty flags
  - `AutoCloseable` — releases descriptor sets back to pool

### Generated Shader Class (ShaderGenerator output)

- Named `<ShaderFileName>Shader.java`, wraps `ShaderInstance` (does NOT extend it)
- Public final fields for each binding, grouped by set with `// Set N` comments
- Inner record(s) mirroring shader structs (stubbed until struct member reflection is complete)
- `cmdDraw(VkCommandBuffer)` stubbed with `// TODO: requires draw command builder`
- Committed to source control; regenerated when shader changes

### ShaderGenerator (code generator)

- `ShaderGenerator.java` in `vulkan-core/shaders/` package, runnable as a `main` class
- Args: `<shaderPath> <outputDir> <javaPackage>` — or `--dir <dir>` to process all shader files
- Process: `ShaderLoader.compileShader()` → walk reflection → emit `.java` source
- `generate-shader.bat` in project root: accepts a file or directory, mirrors
  `generate-vulkan-bindings.bat` pattern

### Reflection & Validation (at shader load time)
- Warn when shader uses variable-count array bindings but `bindlessDescriptors=false`
- Warn when shader's descriptor set count exceeds `maxBoundDescriptorSets`
- Warn when parameters in the same descriptor set are bound to different update frequencies
  - Suggest reorganizing to frequency-boundary set layout: set 0=global/frame, set 1=per-pass, set 2=per-material, set 3=per-object
  - Frequency mismatch within a set → promote entire set to fastest frequency among its bindings

### Descriptor Pool Lifecycle
- Pool sized from shader reflection: descriptor type counts × expected set instance count
  - Default instance count = 1 per shader program; user can override at registration
- Pool grows on `VK_ERROR_OUT_OF_POOL_MEMORY` — chain of pools, always allocate from tail
- Shader registration: reflect → record descriptor requirements → allocate from current pool → grow if needed
  - No upfront total required; shaders can be registered at any time including runtime
- Shader disposal: decrement live-set count on the owning pool. Pool is NOT destroyed immediately.
- `trimPools()`: destroys any pool where live-set count = 0 and it is not the active allocation pool
  - User calls at natural low-pressure moments (level load complete, loading screen, etc.)
  - All remaining pools destroyed at shutdown

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

After regenerating, extend `ShaderLoader.ShaderReflection` to:
- Call `spvReflectEnumeratePushConstantBlocks` and store `PushConstantBlockInfo`
  (name, offset, size, members) alongside the existing descriptor set map
- Walk `SpvReflectBlockVariable.members` recursively to build `StructMemberInfo`
  (name, offset, size, type flags) for struct mirroring in the code generator
- Add `SpvReflectTypeFlags` enum to spirv-reflect-bindings (matching the existing
  `SpirvReflectDescriptorType` pattern in `generate-spirv-reflect-enums.bat`)

## Notes / Deferred
- Materials are not a vulkan-core concept. Belongs in sample-app or higher engine layer.
- GLTF uses PBR material model — sample-app may add PBR support later to match.
- Bindless vs non-bindless is entirely the shader author's choice. The library supports both.
- UBO constant cache advantage only meaningful for a single non-rotating buffer where all invocations
  read the same address every frame (e.g. global constants). Ring-buffered UBO loses this advantage
  vs ring-buffered SSBO — use SSBO for rotating data.




# Deferred Destroy / Cleaner Safety Net

## Design

Every wrapper class that owns a Vulkan object requiring explicit destruction gets:

1. **A static nested destroy record** — captures only raw `MemorySegment` handles copied out at construction time. No reference to the wrapper, no arena, no arena-backed segments. Contains the destroy logic as a method.

2. **Explicit `close()` path** — calls the destroy record's method directly and immediately. Cancels the cleaner action so it cannot fire again.

3. **Cleaner safety-net path** — if `close()` was never called and the wrapper is GC'd, the cleaner enqueues the destroy record to the `VkDeferredDestroyQueue` rather than destroying directly. Never blocks the cleaner thread.

## VkDeferredDestroyQueue

- A single app-wide `LinkedBlockingQueue<Runnable>` drained by a dedicated daemon thread.
- Lifecycle is independent of `VkDevice` — must be started before device creation and shut down (with a final drain) after `vkDestroyDevice`.
- Shutdown: send a poison pill, drain thread executes all remaining items, then exits.
- The drain thread is the only thread that calls Vulkan destroy functions from the cleaner path.

## Double-free prevention

Each wrapper has an `AtomicBoolean closed`. `close()` CAS's it to true, destroys immediately, and calls `cleanable.clean()` to cancel the cleaner. The cleaner action checks the flag — if already true, it's a no-op. If false, it enqueues.

Since `Cleaner.Cleanable.clean()` is idempotent and the cleaner action is registered on the *record* (not the wrapper), there is no resurrection risk.

## Which wrappers need this

Owns destruction (needs the pattern):
- `VkBuffer` (buffer + memory)
- `VkImage` (image + memory)
- `VkDevice`
- `VkInstance`
- `VkCommandPool`
- `VkFence`
- `VkSemaphore`
- `VkShaderModule`
- `VkPipeline`
- `VkRenderPass`
- `VkFramebuffer`
- `VkDescriptorPool`
- `VkDescriptorSetLayout`
- `VkImageView`
- `VkSampler`
- `VkSwapchain`
- `VkSurface`

Does not own destruction (skip):
- `VkPhysicalDevice` — retrieved, not created
- `VkQueue` — retrieved from device, not created
- `VkCommandBuffer` — freed via pool reset, not individually destroyed

## Notes

- The destroy record must not reference the arena. Handles must be copied as `MemorySegment` values at construction, before the arena could be closed.
- `VkDevice.close()` must drain `VkCommandPoolRegistry` before calling `vkDestroyDevice`, as pools must be destroyed before the device.
- `VkDeferredDestroyQueue` shutdown must happen after `VkDevice.close()` to ensure all enqueued destroys execute while the device is still valid — caller's responsibility to order this correctly.
- Use-after-free from GPU still using a resource at destroy time is the caller's problem. The library does not track fence/semaphore state for leaked objects.
