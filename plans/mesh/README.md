# Mesh System Plan

Status: planning only. Nothing in this directory is implemented yet. These documents exist to pin
down intent and non-goals before any code is written, specifically to prevent the scope explosion
that mesh systems are prone to.

Target module: `vulkan-ffm-mesh`
Depends on: `vulkan-core`, `helpers-core`
Must not depend on: `vulkan-ffm-graph`, `vulkan-ffm-node-trees`, `vulkan-ffm-sample-ui-layers`, `sample-app`

---

## Summary

"Mesh" is not one concept. It is a conflation of roughly seven orthogonal axes. Every mesh library
that gets scrapped got scrapped because one class touched all seven at once, which made every new
requirement a cross-cutting change. The plan here is to name the axes, give each a narrow
vocabulary, and keep one thin aggregate type that nothing else depends on.

### The seven axes

| # | Axis | Question it answers | Owner |
|---|------|---------------------|-------|
| 1 | Attribute semantics and element formats | What data exists, how is one element encoded | `vulkan-ffm-mesh` Layer 0 |
| 2 | Layout | Interleaved vs planar, quantized vs padded, stride/offset mapping | `vulkan-ffm-mesh` Layer 0 |
| 3 | Residency | Where the bytes are right now | `vulkan-core` buffers (already solved) |
| 4 | Topology and partitioning | Triangles, strips, patches, meshlets, points, tetrahedra; submeshes, clusters, chunks | `vulkan-ffm-mesh` Layer 2 |
| 5 | Work description | How a partition becomes draw, dispatch, or BLAS-build arguments | `vulkan-ffm-mesh` Layer 4 |
| 6 | Selection | LOD and culling granularity, and where the decision is made | `vulkan-ffm-mesh` Layer 5 (deferred) |
| 7 | Provenance and lifecycle | File, procedural, GPU-generated; versioning, eviction | `vulkan-ffm-mesh` Layers 1 and 3 |

Axis 3 is already fully solved by the buffer strategy system in `vulkan-core`. The mesh module
references `IBuffer` ranges and never re-solves residency. That removes roughly a third of the usual
scope of a mesh library before any code is written.

### The layers

```
Layer 0  Vocabulary            AttributeSemantic, AttributeFormat, MeshLayout        (no Vulkan, no GPU)
Layer 1  Geometry sources      AttributeStream, IndexStream, GeometrySource          (residency-agnostic)
Layer 2  Partitioning          PrimitiveTopology, GeometryPartition, PartitionSet    (open topology)
Layer 3  Residency and upload  GeometryAllocator, UploadPlan/Planner/Executor        (built on IBuffer)
Layer 4  Consumption           GeometryBinding, GeometryDrawRange, GeometryTable     (plain data, no recording)
Layer 5  Selection / LOD       deferred until Layers 0-4 are proven by samples
```

### The four performance invariants

These are cheap to honor now and expensive to retrofit. They are the answer to "will this reach
ultimate speeds", and they are documented in full in `07-performance-invariants.md`.

1. Steady state involves zero per-mesh CPU work. This requires a GPU-resident descriptor table
   (`GeometryTable`), not merely GPU-resident vertex data.
2. Every per-element API has a record form for setup and a primitive form for hot loops, and the
   record form is implemented on top of the primitive form.
3. `GeometrySource` writes into a caller-provided destination. It never produces host arrays as its
   primary interface. This is what makes one-copy file-to-VRAM upload possible.
4. Layouts are offset-explicit and `MemorySegment`-based, never cursor-based, so bulk transcode can
   be parallelized across cores.

---

## Structure

### Package layout

```
io.github.yetyman.vulkan.mesh/                  Layer 0 - vocabulary, no Vulkan, no GPU
    AttributeSemantic.java                      interned identity token, open to extension
    AttributeFormat.java                        element encoding; VkFormat mapping optional
    ComponentType.java                          F32, F16, U8, S8, U16, S16, U32, S32, PACKED
    MeshLayout.java                             (semantic, element) -> (stream, offset, stride)
    MeshLayout.Builder.java                     interleaved / planar / hybrid / explicit
    InputRate.java                              VERTEX, INSTANCE
    IndexWidth.java                             U8, U16, U32
    StridedCopy.java                            one transcode step; reduces to writeStrided
    DeviceRange.java                            (IBuffer, offset, size, stride) triple
    Mesh.java                                   thin convenience aggregate, depended on by nothing
    Mesh.Builder.java

io.github.yetyman.vulkan.mesh.source/           Layer 1 - producers, residency-agnostic
    GeometrySource.java                         the file-format and procedural seam
    AttributeStream.java                        transcodeInto(layout, dst, offset, stride, window)
    IndexStream.java                            transcodeInto(width, vertexBase, dst, ...)
    Residency.java                              ABSENT, PENDING, HOST, DEVICE, HOST_AND_DEVICE, EVICTING
    ElementWindow.java                          (firstElement, elementCount)
    SegmentGeometrySource.java                  MemorySegment-backed; the primitive everything adapts to
    SegmentAttributeStream.java
    ArrayAttributeStream.java                   float[]/int[] convenience adapter
    DeviceAttributeStream.java                  already GPU-resident; not host-readable
    GeneratedAttributeStream.java                procedural; writes straight into dst
    primitives/                                 box, sphere, plane, grid, torus generators

io.github.yetyman.vulkan.mesh.partition/        Layer 2 - topology and partitioning
    PrimitiveTopology.java                      interned token; vkTopology() optional
    GeometryPartition.java                      range + bounds + opaque tag + sortKey
    PartitionSet.java                           collection + optional SpatialStructure hierarchy
    PartitionMetadata.java                      typed per-partition side channels, densely stored
    MetadataChannel.java                        typed key carrying its own GpuLayout
    PartitioningStrategy.java
    NativePartitioning.java                     use what the source declared. default.
    SinglePartition.java
    TagPartitioning.java
    ReferenceMeshletBuilder.java                naive, marked reference-only, for tests

io.github.yetyman.vulkan.mesh.residency/        Layer 3 - allocation, upload, residency
    GeometryAllocator.java
    GeometryAllocation.java
    IndexBaseMode.java                          REWRITE_ABSOLUTE, RELATIVE_WITH_DRAW_OFFSET
    DedicatedAllocator.java                     one IBuffer per stream per geometry
    SlabAllocator.java                          over SuballocatorBuffer
    PoolAllocator.java                          one large IBuffer per stream + free list
    SparsePoolAllocator.java                    sparse-bound virtual pool
    UploadOp.java                               sealed: HostCopy, DeviceCopy, Transcode, TranscodeIndices
    UploadPlan.java                             ops + access/stage/queueClass/priority/deferrable
    UploadPlanner.java                          all the logic, no side effects
    UploadExecutor.java                         interface
    TransferBatchExecutor.java                  the standalone implementation
    QueueClass.java                             TRANSFER, COMPUTE, GRAPHICS
    Priority.java
    ResidencyTracker.java
    ResidencyListener.java
    EvictionPolicy.java
    LruEvictionPolicy.java                      the default
    GeometryId.java
    PartitionRef.java

io.github.yetyman.vulkan.mesh.consume/          Layer 4 - work description, no recording
    GeometryBinding.java                        vertex-input, vertex-pulling, and mesh-shader views
    GeometryDrawRange.java                      plain record
    IndirectDrawEncoder.java                    primitive form + record form
    IndirectKind.java                           INDEXED, NON_INDEXED, MESH_TASKS
    GeometryTable.java                          GPU-resident per-partition descriptor table
    GeometryTableRecord.java                    fixed small base record layout

io.github.yetyman.vulkan.mesh.process/          Small, universal, dependency-free processing
    NormalGenerator.java
    TangentGenerator.java
    VertexWelder.java
    BoundsCalculator.java
    Simplifier.java                             interface only; impls in the sibling module
    MeshletBuilder.java                         interface only; impls in the sibling module

io.github.yetyman.vulkan.mesh.lod/              Layer 5 - DEFERRED, do not create yet
```

### Classes by axis

| Axis | Primary types | Package |
|------|---------------|---------|
| 1 - Semantics and formats | `AttributeSemantic`, `AttributeFormat`, `ComponentType` | `mesh` |
| 2 - Layout | `MeshLayout`, `InputRate`, `IndexWidth`, `StridedCopy` | `mesh` |
| 3 - Residency | `Residency`, `DeviceRange`, `ResidencyTracker`, `EvictionPolicy`; plus all of `vulkan-core` `buffers` | `mesh`, `mesh.residency`, `vulkan-core` |
| 4 - Topology and partitioning | `PrimitiveTopology`, `GeometryPartition`, `PartitionSet`, `PartitionMetadata`, `MetadataChannel`, `PartitioningStrategy` | `mesh.partition` |
| 5 - Work description | `GeometryBinding`, `GeometryDrawRange`, `IndirectDrawEncoder`, `GeometryTable` | `mesh.consume` |
| 6 - Selection | deferred | `mesh.lod` |
| 7 - Provenance and lifecycle | `GeometrySource`, `AttributeStream`, `IndexStream`, `GeometryAllocator`, `UploadOp`, `UploadPlan`, `UploadPlanner`, `UploadExecutor` | `mesh.source`, `mesh.residency` |

### Types this module deliberately does not contain

`Drawable`, `Material`, `MaterialSlot`, `Transform`, `SceneNode`, `MeshInstance`, `MeshRenderer`,
`GeometryConsumer`, `LodGroup`, any `*Loader` for a file format, any optimized simplifier or meshlet
builder.

### Dependency direction within the module

Layers depend only downward. Layer 4 reads Layer 3 output, Layer 3 reads Layers 1 and 2, Layers 1 and
2 read Layer 0, Layer 0 depends on nothing but `vulkan-core` enums and `helpers-core` geometry. No
layer imports a layer above it, and `Mesh` is imported by no layer at all.

### Explicit non-goals

Things deliberately excluded from `vulkan-ffm-mesh`:

- `Drawable` or anything that draws itself. Its job splits into `GeometryBinding` (data) and
  app-side command recording (behavior).
- `Material`. Partitions carry an opaque `long tag()` the module never interprets.
- Transforms, scene hierarchy, instance transforms, culling components. Those consume meshes; meshes
  never know about them.
- Any dependency on `vulkan-ffm-graph` or `vulkan-ffm-node-trees`.
- LOD, until Layers 0-4 are clean and proven by sample apps. See `06-lod.md` for the categorization
  work already done, which is recorded but not to be built yet.
- File format readers. Each becomes its own bindings module plus a thin `GeometrySource` adapter.
- Optimized simplifiers and meshlet builders. The plug-in interfaces live here; the optimized
  implementations go to a sibling module.
- Skinning execution. Joint and weight attributes are attributes and belong here; the animation
  system does not.

### Deletions

Both confirmed unused and superseded by this plan:

- `vulkan-core/.../highlevel/VulkanMesh.java` - owns and closes its own buffers, has `draw()`.
  Precisely the biased mesh this plan avoids.
- `sample-app/.../complex/models/GLTFLoader.java` - verified to have no references outside itself.

---

## Document index

| Document | Contents |
|----------|----------|
| `00-prerequisites.md` | `vulkan-core` changes required before Layer 1. Independently valuable. |
| `01-vocabulary.md` | Layer 0. `AttributeSemantic`, `AttributeFormat`, `MeshLayout`. |
| `02-geometry-sources.md` | Layer 1. `AttributeStream`, `IndexStream`, `GeometrySource`, transcode-into-destination. |
| `03-partitioning.md` | Layer 2. Topology as an open token, partitions, partition sets, spatial hierarchy. |
| `04-residency-and-upload.md` | Layer 3. Allocators, upload plan decomposition, residency states, eviction. |
| `05-consumption.md` | Layer 4. `GeometryBinding`, `GeometryDrawRange`, `IndirectDrawEncoder`, `GeometryTable`. |
| `06-lod.md` | Layer 5. Recorded categorization. Deferred, not to be built. |
| `07-performance-invariants.md` | The four invariants in full, plus the bulk-render path end to end. |
| `08-integration-boundaries.md` | How graph and node-tree integration happens without coupling. Sibling modules. |
| `09-roadmap.md` | Phases, what each phase proves, testing and sample-app strategy. |

## Reading order

For a first pass: this file, then `07-performance-invariants.md`, then `09-roadmap.md`. Those three
carry the intent. The layer documents are reference detail.

For implementation: `00-prerequisites.md` first, in order, then layers in order.
