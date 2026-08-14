# LOD System

## Overview

The LOD (Level of Detail) system in `io.github.yetyman.vulkan.mesh.lod` provides structural types
and interfaces for composing any LOD technique from orthogonal building blocks. It is deliberately
unbiased toward any specific approach: discrete LOD chains, cluster DAGs, progressive meshes,
parametric tessellation, and GPU-driven selection are all first-class citizens.

The central insight: LOD schemes differ less in *what* they select than in *where* the decision
happens and *what structural relationships* exist between the available representations. By
separating these concerns into independent types, any named LOD technique emerges from composing
two or three of them, rather than being a special case.

## Four Orthogonal Concerns

Every LOD technique is a specific combination of:

| Concern | Type | Question |
|---------|------|----------|
| Representation Structure | `RepresentationStructure` | What LOD data exists and how nodes relate |
| Selection Policy | `LodSelector` | Which representation to use, given context |
| Selection Execution | `LodSelection` | Where the decision happens (CPU, GPU, hardware) |
| Transition Strategy | `TransitionMode` | How the switch appears visually |

Each concern has its own sealed type. Composing them freely produces any LOD technique.

## Representation Structures

`RepresentationStructure` is sealed with exactly four variants. Every known LOD technique maps to
one of these:

### Flat

Independent variants with no structural relationship. Any one can be selected without affecting any
other.

```java
RepresentationStructure.Flat flat = new RepresentationStructure.Flat(new RepresentationNode[]{
    finest, medium, coarse
});
```

Examples: Unity-style discrete LOD, impostor swaps, billboard fallbacks, quality presets.

### Chain

Ordered refinement where each successive node is derived from (and improves upon) the previous.
Optionally carries a `RefinementStream` for continuous interpolation between levels.

```java
RepresentationStructure.Chain chain = new RepresentationStructure.Chain(nodes, refinementStream);
```

Examples: progressive mesh (vertex splits), cascaded simplification, wavelet terrain, streaming
point cloud density.

### Graph

DAG (directed acyclic graph) where nodes have children representing finer detail. Multiple parents
allowed: a child cluster may refine two adjacent parent clusters at their shared boundary.

```java
RepresentationGraph graph = RepresentationGraph.builder()
    .nodes(nodes)
    .edges(rootIndex, childA, childB)
    .edge(childA, grandchild)
    .build();

RepresentationStructure.Graph dagStructure = new RepresentationStructure.Graph(graph);
```

Examples: Nanite-style cluster LOD, virtual geometry, HLOD trees.

The graph uses compressed sparse row (CSR) format internally: `childOffsets[]` + `childData[]` for
forward traversal, `parentOffsets[]` + `parentData[]` for inverse traversal. This makes the graph
uploadable to a GPU SSBO for GPU-driven LOD selection without conversion.

### Parametric

A single base representation controlled by continuous parameters. The hardware or shader decides
the detail level; no discrete variants exist.

```java
RepresentationStructure.Parametric parametric = new RepresentationStructure.Parametric(
    baseNode,
    new ParameterDescriptor[]{ new ParameterDescriptor("tessLevel", 1.0f, 64.0f) }
);
```

Examples: hardware tessellation, displacement amplitude, SDF iso-level, mesh-shader amplification.

## RepresentationNode

A node is pure structural data: which partitions it covers, its geometric error, triangle count,
world bounds, and an opaque tag. Nodes have no graph pointers -- relationships are expressed by the
containing `RepresentationStructure`.

```java
public record RepresentationNode(
    int[] partitionIndices,    // indices into the owning PartitionSet
    float errorBound,         // world-space max deviation from full detail (0 = lossless)
    long triangleCount,       // total triangles across all partitions
    AABB bounds,              // world-space bounding box
    long tag                  // opaque routing identity
) {}
```

Nodes are value data: safe to copy, reorder, and upload as a dense GPU array.

## RepresentationSet

The top-level "LOD-enabled mesh" type. Groups multiple representations (Meshes or partition subsets)
with a structural shape and a default transition mode.

Two usage modes:
- **Multi-mesh mode**: each node backed by a different Mesh (discrete LOD chains with separate assets)
- **Single-mesh mode**: all nodes reference partitions within one PartitionSet (cluster DAGs, pools)

```java
RepresentationSet lodMesh = RepresentationSet.builder()
    .structure(chain)
    .perNodePartitions(List.of(lod0Partitions, lod1Partitions, lod2Partitions))
    .transitionMode(new TransitionMode.Dither(0.3f))
    .build();
```

`Mesh` itself does NOT hold LOD data. Applications without LOD never encounter `RepresentationSet`.

## Selection

### LodSelector

A stateful instance (not a static method) that holds policy configuration, hysteresis state, and
per-instance transition tracking.

```java
public interface LodSelector {
    LodSelection select(RepresentationStructure representations, LodContext context);
    default void frameAdvance(float deltaTimeSeconds) {}
    default void reset() {}
}
```

Different selectors for different subsystems: terrain, characters, props may each use a different
selector within the same frame.

### LodContext

Everything a selector needs: camera position, view-projection, screen height, error threshold,
per-instance transform and bounds, budget signals (triangle, memory, time), residency queries,
previous-frame feedback, and a typed side channel.

Designed for zero allocation on the hot path: build once per frame, mutate per-instance fields
(`objectTransform`, `objectBounds`) across meshes.

```java
LodContext context = LodContext.builder()
    .cameraPosition(eye)
    .viewProjection(vpMatrix)
    .projectionMatrix(proj)
    .screenHeight(1080)
    .errorThreshold(2.0f)  // pixels
    .objectTransform(worldTransform)
    .objectBounds(worldBounds)
    .residencyQuery(ref -> tracker.residencyOf(ref))
    .build();
```

Includes `projectError(worldError, distance)` utility for screen-space error projection.

### LodSelection (output)

Sealed with four variants matching the three decision locations plus "nothing":

| Variant | Decision location | Output |
|---------|-------------------|--------|
| `Explicit` | CPU | Concrete `GeometryDrawRange` list |
| `Indirect` | GPU compute | `DispatchDescription` producing indirect draw args |
| `Parametric` | Hardware/shader | Named float parameters |
| `None` | N/A | Nothing to render (too far, budget exhausted, not resident) |

All variants carry:
- `residencyRequests()` - partitions to prefetch for next frame
- `transition()` - active `TransitionState`, or null
- `selectedNodeIndex()` - which node was chosen

```java
LodSelection selection = selector.select(structure, context);
switch (selection) {
    case LodSelection.Explicit e -> drawRanges(e.ranges());
    case LodSelection.Indirect i -> recordDispatch(i.dispatch());
    case LodSelection.Parametric p -> setPushConstants(p.parameters());
    case LodSelection.None n -> requestResidency(n.residencyRequests());
}
```

## GPU-Driven LOD (Indirect Path)

`DispatchDescription` abstracts the GPU work that produces selection results. Different paradigms
have fundamentally different dispatch shapes (single frustum cull vs. multi-pass hierarchical
traversal vs. mesh-shader amplification), but all share the output contract: an argument buffer +
count buffer suitable for `vkCmdDrawIndexedIndirectCount`.

```java
public interface DispatchDescription {
    IBuffer argBuffer();
    IBuffer countBuffer();
    long countBufferOffset();
    int maxDrawCount();
    int argStride();
    boolean indexed();
    void record(Recorder recorder);
}
```

The `Recorder` interface decouples dispatch recording from any specific command buffer abstraction,
so the same description works with raw command buffers, frame graph nodes, or test harnesses.

## Transition System

### TransitionMode

Sealed with five variants describing how the switch appears:

- **HardCut** - instant swap, no blending
- **Dither** - complementary dither patterns over N frames
- **Geomorph** - vertex position interpolation between levels
- **CrossFade** - alpha blend of both representations
- **Continuous** - no discrete transition (progressive/parametric)

The mesh module tracks transition state and reports it. Renderers interpret the mode; the module
does not dictate how it looks.

### TransitionState

Mutable per-instance state: `mode`, `fromNodeIndex`, `toNodeIndex`, `factor()` (0-1 blend),
`isComplete()`, `advance(deltaTime)`. A selector creates the state, advances it each frame, and
drops it when complete.

## Budget Arbitration

### LodPolicy

Global per-frame budget allocation across all meshes. Called once before selectors run.

```java
public interface LodPolicy {
    void arbitrate(List<LodBudgetEntry> entries, LodBudget totalBudget);
}
```

`LodPolicy.PASSTHROUGH` (the default) applies no arbitration. Applications with fixed quality
thresholds never need a policy. Complex policies sort by screen contribution, allocate
proportionally, or bias toward player-focused geometry.

### LodBudget

Soft constraints: `maxTriangles`, `maxMemoryBytes`, `maxTimeMs`, `maxResidencyRequests`.
All default to `MAX_VALUE` (unconstrained). Exceeding budgets degrades quality rather than failing.

## Metadata Integration

`LodChannels` defines well-known per-partition metadata channel keys:

```java
LodChannels.ERROR_BOUND     // float: world-space geometric error
LodChannels.PARENT_ERROR    // float: parent's error (for DAG traversal)
LodChannels.NODE_INDEX      // int: maps partition back to its LOD node
LodChannels.LOD_LEVEL       // int: 0 = finest, higher = coarser
LodChannels.GROUP_ID        // int: cluster group for monotonic cut enforcement
```

These live in `PartitionMetadata` as dense arrays -- O(1) lookup, zero boxing, bulk-uploadable to
GPU SSBOs.

## Residency Interaction

A representation that is not resident cannot be selected. This is the normal condition in any
streaming system during camera motion.

Selectors consult `LodContext.residencyOf(PartitionRef)` (non-blocking, non-allocating) and may
return a degraded selection plus residency requests rather than blocking or selecting something
unusable. "Render LOD 2 but please start loading LOD 1" is expected steady state.

## RefinementStream

Interface for progressive mesh / incremental refinement. Given a base `GeometrySource`, applies N
refinement records to produce progressively more detailed geometry:

```java
public interface RefinementStream {
    long recordCount();
    float baseError();
    float errorAt(long recordCount);
    GeometrySource refine(GeometrySource base, long upTo, Arena arena);
    long bytesPerRecord();
}
```

Used by `RepresentationStructure.Chain` for continuous refinement between discrete levels.
The interface is defined; implementations are future work (progressive mesh, wavelet terrain,
subdivision surface).

## Sample: LodSceneLayer

A provisional sample UILayer in `vulkan-ffm-sample-ui-layers/layers/lodscene/` demonstrates
the LOD system:

- Accepts `GeometrySource` objects and builds LOD chains using `QemSimplifier`
- Constructs `RepresentationStructure.Chain` with error bounds from simplification
- Uses screen-space error selection via `LodContext.projectError()`
- Per-frame LOD selection with distance-based level switching
- Supports external camera integration via `CameraSource` functional interface

This is a temporary sample, not a production component.

## Composition Examples

### Discrete LOD (Unity-style)

```
Structure: Flat (3 independent meshes at different detail)
Selector:  CPU-explicit, distance-band thresholds
Selection: Explicit (one draw range per frame)
Transition: HardCut or Dither(0.3s)
```

### Nanite-style Virtual Geometry

```
Structure: Graph (cluster DAG with error bounds per node)
Selector:  GPU-indirect (compute traversal of the DAG)
Selection: Indirect (dispatch writes indirect args into buffer)
Transition: Continuous (DAG cut moves smoothly)
Metadata:  ERROR_BOUND, PARENT_ERROR, NODE_INDEX, GROUP_ID per cluster
```

### Hardware Tessellation

```
Structure: Parametric (one base mesh + tessLevel parameter)
Selector:  CPU-explicit (computes tessLevel from distance)
Selection: Parametric (tessLevel value written to push constants)
Transition: Continuous (tessellation factor changes continuously)
```

### Progressive Mesh with Streaming

```
Structure: Chain (base + RefinementStream)
Selector:  CPU-explicit with residency awareness
Selection: Explicit (draw the highest-loaded refinement)
Transition: Continuous (refinement records apply incrementally)
Residency: Request next batch of records each frame
```

## What Lives Where

| Location | Content |
|----------|---------|
| `vulkan-ffm-mesh/lod/` | Structural types, interfaces, context, metadata keys, transition state |
| `vulkan-ffm-mesh-processing/` | Concrete LOD generation (building chains from QemSimplifier) |
| `vulkan-ffm-sample-ui-layers/` | Concrete selectors, sample scene layers |
| Application code | Paradigm-specific selectors, GPU LOD shaders, policies |

## Known Issues and Future Work

- No `RefinementStream` implementations exist yet (progressive mesh, subdivision)
- No GPU-driven LOD selector sample (the `Indirect` path is defined but not demonstrated)
- `TransitionState` tracking is implemented but no sample renders dither/geomorph blends
- `LodPolicy` budget arbitration has no concrete implementation beyond `PASSTHROUGH`
- DAG-based cluster LOD selection (Nanite-style) has structural support but no working sample
- Error bounds in the sample layer are estimated from decimation ratio rather than measured from
  Hausdorff distance

## Future Advancements

### GPU-Driven DAG Selection (Nanite-style)

The highest-priority unproven path. Requires:
- A compute shader that traverses `RepresentationGraph` (uploaded as CSR SSBOs)
- Per-cluster error projection using GeometryTable bounds + camera UBO
- Monotonic cut enforcement: a cluster is drawn when its own error is acceptable but its parent's
  error is not (use `LodChannels.ERROR_BOUND` + `LodChannels.PARENT_ERROR`)
- Multi-pass: persistent-thread traversal pass, prefix-sum compaction pass, indirect draw emission
- Output: argument buffer + count buffer for `vkCmdDrawIndexedIndirectCount`
- Implementation of `DispatchDescription` for this multi-pass shape
- GPU-to-CPU feedback readback for next-frame visibility and streaming priority

### Geomorphing Support

Full geomorph transitions require:
- A geomorph-aware simplifier that produces vertex correspondence mappings alongside simplified
  geometry (which original vertex each simplified vertex derives from)
- A `GeomorphMapping` type carried as partition metadata or in `RepresentationNode`
- Shader support: vertex shader interpolates between two positions based on `TransitionState.factor()`
- Validation that the two representations share compatible topology before enabling geomorph

### Progressive Mesh Implementation

Concrete `RefinementStream` for vertex-split progressive meshes:
- Binary stream format: base mesh + ordered vertex-split records
- Each record: split vertex index, new position, new connectivity
- `refine(base, upTo, arena)` applies records incrementally
- Error tracking: per-record error delta from Hausdorff measurement
- Streaming integration: records loaded in chunks driven by residency requests

### Concrete LodPolicy Implementations

- **ProportionalPolicy**: distribute triangle budget proportionally to screen-space contribution
- **PriorityPolicy**: player-focused objects get full budget, background degrades first
- **AdaptivePolicy**: use previous-frame GPU feedback (triangle count readback) to adjust thresholds
- **FrameBudgetPolicy**: target frame time; if over budget, globally raise error thresholds

### Cross-Object HLOD

Hierarchical LOD where multiple distant objects merge into a single coarse representation:
- A `HlodGroup` type that associates a coarse `RepresentationNode` with a set of child objects
- When all children are beyond their coarsest threshold, the group node renders instead
- Requires coordination above `RepresentationSet` (per-object) -- a scene-level LOD coordinator
- Integration with the tree/node system: an HLOD component that observes child visibility

### LOD for Non-Triangle Data

Extending beyond surface meshes:
- Point cloud LOD: octree-based decimation, error = point spacing
- Voxel LOD: mipmap-like reduction, error = voxel size
- Volumetric LOD: iso-level stepping, resolution reduction
- May require relaxing `RepresentationNode.errorBound` semantics or adding per-domain error types

### Streaming LOD with Predictive Prefetch

- Camera velocity prediction: estimate where the camera will be in N frames
- Prefetch representations that will be needed at predicted positions
- Priority ordering: closest-to-camera + highest-screen-error-reduction first
- Memory pressure response: cancel low-priority prefetch when budget tightens
- Integration with `ResidencyTracker` priority system

### Dither and Cross-Fade Rendering

Sample implementations for the transition modes:
- Dither: screen-space blue noise pattern, complementary masks on both representations
- Cross-fade: alpha blend with depth pre-pass to avoid sorting
- Integration with frame graph: transition frames may need two draw passes for the same geometry
- Performance consideration: transitions double draw cost; time-limit the transition duration

### Integration with Render Graph

- LOD selection as an explicit graph node: reads camera + GeometryTable, writes selection results
- Automatic barrier insertion between LOD compute and draw
- Temporal resources for feedback readback (GPU writes visibility, CPU reads next frame)
- Conditional passes: skip LOD compute when camera hasn't moved (dirty flag from CameraComponent)

### Per-Cluster GPU Feedback

- Visibility buffer: GPU writes which clusters were actually drawn (for occlusion-aware selection)
- Error feedback: GPU computes actual projected error post-draw for threshold calibration
- Readback via timeline semaphore: non-blocking CPU read two frames later
- Feeds into `LodContext.previousSelection` and `LodPolicy` for next-frame decisions
