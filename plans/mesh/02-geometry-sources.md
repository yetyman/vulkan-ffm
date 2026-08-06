# Layer 1: Geometry sources

Package: `io.github.yetyman.vulkan.mesh.source`

Residency-agnostic. A source may be a file on disk, a memory-mapped region, a procedural generator, a
compute shader's output, or an already-GPU-resident buffer range. Nothing in this layer assumes which.

This layer covers axis 7 (provenance) and the producer half of axis 3 (residency).

---

## The central decision: pull with destination, not push with arrays

This is the single most consequential design choice in Layer 1, and it is easy to get backwards.

### The wrong shape

```java
float[] positions = source.positions();     // do not do this
```

If a source hands back host arrays, the copy count is already lost: file bytes to array, array to
staging, staging to VRAM. Three copies, plus garbage proportional to mesh size, plus a full
materialization of geometry that may be larger than the caller wants resident at once.

### The right shape

```java
/**
 * Transcodes a window of one attribute directly into caller-provided memory, in the target layout.
 *
 * The destination is whatever memory the caller has arranged - typically obtained from
 * IBuffer.acquireWrite, which means it may be mapped device memory, ReBAR memory, or staging
 * memory. The source writes exactly once, in final layout, with quantization applied.
 *
 * The element window makes this parallelizable across threads and enables partial residency:
 * a caller may transcode only the elements it needs now.
 */
void transcodeInto(MeshLayout targetLayout,
                   AttributeSemantic semantic,
                   MemorySegment dst,
                   long dstOffset,
                   long dstStride,
                   long firstElement,
                   long elementCount);
```

With this shape, a memory-mapped glTF buffer view transcodes directly into ReBAR memory in the
target interleaved layout, quantizing on the way, in a single pass with one copy. A procedural
generator writes vertices exactly where they will live. A source that genuinely holds a `float[]`
becomes a thin `MemorySegment.copy` intrinsic.

The element window is load-bearing in three ways: it allows parallel fan-out across cores, it allows
progressive residency (upload the first N elements now), and it allows streaming a mesh larger than
available staging memory.

Host-array convenience adapters remain available for people who already have arrays. They are never
the primary interface.

---

## `AttributeStream`

One attribute's worth of data from one source.

```java
public interface AttributeStream {
    AttributeSemantic semantic();
    AttributeFormat sourceFormat();
    long elementCount();

    /** Current residency state. State, not type - see below. */
    Residency residency();

    /** True if transcodeInto can be called. */
    boolean isHostReadable();

    /** The device-resident range, if any. Empty when not device-resident. */
    Optional<DeviceRange> deviceRange();     // (IBuffer, offset, stride)

    void transcodeInto(MeshLayout targetLayout, MemorySegment dst, long dstOffset,
                       long dstStride, long firstElement, long elementCount);
}
```

### Residency is a state, not a subclass

A stream can be host-readable only, device-resident only, both, or neither (not yet generated). If
those were separate types, every transition would be an object replacement and every consumer would
need instance checks. As a state field, transitions are in-place and consumers ask a question.

```java
public enum Residency { ABSENT, PENDING, HOST, DEVICE, HOST_AND_DEVICE, EVICTING }
```

`ABSENT` is a legitimate steady state, not an error: a procedural source that has not been asked to
generate yet, or a streamed partition that has been evicted.

### Implementation shapes

These are the expected implementations, not an exhaustive or closed set:

| Implementation | Backing | Notes |
|----------------|---------|-------|
| `SegmentAttributeStream` | `MemorySegment` (arena or memory-mapped file) | The fast common case. `transcodeInto` is a strided copy or a strided convert. |
| `ArrayAttributeStream` | `float[]`, `int[]`, etc. | Convenience adapter. `MemorySegment.copy` intrinsic. |
| `DeviceAttributeStream` | `IBuffer` range | Already on the GPU. Not host-readable. Consumed by device-to-device copies. |
| `GeneratedAttributeStream` | a generator function | Writes directly into `dst`. Used by procedural geometry and isosurface extraction. |

---

## `IndexStream`

```java
public interface IndexStream {
    IndexWidth width();            // U8, U16, U32
    long indexCount();
    Residency residency();
    boolean isHostReadable();
    Optional<DeviceRange> deviceRange();

    /**
     * Transcodes indices into dst at targetWidth, applying a vertex base offset.
     * The base offset is what makes shared global vertex pools work: indices stored
     * relative to zero are rewritten relative to the pool allocation.
     */
    void transcodeInto(IndexWidth targetWidth, long vertexBaseOffset,
                       MemorySegment dst, long dstOffset,
                       long firstIndex, long indexCount);
}
```

The `vertexBaseOffset` parameter is not incidental. When geometry lands in a shared global vertex
pool, either indices are rewritten at upload time or every draw carries a `vertexOffset`. Both are
valid; the parameter makes the first possible, and `GeometryDrawRange.vertexOffset` makes the second
possible. Which one a given allocator chooses is documented in `04-residency-and-upload.md`.

`U8` is included because it is legal for `vkCmdBindIndexBuffer` only via
`VK_KHR_index_type_uint8`, but is also legitimately useful as a compact storage form for meshlet-local
indices read from an SSBO. Availability as a bound index type is a device capability question, not a
vocabulary question.

Primitive restart is a property of the topology plus the index stream, exposed as a nullable restart
index.

---

## `GeometrySource`

The producer abstraction. This is the file-format seam and the procedural seam simultaneously.

```java
public interface GeometrySource {
    Set<AttributeSemantic> available();
    AttributeStream stream(AttributeSemantic semantic);
    Optional<IndexStream> indices();
    long elementCount();
    PrimitiveTopology topology();
    AABB bounds();

    /** The layout the source's data is natively in, when it has one. */
    Optional<MeshLayout> nativeLayout();

    /** Partitions the source declares natively (glTF primitives, OBJ groups, etc.). */
    List<GeometryPartition> partitions();
}
```

`nativeLayout()` matters for the fast path: when the source's native layout equals the target layout,
transcoding degenerates to a single flat memory copy of the whole vertex block, and that should be
detected rather than performed attribute by attribute.

`bounds()` is required rather than optional because every allocator, every culling scheme, and every
future LOD selector needs it, and computing it lazily from a possibly-non-host-readable stream is
worse than requiring the source to know.

### Expected implementations, and where they live

| Source | Module | Notes |
|--------|--------|-------|
| `SegmentGeometrySource` | `vulkan-ffm-mesh` | Wraps a set of `MemorySegment`s plus a `MeshLayout`. The primitive everything else adapts to. |
| Procedural primitives (box, sphere, plane, grid, torus) | `vulkan-ffm-mesh` | Small, universally useful, no dependencies. Needed for samples and tests. |
| Isosurface output | `helpers-core` adapter | See below. |
| glTF, OBJ, PLY, USD, FBX, Draco | separate bindings modules | Never in `vulkan-ffm-mesh`. See `08-integration-boundaries.md`. |
| Compute-generated | app-side | Produces `DeviceAttributeStream`s over buffers a compute pass wrote. |

### `MeshOutput` in helpers-core

`helpers-core/.../spatial/isosurface/MeshOutput` currently accumulates `List<Vec3>` vertices and
`List<Integer>` indices and implements `BufferWritable` with a hardcoded 12-byte position-only format
followed by indices, and throws on `readFrom`.

It is free to be changed outright. The target shape:

- Store vertices in a growable `MemorySegment` or `float[]`, not `List<Vec3>`. The boxed
  `List<Integer>` for indices is a particularly costly representation for something that is
  fundamentally an `int[]`.
- Stop implementing a serialization interface. Implement `GeometrySource` instead, or provide an
  adapter to one.
- Optionally accumulate normals, since every isosurface algorithm in that package can produce them
  more accurately from the field than a post-pass can from the triangles.

This is a good early validation case: if `GeometrySource` cannot cleanly express marching cubes
output, the interface is wrong.

---

## Parallel transcoding

Because `transcodeInto` takes an explicit element window and an explicit destination offset, and
because `GpuLayout` is offset-explicit after `00-prerequisites.md` item 1, a large mesh can be
transcoded by partitioning the element range across threads with no coordination:

```java
// conceptual, exact API in Layer 3
IntStream.range(0, workerCount).parallel().forEach(w -> {
    long first = (elementCount * w) / workerCount;
    long last  = (elementCount * (w + 1)) / workerCount;
    source.stream(POSITION).transcodeInto(layout, dst, layout.offsetOf(POSITION),
                                          layout.strideOf(0), first, last - first);
});
```

Whether the module ships a parallel driver or leaves fan-out to the caller is an open question, and
should be decided by measurement rather than in advance. The interfaces permit either.

---

## Design principles for this layer

- The primary data-movement operation writes into a caller-provided destination. Producing host
  arrays is a convenience adapter, never the interface.
- Residency is a state on a stream, never a type distinction.
- Every data-movement operation takes an explicit element window, so partial residency, progressive
  upload, and parallel fan-out all fall out of the same signature.
- `nativeLayout()` exists so the identity case (source layout equals target layout) can be detected
  and collapsed to one flat copy.
- File-format readers are adapters in other modules. Layer 1 defines the seam and ships only the
  dependency-free sources needed for samples and tests.
