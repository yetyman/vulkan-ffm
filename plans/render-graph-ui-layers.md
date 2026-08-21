# Render Graph UI Layers Plan

## Summary

Extend the UILayer system with a first-class render-graph integration path. Graph-aware layers
decompose their work into graph nodes (initialize, resize, render, dispose) and expose them for
the `RenderGraph` to schedule, barrier, and execute. Non-graph layers get automatic wrapping into
graph nodes so a single composite can host both kinds.

All implementation lives in `vulkan-ffm-sample-ui-layers`, which already depends on both
`vulkan-ffm-node-trees` and `vulkan-ffm-graph`. No new module is required.

---

## Goals

1. Define `RenderGraphLayer extends UILayer` as the contract for layers that own graph nodes.
2. Implement three sample graph-aware layers: scene mesh, gizmo overlay, text.
3. Automatically wrap plain `UILayer` instances into graph nodes within the composite.
4. Keep the render graph topology stable frame-to-frame; scene object add/remove updates data
   fed into nodes, not the graph topology itself.

## Non-Goals

- Replacing the existing direct-render path. `UILayer.render()` remains valid for non-graph apps.
- Building the mesh module. The scene mesh layer uses the mesh module *interface boundaries*
  (GeometryBinding, GeometryDrawRange) but the mesh module is a separate plan.
- Defining a universal "graph-aware component" pattern in `vulkan-ffm-node-trees`. That module
  stays graph-agnostic; integration is done in `vulkan-ffm-sample-ui-layers`.

---

## Part 1: The `RenderGraphLayer` Interface

```java
package io.github.yetyman.vulkan.ui.graph;

import io.github.yetyman.vulkan.graph.RenderGraph;
import io.github.yetyman.vulkan.ui.UIFrameContext;
import io.github.yetyman.vulkan.ui.UILayer;

/**
 * A UILayer that decomposes its work into render graph nodes.
 *
 * Layers implementing this interface:
 * - Create and own RenderNode instances during registerNodes()
 * - Track their created nodes internally for later removal
 * - Prepare per-frame data in prepareFrame() before graph execution
 * - Signal whether a graph recompile is needed via needsRecompile()
 *
 * The direct render() path is a no-op for these layers (all drawing is via graph nodes).
 */
public interface RenderGraphLayer extends UILayer {

    /**
     * Register this layer's nodes into the graph.
     * Called once at graph construction time, or when the graph is rebuilt.
     * The layer creates its nodes here and adds them to the graph builder.
     *
     * @param graph the render graph to register nodes into
     */
    void registerNodes(RenderGraph.Builder graph);

    /**
     * Remove this layer's nodes from tracking. Called on layer removal or graph teardown.
     * The layer should release any internal references to graph nodes and resources.
     * Physical node removal from the graph is handled by graph rebuild.
     */
    void unregisterNodes();

    /**
     * Prepare per-frame data before graph execution.
     * Called after update() and before the graph executes for this frame.
     *
     * This is where the layer:
     * - Consumes TraversalView dirty reports
     * - Updates SSBO/uniform data that graph nodes will read
     * - Flushes dirty buffer ranges
     * - Determines if any topology change requires graph recompile
     *
     * @param frame per-frame context (deltaTime, frameArena, etc.)
     */
    void prepareFrame(UIFrameContext frame);

    /**
     * @return true if graph topology has changed and a recompile is needed.
     * Checked by the graph-aware composite after prepareFrame(). Reset by the layer
     * after the composite acknowledges it.
     */
    boolean needsRecompile();

    /**
     * Acknowledges that a recompile was performed. Resets the needsRecompile flag.
     */
    void acknowledgeRecompile();

    /**
     * @return the list of RenderNode instances this layer currently owns.
     * Used by the composite for graph construction and to establish ordering edges.
     */
    java.util.List<io.github.yetyman.vulkan.graph.nodes.RenderNode> ownedNodes();
}
```

### Design Rationale

- `registerNodes` takes `RenderGraph.Builder` not `RenderGraph` because graph topology is fixed
  at build time. Layers declare their nodes during graph construction.
- `prepareFrame` is separate from `update()` to maintain the existing UILayer contract. `update()`
  handles UI-side logic (animations, layout, input response). `prepareFrame()` handles GPU-side
  data preparation (buffer writes, dirty tracking consumption).
- `needsRecompile()` is a pull-based query rather than an event because graph recompilation should
  be batched (multiple layers might need it simultaneously).
- `ownedNodes()` exposes the layer's nodes so the composite can wire ordering edges between layers
  (e.g., scene renders before gizmo, gizmo before text).

---

## Part 2: `prepareFrame` and Graph Recompilation

> I assume this prepareFrame method is there to allow the graph to get all updates in one pass
> and determine if it will need any re-compilation in a single pass?

**Yes, exactly.** The sequence is:

```
for each layer:
    layer.update(frameCtx)           // UI logic, input response, animation

for each RenderGraphLayer:
    layer.prepareFrame(frameCtx)     // consume dirty reports, flush buffers

if (any layer.needsRecompile()):
    graph.recompile()                // topology changed -- rare
    for each layer: layer.acknowledgeRecompile()

graph.execute(frameAllocator, frameIndex, fence)  // runs all nodes
```

`prepareFrame` is the single synchronization point where all layers finalize their GPU data
before the graph executes. This lets the graph:

1. Batch all buffer transfers before any rendering
2. Know the final pass activation state (all layers have set their nodes active/inactive)
3. Detect if any layer's topology changed and recompile once (not per-layer)

---

## Part 3: Wrapping Non-Graph Layers

> Could it be nodes for each part of the lifecycle? Like initialize, resize, render so that
> situations like resize recreation of assets is in the graph even if it's one large chunk?

**Yes.** Non-graph layers get wrapped into multiple nodes covering their full lifecycle:

### `WrappedUILayerNodes` (internal utility in the composite)

```java
/**
 * Wraps a plain UILayer into graph nodes for each lifecycle phase.
 * Used by GraphAwareComposite to include non-RenderGraphLayer instances in the graph.
 */
class WrappedUILayerNodes {
    private final UILayer layer;
    private final CpuWorkNode initNode;       // runs layer.initialize() once
    private final CpuWorkNode resizeNode;     // runs layer.resize() on dimension change
    private final GraphicsPassNode renderNode; // runs layer.preRender() + layer.render() each frame
    // No explicit dispose node -- AutoCloseable is handled by composite.close()

    WrappedUILayerNodes(UILayer layer, ImportedResource swapchainImage) {
        this.layer = layer;

        this.initNode = CpuWorkNode.builder()
            .name(layer.name() + "/init")
            .scheduleHint(ScheduleHint.EARLY)
            .does(ctx -> {
                // No-op after first frame (guarded internally).
                // Initialization happens at composite.initialize() before the graph runs.
            })
            .build();

        this.resizeNode = CpuWorkNode.builder()
            .name(layer.name() + "/resize")
            .does(ctx -> {
                // No-op unless dimensions changed. The composite sets a flag.
            })
            .build();

        this.renderNode = GraphicsPassNode.builder()
            .name(layer.name() + "/render")
            .colorAttachment(swapchainImage)
            .autoRendering(/* inherited from composite's rendering config */)
            .execute(ctx -> {
                Arena frameArena = (Arena) ctx.frameArena();
                VkCommandBuffer cmd = ctx.commandBuffer();
                layer.preRender(cmd, frameArena);
                layer.render(cmd, frameArena);
            })
            .build();
    }
}
```

### Why Multiple Nodes

- **Resize in the graph**: When the swapchain resizes, the composite triggers the resize node.
  Because it's in the graph, the graph knows that resize must complete before any render node
  that uses the resized resources. No manual fence coordination needed.
- **Ordering**: The graph can schedule the wrapped layer's render node relative to other layers'
  render nodes using dependency edges based on `UILayer.order()`.
- **Profiling**: Each wrapped lifecycle phase gets its own GPU timestamp, so the feedback system
  can identify which layers are expensive.

### Lifecycle Details

- `initNode`: Effectively a no-op in the graph (initialization happens before the graph is built
  via `composite.initialize()`). It exists to declare resource writes that other nodes depend on.
  It's marked inactive after the first frame.
- `resizeNode`: Active only during resize frames. The composite activates it, the graph executes
  it, then it's deactivated. This puts resize asset recreation into the graph's scheduling.
- `renderNode`: Active every frame. Records the layer's draw commands.

---

## Part 4: The Graph-Aware Composite

```java
package io.github.yetyman.vulkan.ui.graph;

/**
 * A UIComposite variant that integrates all layers into a RenderGraph.
 *
 * RenderGraphLayer instances contribute their own nodes.
 * Plain UILayer instances are wrapped into WrappedUILayerNodes.
 *
 * The composite owns the graph and manages:
 * - Layer-to-layer ordering edges (based on UILayer.order())
 * - PrepareFrame dispatch before graph execution
 * - Recompile detection and triggering
 * - Resize propagation (activates resize nodes, calls graph.resize())
 */
public class GraphAwareComposite implements AutoCloseable {

    // Layers sorted by order
    private final List<UILayer> layers;
    // Subset that are RenderGraphLayer
    private final List<RenderGraphLayer> graphLayers;
    // Wrappers for non-graph layers
    private final List<WrappedUILayerNodes> wrappedLayers;
    // The render graph
    private RenderGraph graph;
    // Imported swapchain image (rebound each frame)
    private ImportedResource swapchainImage;

    // -- Lifecycle --

    public void initialize() { /* init all layers, build graph */ }
    public void update(UIFrameContext frame) { /* update all layers */ }
    public void prepareAndExecute(UIFrameContext frame, SegmentAllocator alloc,
                                  int frameIndex, MemorySegment fence) {
        // 1. update all layers
        // 2. prepareFrame on graph layers
        // 3. check needsRecompile, recompile if needed
        // 4. rebind swapchain image
        // 5. graph.execute(alloc, frameIndex, fence)
    }
    public void resize(int w, int h) { /* resize layers + graph */ }
    public void close() { /* close graph + layers */ }
}
```

### Layer Ordering in the Graph

Layers are sorted by `order()`. The composite inserts `DependencyEdge` between consecutive
layers' render nodes:

```
Layer A (order=0) render -> Layer B (order=100) render -> Layer C (order=200) render
```

This ensures painter's algorithm ordering is respected even if the graph scheduler would
otherwise reorder them (they all write the same color attachment, so without explicit edges
the scheduler has no ordering constraint).

---

## Part 5: Sample Layer 1 -- Scene Mesh Layer

### Purpose

Renders a 3D scene from a node tree. Meshes are `Component` instances on tree `Node`s.
Depth testing, generic mesh shaders, instanced/indirect draw. Uses `TraversalView<MeshComponent>`
for incremental tracking.

### Graph Topology (Stable)

```
[DepthPrepass]  -->  [OpaquePass]  -->  [TransparentPass]
     |                    |
     v                    v
  depth buffer        color attachment
```

Three `GraphicsPassNode` instances, created once at `registerNodes()`. Never changes.

### Node Tree Integration

```java
public class MeshComponent implements Component {
    // Geometry reference (from mesh module): GeometryBinding + GeometryDrawRange
    private GeometryBinding binding;
    private GeometryDrawRange drawRange;

    // Per-instance GPU data (model matrix, material ID, flags)
    private MeshInstanceData gpuData;

    // Dirty notification: when transform or material changes, marks the node dirty
    // in the TraversalView, which triggers buffer patching in prepareFrame()
}
```

### Data Flow

```
Node tree mutations (add/remove/reparent MeshComponent nodes)
    |
    v
TraversalView<MeshComponent> (incrementally maintained)
    |
    | applyPatches() in prepareFrame()
    v
Instance SSBO (per-instance: mat4 model, uint materialId, uint flags)
    |
    | flush() via TransferBatch
    v
Indirect draw buffer (via IndirectDrawEncoder) + count buffer
    |
    v
[OpaquePass] node: vkCmdDrawIndexedIndirectCount(cmd, indirectBuf, 0, countBuf, 0, maxDraws, stride)
```

### Steady-State Performance

In steady state (no add/remove, no transform changes):
- `applyPatches()` returns `DirtyReport.clean()` -- zero work
- No buffer writes
- No buffer transfers
- The graph node's `execute()` records the same indirect draw call with unchanged data
- **Zero per-mesh CPU work** in steady state (matching the mesh module's performance invariant #1)

When transforms change:
- Only the changed entries in the instance SSBO are patched (O(dirty count))
- Multi-region copy transfers only the modified ranges
- Draw count unchanged -- no command re-recording

When objects are added/removed:
- TraversalView reports additions/removals
- Instance SSBO is patched (append for add, compact or mark-dead for remove)
- Indirect draw buffer updated (new draw count)
- Still no command re-recording (indirect draw reads count from buffer)

### Class List

```
io.github.yetyman.vulkan.ui.layers.scene/
    SceneMeshLayer.java           -- RenderGraphLayer implementation
    MeshComponent.java            -- Component attached to scene nodes (holds GeometryBinding + GeometryDrawRange)
    MeshInstanceData.java         -- BufferWritable per-instance GPU layout
    SceneShaderSet.java           -- vertex/fragment shader pair for opaque/depth/transparent
    SceneDrawManager.java         -- maintains indirect draw buffer via IndirectDrawEncoder + count buffer
```

---

## Part 6: Sample Layer 2 -- Gizmo Overlay Layer

### Purpose

Flat (no depth test) overlay rendering for debugging and editor gizmos. Lines, wireframes,
filled shapes. No scene tree needed -- uses a flat draw list accumulated during `update()`.

### Graph Topology (Stable)

```
[GizmoPass]  (single GraphicsPassNode, no depth, renders on top)
```

One node. Always active.

### Data Flow

```
Application code (during update()):
    gizmoLayer.addLine(a, b, color)
    gizmoLayer.addWireBox(min, max, color)
    gizmoLayer.addCircle(center, radius, color)
    |
    v
OverlayDrawList (tessellates into OverlayVertex[])
    |
    | prepareFrame(): upload vertices to SSBO or vertex buffer
    v
[GizmoPass] node: vkCmdDraw(cmd, vertexCount, 1, 0, 0)
```

### Event Propagation

Gizmos can be interactive (drag handles, pick targets). The layer implements `handleInput()`:
- During capture phase: hit-test mouse position against active gizmos
- During bubble phase: if a gizmo was hit, consume the event and begin drag

Hit testing uses simple screen-space distance (no depth buffer, no ray casting needed since
gizmos are 2D overlays or have known screen projections).

### Class List

```
io.github.yetyman.vulkan.ui.layers.gizmo/
    GizmoOverlayLayer.java        -- RenderGraphLayer implementation
    GizmoDrawList.java            -- per-frame draw accumulation + tessellation
    GizmoVertex.java              -- pos2D + color4 (24 bytes) or pos3D projected
    GizmoHandle.java              -- interactive handle (position, type, callback)
    GizmoShader.java              -- simple vertex/fragment pair (no depth)
```

---

## Part 7: Sample Layer 3 -- Graph-Aware Text Layer

### Purpose

GPU-driven text rendering through graph nodes. Same architecture as the existing
`GPUDrivenTextLayer` concept but routed through the render graph for proper barrier management
and scheduling.

### Graph Topology (Stable)

```
[AtlasUpload]  -->  [TextRender]
  (transfer)         (graphics)
```

Two nodes:
- `AtlasUpload`: transfers newly rasterized glyphs to the GPU font atlas (active only when
  new glyphs are needed -- otherwise deactivated, zero cost)
- `TextRender`: instanced draw of glyph quads from SSBO

### Data Flow

```
Application code (during update()):
    textLayer.text("Hello", x, y, font, size, color)
    |
    v
TextBatch (accumulates GlyphInstance[] from FontRegistry)
    |
    | prepareFrame():
    |   - check if new glyphs need rasterization -> activate AtlasUpload node
    |   - upload GlyphInstance SSBO
    v
[AtlasUpload] node: vkCmdCopyBufferToImage (staging -> atlas)
[TextRender] node: vkCmdDraw(cmd, 4, glyphCount, 0, 0)  // triangle strip instanced
```

### Differential Updates

Text layers typically rewrite their entire glyph buffer every frame (text content changes
frequently). However, for static text (labels, HUD elements), the TraversalView pattern applies:

- Tree nodes with `TextComponent` are tracked in a `TraversalView<TextComponent>`
- Static text entries stay clean frame-to-frame
- Only dynamic text (counters, chat) triggers buffer writes

### Class List

```
io.github.yetyman.vulkan.ui.layers.text/
    GraphTextLayer.java           -- RenderGraphLayer implementation
    TextComponent.java            -- Component for tree-node-attached text
    GlyphInstance.java            -- std430 per-glyph GPU data (BufferWritable)
    TextBatch.java                -- per-frame glyph accumulation
    FontAtlasManager.java         -- glyph rasterization + atlas packing + upload tracking
    TextShader.java               -- instanced triangle-strip vertex/fragment pair
```

---

## Part 8: Implementation Order

### Phase 1: Interface and Composite

1. Create `io.github.yetyman.vulkan.ui.graph` package in `vulkan-ffm-sample-ui-layers`
2. Implement `RenderGraphLayer` interface
3. Implement `GraphAwareComposite` with:
   - Graph construction from layers
   - Layer ordering edges
   - prepareFrame dispatch
   - Recompile detection
   - Non-graph layer wrapping (`WrappedUILayerNodes`)
4. Implement `WrappedUILayerNodes` with init/resize/render node wrapping

### Phase 2: Gizmo Overlay Layer (simplest, no tree needed)

5. `GizmoOverlayLayer` implementing `RenderGraphLayer`
6. `GizmoDrawList` with tessellation
7. `GizmoVertex` and shader
8. `GizmoHandle` for interactive gizmos

### Phase 3: Text Layer

9. `GraphTextLayer` implementing `RenderGraphLayer`
10. `FontAtlasManager` (or reuse FontRegistry from assets)
11. `GlyphInstance` BufferWritable
12. `TextBatch` accumulation
13. Two-node topology (atlas upload + text render)

### Phase 4: Scene Mesh Layer (most complex, uses mesh module)

14. `SceneMeshLayer` implementing `RenderGraphLayer`
15. `MeshComponent` for tree nodes (wraps `GeometryBinding` + `GeometryDrawRange`)
16. `MeshInstanceData` BufferWritable
17. `SceneDrawManager` with `IndirectDrawEncoder` + count buffer for `vkCmdDrawIndexedIndirectCount`
18. Three-node topology (depth prepass + opaque + transparent)
19. Integration with `vulkan-ffm-mesh` consume layer types

### Phase 5: Sample Application

20. Create a sample app in `sample-app` that:
    - Builds a `GraphAwareComposite` with all three layers
    - Demonstrates dynamic scene (add/remove meshes at runtime)
    - Shows differential update path (steady-state zero CPU work)
    - Mixes a plain UILayer (e.g., FPS counter) wrapped into the graph

---

## Open Questions

1. **Indirect draw for the mesh layer**: Use `vkCmdDrawIndexedIndirectCount` (device feature
   required). Simpler is not a concern -- we target maximum capability and speed. GPU culling
   integration becomes trivial later since the count buffer is already in place.
   **RESOLVED: Use IndirectCount from the start.**

2. **Atlas upload node deactivation**: Node activation change triggers `recompileFromCull`
   which is a fast path (no topology change). This is sufficient for now. Future refinement:
   only recompile when the deactivated node was responsible for compiled blocking constraints
   that forced suboptimal positioning of other nodes. Otherwise skip the recompile entirely.
   **RESOLVED: recompileFromCull for now. Deterministic skip as future optimization.**

3. **Layer removal at runtime**: Layer add/remove triggers a full graph rebuild. Does not need
   to be immediate -- can be lazy (deferred to next frame boundary or next prepareFrame pass).
   **RESOLVED: Full rebuild, timing TBD (lazy is fine).**

4. **Shared depth buffer across layers**: Yes. Layers can declare read edges on resources
   produced by other layers (depth buffer, color attachments, etc.). The graph's resource edge
   system handles this naturally. The sample layers just don't need it yet.
   **RESOLVED: Supported, no special mechanism needed.**

5. **Mesh module dependency**: The mesh module (`vulkan-ffm-mesh`) is implemented and already
   a dependency of `vulkan-ffm-sample-ui-layers`. `GeometryBinding`, `GeometryDrawRange`,
   `IndirectDrawEncoder`, and `GeometryTable` all exist. The scene mesh layer uses them directly.
   **RESOLVED: Use mesh module types directly. No stubs needed.**

---

## Dependency Graph

```
vulkan-ffm-sample-ui-layers
    depends on: vulkan-ffm-node-trees (UILayer, TraversalView, Component, Node, Tree)
    depends on: vulkan-ffm-graph (RenderGraph, GraphicsPassNode, CpuWorkNode, etc.)
    depends on: vulkan-core (VkDevice, ManagedBuffer, ShaderLoader, etc.)

New packages in vulkan-ffm-sample-ui-layers:
    io.github.yetyman.vulkan.ui.graph/       -- RenderGraphLayer, GraphAwareComposite, bridge utilities
    io.github.yetyman.vulkan.ui.layers.scene/ -- SceneMeshLayer
    io.github.yetyman.vulkan.ui.layers.gizmo/ -- GizmoOverlayLayer
    io.github.yetyman.vulkan.ui.layers.text/  -- GraphTextLayer (replaces/evolves GPUDrivenTextLayer concept)
```
