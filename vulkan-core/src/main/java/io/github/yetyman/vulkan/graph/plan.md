# Render Graph

## Overview

The render graph declares pass structure, compiles to an execution plan, and executes with automatic
barriers, resource allocation, descriptor binding, and stats collection. It eliminates manual
synchronization, layout transitions, and descriptor set selection from node lambdas.

---

## Core Infrastructure

- `RenderGraph` — top-level builder, compile, execute (single-queue `executeInto` + multi-queue `execute`)
- `RenderGraphCompiler` — validation, versioning, lifetime computation, culling, scheduling, aliasing
- `RenderGraphExecutor` — per-node barrier emission, auto-rendering, timestamps, debug labels, parallel recording
- `CompiledGraph` — immutable execution plan with active nodes, buckets, lifetimes, aliasing groups
- `RenderGraphVisualizer` — ASCII DAG visualization
- `RenderGraphStats` — per-frame timing collection

---

## Node Types

| Node | Purpose |
|------|---------|
| `GraphicsPassNode` | Rasterization with optional auto-rendering |
| `ComputePassNode` | Compute dispatch |
| `TransferNode` | Explicit copy operations |
| `CpuWorkNode` | Synchronous CPU work with HOST_WRITE barriers |
| `PresentNode` | Swapchain present sink |
| `ExternalResourceNode` | Async-produced resource with semaphore wait |
| `IterativePassNode` | Convergence loops with ping-pong automation |

---

## Resource System

### Transient Resources
Graph-allocated, graph-owned. Eligible for memory aliasing with non-overlapping lifetimes.
```java
.transientImage("gbuffer", ImageDesc.custom(w, h, 1, format, usage, 1, 1, 1))
.transientBuffer("scratch", BufferDesc.storage(size))
```

### Imported Resources
Externally-owned with declared initial/final layouts. The graph emits layout transitions automatically.
```java
ImportedResource swapchain = ImportedResource.builder()
    .name("swapchain")
    .format(VK_FORMAT_B8G8R8A8_SRGB)
    .dimensions(width, height)
    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
    .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
    .build();

// Per frame:
swapchain.rebindWithView(image, imageView);
```

### Temporal Resources
Double/triple-buffered with automatic flip, staleness tracking, and graph-managed descriptor sets.
```java
TemporalResource history = TemporalResource.builder()
    .name("history")
    .descriptor(ResourceDescriptor.buffer(size, usage))
    .bufferCount(2)
    .initialState(InitialState.Clear.BLACK)
    .descriptorBinding(0)                        // single-buffer sets (fragment reads)
    .descriptorLayout(fragLayout)
    .pairedDescriptor(0, 1, computeLayout)       // paired sets (compute read+write)
    .resizeStrategy(TemporalResizeStrategy.clear())
    .build();
```

In node lambdas:
```java
ctx.temporalPairedDescriptorSet("history")  // compute: read@0 + write@1
ctx.temporalReadDescriptorSet("history")    // fragment: read@0 only
ctx.temporalReadHandle("history")           // raw handle if needed
ctx.temporalStaleness("history")            // frames since last write
```

### Persistent Resources
Cross-frame ring buffers with timeline semaphore sync via `PersistentResourceRing`.

---

## Resource Binding Layer

The graph manages descriptor sets and layout transitions so node lambdas only contain draw/dispatch logic.

### Temporal Descriptor Binding
- **Single**: one buffer per set, auto-selected by flip state. For fragment shaders that read one slot.
- **Paired**: read + write in same set, auto-selected by flip state. For compute shaders that read previous and write current.
- Created automatically during graph construction when `descriptorBinding`/`descriptorLayout` or `pairedDescriptor` is configured.

### Auto-Rendering with Imported Resources
```java
GraphicsPassNode.builder()
    .autoRendering(VkRendering.builder().device(device)
        .renderArea(0, 0, w, h)
        .colorAttachment(MemorySegment.NULL, layout, loadOp, storeOp, r, g, b, a))
    .colorAttachment(swapchainImport)  // patches image view per frame, adds write edge
    .execute(ctx -> { /* just draw calls */ })
    .build()
```
The executor patches the image view from the imported resource before `beginCached()`, and calls `end()` after the node executes. Initial and final layout barriers are emitted automatically.

### Memory Property Hints
```java
ResourceDescriptor.buffer(size, usage, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)
BufferDesc.custom(size, usage, memoryProperties)
```

---

## Scheduling & Ordering

### Topological Sort
Kahn's algorithm with priority-based tie-breaking. Respects both resource edges and manual `DependencyEdge` constraints.

### DependencyEdge
Manual ordering without resource involvement:
```java
.dependencyEdge(DependencyEdge.of(nodeA, nodeB, "timer query ordering"))
```
Inactive edges are filtered. Supports `remove()` / `restore()` for dynamic ordering.

### Scheduling Strategies
- `ListSchedulingStrategy` — greedy critical-path with priority tie-breaking
- `AdaptiveSchedulingStrategy` — feedback-driven async compute offloading

### Execution Buckets
Nodes within a bucket that share no dependencies are recorded in parallel on secondary command buffers when the bucket exceeds the parallel threshold.

---

## Degradation & Adaptation

### DegradationStrategy
Graceful pass dropping when over frame budget:
```java
.degradationStrategy(DegradationStrategy.targetFrameRate(60))
.degradationStrategy(DegradationStrategy.dropByPriority(8.0f))
```
Applied before compilation in `executeInto()`. Drops lowest-priority nodes until estimated GPU time fits budget.

### PassGroup
Dynamic pass count per frame with cache invalidation on count change.

### PassMask Caching
Compiled graphs are cached by activation state. Repeated patterns reuse the same compiled plan.

---

## Feedback System

- `TimestampQueryPool` — GPU timestamp collection per node
- `RenderGraphStats` / `FrameStats` / `NodeStats` — per-frame timing aggregation
- `FeedbackHandler` / `AdaptiveFeedbackHandler` — stats-driven adaptation
- Node `onStats()` callback with activation change detection triggering fast recompile

---

## Barrier System

### Strategies
- `SplitBarrierStrategy` — default, minimal barriers with image layout tracking
- `ConservativeBarrierStrategy` — fallback for bindless/unknown access patterns

### Automatic Barrier Emission
Per node, the executor emits:
1. Normal resource read/write barriers (from barrier strategy)
2. Temporal slot barriers (read slot: last-write -> shader-read; write slot: last-read -> shader-write)
3. Optional edge barriers (only if source has been written)
4. Bindless conservative barriers
5. After all nodes: final-layout barriers for imported resources

### Cross-Queue
- `OwnershipTransfer` — release+acquire barrier pairs
- Inter-queue timeline semaphores for ordering

---

## Optional Dependencies

```java
.optionalRead(OptionalEdge.of(ssaoBuffer, accessMask, stageMask, Fallback.clear(1.0f)))
```
If the source's writer is inactive, no barrier is emitted and `ctx.isOptionalAvailable("ssao")` returns false. Fallback types: `ClearValue`, `AlternateResource`, `RetainPrevious`.

---

## Iterative Passes

```java
IterativePassNode.builder()
    .name("light_bounce")
    .maxIterations(16)
    .continueWhen(() -> !converged)
    .execute((ctx, iteration) -> {
        var read = ctx.iterationReadHandle("accumulator");
        var write = ctx.iterationWriteHandle("accumulator");
        // dispatch...
    })
    .build();
```
- `setPingPongSlots(name, slot0, slot1)` registers physical buffers
- `setIterationCount(n)` overrides predicate for a submission
- `ctx.iterationIndex()` returns current iteration (0-based)

---

## GPU-to-CPU Readback

```java
ReadbackHandle rb = ReadbackHandle.builder()
    .name("convergence")
    .source(convergenceBuffer)
    .offset(0).size(4)
    .frequency(ReadbackFrequency.EVERY_SUBMISSION)
    .build();

// In graph builder:
.readback(rb)

// After frame fence signals:
if (rb.isReady()) {
    float metric = rb.readFloat(0);
}
```
The graph allocates a HOST_VISIBLE staging buffer and records the copy after all nodes execute.

---

## Memory Aliasing

- `AliasingStrategy` interface with partial order support
- `LifetimeAliasingStrategy` — non-overlapping lifetime bins
- `SemaphorePartialOrder` — builds partial order from bucket structure for multi-queue aliasing

---

## Temporal Unrolling

- Cycle detection and non-temporal cycle validation
- Starting point resolution with comprehensive error reporting
- Auto-flip advancement after nodes with `writeCurrent` temporal edges
- Cross-frame aliasing: temporal slot lifetimes tracked for aliasing strategy

---

## Compiled Graph Introspection

```java
compiled.passes()      // List<PassInfo> — name, type, bucket, queue, reads, writes
compiled.resources()   // List<ResourceInfo> — name, transient/imported, lifetime
compiled.edges()       // List<EdgeInfo> — from, to, resource, crossQueue
compiled.exportDot()   // Graphviz DOT format
compiled.exportJson()  // JSON format
```

---

## Thread Safety

- `volatile boolean executing` flag prevents concurrent `execute`/`executeInto` calls
- Single-writer model: graph structure is immutable once constructed
- `IllegalStateException` thrown on concurrent access

---

## Temporal Resize

```java
TemporalResource.builder()
    .resizeStrategy(TemporalResizeStrategy.clear())       // reset history
    .resizeStrategy(TemporalResizeStrategy.scale(filter)) // blit old to new
    .resizeStrategy(TemporalResizeStrategy.lazyShrink(5)) // gradual transition
```
Applied during `RenderGraph.resize()` to each temporal resource.

---

## Validation

The compiler detects at build time:
- Orphan reads (resource with no producer and not imported)
- Write-after-write hazards (multiple writers with no intervening read)
- Multiple active writers to the same temporal resource
- Cycles in resource dependencies (with node names listed)
- Temporal edge completeness and starting point validity

All error messages include suggested fixes.

---

## Execution Paths

1. **`executeInto(frameArena, frameIndex, commandBuffer)`** — single command buffer, caller manages submission. Supports PassMask caching, degradation, temporal resolution, barrier emission.
2. **`execute(frameArena, frameIndex, frameFence)`** — multi-queue with per-family command buffers, inter-queue timeline semaphores, parallel secondary recording. Full graph-managed submission.

---

## Future Work

- **Multi-rate region descriptors** — CPU-side spatial tracking for partial-screen updates, region-aware load ops. Deferred as niche optimization.
