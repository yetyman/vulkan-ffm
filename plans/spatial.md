# Spatial Structures Implementation Plan

## Status

## BVH construction strategies

- **SahBuilder** — Surface Area Heuristic. Evaluates possible split planes, picks lowest cost (`traversal_cost + (SA_left/SA_parent)*N_left + (SA_right/SA_parent)*N_right`). O(n log^2 n) naive, O(n log n) with binning. Best quality, slowest build. Not started.
- **LbvhBuilder** — Linear BVH via Morton codes (quantize centroid, interleave bits, sort, build binary radix tree per Karras 2012). O(n) after sort. Lowest quality, fastest build; suitable for per-frame rebuild; produces spatially coherent DFS ordering naturally. Not started.

## R-Tree variants

- **RStarTree** — On overflow, first tries reinserting ~30% of entries before splitting. Better query performance than classic R-tree (less sibling overlap), slightly worse insert latency. Not started.
- **StrBulkLoader** — Sort-Tile-Recursive bulk load: sort by X, partition into sqrt(N) slabs, sort each slab by Y, pack into leaves. Near-optimal for static data, O(n log n), single pass. Intended as an initial bulk load, then switch to incremental `RTree` updates. Not started.

## Array-backed / loose tree variants

- **ArrayQuadtree / ArrayOctree** — flat array with implicit child addressing (`4*i+1..4*i+4` / 8-ary equivalent), fixed max depth, no per-node allocation, rebuild-from-scratch each frame (Morton-sort objects, assign to cells). Intended for high-churn scenes where full rebuild is cheaper than incremental maintenance. Not started.
- **LooseQuadtree / LooseOctree** — cell bounds expanded 2x per dimension so every object fits in exactly one cell (whichever cell's original bounds contain the object's center); queries expand the query region by cell size instead. Avoids duplicate storage and "stored at ancestor" issues. Not started; would need a new `BucketStrategy` value plus loose-bounds query-region expansion logic if folded into the existing `LinkedQuadtree`/`LinkedOctree` rather than built as standalone classes — recommend deciding which approach before starting.

## Additional GPU layouts

`GpuLayout<StructureType>` as a pattern is proven (`BVH`, `RTree`, `LinkedQuadtree`, `LinkedOctree` all ship a DFS/AABB layout). Not yet built:

- **WideBvhLayout (BVH4/BVH8)** — packs 4 or 8 child AABBs per node for SIMD-width GPU AABB tests in one instruction. ~112 bytes/node for BVH4 (parent AABB + 4 child AABBs + child mask + 4 child indices).
- **QuantizedBvhLayout** — child AABBs as 8-bit offsets relative to parent min, halving bandwidth versus full-float AABBs (~16 bytes/node for a quantized BVH2 node).
- **BvhBfsLayout** as a distinct, separately-tested layout — `BVH`'s current `DfsLayout` covers DFS; a dedicated BFS/level-order variant (children at `2*index+1`/`2*index+2`) is not separately implemented, though `TraversalOrder.BFS` exists as an enum value for future layouts to target.
- **FlatGridLayout** — uniform grid as a flat cell array (`objectCount` + fixed-max or indirected `objectIndices` per cell), for `DenseGrid`/`SparseGrid` GPU upload. Not implemented; these grids do not yet have a `GpuLayout`.

## Stable node addressing policy decision

The original plan flagged two addressing strategies for incremental GPU sync and asked for a decision: **index-stable** (fixed array indices, `DirtyTracker.isFullRebuild()` as the escape hatch when topology changes) versus **generation-counted** (pointer-based structures increment a global generation on rebalance; external system detects staleness via generation comparison). The shipped `DirtyTracker`/`SpatialNode.index()` contracts follow the index-stable approach uniformly across all structures (including pointer-based `LinkedQuadtree`/`LinkedOctree`), so the generation-counted alternative was implicitly not adopted. This should be considered settled unless a future structure specifically needs the generation-counted model — worth confirming before implementing streaming/chunk features below, so they build on the same addressing contract.

## Streaming / integration coordination layer

Not part of `helpers-core` by design (per the original plan's non-goals, still valid) — these would live in `vulkan-core`, `vulkan-ffm-graph`, or a new dedicated module:

- **Frame graph node for spatial sync** — reads a structure's `DirtyTracker`, uploads changed nodes via `TransferBatch`. See the "Potential Integration with the Render Graph and GPU Capabilities" section of `docs/helpers/math.md` for the current (call-site composition, not yet a shipped bridge) state of this integration point.
- **Slotted spatial buffer / chunk streaming** — ring buffer of fixed-size slots for chunk-based streaming (e.g. Minecraft-style world loading); the spatial structure serializes into whatever `ByteBuffer` it's given, slot lifecycle is the buffer system's concern. Not started; see `plans/spatial-demos.md` Demo 16 for the intended visual target.
- **Meshlet pool / mesh streaming coordination** — a BVH over meshlets with one leaf per meshlet, each leaf's primitive range pointing into a sub-allocated vertex/index buffer region; `DirtyTracker` drives incremental re-upload of just the spatial index when meshlets stream in/out. Requires coordination between this module's spatial structures, a buffer sub-allocator, and the frame graph. Not started.
- **GPU-side traversal shaders** (frustum culling compute pass reading a GPU-side BVH and outputting visible instance IDs to an indirect draw buffer; LBVH GPU builder) — lives with the shader system when started, not helpers-core. Not started.

