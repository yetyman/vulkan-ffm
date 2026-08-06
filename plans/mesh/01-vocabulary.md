# Layer 0: Vocabulary

Package: `io.github.yetyman.vulkan.mesh`

No Vulkan calls, no GPU resources, no allocation of native memory. Everything in this layer is pure
description and is fully unit-testable without a device.

This layer covers axes 1 and 2: what data exists, how one element is encoded, and how elements are
arranged in memory.

---

## `AttributeSemantic`

### The decision

Not an enum. An identity token, using the same pattern already established by `EventType` in
`vulkan-ffm-node-trees`.

### Why this matters more than anything else in the module

An enum of attribute semantics is the single most common lock-in in mesh libraries. The moment a
user needs `curvature`, `sdfGradient`, `simulationVelocity`, `clusterId`, `boneCountOverride`, or
anything a research paper invented last month, they are forced into `CUSTOM_0..7` and lose all
naming, all tooling, and all self-documentation. Every downstream switch statement then has a
`default` case that means "I do not know what this is", which is the shape of a system that cannot
be extended from outside.

An interned identity token is permanently open, costs the same at runtime (reference comparison),
and matches an existing pattern in the codebase.

### Shape

```java
public final class AttributeSemantic {
    // interned well-known instances
    public static final AttributeSemantic POSITION;
    public static final AttributeSemantic NORMAL;
    public static final AttributeSemantic TANGENT;
    public static final AttributeSemantic BITANGENT;
    public static AttributeSemantic TEXCOORD(int set);
    public static AttributeSemantic COLOR(int set);
    public static AttributeSemantic JOINTS(int set);
    public static AttributeSemantic WEIGHTS(int set);

    /** Interns and returns the semantic for an arbitrary name. */
    public static AttributeSemantic of(String name);

    public String name();
    /** Optional hint only. Never authoritative; the AttributeFormat is authoritative. */
    public int componentCountHint();
}
```

Identity comparison is the contract. `of("position")` must return the same instance as `POSITION` -
interning is case-normalized and by exact name.

The indexed factories (`TEXCOORD(n)`) intern per index, so `TEXCOORD(0) == TEXCOORD(0)`.

### Open question

Whether `componentCountHint` earns its place at all. It is useful for validation messages and for
guessing a default format when a source does not specify one, but it invites callers to treat it as
authoritative. Leaning toward keeping it and documenting it bluntly as a hint.

---

## `AttributeFormat`

Describes how one element of one attribute is encoded in memory.

### The decision

The mapping to `VkFormat` is optional.

### Why

Ultimate-speed vertex encodings frequently have no `VkFormat` at all:

- Octahedral-encoded normals packed into two 16-bit values, decoded in the shader.
- Positions quantized to 16-bit unsigned integers plus a per-partition scale and bias.
- Cluster IDs and material tags bit-packed into a single `uint`.
- Arbitrary bitfield packing invented for one research shader.

None of these can be a vertex input attribute. All of them are read from an SSBO and decoded
manually by the shader. If `AttributeFormat` requires a `VkFormat`, the entire vertex-pulling and
mesh-shader family of paradigms becomes second-class, which is exactly the bias this module exists
to avoid.

### Shape

```java
public final class AttributeFormat {
    public int byteSize();
    public ComponentType componentType();   // F32, F16, U8, S8, U16, S16, U32, S32, PACKED
    public int componentCount();
    public boolean normalized();

    /**
     * The VkFormat value usable as a vertex input attribute, if one exists.
     * Empty means this encoding must be decoded manually by the shader from a
     * storage buffer; it cannot be bound through vkCmdBindVertexBuffers.
     */
    public OptionalInt vertexInputFormat();

    public static AttributeFormat of(ComponentType type, int count, boolean normalized);
    public static AttributeFormat packed(String name, int byteSize);   // opaque, shader-decoded
}
```

`ComponentType.PACKED` plus `packed(name, byteSize)` is the open escape hatch: any encoding at all,
described only by its size and a name for diagnostics.

Well-known statics for the common cases (`F32x3`, `F32x2`, `U8x4_NORM`, `S16x2_NORM`, `R10G10B10A2`,
`OCT16`) as a convenience, following the `VkVertexFormat` precedent in `vulkan-core`.

---

## `MeshLayout`

The mapping from `(semantic, elementIndex)` to `(streamId, byteOffset, stride)`.

### What it unifies

A single type covers every arrangement anyone uses:

| Arrangement | How `MeshLayout` expresses it |
|-------------|-------------------------------|
| Fully interleaved | All semantics map to stream 0 with distinct offsets and a shared stride |
| Fully planar (SoA) | Each semantic maps to its own stream with stride equal to its own size |
| Hybrid | Position in stream 0 alone; everything else interleaved in stream 1 |
| Per-instance data | An additional stream marked with instance input rate |

The hybrid case is not exotic. A separate position-only stream is what depth prepasses, shadow
passes, and meshlet cone culling all want, because it keeps the bytes they touch dense.

### Shape

```java
public final class MeshLayout {
    public Set<AttributeSemantic> semantics();
    public AttributeFormat formatOf(AttributeSemantic s);
    public int streamOf(AttributeSemantic s);
    public long offsetOf(AttributeSemantic s);      // within the stream element
    public long strideOf(int streamId);
    public int streamCount();
    public InputRate inputRateOf(int streamId);     // VERTEX or INSTANCE

    /** Byte offset of element i of semantic s within its stream. */
    public long elementOffset(AttributeSemantic s, long elementIndex);

    public static Builder builder();
}
```

### Builder conveniences

Following the project's builder conventions, plus arrangement shortcuts:

```java
MeshLayout.builder()
    .interleaved(POSITION, NORMAL, TEXCOORD(0))       // one stream, packed in order
    .build();

MeshLayout.builder()
    .stream(0).attribute(POSITION, F32x3)
    .stream(1).attribute(NORMAL, OCT16).attribute(TEXCOORD(0), S16x2_NORM)
    .instanceStream(2).attribute(AttributeSemantic.of("instanceTransform"), F32x16)
    .build();
```

Explicit offsets remain available for callers matching an external layout exactly.

### Derivation of `VkVertexFormat`

```java
/**
 * Derives a vertex input state description for the subset of semantics that have a real
 * VkFormat and are assigned to a stream intended for vertex input binding.
 * Semantics whose AttributeFormat has no vertexInputFormat are omitted, and the caller
 * is responsible for exposing those streams as storage buffers instead.
 */
VkVertexFormat toVertexFormat(Map<AttributeSemantic, Integer> semanticToLocation);
```

Two things to note. First, the semantic-to-location mapping is supplied by the caller, because
location assignment is a shader contract, not a geometry property. Second, the derivation is
deliberately partial - a layout may be only partly expressible as vertex input, and that is a normal
situation, not an error.

A convenience overload can pull the mapping from `ShaderLoader` reflection, but that lives in an
adapter, not in `MeshLayout`, to keep Layer 0 free of shader dependencies.

### Transcode descriptor

`MeshLayout` is also the target description for transcoding (see `02-geometry-sources.md`). Given a
source layout and a target layout, the set of per-semantic strided copies needed is fully determined
by the two layouts. That derivation is a pure function and belongs here:

```java
/** The strided copy operations that convert srcLayout to this layout for one element range. */
List<StridedCopy> transcodeOps(MeshLayout srcLayout, long firstElement, long elementCount);
```

Where each `StridedCopy` reduces to the `writeStrided` primitive added in `00-prerequisites.md`
item 6. When source and target formats differ (quantization), the op carries a converter; when they
match, it is a pure strided memory copy and should be recognized as such so the fast path is taken.

---

## Design principles for this layer

- Nothing here allocates GPU or native memory. Everything is describable, comparable, and testable
  on the CPU with no device.
- Every vocabulary that names a concept from the outside world (semantics, topology, packed formats)
  is open to extension from outside the module. Closed sets are permitted only where the underlying
  hardware concept is genuinely closed.
- `VkFormat` compatibility is optional everywhere, so that shader-decoded encodings are first-class.
- Layout is a property of a placement, not of a type. No type in this module serializes itself.
- Derivations toward specific consumers (`VkVertexFormat`, descriptor bindings, transcode ops) are
  pure functions on `MeshLayout`, so a new consumer is a new function rather than a new field.
