# Layer 2: Topology and partitioning

Package: `io.github.yetyman.vulkan.mesh.partition`

Covers axis 4. This is the layer where submeshes, meshlets, terrain tiles, and clusters stop being
different concepts.

---

## `PrimitiveTopology`

### The decision

Open to extension. Not a closed enum.

### Why

Vulkan's own topology list is closed, but the set of things a mesh module needs to describe is not:

- Meshlet clusters are a partitioning of triangles that has no `VkPrimitiveTopology` value.
- Point splats and Gaussian splats are drawn as instanced quads or via compute, not as
  `VK_PRIMITIVE_TOPOLOGY_POINT_LIST`.
- Tetrahedral and hexahedral volume meshes (FEM, physics) have no rendering topology at all but are
  legitimately meshes.
- Half-edge and winged-edge structures for editing are topologies with no draw path.
- Whatever a research paper needs next.

So the same treatment as `AttributeSemantic`: interned identity tokens, well-known statics for the
Vulkan ones, `of(name)` for anything else, and an optional `VkPrimitiveTopology` mapping.

```java
public final class PrimitiveTopology {
    public static final PrimitiveTopology POINT_LIST;
    public static final PrimitiveTopology LINE_LIST;
    public static final PrimitiveTopology LINE_STRIP;
    public static final PrimitiveTopology TRIANGLE_LIST;
    public static final PrimitiveTopology TRIANGLE_STRIP;
    public static final PrimitiveTopology TRIANGLE_FAN;
    public static final PrimitiveTopology PATCH_LIST;
    public static final PrimitiveTopology MESHLET;          // no Vk mapping
    public static final PrimitiveTopology TETRAHEDRA;       // no Vk mapping
    public static final PrimitiveTopology HALF_EDGE;        // no Vk mapping

    public static PrimitiveTopology of(String name);

    public String name();
    /** Indices consumed per primitive, or 0 if not fixed. */
    public int indicesPerPrimitive();
    public OptionalInt vkTopology();
}
```

`vkTopology()` being empty means "this cannot be handed to a graphics pipeline directly", exactly
parallel to `AttributeFormat.vertexInputFormat()`.

---

## `GeometryPartition`

A named contiguous range of a geometry. This one type covers submeshes, meshlets, terrain tiles,
Nanite-style clusters, and chunk boundaries.

```java
public final class GeometryPartition {
    public String name();               // diagnostics only, may be empty

    public long firstIndex();           // into the index stream, or
    public long firstVertex();          // into the vertex streams when non-indexed
    public long primitiveCount();
    public long vertexCount();

    public PrimitiveTopology topology();
    public AABB bounds();

    /**
     * Opaque routing identity. Uninterpreted by this module.
     * Consumers use it to route partitions to pipelines, materials, or any other
     * app-defined classification. The module never reads it except to copy it.
     */
    public long tag();

    /**
     * Opaque ordering key. Uninterpreted by this module.
     * Separate from tag because routing identity and sort order are different things:
     * a consumer commonly wants to sort by depth or pipeline while routing by tag.
     */
    public long sortKey();
}
```

### On `tag` naming

Considered and rejected: `materialSlot`, `bucket`, `materialIndex`, `classId`. Each smuggles in an
interpretation the module does not have and should not have. `tag` is honest about being meaningless
here, and its Javadoc says so explicitly.

Two fields rather than one because routing and ordering are genuinely independent. Merging them
forces consumers to encode both into one integer and forces the mesh module to have an opinion about
the bit layout.

---

## `PartitionMetadata`

Per-partition side channels, typed, extensible from outside the module.

### Why not fields on `GeometryPartition`

Meshlet culling wants a cone axis and cone cutoff. Cluster LOD wants a parent link and a projected
error bound. Terrain wants a neighbour-level mask for crack-free stitching. Splat rendering wants a
covariance matrix. None of those belong on the partition type, and adding them all would make
`GeometryPartition` the exact god object this plan exists to avoid.

### Shape

Follows the `AssetRegistry` / `AssetType` pattern already in `vulkan-ffm-node-trees`: a typed key,
values stored as dense arrays parallel to the partition list so bulk GPU upload of a metadata channel
is a single contiguous copy.

```java
public final class PartitionMetadata {
    public <T> void put(MetadataChannel<T> channel, int partitionIndex, T value);
    public <T> T get(MetadataChannel<T> channel, int partitionIndex);
    public boolean has(MetadataChannel<?> channel);
    public Set<MetadataChannel<?>> channels();

    /** The dense backing store for a channel, for bulk upload. */
    public <T> MemorySegment raw(MetadataChannel<T> channel);
}

public final class MetadataChannel<T> {
    public static <T> MetadataChannel<T> of(String name, GpuLayout<T> layout);
    public String name();
    public GpuLayout<T> layout();       // dense array stride and encoding
}
```

Requiring a `GpuLayout` on the channel is what makes a metadata channel uploadable without the module
knowing what the channel means. A meshlet cone channel and a cluster error channel are the same code
path.

---

## `PartitionSet`

The collection, plus an optional hierarchy.

```java
public final class PartitionSet {
    public int count();
    public GeometryPartition get(int index);
    public PartitionMetadata metadata();

    /** Optional spatial hierarchy over the partitions. Any SpatialStructure implementation. */
    public Optional<SpatialStructure<GeometryPartition>> hierarchy();

    public AABB bounds();
}
```

### On the spatial structure

Swappable per use case, never assumed. `PartitionSet` holds a `SpatialStructure<GeometryPartition>`
through the interface and is indifferent to which implementation. A BVH suits general clustered
geometry; a quadtree suits terrain; a dense grid suits uniformly chunked voxel worlds; no hierarchy
at all suits a mesh with four submeshes.

Choosing the structure is the caller's decision, and the module must not make it easier to use one
than another.

### Likely additions needed in `helpers-core` spatial

Two capabilities the mesh use case needs that the spatial module may not have yet. Both are
legitimate additions to the spatial subsystem rather than reasons to build a private tree:

1. Incremental refit and update. Rebuilding a BVH every frame is not viable for deforming or
   streaming geometry. A refit that updates bounds bottom-up without changing topology, plus
   incremental insert and remove, is what dynamic meshes need.
2. Per-node payload. Cluster LOD stores an error metric and a parent link per hierarchy node, not
   per leaf object. If `SpatialNode` cannot carry a typed payload, that data has to live in a
   parallel array keyed by `SpatialNode.index()`, which is workable but worse.

Also noted in `00-prerequisites.md` item 2: `SpatialStructure` currently extends `BufferWritable`,
which bakes a single serialization into the structure contract. That should become
`HasGpuLayout` so alternative traversal-order and quantized layouts are equal citizens.

---

## Partitioning strategies

Producing partitions is a separate concern from representing them.

```java
public interface PartitioningStrategy {
    PartitionSet partition(GeometrySource source);
}
```

Shipped in `vulkan-ffm-meshes`:

- `NativePartitioning` - use whatever the source declared (glTF primitives, OBJ groups). The default.
- `SinglePartition` - the whole mesh as one partition.
- `TagPartitioning` - split by an app-supplied per-primitive tag function.

Not shipped here:

- Meshlet building. The interface lives here; optimized implementations go to
  `vulkan-ffm-meshes-processing`. A naive reference implementation in this module is acceptable and
  probably valuable for tests, clearly marked as a reference rather than a production path.
- Simplification-driven cluster hierarchies. Same split.

---

## Design principles for this layer

- One partition type for every granularity. Submeshes, meshlets, tiles, and clusters differ only in
  their metadata and their count.
- Topology is open to extension from outside the module, and a topology need not be renderable.
- Anything that is specific to one partitioning paradigm goes in a metadata channel, never on the
  partition type.
- Metadata channels are uploadable without the module understanding them, because a channel carries
  its own `GpuLayout`.
- The spatial hierarchy is optional and swappable. Improving `helpers-core` spatial is preferred over
  building a private tree.
- Producing partitions and representing partitions are separate concerns with separate types.
