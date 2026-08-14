# Mesh System

## Overview

The mesh system (`vulkan-ffm-mesh` module) manages geometry from source data through GPU residency
to draw dispatch. It separates seven historically-conflated concerns into independent layers that
compose freely, enabling everything from a simple triangle to GPU-driven virtual geometry with a
single command abstraction.

The system is built on one structural insight: a "mesh" is not one concept. It is a conflation of
attribute formats, memory layout, residency, topology, draw dispatch, LOD selection, and lifecycle.
Every mesh library that collapses these into one class becomes rigid under any non-trivial use case.
This system names each axis, gives it a narrow vocabulary, and keeps one thin aggregate (`Mesh`) that
nothing else depends on.

Residency (where bytes live) is solved entirely by the `vulkan-core` buffer system. The mesh module
references `IBuffer` ranges and never re-solves residency, eliminating roughly a third of the usual
scope of a mesh library before any mesh-specific code exists.

## Module Structure

```
vulkan-ffm-mesh/                       Core mesh module (vocabulary, sources, allocation, consumption)
vulkan-ffm-mesh-processing/            Sibling module for expensive/opinionated algorithms
vulkan-ffm-sample-ui-layers/layers/mesh/   Sample UILayers demonstrating each rendering path
vulkan-ffm-sample-ui-layers/layers/lodscene/  Sample LOD scene layer
```

## The Five Layers

Each layer depends only on layers below it. No layer imports a layer above it.

```
Layer 0  Vocabulary            What data exists and how one element is encoded
Layer 1  Geometry Sources      Where geometry comes from (files, procedurals, GPU)
Layer 2  Partitioning          Topology and spatial subdivision
Layer 3  Residency & Upload    Allocation strategy and data transfer
Layer 4  Consumption           How geometry becomes draw commands
Layer 5  LOD                   Representation selection and transitions (see lod.md)
```

## Package Layout

```
io.github.yetyman.vulkan.mesh/                  Layer 0: vocabulary
    AttributeSemantic.java                      Interned identity token, open to extension
    AttributeFormat.java                        Element encoding; VkFormat mapping optional
    ComponentType.java                          F32, F16, U8, S8, U16, S16, U32, S32, PACKED
    MeshLayout.java                             (semantic, format) -> (stream, offset, stride)
    InputRate.java                              VERTEX, INSTANCE
    IndexWidth.java                             U8, U16, U32
    PrimitiveTopology.java                      Interned token; vkTopology() optional
    StridedCopy.java                            One transcode step
    DeviceRange.java                            (IBuffer, offset, size, stride) triple
    ElementWindow.java                          (firstElement, elementCount)
    Mesh.java                                   Thin convenience aggregate (depended on by nothing)
    MeshOps.java                                Reallocation, swap-with-retire

io.github.yetyman.vulkan.mesh.source/           Layer 1: geometry producers
    GeometrySource.java                         The file-format and procedural seam
    AttributeStream.java                        transcodeInto(layout, dst, offset, stride, window)
    IndexStream.java                            transcodeInto(width, vertexBase, dst, ...)
    Residency.java                              ABSENT, PENDING, HOST, DEVICE, HOST_AND_DEVICE, EVICTING
    SegmentGeometrySource.java                  MemorySegment-backed: the primitive everything adapts to
    SegmentAttributeStream.java                 MemorySegment attribute stream
    SegmentIndexStream.java                     MemorySegment index stream
    ArrayAttributeStream.java                   float[]/int[] convenience adapter
    DeviceAttributeStream.java                  Already GPU-resident; not host-readable
    GeneratedAttributeStream.java               Procedural; writes straight into dst
    MutableGeometrySource.java                  Source with dirty tracking for partial updates
    MeshOutputSource.java                       helpers-core isosurface adapter
    primitives/                                 BoxSource, SphereSource, PlaneSource, GridSource, TorusSource

io.github.yetyman.vulkan.mesh.partition/        Layer 2: topology and partitioning
    GeometryPartition.java                      Range + bounds + opaque tag + sortKey
    PartitionSet.java                           Collection of partitions
    PartitionMetadata.java                      Typed per-partition side channels, densely stored
    MetadataChannel.java                        Typed key carrying its own semantics
    FloatChannelKey.java / IntChannelKey.java   Typed metadata channel keys
    PartitioningStrategy.java                   Interface for partitioning decisions
    NativePartitioning.java                     Use what the source declared
    SinglePartition.java                        Whole mesh as one partition
    TagPartitioning.java                        Material/submesh tag-based splitting
    ReferenceMeshletBuilder.java                Naive meshlet builder (for tests)

io.github.yetyman.vulkan.mesh.residency/        Layer 3: allocation and upload
    GeometryAllocator.java                      Interface: allocate, free, indexBaseMode
    GeometryAllocation.java                     Where streams and indices live
    IndexBaseMode.java                          REWRITE_ABSOLUTE, RELATIVE_WITH_DRAW_OFFSET
    DedicatedAllocator.java                     One IBuffer per stream per geometry
    SlabAllocator.java                          Over SuballocatorBuffer
    PoolAllocator.java                          One large IBuffer per stream + free list
    SparsePoolAllocator.java                    Sparse-bound virtual pool with page commit/decommit
    RingAllocator.java                          Ring buffer-based allocation
    UploadOp.java                               Sealed hierarchy (HostCopy, DeviceCopy, TranscodeStream, TranscodeIndices)
    UploadPlan.java                             Ops + scheduling hints (access, stage, queue, priority)
    UploadPlanner.java                          All the logic, no side effects
    UploadExecutor.java                         Interface for executing upload plans
    TransferBatchExecutor.java                  Standalone implementation using TransferBatch
    QueueClass.java                             TRANSFER, COMPUTE, GRAPHICS
    Priority.java                               Upload scheduling priority
    ResidencyTracker.java / DefaultResidencyTracker.java
    ResidencyListener.java                      Callback on residency state changes
    EvictionPolicy.java / LruEvictionPolicy.java
    GeometryId.java / PartitionRef.java         Identity and reference types
    PartitionLoader.java                        Coordinates partition-level loading
    RetireQueue.java                            Deferred free after GPU idle
    ResidencyView.java                          Non-blocking residency query
    ExternalAllocation.java                     Wrap externally-owned buffer ranges

io.github.yetyman.vulkan.mesh.consume/          Layer 4: work description
    GeometryBinding.java                        vertex-input, vertex-pulling, and mesh-shader views
    GeometryDrawRange.java                      Plain record: first/count/instanceCount/baseVertex/etc.
    IndirectDrawEncoder.java                    Writes VkDrawIndexedIndirectCommand into buffers
    IndirectKind.java                           INDEXED, NON_INDEXED, MESH_TASKS
    GeometryTable.java                          GPU-resident per-partition SSBO with dirty tracking
    GeometryTableRecord.java                    Fixed 64-byte base record layout

io.github.yetyman.vulkan.mesh.process/          Processing (dependency-free interfaces + small impls)
    NormalGenerator.java                        Area-weighted face normal averaging
    TangentGenerator.java                       Gram-Schmidt orthogonalization (F32x4 with handedness)
    VertexWelder.java                           Spatial-hash position deduplication
    BoundsCalculator.java                       AABB from position streams
    Simplifier.java                             Interface (impls in sibling module)
    MeshletBuilder.java                         Interface (extends PartitioningStrategy)

io.github.yetyman.vulkan.mesh.lod/              Layer 5: LOD (see lod.md for full documentation)
    RepresentationStructure.java                Sealed: Flat, Chain, Graph, Parametric
    RepresentationGraph.java                    CSR-format DAG for cluster LOD
    RepresentationNode.java                     Partition indices + error bound + bounds + tag
    RepresentationSet.java                      Top-level LOD-enabled mesh type
    LodSelector.java                            Interface: select(structure, context)
    LodSelection.java                           Sealed: Explicit, Indirect, Parametric, None
    LodContext.java                             Camera, budgets, residency, side channel
    LodPolicy.java                              Global budget arbitration across meshes
    LodBudget.java / LodBudgetEntry.java        Budget types
    TransitionMode.java                         Sealed: HardCut, Dither, Geomorph, CrossFade, Continuous
    TransitionState.java                        Mutable per-instance transition tracking
    DispatchDescription.java                    GPU LOD dispatch abstraction
    RefinementStream.java                       Progressive mesh interface
    LodChannels.java                            Well-known metadata channel keys
    ContextKey.java / ParameterDescriptor.java  Typed side channel support
    ResidencyQuery.java                         Non-blocking partition residency lookup
```

## Sibling Module: vulkan-ffm-mesh-processing

```
io.github.yetyman.vulkan.mesh.processing/
    QemSimplifier.java              Garland-Heckbert quadric error metric (pure Java)
    OptimizedMeshletBuilder.java    Greedy adjacency-based vertex reuse maximization
```

These are approach-specific, potentially expensive algorithms that many applications will not need.
The interfaces they implement (`Simplifier`, `MeshletBuilder`) live in the core module so that
alternative implementations (native meshoptimizer bindings, GPU-based simplifiers) can be swapped
without touching the mesh module.

## Four Performance Invariants

These shaped every design decision and are the answer to "will this reach ultimate speeds":

1. **Steady-state zero per-mesh CPU work.** The GPU reads a `GeometryTable` SSBO indexed by
   partition ID. The CPU records a fixed number of commands regardless of scene size. Verified by
   `MeshPoolBenchmark`: ~4.5us CPU recording time from 100 to 10,000 partitions.

2. **Record form plus primitive form.** Every per-element API (GeometryPartition, DrawRange,
   IndirectDrawEncoder) has a record form for setup and a primitive (int/long/float args) form for
   hot loops. The record form delegates to the primitive form.

3. **Source writes into caller-provided destination.** `AttributeStream.transcodeInto(...)` writes
   directly into a destination segment. No intermediate host arrays. This makes one-copy
   file-to-VRAM upload possible when source layout matches target layout.

4. **Offset-explicit, MemorySegment-based layouts.** `MeshLayout` resolves every attribute to a
   concrete (stream, offset, stride). No cursors, no sequential state. Bulk transcode can be
   parallelized across cores.

## Core Design Principles

### Composition over coupling

Nothing in the mesh module owns a material, transform, scene node, or draw method. `Mesh` is a thin
aggregate that carries no behavior beyond wiring. Applications that need only allocation or only
partitioning use those types directly.

### Open topology

`PrimitiveTopology` is an interned identity token, not an enum. Triangle lists, strips, patches,
points, meshlets, and tetrahedra are all first-class. Novel topologies (voxels, NURBS patches) can
be created without modifying the module.

### Allocator polymorphism

`GeometryAllocator` is the single interface between "where geometry lives" and everything above.
Implementations range from `DedicatedAllocator` (simple, one buffer per mesh) through
`PoolAllocator` (global pool, single bind) to `SparsePoolAllocator` (demand-paged virtual memory).
Swapping allocators requires no changes above the interface.

### Upload plan/execute separation

`UploadPlanner` produces an `UploadPlan` (pure data: ops, scheduling hints) without side effects.
`UploadExecutor` executes plans. This separation means upload logic is fully testable without a GPU,
and different backends (TransferBatch, async compute, memory-mapped) compose behind the same plan.

### Three render paths as first-class citizens

`GeometryBinding` resolves the same allocation into three views:
- **Vertex-input path**: `vertexBufferHandles()`, `vertexBufferOffsets()`, `indexBufferHandle()`
- **Vertex-pulling path**: SSBO buffer addresses for shader reads (handles formats without VkFormat)
- **Mesh-shader path**: meshlet partition table + vertex/primitive data SSBOs

No path is privileged. Shader-decoded attributes (oct-encoded normals, quantized positions) use the
vertex-pulling path because they have no VkFormat mapping. `MeshLayout.shaderDecodedSemantics()`
identifies which attributes need this.

## Usage Patterns

### Simple mesh (vertex-input path)

```java
MeshLayout layout = MeshLayout.builder()
    .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
    .stream(1).attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
    .build();

DedicatedAllocator allocator = new DedicatedAllocator(device, queue, MemoryStrategy.DEVICE_LOCAL);

Mesh mesh = Mesh.builder()
    .source(sphereSource)
    .layout(layout)
    .allocator(allocator)
    .build(arena, queue);

GeometryBinding binding = mesh.binding();
GeometryDrawRange range = mesh.partitions().partition(0).drawRange();
// Bind vertex/index buffers, issue draw
```

### GPU-driven rendering (pool allocator)

```java
PoolAllocator pool = new PoolAllocator(device, queue, MemoryStrategy.DEVICE_LOCAL,
    layout, maxVertices, maxIndices, IndexWidth.U32, IndexBaseMode.REWRITE_ABSOLUTE);

GeometryTable table = new GeometryTable(device, queue, maxPartitions, MemoryStrategy.DEVICE_LOCAL);

// Add many meshes to the same pool
for (GeometrySource source : sources) {
    Mesh mesh = Mesh.builder().source(source).layout(layout).allocator(pool).build(arena, queue);
    table.register(mesh.partitions().partition(0), mesh.allocation());
}
table.flush(queue);

// Render: one compute dispatch for culling, one indirect draw for everything
```

### Procedural geometry

```java
SphereSource sphere = new SphereSource(1.0f, 32, 16);
BoxSource box = new BoxSource(2.0f, 2.0f, 2.0f);
PlaneSource plane = new PlaneSource(10.0f, 10.0f, 4, 4);
GridSource grid = new GridSource(100.0f, 100.0f, 100, 100);
TorusSource torus = new TorusSource(1.0f, 0.3f, 32, 16);
```

### Processing pipeline

```java
// Generate normals for a mesh without them
AttributeStream normals = NormalGenerator.generate(source, arena);

// Simplify for LOD
QemSimplifier simplifier = new QemSimplifier();
GeometrySource lod1 = simplifier.simplify(source, arena, 0.5f);  // 50% triangles
GeometrySource lod2 = simplifier.simplify(source, arena, 0.25f); // 25% triangles

// Build meshlets
OptimizedMeshletBuilder meshletBuilder = new OptimizedMeshletBuilder(64, 124);
PartitionSet meshlets = meshletBuilder.partition(source, arena);
```

## Sample Layers (provisional)

Three sample UILayers in `vulkan-ffm-sample-ui-layers` demonstrate the system:

- **MeshDemoLayer** - basic vertex-input path rendering with interleaved layout
- **VertexPullingDemoLayer** - vertex-pulling with oct-encoded normals (shader-decoded format)
- **GpuDrivenDemoLayer** - pool allocator + GeometryTable + compute culling + indirect draw
- **LodSceneLayer** - discrete LOD with QemSimplifier-generated chains and screen-space error selection

These are temporary sample implementations, not production-quality components. They exist to prove
the abstractions work end-to-end.

## Divergences from Plan

1. **LOD implemented during Phase 6** rather than being deferred to a separate Phase 7 cycle. The
   lower layers proved stable enough that the LOD structural types could be built immediately.

2. **glTF adapter deferred**. The format adapter plan called for a separate bindings module plus a
   `GeometrySource` adapter. This remains future work.

3. **Columnar metadata channels** remain in the `future/` plan directory. The simpler
   `PartitionMetadata` with typed channel keys (`FloatChannelKey`, `IntChannelKey`) covers current
   needs without the full columnar system.

4. **Metadata system** uses concrete `FloatMetadataChannel`/`IntMetadataChannel` backed by dense
   arrays rather than the originally-sketched `GpuLayout`-based generic channel. Simpler, faster
   for the known use cases (error bounds, node indices, group IDs).

## Known Gaps

- **Skinned mesh integration point**: Joint weights/indices are representable as attributes, but no
  bone palette, skinning dispatch, or dual-quaternion blending integration is defined. The boundary
  between mesh and animation systems needs a protocol.
- **Morph targets / blend shapes**: No explicit multi-source composition or GPU blend dispatch.
  Representable as parallel GeometrySources but no framework for weighted blending.
- **Per-instance attribute streams**: `InputRate.INSTANCE` exists in vocabulary but the upload and
  binding paths don't explicitly handle instance-rate streams.
- **Ray tracing acceleration structures**: No BLAS/TLAS build path from geometry allocations.
- **Cross-mesh vertex deduplication**: `VertexWelder` is per-source; no shared-pool welding for
  terrain stitching or adjacent chunk seaming.
- **Compressed-at-rest formats**: Draco, meshopt compression, basis-compressed vertices need a
  decompression step before becoming a GeometrySource. No framework for this pipeline exists.

## Known Issues

- No defragmentation pass for `PoolAllocator` (free list only; no compaction)
- `SparsePoolAllocator` exists but is not exercised by any sample
- No file-format adapters (glTF, OBJ, FBX) yet
- `MeshletBuilder` interface supports mesh-shader path but no sample exercises it with actual
  `VK_EXT_mesh_shader`
- Progressive mesh (`RefinementStream`) interface defined but no implementation exists yet
- No GPU-driven LOD selection sample (only CPU-explicit `LodSceneLayer`)

## Future Advancements

### Format Adapters

- **glTF adapter**: bindings module (jextract over cgltf or tinygltf) plus a `GeometrySource`
  adapter. Sparse accessors, interleaved buffer views with unusual strides, and Draco-compressed
  primitives are the stress points. The adapter must not add anything to `GeometrySource`.
- **OBJ/FBX/USD**: each as a separate bindings module + thin adapter, same pattern.
- **Compressed vertex formats**: meshoptimizer decode, Draco decode, or basis-universal decode as
  preprocessing stages that produce standard GeometrySources.

### Mesh Shader Path

- Full `VK_EXT_mesh_shader` sample using `OptimizedMeshletBuilder` output
- Task shader amplification driven by cluster visibility from GeometryTable
- Mesh shader pulling vertices from pool SSBOs using meshlet descriptors
- Integration with the LOD system's `IndirectKind.MESH_TASKS` path

### Pool Defragmentation

- Compaction pass: copy live allocations to a new pool region, update GeometryTable offsets
- Incremental (spread across N frames) to avoid stalls
- Triggered by fragmentation ratio threshold

### Skinning and Animation Integration

- `SkinningComponent` protocol: bone palette reference + weight stream reference
- GPU compute skinning dispatch that reads rest-pose from pool, writes skinned output to a
  transient buffer (graph-managed lifetime)
- Morph target blending as a compute pre-pass producing a blended source

### Scene Integration Protocols

- `MeshComponent` interface for tree nodes that hold renderable geometry
- `CameraComponent` as a well-known tree component with projection/frustum protocol
- `TraversalView<MeshComponent>` for zero-alloc batch collection of visible renderables
- Upload scheduling as a render graph transfer node with automatic barrier insertion
- Culling as a graph compute pass consuming GeometryTable + camera, producing indirect buffer
- Residency-aware traversal: visible-set observer feeding priority load requests

### Hit Testing

- CPU path: ray-AABB traversal of tree hierarchy, then ray-triangle against partition geometry
- GPU path: compute pass reading GeometryTable bounds SSBO, writing hit results to readback buffer
- Integration with UIInputDispatcher: HitTestRequest event flowing through layer capture/bubble
- Spatial acceleration structure (BVH) over tree nodes with bounds components

### Advanced Allocation

- Streaming pool with distance-priority eviction and prefetch heuristics
- Shared vertex pools across multiple sources (terrain chunk stitching)
- Variable-rate allocation: high-detail regions get more pool space dynamically
- Memory budget enforcement with graceful degradation (evict furthest first)

### Processing Advancements

- Attribute-aware simplification (preserve UVs, normals, colors at seams)
- UV atlas generation and repacking
- Mesh optimization passes (vertex cache, overdraw, fetch optimization)
- LOD chain generation with Hausdorff distance error measurement
- Geomorph-compatible simplification (preserving vertex correspondence for interpolation)
- Native meshoptimizer bindings as an optional high-performance processing backend
