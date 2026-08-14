# Possible Advancements: LOD

Status: catalogue of known LOD directions. None scheduled. All are believed supportable by the
existing structural types without rework. Items marked (existing types sufficient) can be built
purely as new implementations of existing interfaces. Items marked (new protocol needed) require
a new type or interface addition.

---

## Post-implementation notes

LOD was implemented during Phase 6 (not deferred as originally planned). The compatibility checks
from the original plan were satisfied:

1. `PartitionMetadata` with `FloatChannelKey`/`IntChannelKey` and `LodChannels` constants.
2. `RepresentationGraph` is a full DAG with CSR storage and multi-parent support.
3. `LodContext.residencyOf(PartitionRef)` delegates to `ResidencyQuery`, non-blocking.
4. `IndirectKind.MESH_TASKS` exists in `IndirectDrawEncoder`.
5. `RefinementStream` interface defined as a composition with `GeometrySource`; the progressive
   case composes base + stream externally without mutating source element counts.

---

## GPU-Driven DAG Selection — Nanite-style (existing types sufficient)

The highest-value unproven path. The `Indirect` selection variant and `DispatchDescription`
interface exist but no implementation exercises them.

Requirements:
- Compute shader traversing `RepresentationGraph` (CSR SSBOs uploaded from `childOffsetsRaw()` etc.)
- Per-cluster screen-space error projection on the GPU (from `LodChannels.ERROR_BOUND`)
- Monotonic cut enforcement: draw cluster when own error < threshold but parent error > threshold
- Multi-pass dispatch: persistent-thread DAG walk, prefix-sum compaction, indirect arg emission
- A concrete `DispatchDescription` implementation wrapping this multi-pass sequence
- Concrete `LodSelector` returning `LodSelection.Indirect`

Builds on: GeometryTable, PoolAllocator, RepresentationGraph, LodChannels metadata.

---

## Hierarchical Spatial Feedback (new protocol needed)

GPU-to-CPU per-cluster feedback for visibility-aware next-frame selection:

- GPU writes a visibility buffer during the draw pass (cluster ID -> visible bit)
- Readback via timeline semaphore (non-blocking, 2-frame latency)
- `LodContext` side channel carries previous-frame visibility data
- Selectors use visibility to: skip selection for fully-occluded objects, prioritize loading of
  objects that were visible but at wrong LOD

New types needed:
- `VisibilityFeedback`: per-cluster boolean array from GPU readback
- `ContextKey<VisibilityFeedback>` for the side channel
- Readback integration with render graph (readback handle returned from draw pass)

This is distinct from `LodContext.previousSelection` (which is the selector's own output) — this
is ground-truth from the GPU about what was actually rendered.

---

## LOD for Non-Triangle Data (new protocol needed)

Extending LOD beyond surface meshes:

- **Point clouds**: octree-based thinning; "error" = point spacing rather than geometric deviation.
  `RepresentationNode.errorBound` semantics need relaxation or a per-domain error metric type.
- **Voxels**: mipmap-like reduction; error = voxel size. Fits `Flat` structure naturally.
- **Volumetric rendering**: iso-level stepping, resolution reduction. Fits `Parametric` structure.
- **SDF fields**: LOD by ray-march step count or SDF resolution. Fits `Parametric`.

Open question: should `errorBound` remain a float with domain-specific interpretation, or should
there be an `ErrorMetric` interface that projects to screen space differently per domain? The
former is simpler; the latter is more principled. Current design uses the former (float, interpret
per domain) which may be sufficient since `LodSelector` implementations are domain-specific anyway.

---

## Cross-Object LOD Dependencies / HLOD (new protocol needed)

Hierarchical LOD where multiple distant objects merge into a single coarse representation:

- At distance, 50 buildings become one impostor mesh
- The coarse representation replaces a *set* of fine objects, not a single one

New types needed:
- `HlodGroup`: associates a coarse `RepresentationNode` with a set of child object IDs
- `HlodSelector`: operates above `LodSelector`; decides when to switch from individual objects to
  group representation
- Scene-level coordination: the HLOD selector runs before individual selectors and marks children
  as "replaced by group" so they skip their own selection

Integration with node tree: an `HlodComponent` on a group node that observes child visibility.
When all children would select their coarsest level, the group activates instead.

This cannot live in `vulkan-ffm-mesh` alone because it needs awareness of multiple objects and
their spatial relationships. It's a scene-level concern. Likely home: a scene integration module
or the sample layers initially.

---

## Geomorphing with Vertex Correspondence (existing types sufficient + processing addition)

Full smooth transitions between LOD levels without visual popping:

- **Geomorph-aware simplifier**: extends `QemSimplifier` (or alternative) to output a mapping from
  each simplified vertex to its original vertex. Required for the vertex shader to interpolate.
- **GeomorphMapping**: per-partition metadata channel carrying the correspondence
- **Shader support**: vertex shader reads `TransitionState.factor()` and interpolates between two
  vertex positions (current LOD + next LOD) based on the mapping
- **Validation**: reject geomorph transitions between representations with incompatible topology

The `TransitionMode.Geomorph` and `TransitionState` types already exist. Missing: the data
(correspondence mapping) and the shader technique.

---

## Progressive Mesh Implementation (existing types sufficient)

Concrete `RefinementStream` for vertex-split progressive meshes:

- Binary stream format: base mesh + ordered collapse/split records
- Each record: vertex ID, new position, new connectivity, error delta
- `refine(base, upTo, arena)` applies N records to produce a source at target quality
- Streaming: records loaded in chunks, residency requests drive chunk loading
- Error curve: monotonically decreasing, queryable for any record count via `errorAt(N)`

Progressive mesh is the cleanest test of the `Chain` + `RefinementStream` combination. If this
works smoothly, the structural design is validated for all incremental-refinement techniques.

---

## Concrete LodPolicy Implementations (existing types sufficient)

- **ProportionalPolicy**: divide triangle budget proportionally to screen-space contribution
  (projected bounds area). Objects with larger screen presence get more triangles.
- **PriorityPolicy**: classify objects (player, NPC, prop, background); allocate budget by class.
- **AdaptivePolicy**: previous-frame GPU feedback (actual triangle count readback) adjusts
  thresholds to stay within frame time budget.
- **TieredPolicy**: fixed quality tiers (Ultra/High/Medium/Low) with per-tier error thresholds.
  Simple, predictable, good default.

---

## Streaming with Predictive Prefetch (existing types sufficient)

- Camera velocity prediction: estimate position at time T + N frames
- Prefetch representations that will be needed at predicted position
- Priority: combines distance + screen-error-reduction + velocity-toward
- Memory pressure response: cancel low-priority prefetch requests when budget tightens
- Integration with `ResidencyTracker` priority system and `PartitionLoader`

---

## Dither and Cross-Fade Rendering Samples (existing types sufficient)

Demonstrate the transition modes that are defined but unexercised:

- Dither: screen-space blue noise pattern, complementary masks on outgoing/incoming representations
- Cross-fade: alpha blend both, depth pre-pass to avoid sorting issues
- Frame graph consideration: transition frames render the same geometry twice (two draw ranges from
  `TransitionState.fromNodeIndex` and `toNodeIndex`)
- Performance: auto-shorten transition duration when frame budget is tight
