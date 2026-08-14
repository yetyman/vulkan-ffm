# Possible Advancements

Status: catalogue of known directions. None of these are scheduled. Each is believed to be
supportable by the existing system design without structural rework — they are additive features
that fit within the existing layer boundaries and interface contracts.

Items are grouped by the axis they extend. Items marked with (protocol) need a new interface or
integration point defined. Items marked with (impl) are concrete implementations of existing
interfaces. Items marked with (module) would live in a new or sibling module.

---

## Mesh System Advancements

### Format Adapters (module)

Each format becomes its own bindings module + thin `GeometrySource` adapter.

- **glTF**: jextract over cgltf or tinygltf. Stress points: sparse accessors, interleaved buffer
  views with unusual strides, Draco-compressed primitives, morph targets as multiple accessors.
  The adapter must not add anything to `GeometrySource`.
- **OBJ**: trivial; text parsing into SegmentGeometrySource.
- **FBX**: Autodesk SDK bindings or ufbx. Complex but the output is standard geometry.
- **USD**: Pixar's library. Large dependency but industry-standard scene description.
- **Compressed vertex formats**: meshoptimizer decode, Draco decode as preprocessing stages that
  produce standard GeometrySources before the upload path sees them.

### Skinning and Animation (protocol + impl)

The mesh module correctly excludes animation execution. What's missing is the integration point:

- **SkinComponent protocol**: declares bone palette reference + weight/joint attribute streams.
  Lives in the mesh module as an interface; implementations live in an animation module.
- **GPU compute skinning dispatch**: reads rest-pose from pool SSBO, reads bone matrices from UBO,
  writes skinned positions/normals to a transient buffer. This dispatch could be a graph compute
  node with declared resource dependencies.
- **Dual-quaternion blending**: alternative skinning strategy behind the same interface.
- **Bone palette management**: per-draw-call bone subset selection for hardware with limited
  uniform space.

The mesh system already handles joint weights and indices as attributes (`AttributeSemantic` can
represent `JOINTS_0`, `WEIGHTS_0`). What's missing is the execution and output binding.

### Morph Targets / Blend Shapes (protocol + impl)

- **MorphTargetSet**: associates N parallel GeometrySources (deltas) with a base source.
- **GPU blend dispatch**: compute shader that reads base + weighted deltas, writes blended output.
- **Weight animation integration**: morph weights driven externally (animation system, audio, etc.)
- **Sparse morph targets**: most vertices have zero delta; sparse representation avoids full-mesh
  copy for each target.

Representable today as parallel GeometrySources but no framework for weighted composition exists.

### Per-Instance Attribute Streams (impl)

`InputRate.INSTANCE` exists in the vocabulary. Missing pieces:

- `MeshLayout.Builder` explicitly marking streams as instance-rate
- Upload path handling instance-rate streams with instance count rather than vertex count
- `GeometryBinding` exposing instance buffer handles separately
- Pool allocator supporting mixed vertex-rate and instance-rate streams

### Ray Tracing Acceleration Structures (protocol + impl)

- **BLASBuilder**: takes a `GeometryAllocation` and produces a `VkAccelerationStructureKHR`.
  Parallel to `GeometryBinding` but for the ray tracing path.
- **TLASBuilder**: takes instance transforms + BLAS references, builds top-level structure.
- **Integration with GeometryTable**: BLAS handle stored alongside draw parameters so the same
  table serves both rasterization and ray tracing.
- **Rebuild vs. refit**: strategy interface for when to full-rebuild vs. cheap-refit on updates.

### Cross-Mesh Vertex Deduplication (impl)

- **SharedVertexWelder**: operates across multiple sources that share a pool allocation. Identifies
  shared boundary vertices (terrain chunk edges, modular architecture seams) and merges them.
- **Stitch indices**: generates index connectivity across source boundaries for seamless rendering.

### Mesh Shader Path (impl)

- Full `VK_EXT_mesh_shader` sample using `OptimizedMeshletBuilder` output
- Task shader amplification driven by cluster visibility from GeometryTable
- Mesh shader pulling vertices from pool SSBOs using meshlet descriptors
- `IndirectKind.MESH_TASKS` dispatch through `IndirectDrawEncoder`
- Integration with LOD system's cluster DAG (task shader does LOD selection per meshlet group)

### Pool Defragmentation (impl)

- Compaction pass: copy live allocations to a new contiguous region, update GeometryTable offsets
- Incremental: spread copies across N frames (budget: X MB per frame) to avoid stalls
- Triggered by fragmentation ratio threshold
- Requires RetireQueue coordination to avoid copying ranges in-flight GPU reads reference

### Advanced Allocation Strategies (impl)

- **Streaming pool with distance-priority eviction**: furthest-from-camera evicted first when
  budget is exceeded.
- **Variable-rate allocation**: regions closer to camera get denser pool packing.
- **Memory budget enforcement with graceful degradation**: global memory watcher that triggers
  eviction policies when VRAM pressure rises.
- **Shared vertex pools for terrain stitching**: adjacent chunks share boundary vertex ranges.

### Processing Advancements (module: vulkan-ffm-mesh-processing)

- **Attribute-aware simplification**: preserve UV seams, normal discontinuities, vertex colors
  at simplification boundaries. Extends QemSimplifier with per-attribute quadrics.
- **UV atlas generation**: chart cutting, parameterization, packing into texture atlases.
- **Mesh optimization passes**: vertex cache optimization (Tom Forsyth / Tipsify), overdraw
  reduction (sort by average facing direction), vertex fetch optimization (reorder for cache).
- **Hausdorff distance measurement**: ground-truth error between simplified and original meshes
  for accurate LOD error bounds.
- **Geomorph-compatible simplification**: produces vertex correspondence mappings alongside
  simplified geometry so vertex shaders can interpolate between levels.
- **Native meshoptimizer bindings**: optional high-performance backend wrapping Arseny Kapoulkine's
  meshoptimizer library via jextract for production-quality optimize/simplify/meshlet operations.
- **Subdivision surface evaluation**: Catmull-Clark or Loop subdivision as a GeometrySource
  adapter that refines a control cage.

### Multi-Draw with Per-Draw Data (protocol)

- **DrawDataChannel**: parallel array of per-draw material IDs, transform indices, texture array
  layers, written alongside indirect draw commands in the same buffer or a parallel SSBO.
- **GeometryTableRecord extension**: the base record has `tag` and `sortKey`; paradigm-specific
  data goes in metadata channels. Formalize the pattern for renderers that need per-draw push
  constants or dynamic offsets.

---

## Supportability Notes

All items above are believed to fit within the existing design without structural rework because:

1. **GeometrySource is the universal input seam.** Any new format or producer just implements this
   interface. The upload, allocation, and binding layers never change.

2. **GeometryAllocator is the universal allocation seam.** New allocation strategies (streaming,
   variable-rate, defrag-capable) implement this interface. Everything above is unaffected.

3. **MeshLayout is open.** New attribute semantics, component types, and formats can be added
   without modifying existing code. The identity-token pattern for `AttributeSemantic` and
   `PrimitiveTopology` means novel data types don't require module changes.

4. **Metadata channels are extensible.** `FloatChannelKey` and `IntChannelKey` allow new per-partition
   data to be attached without changing `GeometryPartition` or `PartitionSet`.

5. **Processing lives in a sibling module.** Expensive algorithms don't burden the core module.
   New algorithms add to the sibling without touching the interface module.

6. **The three render paths are parallel, not exclusive.** Adding ray tracing or mesh shaders
   doesn't disable or conflict with vertex-input or vertex-pulling paths.

The protocol items (skinning, morph targets, ray tracing) are the only ones that need new interface
types. Even these are additive — they define new contracts without modifying existing ones.
