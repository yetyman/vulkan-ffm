# Layer 4: Consumption

Package: `io.github.yetyman.vulkan.mesh.consume`

Covers axis 5. This is the layer where a mesh becomes GPU work, and it is the layer most at risk of
accidentally importing a rendering paradigm. Everything here is plain data that a consumer reads.
Nothing here records a command.

---

## What is deliberately absent

### No `Drawable`

A `Drawable` interface with a `draw(commandBuffer)` method is the standard way mesh libraries become
unusable outside the paradigm their author had in mind. It forces:

- A single dispatch point per mesh, which is per-mesh CPU work, which is the opposite of the bulk
  path.
- An assumption that the mesh knows its pipeline, descriptor sets, and push constants, which drags in
  material and shader concepts.
- An assumption that drawing is the goal, which excludes compute consumption, BLAS building, physics,
  and readback.

Its job splits cleanly into `GeometryBinding` (the data needed to bind) and app-side recording (the
behavior). The split costs nothing and removes all three assumptions.

### No `Material`

Partitions carry `long tag()` and `long sortKey()`, both uninterpreted. See `03-partitioning.md`.

### No `GeometryConsumer` interface

An interface that exists to be implemented once is premature. Consumers read records. If three
distinct consumers turn out to need the same shape, the interface gets extracted then, from evidence.

---

## `GeometryBinding`

The resolved answer to "which buffers, at which offsets and strides, for which semantics".

```java
public final class GeometryBinding {

    // -- vertex-input path --
    /** Buffers to bind, in binding order, with byte offsets. */
    public MemorySegment[] vertexBufferHandles();
    public long[] vertexBufferOffsets();
    public int firstBinding();

    /** Index buffer, if indexed. */
    public Optional<MemorySegment> indexBufferHandle();
    public long indexBufferOffset();
    public IndexWidth indexWidth();

    /** Derived vertex input state for pipeline creation. Partial by design. */
    public VkVertexFormat vertexFormat(Map<AttributeSemantic, Integer> locations);

    // -- vertex-pulling / mesh-shader path --
    /** Every stream as a (buffer, offset, size) triple, for storage-buffer binding. */
    public List<DeviceRange> storageRanges();
    public DeviceRange rangeOf(AttributeSemantic semantic);

    public MeshLayout layout();
}
```

It exposes what is needed to bind. It does not bind. The same binding object serves the vertex-input
path, the vertex-pulling path, and the mesh-shader path, because all three want the same underlying
facts in different shapes.

`vertexFormat(locations)` takes the semantic-to-location mapping from the caller because location
assignment is a shader contract, not a geometry property. It is partial by design: semantics whose
`AttributeFormat` has no `VkFormat` are omitted and must be consumed as storage ranges instead.

---

## `GeometryDrawRange`

```java
public record GeometryDrawRange(
        int indexCount,          // or vertexCount when not indexed
        int instanceCount,
        int firstIndex,          // or firstVertex
        int vertexOffset,        // added to each index; 0 when indices were rewritten absolute
        int firstInstance,
        PrimitiveTopology topology,
        boolean indexed
) {}
```

A plain record. A consumer reads it and calls `vkCmdDrawIndexed` or writes it into an indirect buffer.

---

## `IndirectDrawEncoder`

### The dual path

A record per draw is correct for setup and for low-frequency CPU paths. It is wrong inside a loop
encoding fifty thousand indirect commands, where allocating fifty thousand records per frame is pure
waste.

So the encoder has both forms, and the record form is implemented on the primitive form:

```java
public final class IndirectDrawEncoder {

    /** Bytes per command for the given kind. */
    public static int stride(IndirectKind kind);

    /** Primitive form. No allocation. This is the hot path. */
    public static void encodeIndexed(MemorySegment dst, long commandIndex,
                                     int indexCount, int instanceCount, int firstIndex,
                                     int vertexOffset, int firstInstance);

    public static void encodeNonIndexed(MemorySegment dst, long commandIndex,
                                        int vertexCount, int instanceCount,
                                        int firstVertex, int firstInstance);

    /** Task/mesh shader dispatch form. */
    public static void encodeMeshTask(MemorySegment dst, long commandIndex,
                                      int groupCountX, int groupCountY, int groupCountZ);

    /** Convenience form, implemented on the primitive form. */
    public static void encode(MemorySegment dst, long commandIndex, GeometryDrawRange range);
}
```

Layouts match `VkDrawIndexedIndirectCommand`, `VkDrawIndirectCommand`, and
`VkDrawMeshTasksIndirectCommandEXT` exactly.

This dual-path discipline is a project-wide rule, stated in `07-performance-invariants.md`, and it
matches what already exists in `vulkan-ffm-node-trees` with zero-allocation `fireEvent` and
`TraversalView`.

---

## `GeometryTable`

This is the piece that makes maximum-throughput bulk rendering possible, and it is not optional.

### Why it exists

The fastest bulk render is: bind one vertex pool, one index pool, one descriptor set, issue one
`vkCmdDrawIndexedIndirectCount`. Nothing per mesh, on any frame.

That is only reachable if the GPU can answer "where does partition N live, how big is it, what are
its bounds, what is its tag" without CPU involvement. GPU-resident vertex data is not sufficient; the
GPU also needs the descriptor metadata. A culling or LOD compute shader reads the table by partition
index and writes indirect draw arguments. No CPU touches per-mesh data in steady state.

Without this, every GPU-driven scheme has to build its own parallel table, which means the mesh module
is not actually the foundation for GPU-driven rendering, just a source of buffers.

### Shape

```java
public final class GeometryTable implements AutoCloseable {

    /** Registers a partition, returning its stable index. */
    public int register(GeometryAllocation allocation, GeometryPartition partition);
    public void unregister(int partitionIndex);

    /** Updates a record in place, e.g. after defragmentation or bounds change. */
    public void update(int partitionIndex, GeometryAllocation allocation, GeometryPartition partition);

    /** Flushes dirty ranges to the GPU. Returns a completion for the upload. */
    public GpuCompletion flush(VkQueue queue);

    /** The SSBO a culling or LOD shader binds. */
    public IBuffer buffer();
    public int recordStride();
    public int capacity();
    public int count();

    /** Registers an additional per-partition channel, uploaded as a parallel array. */
    public <T> void attachChannel(MetadataChannel<T> channel);
    public IBuffer channelBuffer(MetadataChannel<?> channel);
}
```

### Record contents

The base record is deliberately small and fixed, because it is read by every GPU-driven shader and
cache behavior matters:

| Field | Type | Purpose |
|-------|------|---------|
| vertexBase | uint | first vertex in the shared pool |
| indexBase | uint | first index in the shared pool |
| indexCount | uint | draw argument |
| vertexCount | uint | draw argument for non-indexed |
| boundsMin | vec3 | culling |
| boundsMax | vec3 | culling |
| tag | uint64 | app routing |
| sortKey | uint64 | app ordering |
| flags | uint | resident, indexed, topology class |

Anything paradigm-specific (meshlet cones, cluster error, terrain neighbour masks, splat covariance)
goes in an attached `MetadataChannel`, uploaded as a parallel array, never in the base record. That is
what keeps the base record from growing without bound as paradigms are added.

### Dirty tracking

Registration and update mark dirty ranges; `flush` uploads only those, coalescing adjacent ranges.
The same `DirtyTracker` concept already used in `helpers-core` spatial and in `TraversalView` applies
directly.

### Indirection guarantee

The table is the single source of truth for where geometry lives, which is what permits
defragmentation and repacking later. The corresponding rule, stated in `04-residency-and-upload.md`,
is that nothing above Layer 3 may cache an allocation location; it reads the table.

---

## `Mesh`

The thin convenience aggregate. Its class Javadoc must be blunt about its role.

```java
/**
 * Convenience aggregate over a geometry's source, allocation, partitions, and binding.
 *
 * This type is deliberately thin and carries no behavior of its own. Nothing in
 * vulkan-ffm-mesh depends on it, and every operation available through it is available
 * directly on the underlying types. It exists so that simple cases read simply, and so
 * that sample code has one obvious noun.
 *
 * If you are building a high-performance or unusual pipeline, ignore this class and compose
 * GeometrySource, GeometryAllocator, PartitionSet, GeometryBinding, and GeometryTable directly.
 * Doing so is the intended path, not a workaround.
 *
 * This class has no material, no transform, no draw method, and no render-paradigm assumptions,
 * and it must never acquire any.
 */
public final class Mesh implements AutoCloseable { ... }
```

Contents: an id, a `GeometrySource`, a `GeometryAllocation`, a `PartitionSet`, a `GeometryBinding`,
bounds, and the table indices of its partitions. Plus a builder that does the obvious thing:
interleaved layout, indexed, device-local, upload immediately.

The correctness test for the convenience layer is that it must be implementable purely on the public
API of the layers beneath it. If a convenience helper needs privileged access to internals, the
public API is incomplete and the fix goes in the lower layer.

---

## Design principles for this layer

- Nothing in this layer records a Vulkan command. It produces data that a consumer records from.
- Every per-element encoding API has a primitive form; record forms are implemented on top of them.
- `GeometryBinding` serves the vertex-input, vertex-pulling, and mesh-shader paths from the same
  facts, and prefers none of them.
- `GeometryTable` is mandatory infrastructure, not an optimization. Without it the module is not a
  foundation for GPU-driven rendering.
- The base table record is fixed and small. Paradigm-specific per-partition data goes in attached
  channels.
- The table is the indirection point for geometry location. Nothing above Layer 3 caches locations.
- `Mesh` is thin, documented as thin, and depended on by nothing.
