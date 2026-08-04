# Spatial Structures Implementation Plan

## Location

`helpers-core/src/main/java/io/github/yetyman/helpers/math/spatial/`

## Design Principles

- Pure CPU structures, queryable and mutable
- No dependency on vulkan-core buffer system or frame graph
- Expose API surface that enables external sync (iterators, dirty tracking, stable addressing)
- All structures use the geometry primitives from `helpers.math.geometry` (AABB, Sphere, Ray, Frustum)
- BuildStrategy + Builder pattern on all public types
- BufferWritable on layout-serialized output (not on the tree structures themselves)

---

## Package Structure

```
io.github.yetyman.helpers.math.spatial/
    SpatialQuery.java               -- query interface
    SpatialStructure.java           -- mutable structure interface
    SpatialNode.java                -- node handle for traversal/addressing
    SpatialLayout.java              -- layout descriptor interface
    DirtyTracker.java               -- dirty region tracking
    NodeIterator.java               -- layout-order node iteration

    quadtree/
        QuadtreeConfig.java         -- bucket strategy, max depth, split threshold
        LinkedQuadtree.java         -- pointer-based, incremental insert/remove
        ArrayQuadtree.java          -- flat array, full-rebuild oriented
        LooseQuadtree.java          -- loose bounds, single-cell guarantee per object

    octree/
        OctreeConfig.java
        LinkedOctree.java           -- pointer-based, incremental
        ArrayOctree.java            -- flat array, fixed depth
        LooseOctree.java            -- loose bounds variant

    bvh/
        BVH.java                    -- logical BVH (node array + topology)
        BvhBuilder.java             -- builder interface
        SahBuilder.java             -- top-down SAH construction
        MedianBuilder.java          -- top-down median/object-median split
        LbvhBuilder.java            -- Morton-sort linear BVH (O(n) after sort)

    rtree/
        RTree.java                  -- balanced R-tree
        RStarTree.java              -- R*-tree (forced reinserts)
        StrBulkLoader.java          -- Sort-Tile-Recursive bulk load

    layout/
        BvhDfsLayout.java           -- DFS-ordered flat node array
        BvhBfsLayout.java           -- BFS/level-order flat array
        WideBvhLayout.java          -- BVH4/BVH8 wide nodes
        QuantizedBvhLayout.java     -- relative 8/16-bit AABBs
        FlatGridLayout.java         -- uniform grid as flat cell array
```

---

## Core Interfaces

### SpatialQuery

Read-only query interface shared by all structures.

```java
public interface SpatialQuery<T> {
    List<T> query(AABB range);
    List<T> query(Sphere range);
    List<T> query(Ray ray, float maxDistance);
    List<T> queryFrustum(Frustum frustum);
    boolean contains(Vec3 point);
    T nearest(Vec3 point);

    // Allocation-free variants (write results into caller-provided list)
    int query(AABB range, List<T> out);
    int query(Sphere range, List<T> out);
    int queryFrustum(Frustum frustum, List<T> out);
}
```

### SpatialStructure

Mutable structure interface. Extends SpatialQuery.

```java
public interface SpatialStructure<T> extends SpatialQuery<T> {
    void insert(T item, AABB bounds);
    void remove(T item);
    void update(T item, AABB newBounds);
    void rebuild();
    void clear();
    int size();
    AABB worldBounds();
}
```

### SpatialNode

Handle representing a node in the tree. Stable addressing for external sync.

```java
public interface SpatialNode {
    int index();              // stable index (survives inserts, invalidated on rebalance)
    AABB bounds();
    boolean isLeaf();
    int childCount();
    int depth();
}
```

### DirtyTracker

Tracks which nodes have changed since last sync. External systems use this to determine what needs re-upload.

```java
public interface DirtyTracker {
    boolean isDirty();
    boolean isFullRebuild();          // entire structure invalidated (rebalance, bulk insert)
    int dirtyNodeCount();
    IntStream dirtyNodeIndices();     // indices of changed nodes
    void clearDirty();                // called by external sync after upload completes
}
```

### NodeIterator

Iterates nodes in a specific layout order. External systems pull from this without the structure knowing about buffers.

```java
public interface NodeIterator {
    boolean hasNext();
    SpatialNode next();
    void reset();
}
```

### SpatialLayout

Extends `GpuLayout<T>` for spatial structure serialization. Defines how a structure's nodes map to bytes.
Lives in `vulkan-core/src/main/java/io/github/yetyman/vulkan/buffers/` alongside `GpuLayout`.

```java
// Spatial structures use the same GpuLayout<T> pattern as primitives:
bvh.writeTo(buf);                           // default BufferWritable (DFS layout)
BvhLayout.WIDE4.writeTo(bvh, buf);          // explicit layout override

// Layouts are static final singletons on the structure or in a companion class:
public static final GpuLayout<BVH> DFS = ...;
public static final GpuLayout<BVH> BFS = ...;
public static final GpuLayout<BVH> WIDE4 = ...;
```

---

## Quadtree Variants

### LinkedQuadtree (pointer-based, incremental)

- Each node holds 4 child references (null if not subdivided)
- Objects stored at deepest containing node OR in leaf only (configurable)
- Split when leaf exceeds threshold; merge when children collectively below threshold
- Best for: dynamic scenes with frequent insert/remove, moderate object count

### ArrayQuadtree (flat array, full-rebuild)

- Nodes stored at `4*i + 1..4*i + 4` (implicit child addressing)
- Fixed max depth determines array size
- No per-node allocation, excellent cache locality
- Rebuild from scratch each frame (sort objects by Morton code, assign to cells)
- Best for: high-churn scenes where rebuild is cheaper than incremental maintenance

### LooseQuadtree (loose bounds)

- Cell bounds expanded by 2x in each dimension
- Every object fits in exactly one cell (the one whose original bounds contain its center)
- Queries must check expanded bounds (query region expanded by cell size)
- No duplicates, no "stored at ancestor" problem
- Best for: games with varied object sizes and frequent movement

### Bucket strategies (configurable via QuadtreeConfig)

```java
public class QuadtreeConfig {
    public enum BucketStrategy {
        POINT_REGION,       // object in cell containing its center
        LOOSE,              // cells expanded, object in one cell guaranteed
        TIGHT_DUPLICATES,   // object in every overlapping leaf
        DEEPEST_CONTAINING  // object at smallest node fully containing it
    }

    int maxDepth;
    int splitThreshold;     // max objects per leaf before split
    int mergeThreshold;     // min objects in node's children before merge
    BucketStrategy strategy;
    AABB worldBounds;       // root cell bounds
}
```

Octree variants mirror these exactly but in 3D (8 children instead of 4).

---

## BVH Variants

### BVH (logical structure)

```java
public class BVH<T> implements SpatialStructure<T>, DirtyTracker {
    // Internal flat node array (left/right child indices, AABB, leaf data)
    // Construction delegated to BvhBuilder
    // Supports incremental refit (update AABBs bottom-up when objects move)
    // Full rebuild via any BvhBuilder strategy

    NodeIterator iterateDfs();
    NodeIterator iterateBfs();
    void refit();                    // bottom-up AABB update without topology change
    void rebuild(BvhBuilder builder); // full reconstruction
}
```

### Construction strategies

**SahBuilder** — Surface Area Heuristic
- Evaluates all possible split planes, picks lowest cost
- Cost = traversal_cost + (SA_left/SA_parent) * N_left + (SA_right/SA_parent) * N_right
- O(n log^2 n) naive, O(n log n) with binning
- Best quality, slowest build

**MedianBuilder** — Median split
- Pick longest axis, sort, split at median
- O(n log n), decent quality
- Good default for moderate-size static geometry

**LbvhBuilder** — Linear BVH
- Compute Morton code for each object (quantize centroid to grid, interleave bits)
- Sort by Morton code
- Build binary radix tree from sorted codes (Karras 2012 algorithm)
- O(n) after sort (sort is O(n log n) or O(n) with radix sort)
- Lowest quality but fastest build — suitable for per-frame rebuild
- Produces spatially coherent DFS ordering naturally

---

## R-Tree Variants

### RTree

- Balanced tree, all leaves at same depth
- Configurable page size (max children per node), min fill factor
- Insert: choose subtree with least enlargement, split on overflow
- Split strategies: linear, quadratic, R*-tree forced reinsert
- Best for: moderate dynamism, bulk-loaded static scenes, disk-friendly access patterns

### RStarTree

- On overflow, first tries reinserting a fraction of entries (typically 30%)
- Only splits if reinsert doesn't resolve overflow
- Better query performance than classic R-tree (less overlap between siblings)
- Slightly worse insert latency

### StrBulkLoader

- Sort-Tile-Recursive: sort by X, partition into sqrt(N) slabs, sort each slab by Y, pack into leaves
- Produces near-optimal tree for static data
- O(n log n), single pass
- Not for dynamic scenes — use as initial load, then switch to incremental updates

---

## Layout Descriptors

### BvhDfsLayout

Nodes in depth-first order. Left child at `index + 1`, right child at `index + skipCount`.

```
Per node (32 bytes):
  float[6] aabb (min xyz, max xyz)    -- 24 bytes
  int      rightChildOffset           -- 4 bytes (0 = leaf)
  int      primitiveCountOrPadding    -- 4 bytes
```

### BvhBfsLayout

Nodes in breadth-first/level order. Children at `2*index + 1` and `2*index + 2`.

Same node format as DFS but different ordering in the array.

### WideBvhLayout (BVH4 / BVH8)

Each node holds 4 or 8 child AABBs packed together.

```
Per node (BVH4, 112 bytes):
  float[6] parentAABB                 -- 24 bytes
  float[6] childAABB[4]               -- 96 bytes (4 children x 24 bytes)
  byte     childMask                  -- 1 byte (which children are valid)
  byte[3]  padding
  int      childIndices[4]            -- 16 bytes
```

Enables SIMD 4-wide AABB test in a single instruction on GPU.

### QuantizedBvhLayout

Child AABBs stored as 8-bit offsets relative to parent. Halves bandwidth.

```
Per node (BVH2, quantized, 16 bytes):
  byte[6]  childMin (relative to parent min, 0-255 range)
  byte[6]  childMax (relative to parent min, 0-255 range)
  int      metadata (child index / leaf flag / primitive range)
```

### FlatGridLayout

Uniform spatial grid as flat array. Cell `(x, y, z)` at index `x + y*gridSizeX + z*gridSizeX*gridSizeY`.

```
Per cell (variable):
  int objectCount
  int objectIndices[maxPerCell]    -- fixed max, or indirection into separate object array
```

---

## API Surface for External Sync

The key methods that enable frame graph integration, buffer management, or any external upload system:

### On all structures:

```java
// Dirty tracking
DirtyTracker dirtyTracker();

// Layout-order iteration (caller pulls nodes, writes them wherever)
NodeIterator iterate(TraversalOrder order);

enum TraversalOrder { DFS, BFS, MORTON }

// Bulk serialization uses GpuLayout<T> pattern (same as primitives)
// Default: structure implements BufferWritable with its default layout
void writeTo(ByteBuffer buf);                // default layout
// Explicit layout via GpuLayout<StructureType>:
// SomeLayout.VARIANT.writeTo(structure, buf);

// Size estimation (for buffer allocation)
int byteSize();                              // default layout size
// layout.byteSize() for alternative layouts

// Stable addressing
int nodeCount();
SpatialNode nodeAt(int index);
```

### On DirtyTracker:

```java
// Incremental sync support
IntStream dirtyNodeIndices();    // which nodes changed
boolean isFullRebuild();         // must re-upload everything (topology changed)
void clearDirty();               // acknowledge sync complete
```

### On GpuLayout implementations for spatial structures:

```java
// Layout handles both full and incremental writes
// Full write:
layout.writeTo(structure, buf);

// Incremental write (layout knows how to map node index to buffer offset):
layout.writeDirty(structure, structure.dirtyTracker(), buf);
```

---

## Stable Node Addressing

Critical for incremental GPU sync. Two strategies:

### 1. Index-stable (array-backed structures)

Nodes have fixed indices. Insert may cause a split (new indices added) or rebalance (indices shuffled). DirtyTracker reports:
- `dirtyNodeIndices()` — nodes whose AABBs or content changed
- `isFullRebuild()` — true if topology changed (indices are no longer meaningful)

When `isFullRebuild()` is true, external system must re-upload the entire buffer.

### 2. Generation-counted (pointer-based structures)

Each node has a generation counter. On rebalance, generation increments globally. External system compares generations to detect when its cached mapping is stale.

Tradeoff: index-stable is simpler for GPU (direct index → buffer offset), but limits structure flexibility. Generation-counted is more forgiving but requires the external system to maintain a mapping table.

Recommendation: support both, let the structure implementation choose. `DirtyTracker.isFullRebuild()` is the escape hatch — when true, the external system does a full re-serialize regardless.

---

## Chunk Streaming Pattern (Slotted Access)

For Minecraft-style chunk loading where spatial data lives in fixed-size slots:

```java
// Not part of this module — but the API enables it:

// External system maintains a ring buffer of slots
// Each slot holds one chunk's serialized spatial data

int slot = ringBuffer.allocateSlot();
chunk.octree().serialize(layout, ringBuffer.slotBuffer(slot));

// On chunk evict:
ringBuffer.freeSlot(slot);
// Slot's buffer region is now reusable, no upload needed
```

The spatial structure doesn't know about slots — it just serializes into whatever ByteBuffer it's given. Slot management is the buffer system's concern.

---

## Dynamic Mesh Streaming Considerations

This module doesn't implement mesh streaming, but the API enables it:

- A BVH over meshlets has one leaf per meshlet. Each leaf's `primitiveRange` field points into a vertex/index buffer region.
- When a meshlet is streamed in, it's inserted into the BVH and its vertex data written to the mesh buffer.
- When evicted, removed from BVH and its buffer region freed.
- DirtyTracker reports which BVH nodes changed, enabling incremental re-upload of just the spatial index.
- The mesh buffer itself is managed by a sub-allocator (outside this module).

The coupling point: a future module (or frame graph node) coordinates between:
1. This module's spatial structure (which meshlets are loaded, where are they spatially)
2. The buffer system's sub-allocator (where meshlet vertex data lives in GPU memory)
3. The frame graph (ensuring upload pass runs before draw pass, barriers correct)

That coordination layer lives outside helpers-core.

---

## Integration Points (Future, Not This Module)

These would live in vulkan-core, a dedicated module, or sample-app:

- **Frame graph node for spatial sync** — reads DirtyTracker, uploads changed nodes via TransferBatch
- **Slotted spatial buffer** — ring buffer of fixed-size slots for chunk-based streaming
- **Meshlet pool** — sub-allocated GPU buffer with spatial index tracking what's loaded
- **Frustum cull compute pass** — reads GPU-side BVH, outputs visible instance IDs to indirect draw buffer
- **LBVH GPU builder** — compute shader that rebuilds the BVH on GPU (Morton sort + Karras construction)

---

## Implementation Order

### Phase 1: Core interfaces + basic quadtree/octree
- SpatialQuery, SpatialStructure, SpatialNode, DirtyTracker, NodeIterator
- QuadtreeConfig, LinkedQuadtree (point-region + loose variants)
- OctreeConfig, LinkedOctree (point-region + loose variants)

### Phase 2: BVH
- BVH, BvhBuilder interface
- MedianBuilder (simplest, good default)
- SahBuilder
- LbvhBuilder

### Phase 3: R-Tree
- RTree with linear/quadratic split
- RStarTree
- StrBulkLoader

### Phase 4: Layouts
- BvhDfsLayout
- BvhBfsLayout
- WideBvhLayout
- FlatGridLayout

### Phase 5: Array-backed variants
- ArrayQuadtree
- ArrayOctree
- MortonQuadtree (sorted morton code array + binary search)

---

## Non-Functional Requirements

- Zero allocation in query hot paths (caller-provided result lists)
- Thread-safe for reads; mutation is caller-synchronized
- DirtyTracker is O(1) per mutation (bitset or index list append)
- Serialization does not allocate (writes directly into caller's ByteBuffer)
- No dependency on vulkan-core buffer system or frame graph
- All structures support BuildStrategy for pooling
- All structures implement BufferWritable? NO — only layout output is BufferWritable.
  The structures themselves are mutable Java objects, not GPU-uploadable blobs.

---

## What's NOT in scope (for this module)

- GPU-side traversal shaders (lives with the shader system)
- Frame graph integration (lives in vulkan-ffm-graph or a new module)
- Buffer sub-allocation / defragmentation (lives in vulkan-core buffers)
- Mesh streaming coordination (future dedicated module or sample)
- SIMD optimization via Panama Vector API (future)
