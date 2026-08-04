# helpers-core Math Library

## Overview

Pure math library in `helpers-core/src/main/java/io/github/yetyman/helpers/math/`. Provides mutable-first vector/matrix/quaternion types, geometric primitives, spatial structures, and GPU-upload integration via `BufferWritable`.

Depends on `vulkan-core` for `BufferWritable` interface only.

---

## Implemented Types

### Foundation (`math/`)

| Type | Fields | byteSize | Notes |
|------|--------|----------|-------|
| Vec2 | x, y | 8 | |
| Vec3 | x, y, z | 12 | |
| Vec4 | x, y, z, w | 16 | |
| Mat3 | 9 floats, column-major | 36 | mColumnRow naming |
| Mat4 | 16 floats, column-major | 64 | mColumnRow naming, m30/m31/m32 = translation |
| Quaternion | x, y, z, w | 16 | identity = (0,0,0,1) |
| Transform | position + rotation + scale | N/A | lazy local/world matrix, parent hierarchy |

All implement `BufferWritable` (except Transform which is a composite). All have `BuildStrategy` + `Builder` with `eager()`/`lazy()` intent flags.

### Geometry (`math/geometry/`)

| Type | Purpose |
|------|---------|
| Plane | normal + signed distance, classify point |
| Ray | origin + direction, pointAt(t) |
| AABB | min/max corners, containment, overlap, transform |
| OBB | center + half-extents + orientation |
| Sphere | center + radius, merge |
| Frustum | 6 planes from VP matrix, test AABB/Sphere/point |
| Intersections | static ray-plane, ray-AABB, ray-sphere, ray-OBB, frustum tests |
| ContainmentResult | INSIDE / OUTSIDE / INTERSECT |

All geometry types have `BuildStrategy` + `Builder` with `eager()`/`lazy()`.

### Spatial Structures (`math/spatial/`) — Planned

See `plans/spatial.md` for full spec. Summary:

- CPU-side queryable/mutable structures (quadtree, octree, BVH, R-tree)
- Layout descriptors for GPU serialization (DFS, BFS, wide, quantized)
- Dirty tracking + stable node addressing for incremental GPU sync
- Integration with buffer system via `SpatialLayout.writeNode()` / `serialize()`

---

## Design Conventions

### Mutable-first with copy variants

- In-place operations modify `this` and return `this` for chaining
- Copy variants use `*New` suffix (e.g., `addNew` returns a new instance)
- Public fields for direct access (no getter overhead)

### Flat fields, not arrays

- Vec3 has `public float x, y, z` not `float[] data`
- Mat4 has 16 named float fields in column-major order
- Enables JIT scalar optimization, avoids bounds checks

### Builders as reusable configurators

- All types have direct constructors AND `builder()`
- Builders designed to be allocated once and reused across frames
- `eager()` / `lazy()` factory methods declare computation timing intent
  - `lazy()` (default): all computation deferred to `build()` time
  - `eager()`: will cache intermediate results when parameters change (not yet implemented, JIT-friendly `final boolean`)
- `build()` uses global `BuildStrategy`; `build(strategy)` overrides per-call

### BuildStrategy

```java
public interface BuildStrategy<T> {
    T obtain();
    default void release(T instance) {}
    static <T> BuildStrategy<T> allocating(Supplier<T> factory) { return factory::get; }
}
```

- Each math type has a static `buildStrategy` field (global default)
- Enables transparent pooling opt-in without changing calling code
- Pooling implementations would call `release()` when objects are no longer needed

### Column-major matrices (GLSL/Vulkan convention)

- Field naming: `mColumnRow` -- e.g., `m30` = column 3, row 0 = translation X
- Writing sequentially (m00, m01, m02, m03, m10, ...) produces what `mat4` in GLSL expects
- `BufferWritable.writeTo()` writes in this order

### Coordinate conventions

- Right-handed coordinate system
- Y-up for world space
- Vulkan NDC: X right, Y down, Z into screen, depth 0..1
- `Mat4.perspective()` and `Mat4.orthographic()` account for Vulkan clip space (Y flip, 0..1 depth)

---

## BufferWritable Integration

All vector/matrix/quaternion types implement `BufferWritable`:

```java
public interface BufferWritable {
    int byteSize();
    void writeTo(ByteBuffer buf);
    void readFrom(ByteBuffer buf);
}
```

This means they work directly with `TypedVkBuffer<T>`:

```java
TypedVkBuffer<Vec3> positions = new TypedVkBuffer<>(buffer, 12, count, false) {
    @Override protected Vec3 getInstance() { return new Vec3(); }
};
positions.write(0, myVec3, queue);
```

Byte sizes are natural/packed (no std140 padding). For std140-aligned layouts (e.g., vec3 as 16 bytes), use a wrapper struct or explicit padding — the base types write their natural size.

---

## GPU-Centric Types — Future Direction

Currently removed. The original GPU variants (GpuVec3, GpuMat4, etc.) were deleted because they duplicated state without clear optimization benefit.

### Planned: Buffer Views (not yet designed)

The intended optimization is a **flyweight/cursor pattern** over `TypedVkBuffer` regions:

- Read/write fields directly from mapped memory at a stride offset
- No materialization of Java objects for iteration
- Iterate 10k transforms without allocating 10k objects

This would be a view/accessor type that sits on top of a buffer, not a standalone math object. Design TBD — depends on how `TypedVkBuffer` evolves.

---

## Mat4 Builder Memoization

The `Mat4Builder` already supports memoization for repeated builds where only some parameters change:

- **Perspective**: caches `tan(fov/2)` — if only near/far change, skips trig
- **LookAt**: caches basis vectors (forward/right/up) — if only eye changes, only recomputes translation
- **TRS**: caches rotation matrix from quaternion — if only position changes, only stamps translation

These caches are active regardless of `eager()`/`lazy()` flag (they're parameter-change detection, not early-apply). The `eager` flag is reserved for future: running the full build computation when parameters are set, rather than at `build()` time.

---

## Non-Functional Properties

- Zero allocation in hot-path operations when using mutable API
- Thread-safe for reads; mutation is caller-synchronized
- No static mutable state beyond `BuildStrategy` (intentionally global-swappable)
- All static factories return new instances (never shared mutables)
- Zero-length normalize returns zero vector (never NaN or throws)
- Float epsilon comparisons where appropriate (`MathUtil.EPSILON = 1e-6f`)
