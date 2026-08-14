# Future: Columnar Metadata Channels with Accessor Views

Status: NOT SCHEDULED. Recorded for continuity.

---

## The idea

A `MetadataChannel` for compound types (Vec3, AABB, etc.) could store its data as flat primitive
arrays in struct-of-arrays (SoA) form rather than as `Object[]`. A `Vec3MetadataChannel` would
hold `float[count * 3]` and present typed access through a reusable cursor/view object.

```java
// Hypothetical
public final class Vec3MetadataChannel implements MetadataStore {
    private float[] data; // [x0, y0, z0, x1, y1, z1, ...]
    
    // Zero-alloc read: writes into caller's reusable Vec3
    public void get(int index, Vec3 dest) {
        int o = index * 3;
        dest.x = data[o];
        dest.y = data[o + 1];
        dest.z = data[o + 2];
    }
    
    // Zero-alloc write: reads from caller's Vec3
    public void set(int index, Vec3 value) {
        int o = index * 3;
        data[o] = value.x;
        data[o + 1] = value.y;
        data[o + 2] = value.z;
    }
    
    // Bulk: single MemorySegment.copy (if stride matches GPU layout exactly)
    @Override
    public void bulkWriteTo(MemorySegment dst, long dstOffset, int from, int to) {
        MemorySegment.copy(data, from * 3, dst, JAVA_FLOAT_UNALIGNED, dstOffset, (to - from) * 3);
    }
}
```

## Benefits over TypedMetadataChannel<Vec3>

- No Object[] allocation per partition (no Vec3 instances in the backing store)
- Bulk write is a single memcpy (primitive arrays are already in GPU-ready layout when stride matches)
- Bulk read is a single memcpy
- Zero-alloc per-element access via the mutable cursor pattern
- Cache-friendly sequential access (no pointer chasing through Object[])

## When to build this

When a profiled workload shows that `TypedMetadataChannel<Vec3>` with its per-element writeTo loop
is a bottleneck. With the current design, the cost is:
- N calls to `GpuLayout.writeTo` (inlinable, no allocation if the GpuLayout doesn't allocate)
- vs. 1 call to `MemorySegment.copy` (intrinsified memcpy)

For large partition counts (100K+) with frequent full re-upload (every frame), this will matter.
For load-time-only upload or incremental dirty-range upload, it likely doesn't.

## What it requires

1. Per-type specialization: `Vec3MetadataChannel`, `AABBMetadataChannel`, etc. Each is a concrete
   class with its own set/get signature. There is no generic base because generics cannot express
   "get into mutable T without allocating T."

2. A way to determine whether the SoA layout matches the GPU target layout. For Vec3 as packed
   xyz floats this is trivially true. For Vec3 as std140-padded (16 bytes), it is not, and a
   transcode step or padding in the backing array is needed.

3. The MetadataStore interface already supports this: `bulkWriteTo` and `bulkReadFrom` are the
   only methods the consumer calls. The implementation decides whether it's a memcpy or a loop.

## Decision criteria

Build when:
- A sample or benchmark shows per-element writeTo as a measurable cost
- Multiple compound-type channels exist and share the pattern (Vec3, Vec4, AABB at minimum)
- The layout-match question has a clear answer for the target use case

Do not build preemptively. The TypedMetadataChannel path is correct and maintainable. The
columnar path is an optimization with a clear measurement-driven trigger.
