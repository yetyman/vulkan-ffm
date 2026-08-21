# Mirroring and Dirty Tracking Plan

## Summary

Redesign CPU-side buffer observability from a decorator (`MirroredBuffer`) into a first-class
third strategy axis (`CpuObservability`) composed alongside `AllocationStrategy` and
`TransferStrategy` inside `ManagedBuffer`. Add dirty tracking as a separate, peer strategy axis
(`DirtyStrategy`) enabling deferred writes, multi-region flushes, and render-graph-driven upload
scheduling.

## Goals

1. Eliminate the `MirroredBuffer` decorator class and its `instanceof ManagedBuffer` gymnastics.
2. Expose CPU observability as a visible, documented strategy axis — not hidden behind the factory.
3. Add dirty tracking (CPU->GPU direction) with a `DirtyStrategy` interface so that writes
   accumulate and flush in coalesced multi-region batches.
4. Support a `deferred` mode flag (mutable, non-final) that controls whether writes immediately
   transfer or accumulate for explicit flush.
5. Add GPU->CPU dirty tracking (`markGpuDirty` + `readDiff`) for compute readback scenarios.
6. Use multi-region `vkCmdCopyBuffer` (regionCount > 1) to flush scattered dirty ranges in one
   command rather than N separate copy commands.
7. Enable the render graph to schedule buffer flushes optimally — after all CPU writers, before the
   first GPU consumer — without the CPU writers blocking on queue submissions.
8. Add SPIR-V access qualifier extraction to `DescriptorBindingInfo` so shader reflection can
   indicate which buffers a shader writes vs. reads.
9. Preserve API simplicity: `BufferFactory.create(DEVICE_LOCAL_MIRRORED, ...)` still returns
   something that works out of the box. Advanced features (deferred, dirty query, GPU-dirty
   marking) are available via a capability interface.

## Non-Goals

- Bidirectional conflict detection (CPU+GPU writing same ranges simultaneously). Left to external
  devs who can read the dirty tracking state themselves.
- Generic `GpuTable<T>` abstraction. `GeometryTable` remains a domain-specific class in the mesh
  module that consumes this infrastructure.
- Implementing mirroring for Ring/Sparse/Suballocator composites in this pass. The interface
  supports them; implementations come when those paths are exercised. Note this limitation in
  their current implementations.

---

## Technical Decisions

### 1. `deferred` is a mutable, non-final field

HotSpot profile-guided speculation treats a rarely-changing field identically to a final one in
compiled code. The branch on `deferred` in the write hot path will be speculated away after
profiling. On the rare `setDeferred(true)` transition, an uncommon trap fires, the call site
recompiles — one-time microsecond cost. This is indistinguishable from `final` in steady state.

`setDeferred(true)` triggers an async flush of any already-dirty ranges before switching mode,
ensuring no stale accumulated state from immediate mode leaks into deferred mode.

### 2. CpuObservability is a visible strategy, not hidden

Like `AllocationStrategy` and `TransferStrategy`, `CpuObservability` is a public interface in the
buffers package. Users composing via `ManagedBuffer.builder()` can pass one explicitly. Users going
through `BufferFactory` get one selected automatically from their `MemoryStrategy` choice.

### 3. CpuObservability and DirtyStrategy are separate, peer axes

These are two separate fields on `ManagedBuffer`, not nested inside each other:
- `CpuObservability` — "do I have CPU-readable memory, and where is it?"
- `DirtyStrategy` — "which byte ranges have changed since last flush?"

Neither wraps or owns the other. `ManagedBuffer` holds both and composes them at flush time:
query the dirty strategy for ranges, copy them from the observability's readable memory to the
primary buffer (or vice versa for GPU->CPU). This avoids forced allocations and unnecessary
object nesting.

### 4. Multi-region vkCmdCopyBuffer for dirty flushes

`vkCmdCopyBuffer(cmd, src, dst, regionCount, pRegions)` already accepts an array of `VkBufferCopy`
regions. `VkCopy` currently always passes `regionCount = 1`. The dirty flush path will allocate an
array of `VkBufferCopy` (via `BumpAllocator`) and issue one call with all coalesced dirty regions.
This reduces command overhead from N calls to 1 when flushing scattered updates.

### 5. DirtyStrategy.markDirty is write-optimized and thread-safe

`markDirty(offset, length)` is likely to be in CPU hot paths and must be appropriately fast. It
is write-optimized: reads (iteration of dirty regions) happen on the rare/scheduled flush path.
The strategy may accept its backing collection through a non-default constructor or factory for
customization. The default can use a plain ArrayList for a simple starting implementation.

`markDirty` may be called concurrently from multiple threads (parallel CPU work nodes writing
disjoint regions of the same mirrored buffer). Thread-safety is the implementation's responsibility.

### 6. SPIR-V access qualifiers are conservative bounds

`NonWritable` / `NonReadable` decorations from SPIR-V provide declared capability, not runtime
behavior. Mega-shaders may declare `readwrite` for buffers written only in some code paths. The
reflection metadata provides the default/automatic case; the graph accepts manual `ResourceEdge`
overrides where the user knows better.

### 7. Mirroring for composites requires per-composite implementations

- **RingBuffer**: mirror per ring slot (or single mirror sized to one slot, tracking current)
- **SparseBuffer**: mirror only committed pages, track commit/decommit
- **SuballocatorBuffer**: mirror the whole backing (already allocated), expose at suballocation offsets

These are future work. The `CpuObservability` interface supports them; implementations are added
when exercised. For now, only `ManagedBuffer` with `DirectAllocationStrategy` (DEVICE_LOCAL)
gets the full mirroring + dirty tracking implementation. Ring/Sparse/Suballocator implementations
should document this limitation clearly.

### 8. GeometryTable migrates to use the new infrastructure

`GeometryTable` currently hand-rolls a `MemorySegment.ofArray` + manual dirty min/max tracking.
After this work, it should use `BufferFactory.create(DEVICE_LOCAL_MIRRORED, ...)` with deferred
mode, write to the buffer via `acquireWrite`, and call `flushDirty` once per frame. Its slot
management (BitSet, register/unregister) remains domain-specific.

### 9. MirrorCapable is NOT implemented by ManagedBuffer

`ManagedBuffer` should not implement `MirrorCapable` when its observability doesn't support it.
Instead, `MirrorCapable` is implemented by the `CpuObservability` implementation that actually
provides mirroring (i.e. the mirrored observability class). Users access it via
`buffer.observability()` and check `instanceof MirrorCapable`, or the buffer exposes a
convenience `asMirrorCapable()` that returns null when not applicable.

This avoids the `SparseCapable` pattern of implementing an interface that throws for most
instances.

### 10. DirtyStrategy auto-selection by buffer size

When not explicitly overridden, the factory selects a dirty strategy based on buffer size:
- < 4KB: `AlwaysDirtyStrategy` (tracking overhead exceeds transfer savings)
- < 1MB: `RangeCoalescingDirtyStrategy`
- >= 1MB: `BitSetDirtyStrategy`

Explicit choice is always available via builder for override.

### 11. flushDirty in immediate mode is a no-op

In immediate mode (`deferred == false`), every `acquireWrite` scope close immediately records the
GPU copy via the transfer batch. The dirty strategy still tracks ranges (for state inspection),
but they are immediately cleared by the transfer that was already issued. `flushDirty` in
immediate mode is therefore always a no-op — there is no un-transferred state to push. This is
valid and harmless to call (returns an already-complete `GpuCompletion`).

---

## Architecture

### Strategy Axes After This Work

```
ManagedBuffer
    AllocationStrategy       - WHERE memory lives (device-local, host-visible, ReBAR, sparse)
    TransferStrategy         - HOW data moves CPU <-> GPU (mapped, staged, direct, sparse)
    CpuObservability         - HOW the CPU observes buffer contents (none, inherent, mirrored)
    DirtyStrategy            - WHICH byte ranges have changed since last flush (always, range, bitset)
```

All four are separate fields, independently swappable, composed inside `ManagedBuffer`.

### CpuObservability Interface

```java
package io.github.yetyman.vulkan.buffers;

/**
 * Strategy for how the CPU observes (reads) buffer contents. Third axis of ManagedBuffer
 * composition, orthogonal to AllocationStrategy and TransferStrategy.
 *
 * Determines whether CPU reads are:
 * - unsupported (None: no CPU reads expected)
 * - free (Inherent: allocation is already host-visible)
 * - via a companion mirror buffer (Mirrored: separate host-visible buffer maintained in sync)
 */
public interface CpuObservability {

    /** @return readable memory for [offset, offset+size), or null if not observable */
    MemorySegment acquireReadable(long offset, long size);

    /** @return true if CPU can read this buffer's contents without a GPU stall */
    boolean isReadable();

    /** @return true if this observability maintains a separate mirror that can diverge */
    boolean isMirrored();

    /**
     * Lifecycle: initialize with the owning buffer's device, size, arena.
     * Called once by ManagedBuffer's constructor after the primary VkBuffer is allocated.
     */
    void initialize(VkDevice device, long bufferSize, MemorySegment primaryHandle, Arena arena);

    /** Lifecycle: release mirror resources. Called by ManagedBuffer.close(). */
    void close();
}
```

### CpuObservability Implementations

#### NoneObservability
- `acquireReadable` returns null
- `isReadable` returns false, `isMirrored` returns false
- Used when CPU reads are not expected (`DEVICE_LOCAL` without mirroring, `SPARSE` without mirroring)

#### InherentObservability
- `acquireReadable` returns the already-mapped primary memory (passed from TransferContext)
- `isReadable` returns true, `isMirrored` returns false
- Used for `MAPPED`, `MAPPED_CACHED`, `REBAR`

#### MirroredObservability (implements MirrorCapable)
- Allocates a host-visible, persistently-mapped `VkBuffer` companion at `initialize` time
- `acquireReadable` returns a slice of the mirror's mapped memory
- `isReadable` returns true, `isMirrored` returns true
- Implements `MirrorCapable` — provides `mirrorMemory()`, mirror handle for copy source
- Used for `DEVICE_LOCAL_MIRRORED`

### MirrorCapable Interface

```java
package io.github.yetyman.vulkan.buffers;

/**
 * Capability interface for CpuObservability implementations that maintain a separate
 * CPU-side mirror buffer. Check via instanceof on the buffer's observability().
 *
 * Provides access to the mirror's underlying resources for flush operations and
 * direct memory access.
 */
public interface MirrorCapable {

    /** @return the mirror's mapped memory for direct CPU access */
    MemorySegment mirrorMemory();

    /** @return the mirror VkBuffer handle (source for CPU->GPU copies, dest for GPU->CPU) */
    MemorySegment mirrorHandle();

    /** @return the size of the mirror buffer */
    long mirrorSize();
}
```

This is narrow and focused: it tells you "there's a separate mirror buffer and here it is."
The deferred/dirty/flush logic lives on `ManagedBuffer` and composes the observability with
the dirty strategy — it doesn't need to be on this interface.

### DirtyStrategy Interface

```java
package io.github.yetyman.vulkan.buffers;

/**
 * Tracks which byte ranges of a buffer have been modified since last flush.
 * Implementations coalesce adjacent/overlapping ranges to minimize transfer count.
 *
 * Write-optimized: markDirty is expected to be in CPU hot paths and must be fast.
 * Region iteration (dirtyRegions) happens on the rare/scheduled flush path.
 *
 * Thread-safe: markDirty may be called concurrently from multiple threads.
 */
public interface DirtyStrategy {

    /** Mark a byte range as dirty. Thread-safe. Must be fast (hot path). */
    void markDirty(long offset, long size);

    /** @return number of coalesced dirty regions available */
    int dirtyRegionCount();

    /** @return the dirty regions as offset+size pairs, coalesced. Caller must not retain. */
    DirtyRegionIterator dirtyRegions();

    /** Clear all dirty state. Called after a successful flush. */
    void clear();

    /** @return true if any ranges are dirty */
    boolean isDirty();
}
```

### DirtyStrategy Implementations

#### AlwaysDirtyStrategy
- `isDirty()` always returns true
- `dirtyRegionCount()` returns 1
- `dirtyRegions()` yields a single region (0, bufferSize)
- `markDirty` is a no-op (everything is always dirty)
- Used for small buffers (< 4KB) where tracking overhead exceeds transfer savings
- `bufferSize` set at construction (or a sentinel indicating "whole buffer")

#### RangeCoalescingDirtyStrategy
- Maintains a sorted list of dirty intervals
- `markDirty` merges overlapping/adjacent intervals (with configurable gap threshold, default 256 bytes)
- `dirtyRegions()` iterates the merged intervals
- Thread-safe via lightweight synchronization (contention is low — writes are fast, reads are rare)
- Primary implementation for medium buffers (4KB - 1MB)
- Default backing collection: plain ArrayList (configurable via constructor)

#### BitSetDirtyStrategy
- Divides buffer into fixed-size pages (e.g. 4KB or 64B granularity, configurable)
- `markDirty` sets bits for affected pages — very fast, O(1) per page
- `dirtyRegions()` scans for contiguous set-bit runs
- Bounded memory overhead proportional to buffer size / page size
- Good for very large buffers (>= 1MB) with scattered small writes (geometry tables, transform arrays)

### DirtyRegionIterator

```java
/** Zero-allocation iterator over dirty regions. */
public interface DirtyRegionIterator {
    boolean hasNext();
    /** Advance to next region. */
    void next();
    long offset();
    long size();
}
```

### Deferred Mode

Added to `ManagedBuffer`:

```java
/** Sets whether writes accumulate (true) or immediately transfer (false). */
void setDeferred(boolean deferred);

/** @return current deferred state */
boolean isDeferred();

/**
 * Flushes all dirty ranges to the GPU using multi-region copy.
 * Returns a GpuCompletion for the batch. No-op if nothing is dirty.
 * In immediate mode (deferred == false), always a no-op since writes already transferred.
 */
GpuCompletion flushDirty(VkQueue queue);

/**
 * Async version: flushes dirty ranges, records into batch, returns immediately.
 * Caller must eventually await the completion or flush the batch.
 * In immediate mode, always a no-op.
 */
GpuCompletion flushDirtyAsync(VkQueue queue);
```

Behavior:
- When `deferred == false` (default): `acquireWrite` / `writeAsync` behave as today — write +
  immediate transfer. The dirty strategy still tracks ranges for state inspection, but they are
  cleared immediately after the copy is issued. `flushDirty` is always a no-op in this mode
  because there is never un-transferred state.
- When `deferred == true`: `acquireWrite` writes to the mirror, marks dirty via the strategy,
  no GPU command issued. GPU transfer happens only on explicit `flushDirty`.
- `setDeferred(true)`: if dirty state exists from immediate-mode tracking, triggers
  `flushDirtyAsync` (though in practice this is a no-op since immediate mode clears dirty
  state on every write). The real purpose is the mode switch.
- `setDeferred(false)`: future writes go back to immediate mode. No retroactive flush needed
  since deferred mode already requires explicit flush.

### GPU->CPU Dirty Tracking

```java
/**
 * Marks a range as modified by the GPU (e.g. after a compute dispatch).
 * Call this after GPU work completes (fence/semaphore awaited), or let the graph call it.
 */
void markGpuDirty(long offset, long size);

/**
 * Reads back GPU-dirty ranges into the mirror. Multi-region copy from primary -> mirror.
 * Only meaningful when observability isMirrored(). No-op otherwise.
 */
GpuCompletion readDiff(VkQueue queue);
```

`ManagedBuffer` holds a second `DirtyStrategy` instance for GPU->CPU tracking. Same interface,
same implementations available. Separate from the CPU->GPU dirty strategy.

### ManagedBuffer Composition After This Work

```java
public final class ManagedBuffer implements IBuffer, SparseCapable {
    private final AllocationStrategy allocation;
    private final TransferStrategy transfer;
    private final CpuObservability observability;
    private final DirtyStrategy cpuDirty;      // CPU->GPU dirty tracking
    private final DirtyStrategy gpuDirty;      // GPU->CPU dirty tracking
    private volatile boolean deferred;
    // ...
}
```

Access patterns:
```java
// Check if this buffer supports mirroring
if (buffer.observability() instanceof MirrorCapable mirror) {
    MemorySegment readable = mirror.mirrorMemory();
    // ...
}

// Deferred mode
buffer.setDeferred(true);
// ... writes accumulate ...
buffer.flushDirty(queue);

// GPU readback
buffer.markGpuDirty(offset, length);
buffer.readDiff(queue);

// Inspect dirty state
DirtyStrategy dirty = buffer.cpuDirtyStrategy();
if (dirty.isDirty()) { /* ... */ }
```

---

## Integration Points

### BufferFactory Changes

```java
case DEVICE_LOCAL_MIRRORED -> managedBuffer(device, size, usage,
        new DirectAllocationStrategy(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value(), false),
        new StagingTransferStrategy(false, transferQueue),
        new MirroredObservability(),
        selectDirtyStrategy(size));
```

`ManagedBuffer.Builder` gains:
```java
public Builder observability(CpuObservability obs) { this.observability = obs; return this; }
public Builder dirtyStrategy(DirtyStrategy dirty) { this.cpuDirty = dirty; return this; }
public Builder gpuDirtyStrategy(DirtyStrategy dirty) { this.gpuDirty = dirty; return this; }
```

If not set explicitly, `ManagedBuffer.Builder.build()` infers:
- Observability: if allocation is persistent-mapped -> `InherentObservability`, otherwise -> `NoneObservability`
- DirtyStrategy: selected by buffer size (< 4KB AlwaysDirty, < 1MB RangeCoalescing, >= 1MB BitSet)
- GpuDirtyStrategy: defaults to same selection as cpuDirty (or None if observability is None)

### TransferStrategy Interaction

When `deferred == true` and `CpuObservability` is mirrored:
- `acquireWrite` returns a scope over the mirror's mapped memory
- Scope's `onCommit` calls `cpuDirty.markDirty(offset, size)` — no GPU copy issued
- The GPU copy happens later in `flushDirty`

When `deferred == false` (immediate mode, current behavior):
- `acquireWrite` returns a scope over the mirror's mapped memory (if mirrored) or staging (if not)
- Scope's `onCommit` records the GPU copy AND calls `cpuDirty.markDirty` then immediately
  `cpuDirty.clear()` for that range (or just doesn't bother tracking at all — implementation detail)
- The transfer is already enqueued

When observability is `InherentObservability` (MAPPED/REBAR):
- `acquireWrite` returns the mapped memory itself (same as today)
- Dirty tracking is irrelevant — writes land directly in GPU-visible memory
- `deferred` has no effect (writes are instantly visible, no copy needed)
- `flushDirty` is a no-op

### VkCopy Multi-Region Extension

Add to `VkCopy`:
```java
/**
 * Issues a multi-region buffer copy in one vkCmdCopyBuffer call.
 */
public static void copyBufferMultiRegion(MemorySegment cmd, MemorySegment src, MemorySegment dst,
                                         long[] srcOffsets, long[] dstOffsets, long[] sizes, int count) {
    BumpAllocator ba = BumpAllocator.get();
    ba.push();
    try {
        MemorySegment regions = ba.alloc(VkBufferCopy.sizeof() * count);
        for (int i = 0; i < count; i++) {
            MemorySegment region = regions.asSlice((long) i * VkBufferCopy.sizeof(), VkBufferCopy.sizeof());
            VkBufferCopy.srcOffset(region, srcOffsets[i]);
            VkBufferCopy.dstOffset(region, dstOffsets[i]);
            VkBufferCopy.size(region, sizes[i]);
        }
        VulkanFFM.vkCmdCopyBuffer(cmd, src, dst, count, regions);
    } finally {
        ba.pop();
    }
}
```

### Render Graph Integration (vulkan-ffm-graph module)

The graph module depends on vulkan-core. It can:
1. Detect `MirrorCapable` on `buffer.observability()` via `instanceof`
2. At compile time, identify buffers where:
   - A `CpuWorkNode` declares a write
   - A subsequent GPU node (graphics/compute) declares a read
   - The buffer has mirrored observability and is deferred
3. Automatically insert a transfer node between them that calls `flushDirty`
4. For GPU->CPU: identify buffers where a `ComputePassNode` declares a write and a subsequent
   `CpuWorkNode` declares a read — insert `readDiff` between them

Opt-out: a flag on the `VkBufferGraphResource` or the relevant `ResourceEdge` indicating
"I'll flush this buffer manually, don't insert automatic flush nodes."

This is additive — the graph works without it (manual flush calls), but can automate it.

### Shader Reflection: Access Qualifier

Add to `DescriptorBindingInfo`:
```java
public enum AccessQualifier { READ_ONLY, WRITE_ONLY, READ_WRITE }

private final AccessQualifier accessQualifier;

public AccessQualifier getAccessQualifier() { return accessQualifier; }
```

Populated from SPIRV-Reflect's `SpvReflectDecorationFlags`:
- `SPV_REFLECT_DECORATION_NON_WRITABLE` -> `READ_ONLY`
- `SPV_REFLECT_DECORATION_NON_READABLE` -> `WRITE_ONLY`
- Neither -> `READ_WRITE`

`ShaderGenerator` emits this in generated wrapper classes. Graph integration layers can use it to
auto-derive `ResourceEdge.read` vs `ResourceEdge.write` from shader bindings.

Add test cases to `ShaderExample` demonstrating:
- A compute shader with `readonly` SSBO -> reflected as `READ_ONLY`
- A compute shader with `writeonly` SSBO -> reflected as `WRITE_ONLY`
- A compute shader with `buffer` (no qualifier) -> reflected as `READ_WRITE`

---

## Migration Path

### Phase 1: Core Infrastructure (DONE)
1. Create `CpuObservability` interface
2. Create `NoneObservability`, `InherentObservability`, `MirroredObservability` implementations
3. Create `MirrorCapable` interface (implemented by `MirroredObservability`)
4. Create `DirtyStrategy` interface and `DirtyRegionIterator`
5. Create `AlwaysDirtyStrategy`, `RangeCoalescingDirtyStrategy`, `BitSetDirtyStrategy`
6. Add `observability`, `cpuDirty`, `gpuDirty` fields + builder methods to `ManagedBuffer`
7. Add `setDeferred`/`isDeferred`/`flushDirty`/`flushDirtyAsync`/`markGpuDirty`/`readDiff` to `ManagedBuffer`
8. Add `observability()`, `cpuDirtyStrategy()`, `gpuDirtyStrategy()` accessors to `ManagedBuffer`
9. Add multi-region `VkCopy.copyBufferMultiRegion`
10. Implement `flushDirty` using multi-region copy composed from dirty strategy + observability
11. Implement `readDiff` using multi-region copy from primary -> mirror

### Phase 2: Factory and Strategy Selection (DONE)
1. Update `BufferFactory` to pass observability + dirty strategy for each `MemoryStrategy` case
2. `DEVICE_LOCAL_MIRRORED` creates `MirroredObservability` + size-based dirty strategy
3. `MAPPED`, `MAPPED_CACHED`, `REBAR` create `InherentObservability` + `AlwaysDirtyStrategy` (no-op in practice)
4. `DEVICE_LOCAL`, `STAGING`, `SPARSE` create `NoneObservability` + no dirty tracking needed
5. `BufferStrategySelector` (`createAutomatic`) selects observability from access patterns:
   - `cpuRead != NEVER` + `gpuWrite != NEVER` -> `MirroredObservability` (bidirectional)
   - `cpuRead != NEVER` + device-local allocation -> `MirroredObservability`
   - Otherwise -> inferred from allocation

### Phase 3: Remove MirroredBuffer Decorator (DONE)

**Status: COMPLETE.**

All usages of the `MirroredBuffer` class that must be migrated to the new infrastructure:

#### Direct instantiations to remove/replace:
- `vulkan-core/.../buffers/BufferExample.java` lines ~617, ~636 — `new MirroredBuffer(device, ...)`
  test code. Replace with `BufferFactory.create(DEVICE_LOCAL_MIRRORED, ...)` + cast to
  `ManagedBuffer` for mirror access via `observability()`.

#### instanceof checks to migrate (typed buffers):
- `vulkan-core/.../buffers/typed/FloatVkBuffer.java` line ~38 — `(buffer instanceof MirroredBuffer m) ? m.mirror().asFloatBuffer() : null`
- `vulkan-core/.../buffers/typed/IntVkBuffer.java` line ~34 — same pattern, `.asIntBuffer()`
- `vulkan-core/.../buffers/typed/LongVkBuffer.java` line ~34 — same pattern, `.asLongBuffer()`
- `vulkan-core/.../buffers/typed/ShortVkBuffer.java` line ~34 — same pattern, `.asShortBuffer()`
- `vulkan-core/.../buffers/typed/DoubleVkBuffer.java` line ~34 — same pattern, `.asDoubleBuffer()`

  These need to change to check:
  `buffer instanceof ManagedBuffer mb && mb.observability() instanceof MirrorCapable mc`
  and then use `mc.mirrorMemory()` to derive the typed buffer view.

#### Javadoc / comment references to update:
- `vulkan-core/.../buffers/ManagedBuffer.java` lines ~338, ~354 — javadoc mentions `MirroredBuffer`
- `vulkan-core/.../buffers/StagingTransferStrategy.java` line ~27 — javadoc recommends `MirroredBuffer`
- `vulkan-core/.../buffers/BufferReadScope.java` line ~10 — javadoc recommends `MirroredBuffer`
- `vulkan-core/.../buffers/BufferExample.java` lines ~614-615 — comments referencing `MirroredBuffer`

#### Methods to remove from ManagedBuffer after MirroredBuffer is gone:
- `copyFromExternal(MemorySegment srcHandle, ...)` — only existed for MirroredBuffer's optimization
- `copyToExternal(MemorySegment dstHandle, ...)` — only existed for MirroredBuffer's refreshFromGpu

  These can be removed once no code routes through MirroredBuffer. The new `flushDirty`/`readDiff`
  path uses `TransferBatch.recordMultiRegion` directly.

#### The class itself:
- `vulkan-core/.../buffers/MirroredBuffer.java` — mark `@Deprecated` first, remove after all migrated

#### Import cleanup:
- All 5 typed buffer files import `MirroredBuffer` — remove after instanceof migration

#### Documentation:
- `docs/major-subsystems/buffers/buffers.md` — rewrite "Composite Wrappers" section and
  "MirroredBuffer and the staging-copy elimination" subsection to describe the new 4-axis model

#### Steps (in order):
1. Mark `MirroredBuffer` as `@Deprecated`
2. Migrate typed buffer `instanceof MirroredBuffer` checks to use `MirrorCapable` on observability
3. Migrate `BufferExample` to use `DEVICE_LOCAL_MIRRORED` + `ManagedBuffer` API
4. Update all javadoc references from `MirroredBuffer` to the new observability API
5. Remove `copyFromExternal` / `copyToExternal` from `ManagedBuffer`
6. Remove `MirroredBuffer.java`
7. Remove now-unused imports in typed buffers
8. Update `buffers.md` documentation

### Phase 4: Shader Reflection (DONE)
1. Add `AccessQualifier` enum to `ShaderLoader`
2. Extract `SPV_REFLECT_DECORATION_NON_WRITABLE` / `SPV_REFLECT_DECORATION_NON_READABLE` in reflection parsing
3. Add `accessQualifier` field to `DescriptorBindingInfo`
4. Update `ShaderGenerator` to emit access qualifier in generated classes
5. Add test shaders and assertions to `ShaderExample`

### Phase 5: Graph Integration (vulkan-ffm-graph) (DONE)
1. Add logic to `GraphCompiler` to detect deferred mirrored buffers
2. Auto-insert flush nodes between CPU writers and GPU readers
3. Auto-insert readDiff nodes between GPU writers and CPU readers
4. Add opt-out flag on `VkBufferGraphResource` or `ResourceEdge` for manual flush control
5. Optional: auto-derive ResourceEdge read/write from shader reflection's AccessQualifier

### Phase 6: GeometryTable Migration (DONE)
1. Replace `MemorySegment.ofArray` + manual `IBuffer` with `BufferFactory.create(DEVICE_LOCAL_MIRRORED, ...)`
2. Use `setDeferred(true)` at construction
3. Replace manual `writeRecord` -> `acquireWrite` on the buffer directly
4. Replace manual `flush` -> `flushDirty`
5. Remove redundant dirty tracking code (keep BitSet slot management as domain-specific concern)

---

## Open Questions (Resolved)

1. **DirtyStrategy selection heuristic**: Auto-select based on size (< 4KB AlwaysDirty,
   < 1MB RangeCoalescing, >= 1MB BitSet). Explicit choice always available via builder override.
   **RESOLVED: yes, auto-select by size.**

2. **Coalescing gap threshold**: Configurable per `RangeCoalescingDirtyStrategy` instance,
   default 256 bytes. This is specific to that strategy implementation; simple and trivially
   upgradeable later since it's behind a strategy pattern.
   **RESOLVED: 256 byte default, configurable.**

3. **Ring/Sparse/Suballocator mirroring**: Deferred to future work. Note as limitation in their
   current implementations. The interface is ready for them.
   **RESOLVED: deferred, document limitation.**

4. **flushDirty on non-deferred buffers**: Always a no-op in immediate mode. Immediate mode
   writes already transfer on scope close — there is never un-transferred state. `flushDirty`
   returns an already-complete `GpuCompletion`. Harmless to call, just does nothing.
   **RESOLVED: valid but always no-op.**

5. **Graph auto-flush opt-out**: A flag on the `VkBufferGraphResource` or the relevant node/edge
   indicating manual flush control.
   **RESOLVED: flag on resource or edge.**

---

## File Inventory (New/Modified)

### New Files
- `vulkan-core/.../buffers/CpuObservability.java`
- `vulkan-core/.../buffers/NoneObservability.java`
- `vulkan-core/.../buffers/InherentObservability.java`
- `vulkan-core/.../buffers/MirroredObservability.java` (implements CpuObservability + MirrorCapable)
- `vulkan-core/.../buffers/DirtyStrategy.java`
- `vulkan-core/.../buffers/DirtyRegionIterator.java`
- `vulkan-core/.../buffers/AlwaysDirtyStrategy.java`
- `vulkan-core/.../buffers/RangeCoalescingDirtyStrategy.java`
- `vulkan-core/.../buffers/BitSetDirtyStrategy.java`
- `vulkan-core/.../buffers/MirrorCapable.java`

### Modified Files
- `vulkan-core/.../buffers/ManagedBuffer.java` — add observability/dirty fields, deferred mode, flush methods, accessors
- `vulkan-core/.../buffers/BufferFactory.java` — pass observability + dirty for each MemoryStrategy
- `vulkan-core/.../buffers/TransferContext.java` — expose observability reference
- `vulkan-core/.../buffers/StagingTransferStrategy.java` — interact with observability/dirty on write
- `vulkan-core/.../command/VkCopy.java` — add `copyBufferMultiRegion`
- `vulkan-core/.../shaders/ShaderLoader.java` — extract access qualifiers in reflection
- `vulkan-core/.../shaders/ShaderGenerator.java` — emit access qualifier
- `vulkan-core/.../buffers/MirroredBuffer.java` — deprecate, then remove
- `vulkan-core/.../buffers/MemoryStrategy.java` — update javadoc for DEVICE_LOCAL_MIRRORED
- `vulkan-ffm-mesh/.../mesh/consume/GeometryTable.java` — migrate to new infrastructure
- `vulkan-ffm-graph/.../graph/GraphCompiler.java` — auto-insert flush/readDiff nodes
- `vulkan-ffm-graph/.../graph/resources/VkBufferGraphResource.java` — add manual-flush flag
- `docs/major-subsystems/buffers/buffers.md` — update to document the four-axis model

### Test Files
- New GLSL shaders with `readonly`/`writeonly` qualifiers for reflection tests
- Updated `ShaderExample` with access qualifier assertions
- New or updated buffer tests exercising deferred mode, dirty tracking, multi-region flush

---

## Future Considerations

### Deferring / Flush Scheduling Strategy

There may eventually be a need for automatic flush policies beyond "manual" and "graph-driven" —
e.g. flush after N writes, flush when dirty bytes exceed a threshold, flush every N ms. This
could manifest as:
- A separate deferring strategy axis
- A dirty strategy variant that triggers flush internally at some threshold
- A graph scheduling hint

There is enough overlap with `DirtyStrategy` (which already knows how much is dirty) and with
the graph's scheduling (which already decides when work runs) that this does not warrant its own
axis yet. Noted here so it is not re-derived from scratch if the need arises.
