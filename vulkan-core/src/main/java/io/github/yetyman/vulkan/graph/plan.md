# Render Graph Design Plan

## Core Philosophy

The graph is a compiler. Setup phase declares intent, compile phase derives optimal execution,
execute phase records commands. The graph never guesses -- every optimization decision is driven
by declared information or measured feedback. Handler sets replace all branching.

---

## Difficulty Assessment

Hardest parts in order:

1. Barrier synthesis -- getting split barriers exactly right across queue ownership transfers,
   feedback edges, and bindless resources simultaneously. The src/dst mask combinations are
   numerous and the failure modes are silent GPU corruption.

   Approach: enumerate every meaningful (srcAccess, dstAccess, srcStage, dstStage) combination
   explicitly. Large swaths of the matrix are identical (e.g. all shader-read -> shader-read
   transitions on the same queue need no barrier at all) and can be handled by a small number
   of clean general rules. The edge cases -- depth attachment needing both EARLY and LATE
   fragment test stages, ownership transfers requiring a release+acquire pair, split barriers
   needing the begin half emitted at write time and the end half at read time -- are enumerated
   individually and handled explicitly. No algorithmic shortcutting on the edge cases.

2. Memory aliasing -- lifetime intervals interact with queue assignments in non-obvious ways.
   A resource "alive" on the compute queue may overlap with one "dead" on the graphics queue
   depending on semaphore positions, not just pass order.

   Each resource version has its own lifetime interval [firstWritePass, lastReadPass] in
   execution order. Two versions can share physical memory only if their intervals do not
   overlap. With multiple queues, "execution order" is a partial order derived from the
   semaphore graph, not a linear sequence. Two resources on different queues with no
   semaphore relationship between them have incomparable lifetimes and cannot be safely aliased
   even if their pass indices don't numerically overlap. The aliaser must build the partial
   order from semaphore edges and reason about overlap within it. The physical allocation for
   an aliasing group lives for the union of all member lifetimes; only one member is live at
   any point in the partial order.

3. Feedback edge ring management -- the graph must correctly version resources across frame
   boundaries while frames-in-flight means multiple versions are simultaneously live. Getting
   the semaphore chain right across 3 frames in flight with feedback resources is the most
   stateful thing in the whole system.

   Per-resource buffering depth is individually configurable rather than a global policy:
     .persistent(taaHistory,    framesBack: 1)  // double -- read N-1, write N
     .persistent(particleState, framesBack: 2)  // triple -- can read N-2
     .persistent(debugOverlay,  framesBack: 0)  // single -- survives frames, no history
   The graph maintains framesBack+1 physical copies and indexes by frameGeneration % (N+1).
   The semaphore chain depth scales with framesBack -- a resource with framesBack: 2 needs a
   3-deep timeline semaphore to ensure the oldest copy is not written before the GPU finishes
   reading it.

4. Adaptive scheduling convergence -- gradient descent style weight adjustment over scheduling
   decisions (queue assignment, early/late positioning, subpass merge candidates). The
   "gradient" is approximated from the delta between predicted cost (weight model) and measured
   cost (GPU timestamps). Needs a momentum term to prevent oscillation on noisy frames and a
   sliding window learning rate that decays as the schedule stabilizes but never reaches zero,
   since scene content changes make this a non-stationary target. A single slow frame must not
   cause a cascade of rescheduling.

---

## Package Structure

```
io.github.yetyman.vulkan.graph/
    RenderGraph.java                    -- top-level builder + compile + execute
    RenderGraphCompiler.java            -- compilation pipeline, pluggable stages
    RenderGraphScheduler.java           -- topological sort + queue assignment
    RenderGraphBarrierEmitter.java      -- barrier synthesis
    RenderGraphAliaser.java             -- memory aliasing
    RenderGraphStats.java               -- per-frame timing, memory, stall data
    RenderGraphFeedback.java            -- stats from N fed into N+1 setup

    nodes/
        RenderNode.java                 -- base interface all nodes implement
        GraphicsPassNode.java           -- rasterization pass
        ComputePassNode.java            -- compute dispatch
        CpuWorkNode.java                -- synchronous CPU work item
        TransferNode.java               -- explicit buffer/image copy
        PresentNode.java                -- swapchain present sink

    resources/
        GraphResource.java              -- base interface: handle, state, lifetime
        GraphBufferResource.java        -- wraps ManagedBuffer
        GraphImageResource.java         -- wraps VkImage wrapper
        GraphResourceVersion.java       -- versioned resource handle (for feedback edges)
        ExternalResource.java           -- imported with declared initial state + semaphore
        ResourceLifetime.java           -- first-write to last-read interval
        ResourceAlias.java              -- aliasing group with non-overlapping lifetimes

    edges/
        ResourceEdge.java               -- directed read/write dependency between nodes
        SemaphoreEdge.java              -- external semaphore dependency (CpuData, streaming)
        FeedbackEdge.java               -- cross-frame versioned dependency

    scheduling/
        ScheduleHint.java               -- EARLY, LATE, CRITICAL_PATH
        QueueAssignment.java            -- which physical queue a node runs on
        ExecutionBucket.java            -- group of nodes that can run in parallel
        SchedulingStrategy.java         -- interface: assign(nodes, queues) -> List<ExecutionBucket>
        ListSchedulingStrategy.java     -- default greedy critical-path heuristic
        AdaptiveSchedulingStrategy.java -- feedback-driven, adjusts weights each frame

    barriers/
        BarrierStrategy.java            -- interface: emit(before, after, resource) -> barrier set
        SplitBarrierStrategy.java       -- default: begin at write, end at read
        ConservativeBarrierStrategy.java -- fallback for bindless/unknown access
        BarrierBatch.java               -- accumulated barriers for a transition point

    memory/
        AliasingStrategy.java           -- interface: alias(resources, lifetimes) -> groups
        LifetimeAliasingStrategy.java   -- default: non-overlapping lifetime bins
        NullAliasingStrategy.java       -- no aliasing, for debugging

    feedback/
        FrameStats.java                 -- GPU timestamps, memory usage, stall points
        FeedbackHandler.java            -- interface: onStats(FrameStats) -> void
        AdaptiveFeedbackHandler.java    -- adjusts scheduling weights from stats
```

---

## Resource Model

### GraphResource (base interface)

```java
public interface GraphResource {
    String name();
    MemorySegment handle();
    int lastAccessMask();
    int lastStageMask();
    int owningQueueFamily();
    void updateState(int accessMask, int stageMask, int queueFamily);
    boolean isTransient();
    boolean isImported();
    ResourceLifetime lifetime();
}
```

### GraphBufferResource

Thin wrapper over ManagedBuffer. Delegates state to the buffer's own fields. The graph calls
updateState after each pass, which writes through to the underlying ManagedBuffer.

### GraphImageResource

```java
public interface GraphImageResource extends GraphResource {
    VkFormat format();
    VkImageLayout currentLayout();
    int width();
    int height();
    int layers();
    int mipLevels();
    int sampleCount();
    void updateLayout(VkImageLayout layout);
}
```

The concrete VkImage wrapper owns currentLayout + access state. VkImageView wraps a VkImage
reference and delegates state queries to it.

### GraphResourceVersion

For feedback edges (TAA history, simulation ping-pong):

```java
public class GraphResourceVersion {
    private final GraphResource resource;
    private final int frameOffset;  // 0 = current, -1 = previous, -2 = two frames ago
    private final int version;      // monotonic write counter
}
```

The graph manages the ring automatically when a resource is declared with persistent(frames: N).

---

## Node Interface

Every node declares everything the graph needs to know. Shader reflection happens once at
CompiledShader creation time and the results are stored. The node interface exposes that
reflected data -- no per-frame shader scanning.

```java
public interface RenderNode {
    String name();
    NodeType type();

    // Resource declarations -- graph derives all barriers and aliases from these
    List<ResourceEdge> reads();
    List<ResourceEdge> writes();
    List<SemaphoreEdge> externalWaits();
    List<SemaphoreEdge> externalSignals();

    // Shader reflection data -- provided at declaration time, not re-scanned
    // null for non-shader nodes (CpuWorkNode, TransferNode)
    List<ShaderReflection> shaderReflections();

    // Bindless declaration -- if non-empty, conservative barriers applied to listed resources
    List<GraphResource> bindlessReads();

    // Scheduling hints
    ScheduleHint scheduleHint();
    QueueCapability requiredQueue();  // GRAPHICS, COMPUTE, TRANSFER, ANY

    // Execution -- called during record phase with resolved queue and arena
    void execute(ExecutionContext ctx);

    // Stats feedback -- called after frame N completes with N's timing data
    // Node uses this to adjust its own configuration for N+1
    void onStats(NodeStats stats);
}
```

### ExecutionContext

```java
public interface ExecutionContext {
    VkCommandBuffer commandBuffer();
    Arena frameArena();
    int frameIndex();           // which frame-in-flight slot
    long frameGeneration();     // monotonic frame counter
    QueueAssignment queue();
    FrameStats previousStats(); // frame N-1's stats for inline adaptation
}
```

---

## Pass Node Builders

### GraphicsPassNode

```java
GraphicsPassNode.builder()
    .name("lighting")
    .reads(gbufferAlbedo, READ_SHADER_FRAGMENT)
    .reads(gbufferNormal, READ_SHADER_FRAGMENT)
    .reads(shadowMap, READ_SHADER_FRAGMENT)
    .writes(hdrColor, WRITE_COLOR_ATTACHMENT)
    .writes(depthBuffer, WRITE_DEPTH_ATTACHMENT)
    .shader(compiledVert)           // reflection data extracted automatically
    .shader(compiledFrag)
    .bindlessReads(materialTextures) // conservative barrier on this heap
    .scheduleHint(ScheduleHint.CRITICAL_PATH)
    .execute(ctx -> {
        // record draw calls
    })
    .onStats(stats -> {
        // adapt: switch to half-res if stats.gpuMs("lighting") > budget
    })
    .build()
```

### ComputePassNode

```java
ComputePassNode.builder()
    .name("gol-sim")
    .reads(cellsA, READ_SHADER_COMPUTE)
    .writes(cellsB, WRITE_SHADER_COMPUTE)
    .shader(compiledComp)
    .requiredQueue(QueueCapability.COMPUTE)
    .scheduleHint(ScheduleHint.EARLY)
    .execute(ctx -> {
        computePipeline.bind(ctx.commandBuffer());
        // dispatch
    })
    .build()
```

### CpuWorkNode

Synchronous CPU work with a defined place in the DAG. The graph schedules it between GPU
submissions at the optimal point and emits host barriers around it.

```java
CpuWorkNode.builder()
    .name("skinning-update")
    .reads(boneBuffer)
    .writes(skinnedVertexBuffer)
    .scheduleHint(ScheduleHint.EARLY)
    .does(ctx -> {
        // CPU work -- graph emits HOST_WRITE barrier after this completes
    })
    .build()
```

### ExternalResource (async CPU data / streaming)

Root node with an external semaphore. The graph treats it identically to a streamed mesh upload.
The readyWhen semaphore becomes a queue wait on whichever pass first consumes the resource.

```java
ExternalResource.builder()
    .name("streamed-mesh")
    .produces(meshBuffer)
    .readyWhen(uploadCompleteSemaphore)
    .initialAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
    .initialStageMask(VK_PIPELINE_STAGE_TRANSFER_BIT)
    .initialQueueFamily(transferQueueFamily)
    .build()
```

---

## Graph Builder

```java
RenderGraph graph = RenderGraph.builder()
    .device(device)
    .queues(graphicsQueue, computeQueue, transferQueue)
    .framesInFlight(3)

    // resource declarations
    .transient(hdrColor, ImageDesc.color(width, height, VK_FORMAT_R16G16B16A16_SFLOAT))
    .transient(depthBuffer, ImageDesc.depth(width, height))
    .persistent(cellsA, 1)           // double buffered feedback resource
    .persistent(taaHistory, 1)       // double buffered
    .persistent(particleState, 2)    // triple buffered -- can read N-2
    .persistent(debugOverlay, 0)     // single -- survives frames, no history
    .imported(swapchainImage, currentLayout, acquireSemaphore)

    // nodes -- graph derives ordering from resource edges
    .node(golSimNode)
    .node(lightingNode)
    .node(bloomNode)
    .node(taaNode)
    .node(tonemapNode)
    .node(uiNode)
    .node(PresentNode.of(swapchainImage, renderFinishedSemaphore))

    // pluggable strategies
    .schedulingStrategy(new AdaptiveSchedulingStrategy())
    .barrierStrategy(new SplitBarrierStrategy())
    .aliasingStrategy(new LifetimeAliasingStrategy())

    // feedback
    .onStats(new AdaptiveFeedbackHandler())

    .build()  // compiles immediately
```

---

## Compilation Pipeline

RenderGraphCompiler runs these stages in order. Each stage is a pluggable handler:

```
1.  Validate          -- all resource edges have declared producers, no orphan reads
2.  VersionResources  -- assign versions to feedback/persistent resources, detect cycles
3.  ComputeLifetimes  -- first-write to last-read interval per resource version
4.  CullPasses        -- remove passes with no path to any sink node (present, persistent write)
5.  AssignQueues      -- SchedulingStrategy assigns each node to a queue
6.  TopologicalSort   -- per-queue ordering respecting cross-queue edges
7.  AliasMemory       -- AliasingStrategy bins non-overlapping transient resources
8.  AllocateTransient -- allocate aliased memory heaps
9.  EmitBarriers      -- BarrierStrategy synthesizes all barriers at transition points
10. BuildBuckets      -- group nodes into parallel ExecutionBuckets
11. RecordTimestamps  -- insert GPU timestamp queries around each node
```

Each stage receives the graph IR and returns a modified IR. Stages are individually replaceable.

### Fast Recompile Paths

- Topology unchanged, resources resized: re-run stages 3-10 (skip validation and versioning)
- Topology unchanged, resources same size: re-run stages 4, 9, 10 (cull + barriers + buckets)
- Nothing changed: use cached execution plan entirely

---

## Execution

```java
// per frame
try (Arena frameArena = Arena.ofConfined()) {
    graph.execute(frameArena, frameIndex);
}
```

Internally:
1. Re-evaluate cull pass (feedback from N-1 may have toggled nodes)
2. Select recompile path based on what changed
3. For each ExecutionBucket in parallel: begin command buffers, call node.execute(ctx), end
4. Submit buckets in dependency order with synthesized semaphores between queues
5. Collect GPU timestamps asynchronously
6. On timestamp readback: call node.onStats() and graph-level onStats()

---

## Resize / Recreation

```java
graph.resize(newWidth, newHeight);
```

Re-runs stages 3-10 with new extents. Transient resources are re-described, re-aliased,
re-allocated. Imported resources are re-imported with new handles. Nodes are not re-declared --
their resource edge declarations are extent-independent. CPU-only, microseconds.

---

## VkImage Wrapper (prerequisite)

Must be built before the graph can handle images correctly.

```java
public class VkImage implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment memory;
    private final VkDevice device;
    private final VkFormat format;
    private final int width, height, layers, mipLevels;
    private volatile VkImageLayout currentLayout;
    private volatile int lastAccessMask;
    private volatile int lastStageMask;
    private volatile int owningQueueFamily;

    public static Builder builder() { return new Builder(); }

    // called by graph and TransientCommandBuffer after any state-changing operation
    public void updateState(VkImageLayout layout, int access, int stage, int family) { ... }

    public static class Builder {
        // format, extent, usage, tiling, samples, mips, layers, initialLayout
        public VkImage build(Arena arena) { ... }
    }
}
```

---

## ManagedBuffer State Tracking Addition

Add to ManagedBuffer (and underlying concrete implementations):

```java
private volatile int lastAccessMask = 0;
private volatile int lastStageMask = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
private volatile int owningQueueFamily = VK_QUEUE_FAMILY_IGNORED;
private Consumer<ManagedBuffer> onHostWrite; // set by graph on import, null otherwise

public void updateState(int accessMask, int stageMask, int queueFamily) {
    this.lastAccessMask = accessMask;
    this.lastStageMask = stageMask;
    this.owningQueueFamily = queueFamily;
}

// called by write() and map() -- notifies graph if imported
void notifyHostWrite() {
    writeGeneration++;
    if (onHostWrite != null) onHostWrite.accept(this);
}
```

---

## What Replaces RenderList

RenderList is removed. Its functionality maps directly:

- Conditional passes       -> node.onStats() toggling node active state
- Multi-pass sequences     -> multiple nodes with resource edges
- Explicit ordering        -> topological sort derived from resource edges
- Graphics/compute passes  -> GraphicsPassNode / ComputePassNode

---

## Frame Timeline Integration

The render graph handles one frame of GPU work. The frame timeline (one level above) handles:

- Streaming/loading jobs spanning multiple frames
- Work-ahead scheduling (N+1 CPU work during N's GPU execution)
- Cross-frame dependency tokens

Integration point: the frame timeline produces ExternalResource entries with completion
semaphores. The graph imports these as root nodes. The graph does not schedule the timeline
work -- it only consumes its outputs.

---

## Implementation Order

1.  VkImage + VkImageView wrappers with state tracking
2.  GraphResource interfaces + GraphBufferResource + GraphImageResource
3.  ResourceEdge, SemaphoreEdge, FeedbackEdge
4.  RenderNode interface + ExecutionContext
5.  RenderGraphCompiler stages 1-4 (validate, version, lifetimes, cull) -- testable DAG structure
6.  ListSchedulingStrategy + stages 5-6 (queue assignment, topo sort)
7.  SplitBarrierStrategy + stage 9 (barriers) -- first executable single-queue graph
8.  LifetimeAliasingStrategy + stages 7-8 (aliasing, allocation)
9.  ExecutionBucket parallel submission + stage 10
10. GPU timestamps + FrameStats + onStats feedback loop
11. AdaptiveSchedulingStrategy + AdaptiveFeedbackHandler
12. CpuWorkNode + ExternalResource
13. FeedbackEdge + persistent resource ring management

Each step produces a testable, runnable system. Steps 1-7 produce a correct single-queue graph.
Steps 8-13 add the advanced capabilities.
