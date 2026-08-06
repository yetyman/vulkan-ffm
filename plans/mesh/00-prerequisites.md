# Prerequisites: vulkan-core changes

## Status

Implemented, build green, `helpers-core` and `vulkan-ffm-mesh` test suites passing:

| Item | Status |
|------|--------|
| 1. `GpuLayout` offset-explicit and `MemorySegment`-based | done |
| 2. Delete `BufferWritable`, add `HasGpuLayout` | done |
| 3. `IBuffer.acquireWrite` / `acquireRead` scopes | done |
| 4. Extract `GpuCompletion` interface | done |
| 5. Fix the `TransferBatch` fence-reuse race | NOT DONE - deferred pending a design discussion about whether it is fixable without baking in a bias |
| 6. Bulk and strided primitive paths on the typed buffers | done |
| 7. Deletions (`BufferWritable`, `VulkanMesh`, `GLTFLoader`) | done |

### Deltas from this plan, decided during implementation

- `GpuCompletion.flush(VkDevice, VkQueue)` became a no-arg `flush()`. Passing a device and queue into
  a completion was awkward and unimplementable for non-batched backings; `TransferCompletion` now
  captures its own device and queue at construction, and the interface declares
  `default void flush() {}` meaning "ensure submitted", a no-op for externally scheduled work.
- `TransferCompletion` kept its name rather than becoming `BatchCompletion`. It is now documented as
  the `TransferBatch`-backed implementation of `GpuCompletion`, and consumers are directed to type
  against the interface.
- `TimelineCompletion` was added as a second `GpuCompletion` implementation, backed by a
  `VkTimelineSemaphore` target value. This is what an external scheduler returns, and its existence
  is what proves the interface extraction was real rather than cosmetic.
- `AABB` gained a `MIN_MAX` `GpuLayout` and implements `HasGpuLayout`. Four spatial structures were
  each hand-writing the same six float writes; they now share it.
- `SpatialStructure` no longer extends any serialization interface at all, rather than exposing
  `defaultLayout()`. A structure has no single canonical layout, and the concrete implementations
  already expose their own `DEFAULT_LAYOUT` statics, so putting one on the interface would have
  re-created the problem in a new place.
- Structures' `byteSize()` became `gpuByteSize()`, because the value is data-dependent (node count)
  and was being confused with `GpuLayout.byteSize()`, which is a fixed per-element stride. The
  variable-size layouts return -1 from `byteSize()` as before.
- `MeshOutput` and `ContourOutput` were converted to offset-explicit `MemorySegment` writers with
  separate vertex and index blocks, plus `writeVertices(dst, offset, stride)` for interleaving and
  `writeIndices(dst, offset, vertexBaseOffset)` for pool-relative rewriting. They are not yet
  `GeometrySource` implementations; that is Phase 2.
- `TransferCompletion.await()` still throws when the batch has not been submitted. Fixing that
  entangles with item 5, so it was left alone deliberately.
- `SparseTransferStrategy.acquireWrite` needed a case the plan did not anticipate: sparse pages are
  mapped independently, so a write spanning more than one page has no contiguous host destination.
  Single-page writes hand back the mapped page directly; multi-page writes gather into a temporary
  segment and scatter into pages on commit. The single-page fast path is the common one for
  page-aligned streaming.

---

## Original plan

All of these are `vulkan-core` (and small `helpers-core`) changes that the mesh module requires and
that improve core independently of meshes. They should land before Layer 1 work begins, because each
of them shapes an interface the mesh module will build directly on top of.

There are no published consumers of this library, so none of these need migration paths or
deprecation cycles. Change the interfaces outright.

---

## 1. `GpuLayout` becomes offset-explicit and `MemorySegment`-based

### Current state

```java
public interface GpuLayout<T> {
    int byteSize();
    void writeTo(T value, ByteBuffer buf);   // writes at buf's current position
    void readFrom(T value, ByteBuffer buf);
}
```

### Problem

Writing at the buffer's current position makes the layout stateful with respect to its destination.
Two consequences, both fatal for mesh transcoding:

- A vertex buffer cannot be filled in parallel. Transcoding a four-million-vertex mesh across cores
  is a routine thing to want, and a shared cursor forbids it without slicing gymnastics per worker.
- Random-access writes (patching one element of a large stream) require slicing and position
  bookkeeping at every call site.

Additionally, heap `ByteBuffer` (which `TypedVkBuffer` currently allocates for scratch) forces an
extra copy at the FFM boundary. `MemorySegment` with unaligned var handles matches direct
`ByteBuffer` throughput and beats heap `ByteBuffer` substantially.

### Target state

```java
public interface GpuLayout<T> {
    int byteSize();
    void writeTo(T value, MemorySegment dst, long offset);
    void readFrom(T value, MemorySegment src, long offset);
}
```

Stateless with respect to the destination, parallel-safe, random-access capable.

### Affected files

All `GpuLayout` implementations in `helpers-core`: `Vec2`, `Vec3`, `Vec4`, `Mat3`, `Mat4`,
`Quaternion`, `DualQuaternion`, and the `DfsLayout` / `DfsAabbLayout` inner classes in `BVH`,
`RTree`, `LinkedOctree`, `LinkedQuadtree`, `SparseGrid`. Mechanical edits.

---

## 2. Delete `BufferWritable`, replace with `HasGpuLayout`

### Problem

`BufferWritable` and `GpuLayout<T>` are the same concern with different ownership:

- `BufferWritable` - the type serializes itself in its one canonical way.
- `GpuLayout<T>` - an external strategy serializes `T`.

Having both creates a permanent "which one do I implement" question, and the answer differs per
type, which is exactly the kind of inconsistency that makes a library hard to learn. Worse,
`SpatialStructure` currently extends `BufferWritable`, which bakes one serialization into a
structure contract that should be layout-agnostic.

For meshes the question does not even arise: there is no canonical vertex layout, so only the
strategy form is usable.

### Target state

```java
public interface HasGpuLayout<T> {
    GpuLayout<T> defaultLayout();
}
```

Types that had a `DEFAULT_LAYOUT` static keep it and implement `HasGpuLayout` by returning it.
Types that were `BufferWritable` only for convenience lose nothing. `SpatialStructure` stops
extending a serialization interface and instead exposes `defaultLayout()`.

The mesh module will implement neither for vertex data. Vertex serialization is always described by
a `MeshLayout` (see `01-vocabulary.md`), which is a different and richer concept than a
single-element layout.

### Note

`MeshOutput` in `helpers-core/.../spatial/isosurface/` currently implements `BufferWritable` with an
`UnsupportedOperationException` on `readFrom`, and hardcodes a 12-byte position-only vertex format
followed by indices. It is free to be changed outright; it will become a `GeometrySource`
implementation. See `02-geometry-sources.md`.

---

## 3. `IBuffer` write and read scopes

This is the single most valuable addition on this list. It serves meshes, the typed buffers, font
atlases, and anything else that produces bytes for the GPU.

### Problem

Producers currently allocate their own intermediate buffer and hand it to `IBuffer.writeAsync`,
which may copy again. Concretely, `FloatVkBuffer.writeAsync`:

```java
ByteBuffer bb = ByteBuffer.allocateDirect(data.length * STRIDE);  // allocation per call
bb.asFloatBuffer().put(data);                                     // copy 1
return buffer.writeAsync(bb, (long) startIndex * STRIDE, queue);  // copy 2, maybe copy 3
```

Two to three copies and per-call garbage, for what should be one intrinsified bulk copy.

The root cause is that the producer owns the intermediate memory. It should not. Which memory is
"closest to final" depends entirely on the buffer's transfer strategy, which is the buffer's
business.

### Target state

```java
/**
 * Acquires a writable region of memory for the given buffer range.
 * The returned segment is the closest-to-final memory the buffer's strategy can offer:
 *   - MAPPED / REBAR: the mapped device memory itself, so the caller's write lands directly
 *   - STAGING / DEVICE_LOCAL: mapped staging memory; close() records the vkCmdCopyBuffer
 *   - SPARSE: mapped committed page memory, or staging for device-local backing
 * Non-coherent memory is flushed on close.
 */
BufferWriteScope acquireWrite(long offset, long size, VkQueue queue);

public interface BufferWriteScope extends AutoCloseable {
    MemorySegment segment();       // fill this
    GpuCompletion completion();    // valid after close(); see item 4
    @Override void close();        // commits: flush, record copy, or no-op
}

BufferReadScope acquireRead(long offset, long size, VkQueue queue);
```

Producers then write exactly once, into memory that is already as close to final as the strategy
allows. On ReBAR hardware that single write lands in VRAM.

`FloatVkBuffer` becomes:

```java
try (var scope = buffer.acquireWrite((long) dstIndex * 4, (long) count * 4, queue)) {
    MemorySegment.copy(src, srcOffset, scope.segment(), ValueLayout.JAVA_FLOAT_UNALIGNED, 0, count);
}
```

One intrinsified bulk copy, zero allocation.

### Interaction with existing optimizations

`MirroredBuffer` already implements a hand-rolled version of this idea via
`ManagedBuffer.copyFromExternal` - it writes once into the mirror's own mapped memory and issues the
GPU copy from there. Once `acquireWrite` exists, `MirroredBuffer` should be reimplemented on top of
it, and `copyFromExternal` / `copyToExternal` may become internal implementation details of the
staging scope rather than separate narrow optimization methods.

---

## 4. Extract `GpuCompletion` interface

### Problem

`TransferCompletion` is a concrete class hard-wired to `BatchTransferCompletion`:

```java
public class TransferCompletion implements AutoCloseable {
    private final BatchTransferCompletion batch;   // only possible backing
}
```

Any other producer of asynchronous GPU work - a timeline-semaphore-based upload, a graph-scheduled
transfer node, a compute pass that generates geometry - cannot produce a completion token. That
means the mesh module would have to depend on `TransferBatch` specifically rather than on the
concept of "GPU work that will finish".

This is the concrete form of "conceptual data hooks and shared interfaces, not direct coupling":
the mesh module depends on the token concept, and whoever schedules the work supplies the
implementation.

### Target state

```java
public interface GpuCompletion extends AutoCloseable {
    void await();
    boolean isComplete();
    void onComplete(Runnable callback);
    CompletableFuture<Void> toFuture();
    @Override void close();

    static GpuCompletion completed() { ... }
}
```

Implementations:

- `BatchTransferCompletion` backed form (rename the current `TransferCompletion` to something like
  `BatchCompletion`, or keep the name as the batch implementation).
- A timeline-semaphore form, wrapping `VkTimelineSemaphore` with a target counter value. This is
  what a graph-scheduled upload will produce.
- `GpuCompletion.completed()` for no-op cases.

### Also fix

`TransferCompletion.await()` currently throws `IllegalStateException` if the batch has not been
submitted. That makes the token's contract depend on external sequencing, which is a hazard for a
mesh streaming system handing tokens around. The interface should either auto-flush or make
"unsubmitted" a legitimate waitable state.

---

## 5. Fix the fence-reuse race in `TransferBatch`

`docs/major-subsystems/buffers/buffers.md` records this as a known unfixed issue:

> fence reuse inside `TransferBatch`/`BatchTransferCompletion` is not synchronized against
> concurrent `vkWaitForFences`/`vkQueueSubmit` calls on the same `VkFence` from multiple threads
> (e.g. a virtual thread spawned by `onComplete` racing the calling thread's next flush).
> Validation layers report `UNASSIGNED-Threading-MultipleThreads-*` intermittently.

Mesh streaming will hit this immediately and constantly: many concurrent uploads, each with an
`onComplete` callback that marks a partition resident. Fix before building streaming on top.

Options noted in the buffers doc: a lock around fence access, or a per-completion fence. A
per-completion fence drawn from `FencePool` is likely the cleaner answer since it removes the shared
mutable resource entirely. But we must be careful not to inadvertently add unnecessary locks and biases to the system.

---

## 6. Bulk and strided primitive paths on the typed buffers

Applies to `FloatVkBuffer`, `IntVkBuffer`, `LongVkBuffer`, `ShortVkBuffer`, `DoubleVkBuffer`.

### Additions

```java
// Write a sub-range of a source array, so the array need not be exactly the write size.
void writeRange(float[] src, int srcOffset, int count, int dstIndex, VkQueue queue);

// Write with a destination stride: the primitive that interleaves a planar attribute stream
// into a packed vertex buffer. componentsPerElement contiguous values are written per element,
// advancing dstStrideBytes between elements.
void writeStrided(float[] src, int srcOffset, int componentsPerElement, int count,
                  int dstElementIndex, long dstStrideBytes, VkQueue queue);

// MemorySegment source overloads for every write, so a memory-mapped file feeds them directly
// with no intermediate array.
void writeRange(MemorySegment src, long srcOffset, int count, int dstIndex, VkQueue queue);
void writeStrided(MemorySegment src, long srcOffset, int componentsPerElement, int count,
                  int dstElementIndex, long dstStrideBytes, VkQueue queue);
```

`writeStrided` is the primitive the mesh transcoder is built on. Every interleaving operation in
Layer 1 reduces to it.

Read counterparts for symmetry: `readRange`, `readStrided`, and `MemorySegment` destination
overloads.

### `TypedVkBuffer` cleanup

- Its thread-local scratch is `ByteBuffer.allocate` - heap memory, which forces an extra copy at the
  FFM boundary. Either switch to a native/arena-backed segment or, preferably, delete the scratch
  entirely and write through `acquireWrite`.
- Its bulk write loop should hand a `MemorySegment` plus per-element offsets to the layout, which
  falls out naturally once item 1 lands.
- With `GpuLayout` offset-explicit, the bulk write loop becomes parallelizable. Worth exposing a
  parallel bulk write once measured, not before.

---

## 7. Deletions

| File | Reason |
|------|--------|
| `vulkan-core/.../highlevel/VulkanMesh.java` | Owns and closes its own buffers, has `draw()`, hardcodes a vertex-input-only paradigm. Exactly the biased mesh this plan replaces. |
| `sample-app/.../complex/models/GLTFLoader.java` | Verified unused: no references outside the file itself and the memory bank. |

`VkVertexFormat` stays in `vulkan-core`. It is a legitimate Vulkan wrapper concern - it builds
`VkPipelineVertexInputStateCreateInfo`. The mesh module derives one from a `MeshLayout` rather than
replacing it.

---

## Ordering

Items 1 and 2 go together (one edit pass over `helpers-core`). Item 3 depends on nothing. Item 4
should land before item 3's `BufferWriteScope.completion()` is finalized. Items 5, 6, 7 are
independent.

Suggested order: 4, 5, 1+2, 3, 6, 7.

Rationale: the completion interface and the fence fix are the load-bearing correctness items and
have the widest blast radius, so they go first while the surface area is small. Layout changes next
since they touch many files. Scopes after completions, because scopes return completions. Bulk
primitive paths after scopes, since they are implemented on scopes. Deletions last, whenever.
