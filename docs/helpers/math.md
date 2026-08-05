# helpers-core Math Library

## Overview

Pure math library in `helpers-core/src/main/java/io/github/yetyman/helpers/math/`. Provides mutable-first vector/matrix/quaternion types, geometric primitives, a fully implemented spatial-structures subsystem (grids, trees, hex/geodesic grids, isosurface extraction), and GPU-upload integration via `BufferWritable` / `GpuLayout`.

Depends on `vulkan-core` for the `BufferWritable` and `GpuLayout` interfaces only.

---

## Implemented Types

### Foundation (`math/`)

| Type | Fields | byteSize | Notes |
|------|--------|----------|-------|
| Vec2 | x, y | 8 | |
| Vec3 | x, y, z | 12 | |
| Vec4 | x, y, z, w | 16 | |
| Mat3 | 9 floats, column-major | 36 | mColumnRow naming |
| Mat4 | 16 floats, column-major | 64 | mColumnRow naming, m30/m31/m32 = translation |
| Quaternion | x, y, z, w | 16 | identity = (0,0,0,1) |
| DualQuaternion | real(x,y,z,w) + dual(x,y,z,w) | 32 (via `REAL_DUAL` GpuLayout) | Encodes rotation + translation as `q_real + epsilon * q_dual` for skinning/rigid-body blending without candy-wrapper artifacts; non-uniform scale not representable; supports `blend`/`accumulate` (DLB), `toMat4`, `transformPoint` |
| Transform | position + rotation + scale | N/A | lazy local/world matrix, parent hierarchy |
| MathUtil | static constants/helpers only | N/A | `EPSILON`, `PI`/`TWO_PI`/`HALF_PI`, deg/rad conversion, `clamp`, `lerp`, `smoothstep`, `fract`, `sign`, `step`, `min3`/`max3` |

All vector/matrix/quaternion types implement `BufferWritable` (except Transform, which is a composite, and MathUtil, which is a static-only utility). All have `BuildStrategy` + `Builder` with `eager()`/`lazy()` intent flags.

### Geometry (`math/geometry/`)

| Type | Purpose |
|------|---------|
| Plane | normal + signed distance, classify point |
| Ray | origin + direction, pointAt(t) |
| AABB | min/max corners, containment, overlap, transform |
| OBB | center + half-extents + orientation |
| Sphere | center + radius, merge |
| Frustum | 6 planes from VP matrix, test AABB/Sphere/point |
| Intersections | static ray-plane, ray-AABB, ray-sphere, ray-OBB, frustum tests |
| ContainmentResult | INSIDE / OUTSIDE / INTERSECT |

All geometry types have `BuildStrategy` + `Builder` with `eager()`/`lazy()`.

### Spatial Structures (`math/spatial/`) — Implemented

Fully implemented (superseding the earlier "planned" spec in `plans/spatial.md`). All mutable structures implement the shared `SpatialStructure<T>` interface (see below); isosurface extraction is a separate, stateless static-method family that operates on scalar fields rather than item collections.

#### Tree / grid structures (`spatial/{quadtree,octree,kdtree,rtree,bvh,grid}/`)

| Type | Summary |
|------|---------|
| LinkedQuadtree\<T\> | Pointer-based quadtree with configurable bucket strategy (`QuadtreeConfig`); partitions X/Z, 4 children per node, Y used for queries only; incremental insert/remove/update with automatic split/merge. |
| LinkedOctree\<T\> | Pointer-based octree with configurable bucket strategy (`OctreeConfig`); 8 children per node; incremental insert/remove/update with automatic split/merge. |
| KDTree\<T\> | Binary space partition alternating axes; excellent for static point clouds and nearest-neighbor queries; rebuilds fully on every mutation rather than incrementally rebalancing. |
| RTree\<T\> | Balanced R-tree, all leaves at the same depth, configurable split strategy (quadratic or linear) via `RTreeConfig`. |
| BVH\<T\> | Bounding Volume Hierarchy binary tree of AABBs with pluggable construction strategy via `BvhBuilder` (default `MedianSplitBuilder`); incremental insert/remove triggers a full rebuild since BVHs do not rebalance well incrementally. |
| DenseGrid\<T\> | Flat fixed-resolution array of cells with O(1) coordinate access; fixed world bounds; best for small bounded worlds. |
| SparseGrid\<T\> | Hash-mapped grid storing only occupied cells; O(1) insert/remove/lookup; clear cost is O(entries) not O(grid size); best for large mostly-empty worlds. |
| HierarchicalGrid\<T\> | Multi-resolution stack of sparse grids (mipmap-like); cell size doubles per level; each object is placed at the coarsest level where it fits in at most 2x2x2 cells, avoiding the "one object spans 100 cells" problem of flat grids. |

#### Hex and geodesic grids (`spatial/hex/`, `spatial/geodesic/`)

| Type | Summary |
|------|---------|
| HexCoord | Axial hex coordinate (q, r) with implicit third cube axis `s = -q - r`; provides the 6 neighbor directions and distance calculations. |
| HexGrid\<T\> | Sparse hash-mapped hex grid keyed by axial coordinate; only occupied cells consume memory; supports range and ring queries. |
| HexLayout | Axial-to-pixel and pixel-to-axial conversion for flat-top/pointy-top hex rendering. |
| GeodesicGrid | Subdivided icosahedron producing a near-uniform sphere grid of hexagonal cells (12 pentagons at the poles/vertices of the base icosahedron); subdivision level N has `10*4^N + 2` vertices; each vertex is a `GeodesicCell` (dual of the triangulation) carrying its neighbor list and a pentagon flag; `findCell(direction)` does nearest-vertex lookup for a point on the unit sphere. |
| GeodesicCell | One cell of a `GeodesicGrid` — index, position, neighbor indices, and whether it is a 5-neighbor pentagon cell rather than a 6-neighbor hexagon. |

#### Isosurface extraction (`spatial/isosurface/`)

Stateless static extractors that sample a `ScalarField2D`/`ScalarField3D` functional interface over a grid and emit either a `ContourOutput` (2D: vertices + line segments) or a `MeshOutput` (3D: vertices + triangle indices). Both output types implement `BufferWritable` for direct GPU upload.

| Type | Summary |
|------|---------|
| MarchingSquares | Classic 2D marching squares over a rectangular grid; 16 cell configurations from 4 corner in/out bits; see the dedicated ambiguity-handling note below for cases 5 and 10. |
| MarchingTriangles | Contour extraction on an equilateral triangulated grid (offset/staggered rows); only 3 edges per cell (2^3 = 8 configurations) so there is no ambiguous saddle case at all. |
| MarchingHexagons | Contour extraction on a flat-top hexagonal grid using axial coordinates; see the dedicated double-edge-crossing note below. |
| MarchingCubes | 3D marching cubes with a procedurally generated lookup table (15 base cases x 24 cube rotations, computed once in a static initializer); ambiguous face configurations are resolved by a configurable `AmbiguityResolution` enum (`POSITIVE`/`NEGATIVE` diagonal), selected per-instance at construction so both conventions remain available and each is internally watertight. |
| MarchingTetrahedra | Splits each cube into 6 tetrahedra before marching, which sidesteps the ambiguous face configurations of Marching Cubes entirely (tetrahedra have no ambiguous cases) at the cost of more primitives. |
| SurfaceNets | Dual-contouring-family method placing one vertex per surface-crossing cell at the average of its edge crossings, then connecting adjacent cells with quads (split into triangles); produces smoother meshes with fewer triangles than Marching Cubes. |
| DualContouring | Hermite-data variant of Surface Nets: each edge crossing stores position and normal so sharp features can be preserved; true implementations solve a QEF (quadric error function) least-squares minimization for vertex placement, but this implementation simplifies vertex placement to the average of crossings (equivalent to SurfaceNets) while still carrying gradient-based normals. |
| AdaptiveMarchingCubes | Builds a `LinkedOctree` over an input point cloud so the octree subdivides naturally in dense regions, derives a smooth density field from per-cell point counts with falloff weighting, then marches each octree leaf at its local resolution — concentrating mesh detail where point density is high. |
| ScalarField2D / ScalarField3D | `@FunctionalInterface` sampling contracts (`sample(x,y)` / `sample(x,y,z)`) implemented by callers (procedural noise, voxel volumes, point-cloud density fields, etc.) and consumed by every extractor above. |
| ContourOutput | 2D extraction result: `Vec2` vertex list + index-pair segment list; `BufferWritable` (write-only; `readFrom` is unsupported). |
| MeshOutput | 3D extraction result: `Vec3` vertex list + flat triangle index list; `BufferWritable` with separate `writeVertices`/`writeIndices` helpers for uploading position and index buffers independently. |

##### Special handling: ambiguous cases in Marching Squares

Cell configurations `5` and `10` are the classic saddle-point ambiguity in 2D marching squares: exactly the two diagonally-opposite corners are "inside" (bottom-left+top-right for `5`, bottom-right+top-left for `10`), so the four edge crossings could be connected as either two separate short segments or, incorrectly, as a single crossing pair — the choice affects whether the contour appears pinched together or separated at that saddle. `MarchingSquares.processCell` resolves both cases explicitly by always emitting **two disjoint segments per ambiguous cell** (case 5: left-to-top and bottom-to-right; case 10: left-to-bottom and right-to-top) rather than picking a single diagonal. This is a fixed convention (not a value-based disambiguation like averaging the four corner samples), so it is consistently one topological choice across the whole field — adequate for typical continuous scalar fields, but note that (unlike `MarchingCubes`, which exposes a configurable `AmbiguityResolution` enum) this choice is not currently parameterized in `MarchingSquares`.

##### Special handling: doubled-up double edge crossings in Marching Hexagons

Each hex cell has 6 possible edge crossings (one per neighbor direction) rather than the 4 of a square cell, so a cell can have more than one pair of crossings when the field varies enough within one hex (e.g. two separate contour strands passing through the same cell, or noisy fields near the iso level). `MarchingHexagons.extract` handles this by collecting every crossing direction for a cell into `crossingDirs`, sorting by direction index, and connecting **consecutive pairs** in sorted order; when there are more than 2 crossings (4 or 6), an additional wraparound segment connects the last crossing back to the first. This means a 4-crossing cell produces 3 segments (not 2), which over-connects compared to a "true" pairing into two independent strands — it is a deliberate simplification that keeps the algorithm branch-free for any even crossing count at the cost of occasionally joining what should be two disjoint contour strands inside a single ambiguous hex. Odd crossing counts (a single unpaired crossing) are left unconnected since `crossingDirs.size() >= 2` gates all segment emission.

#### Shared spatial interfaces (`spatial/`)

| Type | Summary |
|------|---------|
| SpatialStructure\<T\> | Root mutable interface: extends `SpatialQuery<T>` (read side) and `BufferWritable` (structures can serialize their default in-memory layout directly to a buffer). Adds `insert`/`insertAll` (single + bulk, with deferred rebalancing for bulk loads)/`remove`/`update`/`rebuild`/`clear`/`size`/`worldBounds`/`dirtyTracker()`/optional `visitNodes(NodeVisitor)` for debug/visualization traversal. Every tree/grid type above implements this one interface, so callers can swap structures without changing call sites. |
| SpatialQuery\<T\> | Read-only query contract shared by all structures: AABB/Sphere/Ray/Frustum queries in three flavors each — allocating (`List<T>`), streaming (`Stream<T>`), and allocation-free (append to a caller-provided `List<T>`, returning count) — plus point `contains`/`nearest` and non-materializing `count`/`countFrustum`. |
| SpatialNode | Stable node-addressing handle (`index()`, `bounds()`, `isLeaf()`, `childCount()`, `depth()`) used by external sync systems to reference a specific node across frames; index may be invalidated on a full rebuild. |
| DirtyTracker | Change-tracking contract used by external systems (frame graph, buffer managers) to know what needs re-upload: `isDirty()`, `isFullRebuild()`, `dirtyNodeCount()`, `dirtyNodeIndices()` (`IntStream`, empty when a full rebuild occurred), `clearDirty()` (called by the sync consumer after upload). |
| NodeVisitor | `@FunctionalInterface` callback (`visit(bounds, depth, isLeaf, itemCount)`) for walking internal nodes, used for visualization/debugging via each structure's `visitNodes`. |
| TraversalOrder | Enum describing how nodes should be linearized for serialization/iteration: `DFS` (parent immediately followed by left child), `BFS` (level by level), `MORTON` (Z-order, spatially coherent linear ordering — useful for cache-friendly GPU buffer layouts). |

---

## Design Conventions

### Mutable-first with copy variants

- In-place operations modify `this` and return `this` for chaining
- Copy variants use `*New` suffix (e.g., `addNew` returns a new instance)
- Public fields for direct access (no getter overhead)

### Flat fields, not arrays

- Vec3 has `public float x, y, z` not `float[] data`
- Mat4 has 16 named float fields in column-major order
- Enables JIT scalar optimization, avoids bounds checks

### Builders as reusable configurators

- All types have direct constructors AND `builder()`
- Builders designed to be allocated once and reused across frames
- `eager()` / `lazy()` factory methods declare computation timing intent
  - `lazy()` (default): all computation deferred to `build()` time
  - `eager()`: will cache intermediate results when parameters change (not yet implemented, JIT-friendly `final boolean`)
- `build()` uses global `BuildStrategy`; `build(strategy)` overrides per-call

### BuildStrategy

```java
public interface BuildStrategy<T> {
    T obtain();
    default void release(T instance) {}
    static <T> BuildStrategy<T> allocating(Supplier<T> factory) { return factory::get; }
}
```

- Each math type has a static `buildStrategy` field (global default)
- Enables transparent pooling opt-in without changing calling code
- Pooling implementations would call `release()` when objects are no longer needed

### Column-major matrices (GLSL/Vulkan convention)

- Field naming: `mColumnRow` -- e.g., `m30` = column 3, row 0 = translation X
- Writing sequentially (m00, m01, m02, m03, m10, ...) produces what `mat4` in GLSL expects
- `BufferWritable.writeTo()` writes in this order

### Coordinate conventions

- Right-handed coordinate system
- Y-up for world space
- Vulkan NDC: X right, Y down, Z into screen, depth 0..1
- `Mat4.perspective()` and `Mat4.orthographic()` account for Vulkan clip space (Y flip, 0..1 depth)

---

## BufferWritable / GpuLayout Integration

All vector/matrix/quaternion types implement `BufferWritable`:

```java
public interface BufferWritable {
    int byteSize();
    void writeTo(ByteBuffer buf);
    void readFrom(ByteBuffer buf);
}
```

This means they work directly with `TypedVkBuffer<T>` subclasses (see `AbstractBuffer`-family docs in vulkan-core for the base `write`/`read` API); each math type's `writeTo`/`readFrom` is called per element during bulk transfer.

Byte sizes are natural/packed (no std140 padding). For std140-aligned layouts (e.g., vec3 as 16 bytes), use a wrapper struct or explicit padding — the base types write their natural size.

For types that need more than one GPU-facing serialization (e.g. a struct that a compute shader wants packed differently than a vertex shader), the `GpuLayout<T>` strategy interface (`vulkan-core`, `io.github.yetyman.vulkan.buffers.GpuLayout`) decouples "how to serialize" from the type itself:

```java
public interface GpuLayout<T> {
    int byteSize();
    void writeTo(T value, ByteBuffer buf);
    void readFrom(T value, ByteBuffer buf);
}
```

`DualQuaternion` is the current example of this pattern in the math module: its `REAL_DUAL` static layout (real quaternion followed by dual quaternion, 32 bytes) is exposed as `DualQuaternion.DEFAULT_LAYOUT`, and `DualQuaternion` also implements `BufferWritable` directly by delegating to that default layout, plus overloads (`writeTo(buf, layout)` / `readFrom(buf, layout)`) that accept an alternative `GpuLayout<DualQuaternion>` for callers that need a different packing. `SpatialStructure<T>` implementations follow the same pattern at the structure level — `SpatialStructure` itself extends `BufferWritable` for a default serialization, while a `GpuLayout<StructureType>` can describe an alternative traversal-order/quantized layout for GPU consumption (see Data Streaming and GPU Integration below).

---

## GPU-Centric Types — Future Direction

Currently removed. The original GPU variants (GpuVec3, GpuMat4, etc.) were deleted because they duplicated state without clear optimization benefit.

### Planned: Buffer Views (not yet designed)

The intended optimization is a **flyweight/cursor pattern** over `TypedVkBuffer` regions:

- Read/write fields directly from mapped memory at a stride offset
- No materialization of Java objects for iteration
- Iterate 10k transforms without allocating 10k objects

This would be a view/accessor type that sits on top of a buffer, not a standalone math object. Design TBD — depends on how `TypedVkBuffer` evolves.

---

## Math Builder Memoization

Some math primitives like the `Mat4` have a Builder that supports memoization for repeated builds where only some parameters change:

- **Perspective**: caches `tan(fov/2)` — if only near/far change, skips trig
- **LookAt**: caches basis vectors (forward/right/up) — if only eye changes, only recomputes translation
- **TRS**: caches rotation matrix from quaternion — if only position changes, only stamps translation
- **Rationale**: Builders can be used anywhere but practically serve two purposes.
  - One-off sample code. For convenience.
  - Cached, for re-use as a supplier. In this scenario, the builder serves a role as a referencable, configurable, supplier.
    - The general use case in any one section of code is generally going to remain consistent, outside of highly configurable and optimizable scenarios, which may circumvent the builder easily.
    - Therefore in the builders we can reasonably optimize for high re-use and consistent feature usage. 
      - The Mat4Builder is obvious overkill. There to represent the pattern. 
      - if Mat4Builder was set to a certain fov, but then near and far consistently changed, it can be optimized to do even less math per usage. 
      - The builder can be setup to retrieve from pools. 
      - For scenarios where math changes far more often than the object is built, we can even structure the code to do the only required math late on supply. builder.lazy()
      - Best of all this fairly direct structure lets JIT make this all optimally fast in hot path. The user just needs to call the code with honest intent.
        - In the future I may add diagnostic telemetry that allows usage speeds(new construction, builder construction, build calls, accessor calls) to be analyzed and provide clear logs to developers of where these optimizations can be chosen to enhance performance.

These caches are active regardless of `eager()`/`lazy()` flag. They set the calculation timing, not the present of the cached intermediates

---

## Non-Functional Properties

- Zero allocation in hot-path operations when using mutable API
- Thread-safe for reads; mutation is caller-synchronized
- No static mutable state beyond `BuildStrategy` (intentionally global-swappable)
- All static factories return new instances (never shared mutables)
- Zero-length normalize returns zero vector (never NaN or throws)
- Float epsilon comparisons where appropriate (`MathUtil.EPSILON = 1e-6f`)

---

## Primitive Types in TypedVkBuffer

`vulkan-core`'s `buffers/typed/` package provides direct primitive-array typed buffers alongside the generic `TypedVkBuffer<T extends BufferWritable>`. These exist specifically to avoid the per-element `BufferWritable.writeTo`/`readFrom` call overhead when the data is already a flat primitive array — math types like `Vec3`/`Vec4`/`Mat4` are the more common case for structured per-element data, but raw scalar streams (weights, indices, heightmaps, histogram bins, spatial-structure dirty-index lists, etc.) go through these instead:

| Type | Element stride | Notes |
|------|-----------------|-------|
| FloatVkBuffer | 4 bytes | `float[]` direct transfer; optional live `FloatBuffer` mirror view when backed by a `MirroredBuffer`. |
| IntVkBuffer | 4 bytes | `int[]` direct transfer; same mirror pattern. |
| LongVkBuffer | 8 bytes | `long[]` direct transfer. |
| ShortVkBuffer | 2 bytes | `short[]` direct transfer. |
| DoubleVkBuffer | 8 bytes | `double[]` direct transfer (no native GLSL double storage class equivalent — used for CPU-side precision-sensitive data staged through the same buffer strategy machinery, not typically bound as shader input). |

All five follow the same shape: constructor takes the backing `IBuffer` and element `count`, validates `count * STRIDE <= buffer.size()`, exposes `write`/`writeAsync` (going through the same `TransferBatch`/`TransferCompletion` path as every other buffer write) and `read` (zero-copy from the CPU mirror when the backing buffer is a `MirroredBuffer`, otherwise a blocking GPU readback), and `close()` delegates to the underlying `IBuffer`. `DirtyTracker.dirtyNodeIndices()` (an `IntStream`) from a spatial structure is a natural producer for `IntVkBuffer` writes when syncing only the changed node indices rather than re-uploading a whole structure.

---

## Data Streaming

The math/spatial types are designed to feed the buffer-strategy and transfer-batching system in `vulkan-core` (`buffers/`) rather than duplicate any of its logic:

- **Per-element streaming**: any `BufferWritable` (Vec2/3/4, Mat3/4, Quaternion, DualQuaternion, ContourOutput, MeshOutput, or a full `SpatialStructure`) can be pushed through `ManagedBuffer.write`/`writeAsync`, which are automatically batched per-thread-per-queue by `TransferBatchManager` and auto-flushed at the 64MB threshold (`TransferBatch`).
- **Incremental sync via DirtyTracker**: rather than re-uploading an entire spatial structure every frame, a consumer polls `structure.dirtyTracker()`; if `isFullRebuild()` is false, `dirtyNodeIndices()` gives the exact set of changed node slots to re-serialize and write at their existing byte offsets (stable addressing is guaranteed by `SpatialNode.index()` between rebuilds). This turns a "reupload everything" cost into "reupload N changed nodes," which matters for structures like `LinkedQuadtree`/`LinkedOctree` under frequent local mutation (e.g. a moving-object world) versus `KDTree`/`BVH`/`RTree`, which mark `isFullRebuild()` on every mutation today since they rebuild wholesale rather than rebalancing incrementally.
- **Layout selection for streaming**: `TraversalOrder` (`DFS`/`BFS`/`MORTON`) declares the intended linearization when a structure (or a future `GpuLayout<StructureType>`) serializes its nodes into a flat buffer; `MORTON` in particular is meant to keep spatially-adjacent nodes byte-adjacent, which is the layout GPU traversal shaders (BVH ray tracing, octree cone tracing, sparse voxel readers) want for coherent memory access, while `DFS`/`BFS` match CPU-side recursive/level-order traversal expectations.
- **Isosurface output streaming**: `ContourOutput`/`MeshOutput` are generated fresh per extraction call (they are not incrementally dirty-tracked like `SpatialStructure`), so the typical streaming pattern is: regenerate on the CPU when the underlying `ScalarField2D`/`ScalarField3D` changes, then push the whole `MeshOutput` (or just its vertex/index halves via `writeVertices`/`writeIndices`) through a `TransferBatch` in one shot — there is no partial-update path for isosurface results today.

---

## Potential Integration with the Render Graph and GPU Capabilities

The spatial/math module has no compile-time dependency on `vulkan-ffm-graph` (per the multi-module import rules, `vulkan-ffm-graph` depends on `vulkan-core`, not the reverse), so all integration is call-site composition rather than a built-in bridge today. The natural seams are:

- **Buffer resources as graph nodes**: `vulkan-ffm-graph`'s `VkBufferGraphResource` (`graph/resources/`) wraps any `IBuffer` — including the `ManagedBuffer`/`TypedVkBuffer` instances backing a serialized `SpatialStructure` or an isosurface `MeshOutput` — as a `transientResource`, `imported`, or `persistent` graph resource. A `CpuWorkNode` (or a dedicated pass) that runs `MarchingCubes.extract`/`SurfaceNets.extract`/spatial-structure `rebuild()` can write its output into a buffer that a subsequent `GraphicsPassNode` consumes as a vertex/index buffer, with the graph's automatic barrier insertion (`BarrierStrategy`/`SplitBarrierStrategy`) handling the CPU-write-to-GPU-read transition instead of manual `VkBufferBarrier` calls.
- **Frame-graph-driven incremental sync**: `DirtyTracker` maps naturally onto `TemporalResource`/`PersistentResourceRing` in the graph module — a per-frame `CpuWorkNode` checks `dirtyTracker().isDirty()` and, on a partial update, only re-records the transfer for `dirtyNodeIndices()`, letting the graph's scheduling/aliasing strategy treat the structure's backing buffer like any other persistent resource that needs partial re-upload rather than a full-frame rebuild.
- **Compute-driven structure traversal**: `SpatialQuery<T>`'s allocation-free query variants exist for CPU-side culling (e.g. frustum culling before building an indirect-draw list), but the same node/bounds data serialized via a `GpuLayout` in `MORTON`/`BFS` order is what a GPU traversal compute shader (frustum culling, BVH-based ray queries, sparse voxel cone tracing against an octree) would consume — this is currently an integration point rather than a shipped feature: nothing in `vulkan-ffm-graph` yet auto-generates traversal shaders from a `SpatialStructure`, but the layout/indexing contracts (`SpatialNode.index()`, `TraversalOrder`) are specifically shaped to make that straightforward to add as a `ComputePassNode`.
- **Isosurface extraction as a graph pass**: because `MarchingCubes`/`SurfaceNets`/etc. are pure functions of a `ScalarField3D`, an `IterativePassNode` or `CpuWorkNode` can regenerate a mesh only when the field's driving data (e.g. a voxel `DenseGrid<Float>`/`SparseGrid<Float>` density field) changes, then hand the resulting `MeshOutput` buffer into the graph as a transient resource for that frame's draw pass — this composition is possible today with existing graph primitives, it is just not pre-wired.
- **GPU compute for extraction itself**: the isosurface extractors here are CPU-only; if extraction needs to move to the GPU (e.g. a compute shader implementation of marching cubes for real-time voxel terrain), the natural fit is a `ComputePassNode` writing directly into a `VkBufferGraphResource`-backed vertex/index buffer, sidestepping the CPU `MeshOutput` representation entirely for that path while keeping the CPU implementations available for tooling, baking, and platforms without compute shader support.

---

## Sample Apps

`sample-app` (`io.github.yetyman.vulkan.sample`) exercises the math/spatial module through a dedicated `sample/spatial/` package of live, GLFW-windowed Vulkan demos, in addition to the module's broader array of simple/graph/UI/complex sample apps that exercise the rest of the engine:

### sample/spatial/ — math and spatial-structure demos

| App | Demonstrates |
|-----|---------------|
| MarchingSquaresApp | Live 2D `MarchingSquares` contour extraction from a `ScalarField2D`, rendered via `Scene3DOverlayLayer`. |
| MarchingHexagonsApp | Live `MarchingHexagons` contour extraction on an axial hex grid. |
| MarchingTrianglesApp | Live `MarchingTriangles` contour extraction on a staggered-row triangulated grid. |
| MarchingCubesApp | Live 3D `MarchingCubes` mesh extraction with `AmbiguityResolution` selection. |
| MarchingTetrahedraApp | Live 3D `MarchingTetrahedra` mesh extraction, contrasted against Marching Cubes' ambiguous cases. |
| AdaptiveMarchingApp | `AdaptiveMarchingCubes` driven by an octree-backed point cloud, showing resolution concentrating in dense regions. |
| Quadtree2DApp | `LinkedQuadtree` insert/query/visualize loop with `NodeVisitor`-driven overlay drawing. |
| HexGrid2DApp | `HexGrid`/`HexCoord`/`HexLayout` axial hex grid population and range/ring queries. |
| SpatialPlaygroundApp | Interactive comparison across `BVH`, `KDTree`, `RTree`, `LinkedOctree`, `SparseGrid`, and `HierarchicalGrid` against the same dataset, with `OctreeConfig`-driven construction and `Scene3DOverlayLayer`/`SpatialOverlayHelper` visualization. |
| OrbitCamera / OrbitCameraLayer | Shared camera rig used by the spatial demo apps for orbiting inspection of 3D structures/meshes. |
| TransformComponent / TransformTreeExample | `Transform` hierarchy usage example (parent/child local-to-world composition) independent of the spatial-query structures. |

### Other sample-app packages (for context)

- `simple/` — minimal single-concept apps: `SimpleTriangleApp`, `GameOfLifeApp`, `NBodyApp`, `DraggableSquaresApp`, `TemporalTrailApp`, `InputVisApp`, plus matching `*GraphicsFrame` renderer classes.
- `graph/` — `vulkan-ffm-graph` driven demos: `RenderGraphApp` (edge-detect post-process), `TemporalTrailGraphApp`, `TemporalUnrollingExample`.
- `ui/` — node-tree/UILayer demos: `TextExampleApp`, `MultiLayerExampleApp`, `Scene3DOverlayExampleApp`, `HoverHighlightLayer`, plus `ui/treedemo/` (`TreeDemoFrame`, `TreeDraggableSquaresApp`, `DraggableComponent`).
- `complex/` — `ComplexTriangleApp` and supporting `models/` (GLTF loading), `postprocessing/` (`AdaptiveAA`), plus threading/culling/input subpackages for a full-featured reference app.
- `windowing/` — `GLFWWindowSystem`, `GLFWInputSystem`, and adapter classes bridging GLFW to the engine's `WindowSystem`/`InputSystem` interfaces, used by every sample app above.

The `sample/spatial/` apps are the primary reference implementations for how to wire a `SpatialStructure`/isosurface extractor into an actual running Vulkan frame loop (window creation, overlay rendering, input-driven parameter changes), and are the best starting point when integrating a new math/spatial type into a real render path.


