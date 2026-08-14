# Possible Advancement: Scene Integration

Status: vision document. Describes how mesh, UI layers, node trees, and render graph could compose
into a unified scene pipeline. Not scheduled. Records the requirements each subsystem would need to
have advanced to before this composition becomes natural.

---

## The Goal

A single scene where:
- Mesh objects live as tree nodes with components (transform, LOD, bounds, material)
- Camera is a tree component queryable by any system through DI
- Upload is a render graph transfer node with automatic barrier insertion
- Culling is a graph compute node reading GeometryTable + camera, writing indirect draw buffer
- LOD selection is either CPU (tree traversal) or GPU (graph compute node) depending on technique
- Hit testing flows through UIInputDispatcher into tree spatial queries and back
- Residency is driven by traversal visibility (what the camera sees drives what gets loaded)

All of this assembled from composable protocol types, not a monolithic scene graph class.

---

## Strategy: Prototype in Sample UI Layers, Promote What Stabilizes

Initial home: `vulkan-ffm-sample-ui-layers` (new package, e.g. `layers/scene/`)

The integration types start as sample-quality code in the sample layers module because:
1. Sample layers already depend on mesh, node trees, UI, and vulkan-core
2. Mistakes are cheap to fix when the code isn't a dependency of other modules
3. Each protocol interface gets validated by a working sample before promotion
4. Promotion criteria: the interface is stable, has no sample-layer-specific coupling, and would
   benefit from being available to other modules

Promotion targets:
- Protocol interfaces with zero multi-module dependency (e.g. `Renderable`, `CameraProtocol`) may
  promote to `vulkan-ffm-node-trees` or even `vulkan-core`
- Graph node implementations may promote to `vulkan-ffm-graph`
- Mesh-specific components stay in the mesh module or a new `vulkan-ffm-scene` module

---

## Required Subsystem Prerequisites

### Node Tree System Must Have

- [ ] Well-defined component lifecycle for GPU-resource-owning components (coordinate cleanup with
  device idle / retire queue)
- [ ] Spatial bounds as a standard component pattern (AABB or OBB, world-space, dirty-tracked)
- [ ] Spatial acceleration structure (BVH or spatial hash) over bounded nodes, incrementally
  maintained via TraversalView dirty tracking
- [ ] DI-resolvable well-known components (camera, spatial root) via `LookupScope.TREE`

### Render Graph Must Have

- [ ] Transfer nodes as first-class pass type (not just graphics/compute)
- [ ] Transient buffer resources with automatic lifetime (for skinning output, indirect draw
  buffers, readback staging)
- [ ] External resource import for long-lived pool buffers and GeometryTable SSBO
- [ ] Readback handles for GPU-to-CPU feedback (LOD visibility, hit test results)
- [ ] Conditional pass execution (skip cull pass when camera unchanged)

### Mesh System Must Have

- [ ] Everything in Phases 1-6 (done)
- [ ] LOD structural types (done)
- [ ] GeometryTable flush integrated with a transfer node (not just immediate queue submit)
- [ ] Pool allocator defragmentation (at least basic compaction)

### UI Layer System Must Have

- [ ] Layer-to-layer communication beyond input events (query protocol for camera state,
  selection state, etc.)
- [ ] Async result propagation (hit test dispatches on frame N, result arrives frame N+2)

---

## Component Protocols

These are the typed contracts between subsystems. Each is a small interface.

### MeshComponent

```java
/** A tree component that holds renderable geometry. */
public interface MeshComponent extends Component {
    GeometryBinding binding();
    PartitionSet partitions();
    GeometryAllocation allocation();
    MeshLayout layout();
    AABB localBounds();
}
```

Collected by `TraversalView<MeshComponent>` for batch rendering.

### LodMeshComponent

```java
/** Extension: mesh with LOD support. */
public interface LodMeshComponent extends MeshComponent {
    RepresentationSet representations();
    LodSelection currentSelection();
    void select(LodContext context);  // called by LOD traversal pass
}
```

### CameraComponent

```java
/** Camera state published to the tree's DI system. */
public interface CameraComponent extends Component {
    Vec3 position();
    Mat4 viewMatrix();
    Mat4 projectionMatrix();
    Mat4 viewProjectionMatrix();
    Frustum frustum();
    float nearPlane();
    float farPlane();
    float fovY();
    float aspectRatio();
}
```

Registered via DI so any component in the tree can resolve it via `LookupScope.TREE`.

### BoundsComponent

```java
/** World-space bounds for spatial queries. Dirty-tracked for BVH update. */
public interface BoundsComponent extends Component {
    AABB worldBounds();
    boolean isDirty();
    void clearDirty();
}
```

`TraversalView<BoundsComponent>` feeds the spatial acceleration structure.

### HitTestable

```java
/** Component that can be hit-tested against a ray. */
public interface HitTestable extends Component {
    /** CPU-side ray test against local geometry. May return empty if GPU-only. */
    Optional<HitResult> rayTest(Ray ray);
    /** Whether this component participates in GPU batch hit testing. */
    boolean supportsGpuHitTest();
}
```

---

## Graph Node Types

These are render graph nodes that would be prototyped in sample layers.

### GeometryUploadNode

- **Input**: dirty mesh list (from TraversalView dirty tracking)
- **Output**: transfer commands that move data to pool buffers
- **Declares**: write access to pool vertex/index buffers
- **Barrier**: transfer-write -> vertex-attribute-read before draw passes

### CullPassNode

- **Input**: GeometryTable SSBO (imported resource), camera UBO
- **Output**: indirect draw argument buffer, draw count buffer (transient resources)
- **Declares**: compute shader read of table + camera, write of indirect buffers
- **Barrier**: compute-write -> indirect-draw-read before graphics pass

### LodSelectionNode (GPU path)

- **Input**: RepresentationGraph SSBOs, error bounds, camera UBO
- **Output**: per-cluster visibility flags or direct indirect draw emission
- **Declares**: compute read of DAG data, write of selection output
- **Barrier**: compute-write -> (feeds CullPassNode or directly feeds draw)
- **Feedback output**: readback buffer with per-cluster visibility for next frame

### HitTestNode (GPU path)

- **Input**: ray origin/direction (from UI event), GeometryTable bounds SSBO
- **Output**: readback buffer with hit results (partition ID, distance, barycentric)
- **Declares**: compute read of table + bounds, write of results
- **Async**: result available N frames later via readback handle

### SkinningNode

- **Input**: rest-pose vertex SSBO (from pool), bone matrix UBO (from animation)
- **Output**: skinned vertex buffer (transient, lives one frame)
- **Declares**: compute read of source + bones, write of output
- **Barrier**: compute-write -> vertex-attribute-read before draw

---

## Hit Testing Flow

The full path from user click to hit result:

```
1. User clicks -> GLFW callback -> InputEvent (POINTER_DOWN)
2. UIInputDispatcher capture phase -> traverses layers top to bottom
3. SceneLayer receives event, converts screen coords to ray via CameraComponent
4. CPU fast path: ray-AABB test against spatial BVH (from TraversalView<BoundsComponent>)
5. Candidates narrowed to ~5-20 nodes with AABB hits
6. CPU precise path: ray-triangle against partition geometry (optional, for small meshes)
   OR
   GPU path: dispatch HitTestNode with ray + candidate partition IDs
7. Result propagation:
   - CPU path: immediate HitResult available this frame
   - GPU path: HitResult available frame N+2 via readback handle
8. HitResult bubbles back through UIInputDispatcher bubble phase
9. Selected node receives focus/selection event
```

---

## Residency-Driven Loading

```
1. Camera moves -> CameraComponent dirty
2. LOD traversal pass iterates TraversalView<LodMeshComponent>
3. For each: select(context) -> may return LodSelection with residencyRequests
4. Residency requests collected into priority-ordered batch
5. ResidencyTracker / PartitionLoader issues load commands via TransferBatch
6. Graph schedules upload transfer node for next frame
7. When upload completes (GpuCompletion), partition becomes DEVICE resident
8. Next frame: selector sees it as resident, may select finer LOD
```

---

## Incremental Build Path

This is not a single deliverable. It emerges incrementally:

1. **CameraComponent** - smallest useful piece. Define interface, implement for orbit camera,
   register in tree DI. Immediately useful for LodSceneLayer (replaces manual setCamera).

2. **BoundsComponent + spatial structure** - dirty-tracked AABB on nodes, BVH built from
   TraversalView. Enables fast spatial queries without coupling to any renderer.

3. **MeshComponent + TraversalView** - batch collection of renderables. Enables a generic
   renderer that iterates the view rather than managing its own mesh list.

4. **GeometryUploadNode** - graph-integrated upload. Requires graph transfer node support.
   Replaces manual flush() calls with automatic scheduling.

5. **CullPassNode** - graph-integrated culling. Requires transient buffer support in graph.
   Replaces inline culling dispatch in GpuDrivenDemoLayer.

6. **LodSelectionNode** - graph-integrated GPU LOD. Builds on CullPassNode. The big milestone.

7. **HitTestNode + UI flow** - the end-to-end interactive path.

Each step is independently useful and validates the protocol it introduces before the next step
builds on it.

---

## Promotion Criteria

A type moves from `vulkan-ffm-sample-ui-layers` to a lower module when:

1. Its interface has been stable across 3+ iterations without breaking changes
2. It has no imports from sample-layer-specific code
3. At least two different consumers use it (proving generality)
4. Its dependency footprint fits the target module (e.g. a pure protocol interface with no Vulkan
   imports could go in `vulkan-ffm-node-trees`; a graph node goes in `vulkan-ffm-graph`)

Types that never stabilize stay in sample layers permanently. That's fine -- not every experiment
deserves promotion.
