# Performance invariants

This document answers "will this design reach ultimate speeds". The answer is yes, conditional on
four invariants. Each is cheap to honor now and expensive to retrofit, which is why they are recorded
before implementation begins rather than discovered during optimization.

Each invariant states the rule, why it matters, and what breaks if it is violated.

---

## Invariant 1: Steady state involves zero per-mesh CPU work

### The rule

In the steady state of a bulk render, no Java code executes per mesh, per partition, or per draw. The
CPU records a fixed, scene-size-independent number of commands.

### Why

The fastest possible bulk render is:

```
bind one vertex pool
bind one index pool
bind one descriptor set
vkCmdDrawIndexedIndirectCount
```

That is four commands for an entire scene, regardless of whether the scene has ten objects or a
hundred thousand. Everything else - visibility, LOD, draw argument generation, sorting - happens on
the GPU reading GPU-resident data.

### What it requires

Two things, both of which are in the plan for that reason:

1. `PoolAllocator` - all geometry in shared buffers, so binding is scene-independent
   (`04-residency-and-upload.md`).
2. `GeometryTable` - a GPU-resident per-partition descriptor table, so a compute shader can answer
   "where does partition N live and how big is it" with no CPU involvement
   (`05-consumption.md`).

`GeometryTable` is the invariant's load-bearing piece and the reason it is mandatory infrastructure
rather than an optimization. GPU-resident vertex data alone is not sufficient; the metadata has to be
resident too.

### What breaks without it

Every GPU-driven scheme builds its own parallel descriptor table, which means this module is not
actually a foundation for GPU-driven rendering, just a source of buffers. That is the difference
between a mesh system and a mesh loader.

### The end-to-end bulk path

Once the invariant holds, the full path is:

```
load time:
  GeometrySource -> UploadPlanner -> UploadPlan -> UploadExecutor -> PoolAllocator ranges
  partitions registered in GeometryTable, table flushed

per frame, GPU only:
  compute pass reads GeometryTable + camera, writes visible partition indices
  compute pass reads visible indices + GeometryTable, writes VkDrawIndexedIndirectCommand array
                                                       and a draw count

per frame, CPU:
  bind pools, bind descriptor set, vkCmdDrawIndexedIndirectCount
```

Notably, none of the per-frame GPU work is in this module. The module's contribution is that the
table is correct and the pools are shared, which is what makes the app's two compute shaders
possible. That is the correct division: the module provides the foundation, the app provides the
paradigm.

---

## Invariant 2: Every per-element API has a primitive form

### The rule

Any API that may be called once per element in a hot loop has a form taking primitives and a
`MemorySegment`, allocating nothing. Record and object forms exist for convenience and are
implemented on top of the primitive form, never the reverse.

### Why

Records allocate. Fifty thousand indirect draw commands per frame means fifty thousand record
allocations per frame if the record form is the only form, which is pure garbage for data that is
immediately serialized and discarded.

### Where it applies concretely

| API | Record form | Primitive form |
|-----|-------------|----------------|
| `IndirectDrawEncoder` | `encode(dst, i, GeometryDrawRange)` | `encodeIndexed(dst, i, int, int, int, int, int)` |
| `GeometryTable` updates | `update(i, allocation, partition)` | direct segment writes into the dirty range |
| `GpuLayout` | n/a | already primitive after prerequisite 1 |
| Typed buffer writes | list and object forms | `writeRange`, `writeStrided` with arrays and segments |

### Precedent

This is already the established discipline in this codebase: `vulkan-ffm-node-trees` has zero-alloc
`fireEvent` and O(1) `TraversalView` operations for exactly this reason. The mesh module should not
be the place the discipline lapses.

---

## Invariant 3: Sources write into a caller-provided destination

### The rule

`GeometrySource` and `AttributeStream` never expose host arrays as their primary interface. The
primary operation writes into a `MemorySegment` the caller supplies, in the caller's target layout,
over a caller-specified element window.

### Why

Copy count. With array-producing sources, uploading a mesh from a file costs:

```
file bytes -> host array        (copy 1, plus garbage proportional to mesh size)
host array -> staging buffer    (copy 2)
staging    -> VRAM              (copy 3, GPU)
```

With destination-provided transcoding, and with `IBuffer.acquireWrite` from prerequisite 3:

```
memory-mapped file bytes -> mapped staging or ReBAR memory, in final layout, quantized  (copy 1)
staging -> VRAM                                                                          (copy 2, GPU, elided on ReBAR)
```

On ReBAR hardware the second copy disappears entirely: the transcode writes directly to VRAM.

### Secondary benefits, all falling out of the same signature

- The element window makes transcoding parallelizable across cores with no coordination.
- The element window makes progressive residency possible: transcode the first N elements now.
- The element window makes meshes larger than available staging memory uploadable in chunks.
- No host materialization means peak memory is bounded by the staging window, not by mesh size.

### What breaks without it

Large-mesh streaming becomes memory-bound on the host side, upload throughput drops by roughly the
copy-count ratio, and parallel transcoding requires an entirely different second interface.

---

## Invariant 4: Layouts are offset-explicit, never cursor-based

### The rule

`GpuLayout` and every layout-like interface take an explicit destination and offset. No interface
mutates a shared position cursor.

```java
void writeTo(T value, MemorySegment dst, long offset);   // yes
void writeTo(T value, ByteBuffer buf);                   // no - implicit position
```

### Why

A shared cursor forbids parallel fill. Transcoding a four-million-vertex mesh across cores is routine,
and with a cursor-based interface every worker needs its own slice with its own bookkeeping, or the
whole operation serializes.

Secondary: random-access patching of one element in a large stream requires slicing gymnastics with a
cursor and is a single call with an offset.

Tertiary: `MemorySegment` with unaligned var handles matches direct `ByteBuffer` throughput and
substantially beats heap `ByteBuffer`. `TypedVkBuffer` currently allocates heap scratch, which forces
an extra copy at the FFM boundary.

### Status

This is prerequisite 1 in `00-prerequisites.md`. It is a change to existing `vulkan-core` and
`helpers-core` code, roughly a dozen mechanical edits, and it is why the prerequisite list exists
before Layer 1.

---

## Convenience does not bypass the invariants

The convenience layer (`Mesh`, its builder, procedural primitives) must be implementable purely on
the public API of the layers beneath it.

If a convenience helper needs privileged access to internals to be efficient, that is evidence the
public API is incomplete, and the fix goes in the lower layer rather than in the helper. This keeps
convenience and performance from diverging into two separate code paths where only one gets
maintained.

Consequence: a user who outgrows `Mesh` and drops to composing the layers directly is following the
intended path, not working around the library. That should be stated in the class documentation, and
it is.

---

## Extensibility as a performance concern

The open-token vocabularies (`AttributeSemantic`, `PrimitiveTopology`, `MetadataChannel`,
`AttributeFormat.packed`) are usually framed as extensibility features. They are also performance
features, because the fastest encodings are the unusual ones:

- Oct-encoded normals halve normal bandwidth and have no `VkFormat`.
- Quantized 16-bit positions with per-partition scale and bias halve position bandwidth and require a
  shader decode.
- Bit-packed cluster and material IDs cost one `uint` for what would otherwise be several attributes.

A closed format enum or a mandatory `VkFormat` would make every one of those a second-class path. The
escape hatches are what allow the module to reach speeds a fixed vocabulary cannot.

The corresponding rule: a user must always be able to bypass the module's abstractions entirely,
constructing a `GeometryBinding` from raw `IBuffer` ranges and encoding draw arguments by hand,
without the module objecting. That escape hatch is a feature and must be kept working.

---

## What to measure, and when

Do not optimize before measuring, but do instrument early. Per `code style`, when cycling on a root
cause, add instrumentation rather than guessing.

Minimum instrumentation to add during Phase 2:

- Bytes uploaded, copy count per upload, and wall time per upload plan.
- Staging memory high-water mark.
- Transcode throughput in elements per second, per attribute, single-threaded and parallel.
- Count of identity-layout fast-path hits versus per-attribute transcode paths.

Minimum instrumentation to add during Phase 4:

- CPU command-recording time per frame, which should be flat as scene size grows. If it is not flat,
  Invariant 1 is being violated somewhere.
- Allocations per frame, which should be zero in the render path.
