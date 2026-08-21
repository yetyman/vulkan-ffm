# Buffer System

## Overview

The buffer system provides a composable, strategy-based abstraction over Vulkan buffer memory.
Four orthogonal concerns are represented as separate, independently swappable strategy interfaces,
composed inside a single concrete implementation (`ManagedBuffer`):

1. **AllocationStrategy** -- where memory lives (device-local, host-visible, ReBAR, sparse)
2. **TransferStrategy** -- how data moves between CPU and GPU (mapped, staged, direct, sparse)
3. **CpuObservability** -- how the CPU observes buffer contents (none, inherent, mirrored)
4. **DirtyStrategy** -- which byte ranges have changed since last flush (always, range, bitset)

This replaced an earlier inheritance-based design (`AbstractBuffer` with one subclass per memory
type, plus a `MirroredBuffer` decorator) that forced fixed combinations of allocation and transfer
behavior and could not represent buffers with different needs on any axis without duplicating logic
across subclasses or wrapping with decorators.

A generic `IBuffer` interface sits above `ManagedBuffer` so that custom buffer implementations
(graph-transient resources, externally-owned handles, test doubles) can participate in the same
APIs -- descriptor binding, typed views, copy operations -- without depending on the strategy
composition machinery.

## Package Structure

```
io.github.yetyman.vulkan.buffers/
    IBuffer.java                    - generic contract: handle/size/usage/write/read/copy/scopes/close
    ManagedBuffer.java               - primary IBuffer impl: composes all four strategy axes
    AllocationStrategy.java          - interface: where memory lives, how it is mapped
    DirectAllocationStrategy.java    - one dedicated VkBuffer + VkDeviceMemory, fixed memory property flags
    ReBarAllocationStrategy.java     - DEVICE_LOCAL | HOST_VISIBLE | HOST_COHERENT, always persistently mapped
    SparseAllocationStrategy.java    - SPARSE_BINDING VkBuffer, owns a SparsePageAllocator
    TransferStrategy.java            - interface: how data moves CPU <-> GPU, incl. acquireWrite/acquireRead
    MappedTransferStrategy.java      - direct memcpy into persistent map, flush/invalidate on non-coherent
    DirectTransferStrategy.java      - direct memcpy into ReBAR memory, always coherent
    StagingTransferStrategy.java     - staging buffer + vkCmdCopyBuffer; persistent or transient staging
    SparseTransferStrategy.java      - page-commit-aware; host-visible per-page memcpy or staged device-local
    TransferContext.java             - per-buffer state bundle passed into stateless strategy instances
    CpuObservability.java            - interface: how the CPU reads buffer contents
    NoneObservability.java           - no CPU reads expected (device-local without mirroring)
    InherentObservability.java       - allocation is already host-visible (MAPPED, REBAR)
    MirroredObservability.java       - separate host-visible companion buffer for CPU reads + staging source
    MirrorCapable.java               - capability interface for observability implementations with a mirror buffer
    DirtyStrategy.java               - interface: which byte ranges changed since last flush
    DirtyRegionIterator.java         - zero-allocation iterator over dirty regions
    AlwaysDirtyStrategy.java         - whole buffer always dirty (< 4KB: tracking overhead exceeds savings)
    RangeCoalescingDirtyStrategy.java - sorted interval list with merge (4KB - 1MB)
    BitSetDirtyStrategy.java         - fixed-size page bitmap (>= 1MB)
    SparseCapable.java                - capability interface: pageSize/commitPages/decommitPages/isCommitted
    MemoryStrategy.java               - enum vocabulary for BufferFactory strategy selection
    BufferFactory.java                - primary construction entry point; builds strategy compositions
    BufferStrategySelector.java       - automatic strategy selection from access-pattern parameters
    BufferStrategyTable.java          - decision matrix backing the automatic selector
    AccessFrequency.java, DataScale.java, BufferUsage.java - selector input vocabulary
    RingBuffer.java                   - N-buffered composite wrapping independent IBuffer instances
    SuballocatorBuffer.java           - fixed-slot slab allocator over one backing IBuffer
    SparsePageAllocator.java          - page bind/unbind/map/unmap lifecycle for sparse buffers
    GpuLayout.java                    - offset-explicit serialization strategy for a type
    HasGpuLayout.java                 - implemented by types declaring a canonical GpuLayout
    BufferWriteScope.java, DefaultBufferWriteScope.java
    BufferReadScope.java,  DefaultBufferReadScope.java
                                       - borrowed native memory for one buffer range; see Write and Read Scopes
    GpuCompletion.java                - scheduler-agnostic handle to asynchronous GPU work
    TransferCompletion.java           - GpuCompletion backed by a TransferBatch generation
    TimelineCompletion.java           - GpuCompletion backed by a timeline semaphore target value
    CompletedGpuCompletion.java       - shared no-op completion for work needing no submission
    TransferBatch.java, TransferBatchManager.java, BatchTransferCompletion.java
                                       - per-thread per-queue command batching and completion tracking

io.github.yetyman.vulkan.buffers.typed/
    TypedVkBuffer.java                - typed array view over any IBuffer, serialized by a required GpuLayout
    FloatVkBuffer, IntVkBuffer, LongVkBuffer, ShortVkBuffer, DoubleVkBuffer
                                       - primitive typed views: bulk, strided, and MemorySegment paths
```

## Core Composition

```java
public final class ManagedBuffer implements IBuffer, SparseCapable {
    private final AllocationStrategy allocation;    // where memory lives, how it is mapped
    private final TransferStrategy transfer;        // how data moves CPU <-> GPU
    private final CpuObservability observability;   // how the CPU observes buffer contents
    private final DirtyStrategy cpuDirty;           // CPU->GPU dirty tracking
    private final DirtyStrategy gpuDirty;           // GPU->CPU dirty tracking
    private volatile boolean deferred;              // whether writes accumulate or transfer immediately
    private final VkBuffer vkBuffer;
}
```

All four strategy axes are fully orthogonal -- any allocation strategy can be paired with any
transfer strategy its capabilities support, any observability, and any dirty strategy.
`TransferContext` bundles everything a transfer strategy needs to act on a specific `ManagedBuffer`
(device, arena, mapped memory or null, sparse page allocator or null) so that strategy instances
themselves are stateless with respect to any single buffer.

### Strategy pairings behind each `MemoryStrategy`

| MemoryStrategy          | AllocationStrategy         | TransferStrategy                  | CpuObservability | DirtyStrategy | CPU copies per write |
|--------------------------|-----------------------------|-------------------------------------|---|---|:---:|
| `MAPPED`                | Direct, HOST_VISIBLE\|COHERENT, persistent | Mapped(coherent=true)  | Inherent | forSize(n) | 1 |
| `MAPPED_CACHED`          | Direct, HOST_VISIBLE\|CACHED, persistent   | Mapped(coherent=false) | Inherent | forSize(n) | 1 |
| `DEVICE_LOCAL`           | Direct, DEVICE_LOCAL, not persistent       | Staging(transient)     | None | forSize(n) | 1 (+1 GPU copy) |
| `DEVICE_LOCAL_MIRRORED`  | Direct, DEVICE_LOCAL, not persistent       | Staging(transient)     | Mirrored | forSize(n) | 1 (+1 GPU copy) |
| `STAGING`                | Direct, DEVICE_LOCAL, not persistent       | Staging(persistent)    | None | forSize(n) | 1 (+1 GPU copy) |
| `REBAR`                  | ReBar, always persistent   | Direct                              | Inherent | forSize(n) | 1 |
| `SPARSE`                 | Sparse, owns page allocator | Sparse (host-visible memcpy or staged device-local) | None | forSize(n) | 1 (host-visible) or 1+1GPU (device-local) |

`RING_BUFFER` and `SUBALLOCATOR` are not leaf strategy pairings -- they are composite `IBuffer`
wrappers over N independent `ManagedBuffer`/`IBuffer` instances (`RingBuffer`) or slices of one
(`SuballocatorBuffer`); see Composite Wrappers below.

## CPU Observability

`CpuObservability` is the third strategy axis, determining whether and how the CPU can read buffer
contents without a GPU stall:

### NoneObservability
- CPU reads are not expected. `acquireReadable` returns null.
- Used for `DEVICE_LOCAL` (without mirroring), `SPARSE`.
- Reads through `IBuffer.acquireRead` fall through to the transfer strategy's stalling readback.

### InherentObservability
- The allocation is already host-visible (persistently mapped). `acquireReadable` returns the
  mapped memory itself -- zero cost, zero copy.
- Used for `MAPPED`, `MAPPED_CACHED`, `REBAR`.
- No separate mirror needed: the CPU already has direct visibility.

### MirroredObservability (implements MirrorCapable)
- Allocates a separate host-visible, persistently-mapped `VkBuffer` companion at initialization.
- `acquireReadable` returns a slice of the mirror's mapped memory.
- The mirror serves dual purpose:
  - CPU-readable surface: zero-cost random-access reads without GPU stalls
  - Staging source: CPU->GPU copies issue directly from the mirror's memory, eliminating the
    redundant second CPU copy into a throwaway staging buffer
- Used for `DEVICE_LOCAL_MIRRORED`.
- Check via `buffer.observability() instanceof MirrorCapable mirror`.

### MirrorCapable interface

```java
public interface MirrorCapable {
    MemorySegment mirrorMemory();   // direct CPU access to mirrored data
    MemorySegment mirrorHandle();   // VkBuffer handle (source for CPU->GPU, dest for GPU->CPU)
    long mirrorSize();              // mirror size in bytes (matches primary)
}
```

## Dirty Tracking

`DirtyStrategy` tracks which byte ranges of a buffer have been modified since last flush.
`ManagedBuffer` holds two instances: one for CPU->GPU direction, one for GPU->CPU.

### Implementations

| Strategy | Buffer size | Behavior |
|----------|-------------|----------|
| `AlwaysDirtyStrategy` | < 4KB | Whole buffer always dirty; tracking overhead exceeds savings |
| `RangeCoalescingDirtyStrategy` | 4KB - 1MB | Sorted interval list with configurable gap merge (default 256B) |
| `BitSetDirtyStrategy` | >= 1MB | Fixed-size page bitmap; O(1) markDirty, bounded memory |

Auto-selected by `DirtyStrategy.forSize(bufferSize)`. Explicit choice available via
`ManagedBuffer.Builder.dirtyStrategy()`.

### Thread safety

`markDirty(offset, size)` is thread-safe and write-optimized (hot path). Region iteration
(`dirtyRegions()`) happens only on the rare/scheduled flush path.

## Deferred Mode

`ManagedBuffer` supports a `deferred` mode that controls whether writes immediately transfer or
accumulate for explicit flush:

```java
buffer.setDeferred(true);
// ... writes accumulate in the mirror, dirty strategy tracks ranges ...
buffer.flushDirty(queue);  // multi-region copy, all dirty ranges in one vkCmdCopyBuffer call
```

- **Immediate mode** (default, `deferred == false`): writes transfer immediately on scope close.
  `flushDirty` is always a no-op since there is never un-transferred state.
- **Deferred mode** (`deferred == true`): writes land in the mirror only, marking the dirty
  strategy. GPU transfer happens only on explicit `flushDirty` or `flushDirtyAsync`.

For observability types without a mirror (`InherentObservability`), deferred mode has no effect --
writes land directly in GPU-visible memory with no copy needed.

### GPU->CPU readback

```java
buffer.markGpuDirty(offset, length);  // after a compute dispatch writes this buffer
buffer.readDiff(queue);                // multi-region copy from primary -> mirror
```

`markGpuDirty` uses the separate `gpuDirty` strategy instance. `readDiff` copies only the
GPU-dirty ranges back into the mirror, then clears the GPU dirty state.

## Composite Wrappers

`RingBuffer` and `SuballocatorBuffer` implement `IBuffer` directly and hold `IBuffer` reference(s)
internally -- no inheritance from `ManagedBuffer` or any shared base class. This is deliberate:
these represent orthogonal concerns from allocation/transfer (multiplicity and slicing respectively)
and neither needs its own allocation or transfer logic -- they delegate to whatever `IBuffer`(s)
they wrap.

- **`RingBuffer`** -- N-buffered, one slot per frame in flight. Either N independent underlying
  buffers (separate-buffers mode, rebind each frame) or one buffer sized `N * alignedFrameSize`
  bound with a dynamic offset (single-offset mode, bind once). Tracks in-flight `TransferCompletion`
  per slot and awaits it before slot reuse.
- **`SuballocatorBuffer`** -- fixed-slot slab allocator over one backing `IBuffer`. O(1) alloc/free
  via a free-slot stack. Each `Suballocation` implements `IBuffer` itself, delegating reads/writes
  to the backing buffer at a fixed offset; `close()` returns the slot to the slab.

NOTE: Mirroring for Ring/Sparse/Suballocator composites requires per-composite implementations
and is deferred to future work. The `CpuObservability` interface supports them; implementations
will be added when those paths are exercised.

## Sparse Buffers

`SparseAllocationStrategy` allocates a `VkBuffer` with `VK_BUFFER_CREATE_SPARSE_BINDING_BIT` and no
backing memory bound at creation, and owns a `SparsePageAllocator` for page lifecycle. Pages are
committed on demand:

- `SparseTransferStrategy.writeAsync`/`read` call `ensurePagesCommitted`/`validatePagesCommitted`
  before touching the buffer, then either memcpy directly into a mapped page (host-visible
  underlying memory) or route through a transient staging buffer + `vkCmdCopyBuffer`
  (device-local underlying memory).
- `ManagedBuffer` implements `SparseCapable` when its allocation strategy is
  `SparseAllocationStrategy`, exposing `pageSize()`, `commitPages(offset, length)`,
  `decommitPages(offset, length)`, and `isCommitted(offset, length)` for explicit control --
  e.g. virtual texturing or streaming systems that need to evict pages, not just commit them.
  `decommitPages` only unbinds pages fully covered by the given range; a page partially covered at
  either boundary is left committed.
- `SparseCapable` is not part of `IBuffer` -- arbitrary custom buffer implementations have no
  inherent page semantics. Check `instanceof SparseCapable` rather than assuming every `IBuffer`
  supports it.

## Construction

`BufferFactory` is the primary construction entry point and the primary place `MemoryStrategy`
values are translated into concrete strategy compositions:

```java
IBuffer buf = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, size, BufferUsage.STORAGE, device, queue);
SuballocatorBuffer slab = BufferFactory.createSlab(totalSize, slotSize, usage, MemoryStrategy.MAPPED, device, queue);
ManagedBuffer sparse = BufferFactory.createSparse(size, usage, MemoryStrategy.DEVICE_LOCAL, device, sparseQueue, transferQueue);
IBuffer auto = BufferFactory.createAutomatic(cpuWrite, cpuRead, gpuRead, gpuWrite, size, usage, device, queue);
```

`ManagedBuffer.builder()` also exists for callers who need to compose strategies directly rather
than through the `MemoryStrategy` enum vocabulary -- `BufferFactory` is preferred for common cases.

### Automatic strategy selection and the ReBAR upgrade

`BufferStrategySelector.select(cpuWrite, cpuRead, gpuRead, gpuWrite, size)` runs a decision matrix
(`BufferStrategyTable`) over access-frequency parameters to pick a `MemoryStrategy`. On ReBAR-capable
hardware, the selector additionally upgrades `STAGING` and `DEVICE_LOCAL` selections to `REBAR` when
`cpuWrite != NEVER` -- same CPU-side write cost, no staging allocation, no GPU copy command. This
upgrade only applies to the automatic selector. Explicit `BufferFactory.create(MemoryStrategy, ...)`
calls are always honored literally -- an explicit request for `DEVICE_LOCAL` never gets silently
redirected to `REBAR`, since "device local" carries real meaning for callers doing manual capacity
or residency planning.

The upgrade is guarded on `cpuWrite != NEVER` because `DEVICE_LOCAL` is also the selector's fallback
for buffers the caller says the CPU never writes -- allocating CPU-writable ReBAR memory for those
would be pure waste on hardware where the ReBAR-visible VRAM pool is limited.

## Write and Read Scopes

`IBuffer.acquireWrite(offset, size, queue)` hands back a `BufferWriteScope` over the memory the
buffer's strategy considers closest to final, so a producer of bytes writes exactly once and never
owns an intermediate buffer of its own:

| Strategy | What the scope hands back | What `close()` does |
|----------|---------------------------|---------------------|
| `MAPPED` (coherent) | the mapped device memory | nothing |
| `MAPPED_CACHED` | the mapped device memory | flushes the non-coherent range |
| `REBAR` | the mapped device memory, which is VRAM | nothing |
| `DEVICE_LOCAL` / `STAGING` | mapped staging memory | records the `vkCmdCopyBuffer` |
| `DEVICE_LOCAL_MIRRORED` (immediate) | the mirror's mapped memory | records the GPU copy from mirror |
| `DEVICE_LOCAL_MIRRORED` (deferred) | the mirror's mapped memory | marks dirty only, no GPU copy |
| `SPARSE`, single page | the mapped page directly | flushes if non-coherent, unmaps |
| `SPARSE`, multi-page | a temporary gather segment | scatters into the separately-mapped pages |

The point is that which memory is "closest to final" is the buffer's business, not the producer's.
This is what makes a bulk write one intrinsified `MemorySegment.copy` rather than an allocation plus
two or three copies, and on ReBAR hardware it means the caller's write lands directly in VRAM with no
GPU command at all.

`acquireRead` is the counterpart: zero-copy for mapped, ReBAR, and mirrored buffers, and a
pipeline-stalling readback into temporary host memory for non-observable device-local buffers.

The sparse multi-page case exists because sparse pages are mapped independently and therefore have no
contiguous host address range. The single-page fast path is the common one for page-aligned streaming.

## Typed Views

`TypedVkBuffer<T>` and the primitive views (`FloatVkBuffer`, `IntVkBuffer`, etc.) wrap any `IBuffer`
and are agnostic to its underlying strategy. All of them write through `acquireWrite`.

`TypedVkBuffer` requires a `GpuLayout<T>` rather than deriving serialization from the element type.
There is no single correct serialization for a type -- packed, padded, and quantized variants are all
legitimate -- so requiring the caller to name one keeps them equal citizens. `GpuLayout` is
offset-explicit and `MemorySegment`-based rather than cursor-based, which is what allows a large
buffer to be filled in parallel across threads and a single element deep in an array to be patched
with one call.

`TypedVkBuffer` supports an optional CPU mirror (retained Java objects, zero-deserialization reads)
independent of whether the underlying `IBuffer` has `MirrorCapable` observability -- these are two
different mirroring concerns: `TypedVkBuffer`'s mirror retains typed Java objects,
`MirroredObservability` retains raw bytes for zero-GPU-cost reads at the `IBuffer` level.

The primitive views additionally expose `writeRange` (a sub-range of a source array), `writeStrided`
(N contiguous components per element, advancing an arbitrary destination stride -- the primitive for
interleaving a planar attribute stream into a packed vertex buffer), and `MemorySegment` source
overloads so a memory-mapped file can feed them with no Java array involved.

Primitive typed buffers detect `MirrorCapable` observability on the backing buffer at construction
and derive a typed buffer view from the mirror's memory for zero-cost reads:
```java
this.mirror = (buffer instanceof ManagedBuffer mb && mb.observability() instanceof MirrorCapable mc)
        ? mc.mirrorMemory().asByteBuffer().asFloatBuffer() : null;
```


## Transfer Batching

Writes and copies are recorded into a per-thread, per-queue `TransferBatch` (managed by
`TransferBatchManager`), which accumulates copy commands into one command buffer and auto-flushes at
a 64MB threshold. `GpuCompletion` is the caller-facing handle for a batched operation -- `await()`,
`isComplete()`, `onComplete(callback)` (spawns a virtual thread), `toFuture()`, `flush()`. Synchronous
`IBuffer.write`/`copyTo` call `writeAsync`/`copyToAsync` then immediately flush and await.

Batching is the primary synchronization-reduction mechanism in the system: sync cost and
`vkQueueSubmit` cost are per submission, not per copy, and a submission is a kernel transition that
dominates the choice of sync primitive.

### Multi-region copies for dirty flushes

`flushDirty` and `readDiff` use `vkCmdCopyBuffer` with `regionCount > 1` when multiple dirty ranges
exist, issuing all coalesced regions in a single command rather than N separate copy commands. This
reduces command overhead proportionally to the number of scattered dirty regions.

### Completion is a timeline value, not a fence

Each `TransferBatch` owns one monotonic `VkTimelineSemaphore`. Generation N signals value N; a
completion handed out for generation N waits for that value. No fence is involved and
`vkQueueSubmit` is passed `VK_NULL_HANDLE`.

This replaced a design where one `VkFence` was created per batch and reused for every generation,
which was unsound rather than merely noisy:

- A fence must be reset before reuse, and both `vkResetFences` and the fence parameter of
  `vkQueueSubmit` require external synchronization of that fence. Resetting a fence while a wait on
  it is pending is undefined.
- A completion view for generation N held a reference to the shared fence. Once generation N+1 reset
  and resubmitted it, N's view was waiting on N+1's signal, `isComplete()` returned false for work
  that had finished, and if N+1 was never submitted, `await()` blocked forever.

A timeline value has none of these problems because there is nothing to reset:

| Property | Consequence |
|----------|-------------|
| Counter is monotonic | Generation N's target stays meaningful no matter how many generations follow |
| No reset operation | No mutation of shared state, so no external synchronization requirement |
| `vkWaitSemaphores` is internally synchronized | Any number of threads may wait on different values concurrently, which is what `onComplete` does |
| Completion is a comparison | `counterValue() >= target` cannot give a false negative after reuse |
| One object per batch | Unlimited generations, no pool, no per-submission allocation |
| One fewer API call per flush | `vkResetFences` disappears entirely |

The race is therefore removed by construction rather than guarded by a lock, and the result is
marginally faster than the original.

Timeline semaphores are consequently a hard requirement of this library, not an optional feature.
`VulkanContext.Builder` verifies Vulkan 1.2 or `VK_KHR_timeline_semaphore` and fails with an
explanatory error rather than letting semaphore creation fail at first write.

### Thread ownership

A batch belongs to exactly one thread -- the one that first requested it -- because it records into a
single command buffer that no other thread may touch. `record`, `flush`, `waitUntil`, and `signalOn`
assert this and throw an explanatory `IllegalStateException` otherwise. `destroy` deliberately does
not, since it runs during device teardown after `vkDeviceWaitIdle`.

`TransferCompletion` holds its owning `TransferBatch` directly. It previously looked the batch up
from the thread-local registry inside `flush()`, which meant that flushing from any thread other
than the recording thread silently found no batch and did nothing, leaving `await()` to block on
work that had never been submitted. `onComplete` now flushes on the calling thread before spawning
its waiter, for the same reason.

`await()` flushes first when the generation has not been submitted, so `writeAsync(...).await()` is
correct without an explicit `flush()` call.

### Reducing waits further

Timeline semaphores remove the race and the reset; they do not remove the stall, since
`vkWaitSemaphores` still blocks its caller. Eliminating stalls is a separate concern and is mostly a
matter of knowing who the consumer is:

- When the consumer is the GPU, no CPU wait is needed at all. On one queue, submission order plus a
  pipeline barrier is sufficient and needs no sync object; across queues, the consuming submission
  waits on the transfer's timeline value.
- Mapped and ReBAR writes record no GPU work and return an already-complete token, so there is
  nothing to wait on in the first place.
- Staging reclamation is better handled by a retire list keyed on timeline value, read once per
  frame, than by a wait per transfer.

**Known issue:** `TransferBatch.open()` allocates a fresh command buffer from the pool for every
generation and never returns it, so a long-running loop accumulates command buffers in the pool
proportional to its flush count. Pre-existing, unrelated to the completion rework, and not yet
fixed; the fix is either resetting the pool once a generation's completion has been released, or
recycling a small ring of command buffers per batch.


## Design Principles

- Allocation, transfer, observability, and dirty tracking are always independently swappable; a
  strategy pairing that doesn't exist yet is a missing `MemoryStrategy` case in `BufferFactory`,
  not a reason to add inheritance
- `IBuffer` stays minimal so arbitrary custom implementations (graph-transient resources, test
  doubles) can participate in descriptor binding, typed views, and copy operations without adopting
  the strategy composition machinery
- Capabilities that don't apply to every buffer (sparse page control, mirror access) are capability
  interfaces (`SparseCapable`, `MirrorCapable`), not methods on `IBuffer` that throw for most
  implementations
- Composite wrappers (`RingBuffer`, `SuballocatorBuffer`) compose `IBuffer` instances rather than
  extending a shared base -- multiplicity and slicing are concerns orthogonal to
  allocation/transfer/observability and to each other
- The automatic strategy selector may make hardware-aware substitutions (ReBAR upgrade); explicit
  `BufferFactory.create` calls with a literal `MemoryStrategy` never do
- Producers of bytes do not own intermediate memory. `acquireWrite` inverts that ownership so the
  strategy decides where a write lands, which is what keeps the copy count at one
- Serialization is always a `GpuLayout`, never a method on the type being serialized, and layouts are
  offset-explicit so bulk fills can be parallelized
- Synchronization primitives are chosen so that unsafe states are unrepresentable rather than guarded.
  Completion is a monotonic timeline value precisely because a value needs no reset, and a reset is
  the only thing that made the previous fence-based design racy
- Consumers depend on the `GpuCompletion` concept, not on whoever submitted the work, so an external
  scheduler can supply its own implementation
- Mirroring is a first-class strategy axis, not a decorator; it provides both CPU-readable visibility
  and staging-copy elimination in one allocation, and integrates with dirty tracking and deferred
  mode for efficient batched transfers
