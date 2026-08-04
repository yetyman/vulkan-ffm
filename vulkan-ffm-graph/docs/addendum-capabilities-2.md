# Frame Graph Addendum: Capabilities (Part 2)

Continuation of addendum-capabilities.md.

---

## Resource Resize and Incremental Recompilation

### Problem

When the swapchain resizes (or any resource changes dimensions/format), the graph must adapt. Full recompilation from scratch is wasteful when only resource descriptors changed but topology is identical.

### Incremental Recompilation

The compiled graph has two layers:
1. **Topology** — pass ordering, dependency edges, aliasing groups (expensive to compute)
2. **Resource bindings** — physical resource handles, barrier parameters, viewport/scissor values (cheap to update)

When only resource descriptors change (resize), the graph can do an **incremental recompile** that keeps the topology and only re-resolves resource bindings:

```java
// Full recompile (topology changed - passes added/removed)
CompiledGraph compiled = graph.compile(mask);  // full cost

// Incremental recompile (only resource descriptors changed)
compiled.rebindResources();  // cheap - reuses topology, updates physical bindings
```

The graph auto-detects which type is needed:
- Pass added/removed/reordered → full recompile
- Resource descriptor changed (size, format) → incremental if topology unchanged
- Pass activation mask changed → select from cache or full recompile

### Temporal Resource Resize Strategies

When a temporal resource resizes, its history becomes invalid (wrong dimensions). Strategy pattern controls behavior:

```java
public interface TemporalResizeStrategy {
    /** Discard history, reinitialize from initialState */
    static TemporalResizeStrategy clear() { ... }
    
    /** Scale previous history to new dimensions (bilinear/nearest) */
    static TemporalResizeStrategy scale(FilterMode filter) { ... }
    
    /** Keep old dimensions for N frames while lazily transitioning 
     *  (useful for gradual resolution changes) */
    static TemporalResizeStrategy lazyShrink(int transitionFrames) { ... }
    
    /** Application provides a custom resize handler */
    static TemporalResizeStrategy custom(ResizeHandler handler) { ... }
}

// Usage
TemporalResource history = graph.temporal("taa_history")
    .format(VK_FORMAT_R16G16B16A16_SFLOAT)
    .size(width, height)
    .bufferCount(2)
    .initialState(InitialState.Clear.BLACK)
    .resizeStrategy(TemporalResizeStrategy.scale(FilterMode.BILINEAR))
    .build();
```

### Strategies Explained

| Strategy | Behavior | Use Case |
|----------|----------|----------|
| `clear()` | Discard all history, reset to initialState | Acceptable visual pop (e.g. GI accumulator) |
| `scale(filter)` | Blit old history to new dimensions | TAA history, motion vectors (preserves temporal stability) |
| `lazyShrink(N)` | Keep old size for N frames, then resize | Gradual resolution scaling (dynamic resolution) |
| `trim()` | Crop if shrinking, pad with clear if growing | Tile-based resources where edges are expendable |
| `centerCopy()` | Nearest-neighbor copy centered, clear borders | HUD/overlay history where centering matters |
| `custom(handler)` | Application callback decides | Complex cases (e.g. reprojection-aware resize) |

### Transient Resource Resize

Transient resources simply reallocate at new dimensions on next submission. No history to preserve. The aliasing map may change (different sizes = different aliasing opportunities), triggering incremental recompile of the aliasing layer only.

### Use-While-Invalid Strategies (2D Resources)

When a resource has been resized but not all consumers have adapted yet (e.g. during a multi-frame transition), the graph needs a policy for what to provide to readers expecting the old dimensions:

```java
public interface InvalidResourceStrategy {
    /** Scale existing content to expected dimensions (GPU blit) */
    static InvalidResourceStrategy scale(FilterMode filter) { ... }
    
    /** Provide cleared resource at new dimensions */
    static InvalidResourceStrategy clear() { ... }
    
    /** Provide exact old content (reader must handle dimension mismatch) */
    static InvalidResourceStrategy exact() { ... }
    
    /** No safety net - undefined behavior, reader's problem */
    static InvalidResourceStrategy unsafe() { ... }
    
    /** Tile/repeat the old content to fill new dimensions */
    static InvalidResourceStrategy tile() { ... }
    
    /** Clamp-to-edge: old content in valid region, edge pixels extended outward */
    static InvalidResourceStrategy clampEdge() { ... }
}
```

---

## GPU Readback

### Problem

After a GPU pass writes a resource, application code sometimes needs to read that data on the CPU (convergence metrics, occlusion query results, screenshot capture, debug visualization).

### Model

Readback is NOT a separate pass type. It is a **special edge** on an existing resource that says "after the last GPU writer completes, stage this to CPU-visible memory and signal when ready."

```java
// Declare a readback on a resource
ReadbackHandle convergenceReadback = graph.addReadback("convergence_metric")
    .source(convergenceBuffer)           // GPU resource to read back
    .offset(0)                           // byte offset into source
    .size(4)                             // bytes to read (just a float)
    .frequency(ReadbackFrequency.EVERY_SUBMISSION)  // or ONCE, ON_DEMAND
    .build();

// In application code, after graph execution:
if (convergenceReadback.isReady()) {
    float metric = convergenceReadback.read(FloatBuffer.class).get(0);
}
```

### Execution Model

The graph handles readback as:

1. After the last writer of the source resource completes (determined by dependency graph)
2. Insert a transfer operation: copy from source to a CPU-visible staging buffer
3. Insert appropriate barrier (source's write stage → TRANSFER_SRC)
4. The staging buffer is persistently mapped (no map/unmap per frame)
5. Signal a fence or timeline semaphore value when the copy completes
6. `isReady()` checks the fence/semaphore without blocking
7. `await()` blocks until ready (for offline/non-interactive use)

### Latency

Readback inherently has latency — the data isn't available until the GPU finishes the relevant work. For double-buffered temporal resources, you're typically reading data from N-1 or N-2 frames ago:

```
Frame N:   GPU writes convergence metric
Frame N:   Graph inserts copy to staging buffer
Frame N+1: Fence signals, CPU can read frame N's metric
```

For convergence-driven iteration, this means the predicate uses the PREVIOUS frame's metric to decide this frame's iteration count. This is standard and acceptable for real-time.

### ReadbackFrequency Options

| Frequency | Behavior |
|-----------|----------|
| `EVERY_SUBMISSION` | Copy staged every submission, always fresh (1-frame latency) |
| `EVERY_N(n)` | Copy staged every N submissions (reduces transfer bandwidth) |
| `ON_DEMAND` | Only copy when `convergenceReadback.request()` is called |
| `ONCE` | Copy once on next submission, then stop |

### Interaction with Temporal Resources

Reading back a temporal resource reads the "current write" slot (the one being written this frame). The data is available next frame. This naturally feeds into convergence predicates:

```java
// Temporal resource for iterative refinement
TemporalResource giAccum = graph.temporal("gi_accumulator")...;

// Readback the convergence metric written alongside GI
ReadbackHandle convergence = graph.addReadback("gi_convergence")
    .source(convergenceBuffer)  // written by the GI pass
    .size(4)
    .frequency(ReadbackFrequency.EVERY_SUBMISSION)
    .build();

// Iterative pass uses previous frame's readback to decide iteration count
graph.addIterativePass("gi_bounce")
    .continueWhen(() -> {
        if (!convergence.isReady()) return true;  // no data yet, keep iterating
        return convergence.read(FloatBuffer.class).get(0) > threshold;
    })
    ...;
```

### Readback of Partial Resources

For large resources (textures, large buffers), reading back the entire thing is wasteful:

```java
// Read back a single pixel (e.g. for color picking)
ReadbackHandle pixelPick = graph.addReadback("color_pick")
    .source(colorAttachment)
    .region(mouseX, mouseY, 1, 1)  // single pixel
    .frequency(ReadbackFrequency.ON_DEMAND)
    .build();

// Trigger on click
if (mouseClicked) pixelPick.request();
```

---

## External / Imported Resources

Resources not created by the graph but used by it. The graph doesn't own their lifetime but needs to know their state for barrier insertion.

### Declaration

```java
// Import a swapchain image (externally owned, changes each frame)
ExternalResource swapchainImage = graph.importResource("swapchain")
    .image()
    .format(swapchainFormat)
    .size(width, height)
    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)       // layout when graph receives it
    .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)   // layout graph must leave it in
    .build();

// Import a pre-existing buffer from another system
ExternalResource meshBuffer = graph.importResource("mesh_data")
    .buffer()
    .size(meshBufferSize)
    .currentAccess(VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT)  // current access state
    .build();

// Each submission, bind the actual handle (may change per frame for swapchain)
graph.bindExternal(swapchainImage, acquiredSwapchainImageHandle);
graph.bindExternal(meshBuffer, meshBuffer.handle());  // stable handle
```

### Barrier Behavior

- The graph inserts a barrier at first use to transition from `initialLayout` to whatever the first reader/writer needs
- The graph inserts a barrier at last use to transition to `finalLayout` (for handoff back to external owner)
- Between first and last use, the graph manages layouts normally

### Lifetime Rules

- External resources are NEVER aliased (graph doesn't own their memory)
- External resources are NEVER freed by the graph
- If an external resource is not bound before execution, that's a runtime error
- External resources can be used as temporal read sources (e.g. "previous frame's swapchain image" for motion blur — though this requires the application to hold the previous image)

---

## Queue Selection Policy

Queue preference on passes is a **hint**, not a hard requirement.

### Policy

```
1. If the preferred queue is available and not saturated: use it
2. If the preferred queue is unavailable (device doesn't have it): fall back to a compatible queue
3. If multiple compatible queues are available: prefer the one with fewer pending passes
4. If only one queue family exists (common on integrated GPUs): everything goes there
5. Transfer-only queue is preferred for pure copy operations (frees graphics/compute capacity)
6. Async compute is preferred for compute passes that don't depend on in-flight graphics work
```

### Compatibility Fallback

| Preferred | Fallback Order |
|-----------|---------------|
| GRAPHICS | (only graphics queues support graphics) |
| COMPUTE | GRAPHICS (graphics queues support compute) |
| TRANSFER | COMPUTE → GRAPHICS (all queues support transfer) |

### Queue Family Ownership Transfers

When the compiler assigns two dependent passes to different queue families, it automatically inserts:
1. Release barrier on source queue (release ownership)
2. Semaphore signal on source queue
3. Semaphore wait on destination queue
4. Acquire barrier on destination queue (acquire ownership)

This is invisible to the application — it's a consequence of the compiler's queue assignment decisions.
