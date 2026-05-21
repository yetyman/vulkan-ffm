# Scene Systems

## Overview

Standalone, unbiased helper systems for spatial reasoning. These live in `vulkan-core` under a `scene/` package. They have zero coupling to rendering technique, graph, object system, or application structure.

Anything that has spatial extent can participate. Discrete meshes, volumetric regions, algorithmic generators, particle emitters, procedural anchors - all are equal as long as they implement `Bounded`.

## Package Structure

```
io.github.yetyman.vulkan.scene/
    Bounded.java              - interface: boundingSphere(), boundingBox()
    BoundingSphere.java       - center + radius
    BoundingBox.java          - AABB (min, max)
    FrustumPlanes.java        - 6 planes extracted from VP matrix

io.github.yetyman.vulkan.scene.camera/
    Camera.java               - interface: viewMatrix, projectionMatrix, frustumPlanes, position, forward
    PerspectiveCamera.java    - standard perspective projection
    OrthographicCamera.java   - orthographic projection
    CameraController.java     - interface for input-driven camera movement (FPS, orbit, etc.)

io.github.yetyman.vulkan.scene.culling/
    FrustumCuller.java        - static: cull(FrustumPlanes, Collection<Bounded>) -> visible set
    CullResult.java           - visible set + stats (total tested, passed, culled)

io.github.yetyman.vulkan.scene.spatial/
    SpatialIndex.java         - interface: insert, remove, update, query(frustum), query(sphere)
    BVH.java                  - bounding volume hierarchy (dynamic, mixed-size objects)
    Octree.java               - octree (uniform density, static-ish worlds)
    FlatList.java             - trivial impl for small object counts (<100)

io.github.yetyman.vulkan.scene.lod/
    LodSelector.java          - interface: select(distance, screenSize, lodCount) -> level index
    ScreenSpaceErrorSelector.java  - standard screen-space error metric
    DistanceBandSelector.java      - simple distance-based bands
```

## Design Principles

- **Zero rendering coupling** - these systems produce data (visible sets, matrices, LOD indices). They never touch command buffers, descriptors, or pipelines.
- **Zero graph coupling** - the graph doesn't know about cameras or culling. Application code runs culling, then feeds the visible set into graph pass recording callbacks.
- **Composable** - use any camera with any spatial index with any culler. Mix and match.
- **Zero cost when unused** - no background threads, no allocations, no registration. Call when needed.
- **Unbiased** - no assumptions about what is being rendered. A `Bounded` could be a mesh, a volumetric region, a sound source, a physics body, anything with spatial extent.

## Camera Interface

```java
public interface Camera {
    float[] viewMatrix();          // 4x4 column-major
    float[] projectionMatrix();    // 4x4 column-major
    float[] viewProjectionMatrix(); // combined VP
    FrustumPlanes frustumPlanes(); // extracted from VP
    float[] position();            // world-space eye position (vec3)
    float[] forward();             // world-space forward direction (vec3)
}
```

Standard impls handle the math. Strange cameras (cubemap faces, oblique clip planes, portal cameras) just implement the interface and produce valid matrices.

## Spatial Index Interface

```java
public interface SpatialIndex<T extends Bounded> {
    void insert(T item);
    void remove(T item);
    void update(T item);  // item moved or resized
    
    // Batch operations for streaming worlds
    void insertAll(Collection<T> items);
    void removeAll(Collection<T> items);
    
    List<T> query(FrustumPlanes frustum);       // frustum culling
    List<T> query(BoundingSphere sphere);       // radius query
    List<T> query(BoundingBox box);             // box query
    
    int size();
    void clear();
    void rebuild();  // full rebuild (for bulk insert scenarios)
}
```

Multiple impls behind this interface. Application chooses based on scene characteristics:
- `BVH` - best general-purpose, handles dynamic scenes with mixed-size objects
- `Octree` - best for uniform-density static/semi-static worlds
- `FlatList` - trivial O(n) scan, best for <100 objects (no overhead)

### Batch and Incremental Updates

For streaming worlds where thousands of objects appear/disappear per frame:

- `insertAll` / `removeAll` allow implementations to defer rebalancing until the batch is complete (BVH can batch-rebuild affected subtrees rather than rebalancing per-insert)
- `rebuild()` is for bulk-load scenarios (level load, teleport) where incremental updates are worse than a full reconstruction
- Implementations should track dirty state internally and defer expensive rebalancing to query time or explicit `rebuild()` call

### Scope Note: GPU-Accelerated Culling

The systems described here are **CPU-side spatial utilities**. GPU-accelerated culling (HiZ occlusion, compute-shader frustum culling, indirect draw buffer generation) is a fundamentally different system that:
- Lives as graph passes (compute dispatches), not as CPU utilities
- Produces indirect draw buffers rather than object lists
- Requires tight integration with the buffer system and descriptor management
- Has its own planning scope and design considerations

GPU culling is **out of scope for this document** and will be planned separately if/when needed. The CPU spatial index here serves a different role: broad-phase filtering for small-to-medium scenes, spatial queries for gameplay/physics, and as input to GPU culling pipelines (providing the initial candidate set to upload).

## LOD Selection Interface

```java
public interface LodSelector {
    /**
     * @param distance     distance from camera to object center
     * @param screenSize   approximate screen-space size in pixels (diameter)
     * @param lodCount     number of available LOD levels (0 = highest detail)
     * @return selected LOD level index [0, lodCount-1]
     */
    int select(float distance, float screenSize, int lodCount);
}
```

This is selection only - choosing which pre-existing representation to use. It doesn't know what the representations ARE (mesh LODs, imposters, volumetric resolution tiers, procedural detail levels). The application maps the returned index to whatever its LOD levels mean.

## Usage Pattern

```java
// Setup
Camera camera = new PerspectiveCamera(fov, aspect, near, far);
SpatialIndex<SceneObject> index = new BVH<>();
LodSelector lodSelector = new ScreenSpaceErrorSelector(targetPixelError);

// Per frame (before graph submission)
camera.update(position, rotation);
FrustumPlanes frustum = camera.frustumPlanes();

// Cull
List<SceneObject> visible = FrustumCuller.cull(frustum, index.query(frustum));

// LOD select
for (SceneObject obj : visible) {
    float dist = distance(camera.position(), obj.position());
    float screenSize = estimateScreenSize(obj.boundingSphere(), dist, fov, screenHeight);
    int lod = lodSelector.select(dist, screenSize, obj.lodCount());
    obj.setActiveLod(lod);
}

// Feed into graph pass
graph.addRenderPass("main_scene")
    .record((cmd, arena) -> {
        for (SceneObject obj : visible) {
            obj.draw(cmd, obj.activeLod());
        }
    });
```

The scene systems are completely done before the graph executes. They produce a visible set; the graph consumes it.

## Implementation Priority

These are independent of the frame graph and can be built in parallel or after:

1. `Bounded`, `BoundingSphere`, `BoundingBox` - trivial data types
2. `FrustumPlanes` - extraction from VP matrix
3. `Camera` interface + `PerspectiveCamera` + `OrthographicCamera`
4. `FrustumCuller` - static utility, sphere-vs-planes and AABB-vs-planes
5. `SpatialIndex` interface + `FlatList` (trivial) + `BVH` (real work)
6. `LodSelector` interface + `ScreenSpaceErrorSelector`
7. `Octree` (if needed by sample apps)
