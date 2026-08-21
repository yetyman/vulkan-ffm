# GPU-Backed Spatial Structure Variants

## Idea

Spatial data structures (quad trees, KD-trees, octrees, BVHs) all have a "flattened for GPU"
representation — node arrays, AABB arrays, index arrays — that gets uploaded for GPU-side
traversal (culling, ray queries, broadphase, LOD selection). Today these are linearized ad-hoc
and uploaded manually. With deferred mirrored buffers and dirty tracking now available, GPU
variants of these structures could maintain their flattened GPU representation as a first-class
concern, with automatic partial-upload on mutation.

## Approach: GPU Variants, Not Wrappers

The pure-CPU structures in `helpers-core` stay as-is — no Vulkan dependency, no buffer imports.
Instead, GPU variants are separate classes (likely in a module that depends on both `helpers-core`
and `vulkan-core`) that implement the same logical operations but internally maintain a
`ManagedBuffer(DEVICE_LOCAL_MIRRORED, deferred=true)` for the linearized representation.

A GPU variant IS-A spatial structure with an additional contract: its flattened layout is always
available as a bindable SSBO, and mutations automatically dirty-track the affected regions.

## Example: GpuQuadTree

- Internal state: the quad tree's node structure (same as CPU variant)
- Additionally holds: a `ManagedBuffer` in deferred mirrored mode containing the flattened node array
- On insert/remove/split/merge: updates the internal tree AND writes the affected node range
  into the buffer via `acquireWrite`. The dirty strategy tracks which pages changed.
- On flush: `flushDirty(queue)` transfers only mutated regions to the GPU
- Exposes: `IBuffer buffer()` for binding as SSBO in a compute shader

## Where This Fits

- GPU culling: the BVH or quad tree is traversed by a compute shader that reads the node SSBO
  and emits indirect draw commands
- Streaming terrain: octree nodes are committed/decommitted as the camera moves, only newly
  loaded subtrees are uploaded
- Physics broadphase: dynamic AABB tree updated per frame, only moved objects dirty their nodes
- Ray queries: BVH rebuilt partially (refit) after animation, only refitted nodes transferred

## Dirty Tracking Alignment

The buffer's `DirtyStrategy` granularity should match the node stride. For a quad tree with
32-byte nodes, a `BitSetDirtyStrategy` with 32-byte page granularity means each node mutation
dirties exactly one bit — no waste, no over-transfer. This is configurable at construction.

## Module Placement

Uncertain. Options:
- A `vulkan-ffm-spatial` module (new) that depends on `helpers-core` + `vulkan-core`
- Inside `vulkan-ffm-mesh` if these are primarily mesh-culling structures
- Inside `vulkan-core` as a `buffers/spatial/` subpackage if they're general enough

Leaning toward a separate module to keep the dependency graph clean, but this depends on how
many structures end up here and whether they're mesh-specific or general.

## Scope

Not implementing now. This plan exists so the idea isn't re-derived. Prerequisites are stable:
- Deferred mirrored buffers (done)
- Dirty tracking with configurable page granularity (done)
- Multi-region flush (done)

When a concrete use case drives it (GPU culling, streaming, physics), pick a structure and build
the GPU variant. The `GeometryTable` migration is the proof-of-concept that this pattern works.
