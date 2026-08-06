# Layer 5: Selection and LOD

Status: DEFERRED. Do not build any of this until Layers 0 through 4 are clean, sturdy, and proven by
working sample applications. Do not create the package.

This document exists only to record the categorization work already done, so that it is not lost and
so that Layers 0 through 4 can be checked against it for accidental incompatibility.

---

## Why it is deferred

LOD is the broadest axis in the whole system, with the widest spread of implementations, and it is the
axis most likely to retroactively distort the layers beneath it if approached prematurely. Every
category below needs Layers 0 through 4 to already work. None of them can validate those layers,
because a broken foundation plus a broken LOD system produces no useful signal about which is broken.

The deferral is not a lack of interest. LOD abstraction breadth is a primary long-term goal for this
module. It is a sequencing decision.

---

## The one structural insight worth recording

LOD schemes differ less in what they select than in where the decision happens. There appear to be
exactly three categories, and everything commonly named as a distinct LOD technique composes from one
or two of them.

| Category | Decision location | Output | Examples |
|----------|-------------------|--------|----------|
| CPU-explicit | Java, per frame or per change | concrete draw ranges | Unity-style discrete LOD, LODGroup, HLOD, impostor swaps, billboard fallbacks |
| GPU-indirect | compute shader | indirect draw arguments plus a count | Nanite-style cluster LOD, GPU-driven culling and selection, virtual geometry |
| Hardware-implicit | fixed-function or task shader | parameters only | hardware tessellation, displacement, mesh-shader amplification |

Compositions:

- Terrain CDLOD is CPU-explicit at coarse scale and GPU-indirect or hardware-implicit within a tile.
- Progressive meshes are CPU-explicit plus a residency request for the next refinement record set.
- Virtual geometry is GPU-indirect plus partition-granular residency.

No listed technique needed a fourth category. If one is found, that is a signal the categorization is
wrong and should be revised rather than extended with special cases.

---

## Sketched shapes

Recorded for continuity, not as a specification. Names and signatures will change.

```java
/** Instance method on a selector instance. Selectors hold policy and budget state. */
public interface LodSelector {
    LodSelection select(LodContext context);
}
```

Note: an earlier draft of this discussion wrote this as though it were a static call. It is not. A
selector is a stateful object holding policy, budget, and hysteresis state, and there will be many
selector instances with different configurations in one application.

```java
public sealed interface LodSelection {
    /** CPU-explicit: concrete ranges to draw now. */
    record Explicit(List<GeometryDrawRange> ranges) implements LodSelection {}

    /** GPU-indirect: arguments already in a buffer, count in another. */
    record Indirect(IBuffer args, IBuffer count, int maxDraws) implements LodSelection {}

    /** Deferred: a dispatch that will produce the selection on the GPU. */
    record Deferred(DispatchDescription dispatch) implements LodSelection {}

    /** Hardware-implicit: parameters only; the hardware decides. */
    record Parametric(Map<String, Object> params) implements LodSelection {}

    /** Nothing to draw. */
    record None() implements LodSelection {}
}
```

`RepresentationSet` needs to cover four structural shapes:

| Shape | Structure | Example |
|-------|-----------|---------|
| Flat | list of independent variants | discrete LOD chain |
| Chain | ordered refinement records | progressive mesh, vertex split |
| DAG | hierarchy with error bounds | cluster LOD, virtual geometry |
| Parametric | one representation plus parameters | tessellation, displacement, SDF |

`LodContext` carries camera and frustum from `helpers-core`, transform, screen dimensions, budgets
(triangle, memory, time), and previous-frame feedback. It needs a typed side channel, following the
`AssetRegistry` pattern, so a research scheme can supply its own inputs without changing the
interface.

`LodPolicy` arbitrates budget across many meshes, which is a global optimization distinct from
per-mesh selection. Interface with a trivial default.

---

## The one coupling that is real

A representation that is not resident cannot be selected. This is not an edge case; it is the normal
condition in any streaming system during motion.

The intended shape is that a selector consults a residency view and may return a degraded selection
plus a residency request, rather than either blocking or returning something unusable. This mirrors
the degradation concept in `vulkan-ffm-graph` without depending on it.

The relevant constraint on Layers 0 through 4 is therefore: `ResidencyTracker` must expose a
non-blocking query and a request-with-priority operation, which `04-residency-and-upload.md` already
specifies. Nothing else in the lower layers needs to anticipate LOD.

---

## Compatibility checks to run before building this

When the time comes, verify these hold. If any fails, fix the lower layer rather than working around
it in the LOD layer:

1. A partition can be registered in `GeometryTable` with a parent link and an error bound via an
   attached metadata channel, without changing the base record.
2. `PartitionSet.hierarchy()` can hold a DAG, not only a tree. If `SpatialStructure` cannot express a
   DAG, that is a spatial module gap.
3. A partition's residency can be queried without blocking and without allocation.
4. `IndirectDrawEncoder` can write mesh-task dispatch commands, so hardware-implicit and GPU-indirect
   paths share an output format.
5. `GeometrySource` can represent a refinement record stream (vertex splits), not only a complete
   mesh. This one is uncertain and may require a Layer 1 addition.
