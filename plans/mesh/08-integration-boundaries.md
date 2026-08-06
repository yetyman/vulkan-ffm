# Integration boundaries

How `vulkan-ffm-meshes` reaches every capability the frame graph and node trees offer, while depending
on neither.

---

## Module dependency edges

```
vulkan-bindings, glfw-bindings, shaderc-bindings, spirv-reflect-bindings, stb-truetype-bindings
        |
        v
   vulkan-core  <-------------------  helpers-core
        |    \                            ^
        |     \___________________        |
        v                        v        |
 vulkan-ffm-graph        vulkan-ffm-meshes (depends on both vulkan-core and helpers-core)
 vulkan-ffm-node-trees            |
        \                         |
         \                        v
          \             vulkan-ffm-meshes-processing  (sibling, later)
           \                      |
            \_____________________|_______ sample-app, vulkan-ffm-sample-ui-layers
```

Rules:

- `vulkan-ffm-meshes` must not import `io.github.yetyman.vulkan.graph.*` or
  `io.github.yetyman.vulkan.nodetree.*` or `io.github.yetyman.vulkan.ui.*`.
- Nothing in a library module imports `sample-app`.
- `helpers-core` already depends on `vulkan-core` (for `GpuLayout`), so `vulkan-ffm-meshes` depending
  on both is consistent with the existing graph.

---

## The principle

The mesh module depends on concepts, and whoever schedules the work supplies the implementation.

Concretely, three seams carry all of it:

1. `UploadPlan` - work described as data, with scheduling hints, executable by anyone.
2. `GpuCompletion` - asynchronous GPU work described as an interface, producible by anyone.
3. Plain records (`GeometryBinding`, `GeometryDrawRange`) - consumed by anyone, recorded by anyone.

None of the three names a graph type. All three are `vulkan-core` concepts or local records.

---

## Reaching every frame-graph capability without depending on it

| Frame graph capability | The conceptual hook that earns it | Where the hook is defined |
|------------------------|-----------------------------------|---------------------------|
| Automatic barrier insertion | `UploadPlan.dstAccessMask`, `dstStageMask` - plain `VkAccessFlags` and `VkPipelineStageFlags` ints | `04-residency-and-upload.md` |
| Queue assignment | `UploadPlan.preferredQueue` is a `QueueClass` (TRANSFER, COMPUTE, GRAPHICS), never a `VkQueue` | same |
| Queue family ownership transfer | Falls out of declaring access, stage, and queue class; the executor computes the transfer | same |
| Priority scheduling | `UploadPlan.priority` | same |
| Degradation under budget pressure | `UploadPlan.deferrable`, plus partition-granular residency so a partial upload is a valid state | same |
| Readback | Readback expressed as an `UploadPlan` in the opposite direction plus a `GpuCompletion` | same |
| Temporal resources | Deformed or skinned output expressed as an N-slot ring request, satisfied by `RingBuffer` or by graph temporal resources | Layer 3 |
| Memory aliasing | Eviction and reclaim callbacks on `GeometryAllocator`, so an external allocator can move or reclaim | Layer 3 |
| Pass masks and multi-rate | Nothing needed; the mesh module has no per-frame passes of its own | n/a |

Every entry is an int, a small local enum, or an interface already present in `vulkan-core`.

### Why `QueueClass` rather than a queue

Handing the mesh module a `VkQueue` forces it to decide which queue to use, which requires knowing the
device's queue topology and what else is competing for it. Declaring a class lets the scheduler decide,
which is what the graph is for. The standalone `TransferBatchExecutor` resolves the class to a concrete
queue itself using whatever it was configured with.

---

## The two upload executors

```java
public interface UploadExecutor {
    GpuCompletion execute(UploadPlan plan, VkQueue queue);
}
```

### `TransferBatchExecutor` - ships in the mesh module

Acquires an `IBuffer` write scope per op, runs transcodes directly into the scope's segment, closes the
scope, returns the batch's `GpuCompletion`. Needs no graph. This is the path samples and tests use.

### Graph-recording executor - lives outside the module

Translates `UploadOp`s into `TransferNode`s, uses the access and stage masks for barriers and the queue
class for assignment, returns a timeline-semaphore-backed `GpuCompletion`.

Placement decision: this starts in `sample-app`. It is promoted to an adapter module
(`vulkan-ffm-meshes-graph`) only after the same code has been written three times. Creating the adapter
module preemptively would be a guess at what the adapter needs to look like, made before any evidence.

Because both executors consume the same `UploadPlan`, the mesh module never learns which is in use.

---

## Node tree integration

No dependency, in either direction, and no adapter is expected to be needed inside either module.

A scene built on node trees would have a component holding a `Mesh` or a set of partition table
indices, plus a transform component, plus a culling component. All of that composition lives in the
node-tree module's consumer or in an app. The mesh module contributes nothing to it and knows nothing
about it.

The one thing worth noting: `TraversalView<C>` with its incremental dirty tracking is a natural fit for
maintaining a dense array of visible mesh instances for bulk upload. That is a consumer-side pattern,
not a mesh-module concern, and it works precisely because `GeometryTable` uses stable partition indices
that a traversal can reference.

---

## Sample UI layer integration

`vulkan-ffm-sample-ui-layers` is the intended home for the first mesh-consuming sample layers, since it
already depends on `vulkan-core` and would depend on `vulkan-ffm-meshes`.

Expected sample layers, in roadmap order:

- A basic mesh layer: one procedural mesh, dedicated allocator, vertex-input path, forward draw.
  Proves Layers 0 through 4 end to end.
- A pooled mesh layer: many meshes, pool allocator, one bind, per-mesh draws.
  Proves the allocator swap requires no changes above `GeometryAllocator`.
- A GPU-driven layer: pool allocator, `GeometryTable`, compute culling, `vkCmdDrawIndexedIndirectCount`.
  Proves Invariant 1 - flat CPU time as scene size grows.

The existing `Scene3DOverlayLayer` and `OverlayRenderer` in that module are a useful reference for
pipeline variant handling, and are explicitly not a thing the mesh module should absorb.

---

## Sibling modules

### `vulkan-ffm-meshes-processing`

For CPU-side geometry processing that is large, specialized, or dependency-heavy.

Stays in `vulkan-ffm-meshes`:

- Normal generation, tangent generation, vertex welding, bounds computation. Small, universally useful,
  no dependencies, and needed by nearly every source.
- The interfaces that a simplifier or meshlet builder plugs into, plus naive reference implementations
  clearly marked as reference rather than production paths.

Goes to the sibling:

- Optimized simplification, optimized meshlet building, vertex cache optimization, quantization
  analysis, UV unwrapping, and anything biased toward one asset pipeline.
- Complex scene assembly.

### Format modules

Each file format becomes its own bindings module (if it wraps a native library) plus a thin
`GeometrySource` adapter. Never inside `vulkan-ffm-meshes`, because a format reader in the core mesh
module drags its native dependency onto everyone.

Candidates: glTF, OBJ, PLY, USD, FBX, Draco, Alembic.

Policy on native libraries: discuss before adding. The bar is a library that is genuinely universal or
small and near-perfect for a broad use of its case. Anything specialized or opinionated stays out, or
goes into a sibling module where opting in is explicit.

---

## Design principles for this document

- The mesh module depends on concepts (`UploadPlan`, `GpuCompletion`, records), never on schedulers.
- Scheduling hints are ints, small local enums, and interfaces from `vulkan-core`. Never graph types.
- Adapters live app-side first and are promoted to modules only after the same code is written three
  times.
- A capability the graph offers is reachable if and only if the mesh side declares enough intent for an
  external scheduler to act. Declaring intent is cheap; importing a scheduler is not.
- File format readers and optimized processing live in sibling modules so the core mesh module carries
  no native dependencies.
