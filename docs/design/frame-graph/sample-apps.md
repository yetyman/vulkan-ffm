# Frame Graph Sample Applications

## Sample 1: Multi-Pass Post-Processing with Runtime Toggle

### Purpose
Demonstrates the graph's ability to recompile on the fly when passes are enabled/disabled at runtime. Shows correct barrier insertion, memory aliasing changes, and compiled graph caching.

### Scene
A simple 3D scene (rotating textured cube or loaded GLTF model) with a configurable post-processing chain.

### Pass Chain
```
Depth Pre-pass -> G-Buffer -> Lighting -> [SSAO] -> [SSR] -> [Bloom] -> Tonemap -> [FXAA] -> Present
```

Passes in brackets are toggleable at runtime via keyboard.

### Key Bindings
- `1` - Toggle SSAO
- `2` - Toggle SSR  
- `3` - Toggle Bloom
- `4` - Toggle FXAA
- `0` - Toggle all off / all on
- `R` - Force graph recompile (for testing cache invalidation)
- `P` - Print current pass mask and cached variant count

### What This Demonstrates

1. **Pass activation predicates** - each effect's `activateWhen()` checks a boolean flag
2. **Compiled graph caching** - toggling effects hits cached variants after first compile
3. **Barrier correctness** - removing SSAO from the middle means Lighting output goes directly to Bloom (or Tonemap if Bloom is also off). Barriers must adapt.
4. **Memory aliasing changes** - with SSAO off, its intermediate buffer memory can be reused by other transient resources. The aliasing map changes per variant.
5. **No manual barrier management** - the application just toggles booleans, the graph handles everything

### Implementation Notes
- Each post-processing effect is a single render pass reading the previous output and writing to a transient intermediate
- Tonemap is always active (it's the final color transform)
- Present pass reads the final output and blits to swapchain
- Display an on-screen overlay showing: active passes, frame time, cached variant count

---

## Sample 2: VR Stereo Rendering with Async Reprojection

### Purpose
Demonstrates multi-queue scheduling, shared resource reads without redundant barriers, and temporal resources for reprojection history.

### Architecture
```
Queue: Graphics                          Queue: Async Compute
  |                                        |
  Shadow Maps (shared)                     |
  |                                        |
  Left Eye Render                          |
  |                                        |
  Right Eye Render                         |
  |                                        |
  Signal semaphore ----------------------> Async Timewarp/Reprojection
  |                                        |
  (next frame starts)                      Present to HMD
```

### Graph Structure

```java
// Shared resources
GraphResource shadowMap = graph.createTransientResource("shadow_map")...;
GraphResource leftEye = graph.createTransientResource("left_eye")...;
GraphResource rightEye = graph.createTransientResource("right_eye")...;

// Temporal for reprojection (needs previous frame)
TemporalResource convergenceHistory = graph.temporal("reprojection_history")
    .bufferCount(2)
    .build();

// Shadow pass (graphics queue, shared by both eyes)
graph.addRenderPass("shadows")
    .queuePreference(QueueType.GRAPHICS)
    .writes(shadowMap)
    .record(...);

// Left eye (graphics queue)
graph.addRenderPass("left_eye")
    .queuePreference(QueueType.GRAPHICS)
    .reads(shadowMap)  // shared read - no barrier between left and right
    .writes(leftEye)
    .record(...);

// Right eye (graphics queue)
graph.addRenderPass("right_eye")
    .queuePreference(QueueType.GRAPHICS)
    .reads(shadowMap)  // same shared read
    .writes(rightEye)
    .record(...);

// Async reprojection (compute queue - overlaps with next frame's shadows)
graph.addComputePass("reprojection")
    .queuePreference(QueueType.COMPUTE)
    .reads(leftEye)
    .reads(rightEye)
    .readsTemporalPrevious(convergenceHistory)
    .writesTemporalCurrent(convergenceHistory)
    .writes(finalOutput)
    .record(...);
```

### What This Demonstrates

1. **Multi-queue scheduling** - graphics and async compute with automatic semaphore insertion
2. **Shared resource optimization** - both eyes read shadow map, graph inserts ONE barrier (not two)
3. **Temporal resources** - reprojection history is a declared cycle, not a manual hack
4. **Overlap** - next frame's shadow pass can start while reprojection is still running on compute queue (no dependency between them)
5. **Queue family ownership transfers** - if graphics and compute are different queue families, the graph handles ownership transfer for leftEye/rightEye

### Implementation Notes
- Simulated VR (render to two viewports side-by-side, no actual HMD required)
- Reprojection is a simple timewarp shader (rotation-only, no positional)
- Display both eyes in a single window for visualization
- Show timing overlay: graphics queue utilization, compute queue utilization, overlap percentage

---

## Sample 3: Multi-Rate Partial-Screen Rendering

### Purpose
Demonstrates the multi-rate rendering capability from multi-rate-rendering.md. Center of screen renders at full rate, periphery at half rate.

### Architecture
```
Frame 0: [Center] + [Left+Top]     -> Composite -> Present
Frame 1: [Center] + [Right+Bottom] -> Composite -> Present
Frame 2: [Center] + [Left+Top]     -> Composite -> Present
...
```

### What This Demonstrates

1. **Pass activation predicates** with frame-index-based scheduling
2. **Persistent resource** with partial region writes
3. **loadOp = LOAD** for preserving non-written regions
4. **Correct presentation** every frame despite partial updates
5. **Reduced GPU work** on each submission (only rendering ~75% of pixels per frame)

### Visualization
- Color-code regions by freshness (bright = just rendered, dim = 1 frame stale)
- Show per-region frame timing
- Toggle between full-rate (all regions every frame) and multi-rate with a key press for comparison

---

## Sample 4: Iterative Compute with Convergence

### Purpose
Demonstrates iterative passes with convergence predicates. A simple diffusion/blur simulation that runs N iterations per frame until convergence.

### Architecture
```
Initialize Grid -> [Iterate: Diffuse + Check Convergence] x N -> Visualize -> Present
```

### What This Demonstrates

1. **Iterative pass** with `maxIterations` and `continueWhen` predicate
2. **Ping-pong buffers** automatically managed by the graph for self-dependent iteration
3. **Variable iteration count** - early frames need many iterations, later frames converge quickly
4. **GPU conditional rendering** or CPU readback for convergence check (configurable)

### Implementation Notes
- 2D heat diffusion on a compute shader
- Initial state: random hot spots
- Each iteration: 5-point stencil averaging
- Convergence: max delta < threshold
- Visualize as a heatmap color ramp
- Display iteration count per frame in overlay
