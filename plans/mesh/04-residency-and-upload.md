# Layer 3: Residency and upload

Package: `io.github.yetyman.vulkan.mesh.residency`

Covers axis 3 as it applies to meshes, and axis 7's lifecycle half. Built entirely on `IBuffer` and
the buffer strategy system. This layer allocates nothing itself and implements no transfer logic.

---

## `GeometryAllocator`

The interface that makes meshlet-pool rendering a configuration choice rather than a rewrite.

```java
public interface GeometryAllocator extends AutoCloseable {

    /** Reserves space for one geometry in the target layout. */
    GeometryAllocation allocate(MeshLayout layout, long vertexCount,
                                IndexWidth indexWidth, long indexCount);

    void free(GeometryAllocation allocation);

    /**
     * Whether indices are rewritten with a vertex base offset at upload time,
     * or left relative to zero and compensated by GeometryDrawRange.vertexOffset.
     */
    IndexBaseMode indexBaseMode();
}

public interface GeometryAllocation {
    /** The buffer and byte range backing stream i of the layout. */
    DeviceRange vertexRange(int streamId);
    Optional<DeviceRange> indexRange();

    /** First vertex index within the shared pool, or 0 for dedicated allocations. */
    long vertexBase();
    /** First index within the shared pool, or 0. */
    long indexBase();
}
```

### Implementations

| Implementation | Backing | When it wins |
|----------------|---------|--------------|
| `DedicatedAllocator` | one `IBuffer` per stream per geometry | Simplest, most debuggable. Fine for a handful of large meshes. Bad for thousands. |
| `SlabAllocator` | `SuballocatorBuffer` | Fixed-slot geometry of similar size. O(1) alloc and free. |
| `PoolAllocator` | one large `IBuffer` per stream with a free list | The bulk-render and meshlet-pool case. All geometry shares buffers, so binding happens once for the whole scene. Prerequisite for a single `vkCmdDrawIndexedIndirectCount` over everything. |
| `SparsePoolAllocator` | `ManagedBuffer` with `SparseAllocationStrategy` | Virtual geometry and streaming. Address space is reserved up front; pages are committed per resident partition and decommitted on eviction. |

`PoolAllocator` is the one that matters for maximum throughput, and it is the reason
`GeometryAllocator` exists as an interface at all. Phase 4 of the roadmap exists specifically to
prove that swapping `DedicatedAllocator` for `PoolAllocator` requires no changes above this
interface. If it does, the interface is wrong.

### `IndexBaseMode`

```java
public enum IndexBaseMode {
    /** Indices are rewritten at upload time to be absolute within the pool. */
    REWRITE_ABSOLUTE,
    /** Indices stay relative to zero; draws carry vertexOffset. */
    RELATIVE_WITH_DRAW_OFFSET
}
```

Both are legitimate and the choice is the allocator's. `REWRITE_ABSOLUTE` costs a transcode pass over
indices but allows merging draws that would otherwise need different `vertexOffset` values.
`RELATIVE_WITH_DRAW_OFFSET` is free at upload time and is what `vkCmdDrawIndexed` was designed for.
`IndexStream.transcodeInto` takes a `vertexBaseOffset` precisely so the first mode is expressible.

---

## Upload

`UploadPlan` was initially conceived as one class and that was wrong; it would have accumulated
planning, execution, and barrier logic. The decomposition below keeps each piece small and
independently testable.

### `CopyOp` and `TranscodeOp`

Two distinct op shapes because copying and transcoding are genuinely different operations, not
variants of one.

```java
public sealed interface UploadOp {

    /** Straight byte copy from host memory to a device buffer range. */
    record HostCopy(MemorySegment src, long srcOffset,
                    IBuffer dst, long dstOffset, long size) implements UploadOp {}

    /** Device-to-device copy. Used for repacking, defragmentation, and compute-produced geometry. */
    record DeviceCopy(IBuffer src, long srcOffset,
                      IBuffer dst, long dstOffset, long size) implements UploadOp {}

    /**
     * Layout-converting write. The source writes directly into the destination in target layout,
     * so no intermediate buffer exists. This is the op that makes one-copy upload possible.
     */
    record Transcode(GeometrySource source, AttributeSemantic semantic,
                     MeshLayout targetLayout,
                     IBuffer dst, long dstOffset, long dstStride,
                     long firstElement, long elementCount) implements UploadOp {}

    /** Index transcode, including base rewriting. */
    record TranscodeIndices(IndexStream source, IndexWidth targetWidth, long vertexBaseOffset,
                            IBuffer dst, long dstOffset,
                            long firstIndex, long indexCount) implements UploadOp {}
}
```

### `UploadPlan`

Genuinely tiny. Pure immutable data, no logic.

```java
public record UploadPlan(
        List<UploadOp> ops,
        int dstAccessMask,          // VkAccessFlags the destination will be read with
        int dstStageMask,           // VkPipelineStageFlags of the first consumer
        QueueClass preferredQueue,  // TRANSFER, COMPUTE, or GRAPHICS - a class, not a queue
        Priority priority,
        boolean deferrable          // may be split across frames or dropped under budget pressure
) {}
```

The four hint fields are the entire mechanism by which a frame-graph executor can do barrier
insertion, queue assignment, ownership transfer, priority scheduling, and degradation, without the
mesh module importing a single graph type. See `08-integration-boundaries.md`.

`QueueClass` and `Priority` should be small enums local to this module or to `vulkan-core`, not
imported from `vulkan-ffm-graph`.

### `UploadPlanner`

All the real logic. No execution.

```java
public final class UploadPlanner {
    public UploadPlan plan(GeometrySource source, MeshLayout targetLayout,
                           GeometryAllocation allocation, ElementWindow window);

    public UploadPlan planMetadata(PartitionSet partitions, MetadataChannel<?> channel,
                                   GeometryTable table);
}
```

Responsibilities:

- Detect the identity case: when `source.nativeLayout()` equals `targetLayout`, emit one `HostCopy`
  over the whole vertex block instead of per-attribute `Transcode` ops.
- Split large uploads into windows that fit available staging capacity.
- Decide index base handling from `allocator.indexBaseMode()`.
- Order ops so that a partial failure leaves a consistent state.

### `UploadExecutor`

```java
public interface UploadExecutor {
    GpuCompletion execute(UploadPlan plan, VkQueue queue);
}
```

Two implementations, one of which does not live here:

- `TransferBatchExecutor` - ships in `vulkan-ffm-meshes`. Acquires an `IBuffer` write scope per op,
  runs transcodes directly into the scope's segment, closes the scope, returns the batch's
  `GpuCompletion`. This is the standalone path and needs no graph.
- A graph-recording executor - lives app-side initially, promoted to an adapter module only after the
  same code is written three times. It translates ops into transfer nodes and returns a
  timeline-semaphore-backed `GpuCompletion`.

Because both consume the same `UploadPlan`, the mesh module never learns which one is in use.

---

## Residency tracking

### Granularity is per partition, not per mesh

Per-mesh residency cannot express any of the things streaming exists for: loading a coarse
representation first, keeping only visible terrain tiles resident, evicting distant clusters. The
tracking unit is the partition.

```java
public interface ResidencyTracker {
    Residency stateOf(GeometryId geometry, int partitionIndex);

    /** Requests residency. Returns a token that completes when the partition is usable. */
    GpuCompletion request(GeometryId geometry, int partitionIndex, Priority priority);

    /** Releases a residency claim. Eviction is the policy's decision, not immediate. */
    void release(GeometryId geometry, int partitionIndex);

    void addListener(ResidencyListener listener);
}

public interface EvictionPolicy {
    /** Selects partitions to evict to free at least the requested bytes. */
    List<PartitionRef> selectVictims(long bytesNeeded, ResidencyView view);
}
```

`EvictionPolicy` is an interface with an LRU default. Budget-aware, distance-aware, and
priority-aware policies are app or research concerns.

`ResidencyListener` is what lets a `GeometryTable` update itself and what lets a future LOD selector
know which representations are actually available.

### Interaction with sparse buffers

With `SparsePoolAllocator`, residency maps directly onto `SparseCapable.commitPages` and
`decommitPages`. The mesh module does not reimplement page tracking; it decides which partitions
should be resident and calls into the buffer.

`decommitPages` only unbinds pages fully covered by the given range, so the allocator must be aware
of page granularity when placing partitions if eviction is expected to actually reclaim memory.
This is a real constraint on `SparsePoolAllocator`: partitions intended to be independently evictable
should be page-aligned, which trades some memory for reclaimability. That tradeoff should be a
constructor option, not a hardcoded choice.

---

## Defragmentation

Pool allocators fragment. Since `DeviceCopy` exists as an op and `GeometryTable` is the single source
of truth for where geometry lives, defragmentation is expressible as a plan: copy allocations to new
locations, then update table records. It needs one guarantee from consumers, which is that they read
locations from the table rather than caching them.

Not a Phase 4 concern, but the design must not preclude it. The concrete requirement is that
`GeometryAllocation` locations are never assumed stable by anything above this layer, and that the
table is the indirection point.

---

## Design principles for this layer

- The mesh module allocates no memory and implements no transfer logic. It composes `IBuffer`,
  `TransferBatch`, and the write scopes from `00-prerequisites.md`.
- Planning and execution are separate types. The planner has all the logic and no side effects; the
  executor has all the side effects and no policy.
- Every upload op carries enough hint data (access, stage, queue class, priority, deferrability) for
  an external scheduler to do its job, and no more.
- Residency granularity is the partition. Per-mesh residency cannot express streaming.
- Eviction is a policy interface with a default, never a hardcoded rule.
- Allocation locations are not stable. The `GeometryTable` is the indirection point, which is what
  makes defragmentation and repacking possible later.
