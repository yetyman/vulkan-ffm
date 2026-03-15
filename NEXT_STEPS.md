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
