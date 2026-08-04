# Multi-Rate and Partial-Screen Rendering

## Goal

The graph supports passes that execute at different rates, render to partial regions, and share persistent resources across submissions. The application does not manually manage barriers or resource lifetimes for inactive passes or retained regions.

## Core Concepts

### Submission vs Frame

A **submission** is one execution of the graph. It is NOT necessarily one frame. A single displayed frame might be the composite result of multiple submissions (center region at 60Hz, periphery at 30Hz), or one submission might produce multiple frames worth of data.

The graph operates per-submission. The application controls submission rate and pass activation.

### Resource Lifetime Classes

| Class | Behavior | Aliasable | Managed By |
|-------|----------|-----------|------------|
| `TRANSIENT` | Allocated per-submission, freed after last reader | Yes | Graph (automatic) |
| `PERSISTENT` | Survives across submissions, never aliased | No | Application (explicit create/destroy) |
| `TEMPORAL` | Auto-double/triple-buffered, "current"/"previous" semantics | Cross-frame only | Graph (automatic flip) |

### Pass Activation

Each pass has an optional activation predicate evaluated per submission. Inactive passes are completely elided from the compiled graph. Their outputs follow these rules:

- **Transient outputs of inactive passes:** not allocated, downstream readers must not exist (validation error if an active pass reads a transient resource only written by inactive passes)
- **Persistent outputs of inactive passes:** retain their content from the most recent active submission. No barriers inserted. Downstream readers see stale-but-valid data.
- **Temporal outputs of inactive passes:** do NOT advance the flip counter. Downstream temporal reads get the most recent write, which may be from N submissions ago.

### Region Descriptors

Render passes can declare a render region (viewport + scissor) that is a subset of the full render target. This metadata enables:

- Correct `loadOp` selection: `LOAD` for persistent targets with valid prior content in non-written regions
- Partial-resource barriers when hardware supports them
- Validation that multiple passes writing to the same persistent target don't overlap regions

## API Design

### Declaring Resources

```java
// Transient - lives only within this submission, aliasable
GraphResource blurIntermediate = graph.createTransientResource("blur_temp")
    .format(VK_FORMAT_R16G16B16A16_SFLOAT)
    .size(width, height)
    .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
    .build();

// Persistent - survives across submissions, partial updates allowed
GraphResource compositeOutput = graph.createPersistentResource("composite_output")
    .format(VK_FORMAT_B8G8R8A8_SRGB)
    .size(width, height)
    .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
    .build();

// Temporal - auto-double-buffered, cycle support (see temporal-unrolling.md)
TemporalResource history = graph.temporal("taa_history")
    .format(VK_FORMAT_R16G16B16A16_SFLOAT)
    .size(width, height)
    .bufferCount(2)
    .initialState(InitialState.CLEAR)
    .build();
```

### Pass Activation Predicates

```java
// Always active
graph.addRenderPass("center_render")
    .activateWhen(() -> true)
    // ...

// Every other submission
graph.addRenderPass("periphery_left")
    .activateWhen(() -> submissionIndex % 2 == 0)
    // ...

// Conditional on application state
graph.addRenderPass("ssao")
    .activateWhen(() -> settings.ssaoEnabled)
    // ...

// Conditional on performance budget
graph.addRenderPass("high_quality_shadows")
    .activateWhen(() -> lastFrameTimeMs < 12.0)
    // ...
```

### Region Descriptors

```java
graph.addRenderPass("center_render")
    .activateWhen(() -> true)
    .renderRegion(width / 4, 0, width / 2, height)  // center half
    .writes(compositeOutput)
    .reads(sceneData)
    .record((cmd, arena) -> {
        // Set viewport and scissor to match region
        // Draw scene for center region
    });

graph.addRenderPass("periphery_left_top")
    .activateWhen(() -> submissionIndex % 2 == 0)
    .renderRegion(0, 0, width / 4, height / 2)  // left-top quarter
    .writes(compositeOutput)
    .reads(sceneData)
    .record((cmd, arena) -> { /* draw left-top */ });

graph.addRenderPass("periphery_right_bottom")
    .activateWhen(() -> submissionIndex % 2 == 1)
    .renderRegion(3 * width / 4, height / 2, width / 4, height / 2)
    .writes(compositeOutput)
    .reads(sceneData)
    .record((cmd, arena) -> { /* draw right-bottom */ });
```

### Full Multi-Rate Example

```java
// Render center every frame at full rate
// Render periphery regions alternating every other frame
// Present the composite every frame

FrameGraph graph = FrameGraph.builder()
    .queues(graphicsQueue, computeQueue, transferQueue)
    .build();

GraphResource compositeOutput = graph.createPersistentResource("composite")
    .format(VK_FORMAT_B8G8R8A8_SRGB)
    .size(width, height)
    .build();

GraphResource sceneUniforms = graph.createTransientResource("uniforms")
    .bufferSize(uniformSize)
    .usage(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT)
    .build();

// Center: every frame
graph.addRenderPass("center")
    .activateWhen(() -> true)
    .renderRegion(width / 4, 0, width / 2, height)
    .writes(compositeOutput)
    .reads(sceneUniforms)
    .record((cmd, arena) -> { /* full-rate center render */ });

// Left+Top: even frames
graph.addRenderPass("left_top")
    .activateWhen(() -> submission % 2 == 0)
    .renderRegion(0, 0, width / 4, height)
    .writes(compositeOutput)
    .reads(sceneUniforms)
    .record((cmd, arena) -> { /* half-rate left render */ });

// Right+Bottom: odd frames
graph.addRenderPass("right_bottom")
    .activateWhen(() -> submission % 2 == 1)
    .renderRegion(3 * width / 4, 0, width / 4, height)
    .writes(compositeOutput)
    .reads(sceneUniforms)
    .record((cmd, arena) -> { /* half-rate right render */ });

// Present: every frame (reads the composite with mixed-freshness regions)
graph.addRenderPass("present")
    .activateWhen(() -> true)
    .reads(compositeOutput)
    .record((cmd, arena) -> { /* blit to swapchain */ });
```

## Compilation Behavior with Partial Activation

### Per-Submission Compilation

```
Input: Graph G, submission index S, activation predicates
Output: Compiled submission-specific DAG

1. Evaluate all activation predicates with current state -> active pass set (PassMask)
2. For each inactive pass:
   a. If it writes TRANSIENT resources: mark those resources as "not allocated this submission"
   b. If it writes PERSISTENT resources: mark those as "retain previous content"
   c. If it writes TEMPORAL resources: do NOT advance that temporal's flip counter
3. Validate: no active pass reads a transient resource that is only written by inactive passes
4. Build subgraph of active passes only
5. For persistent resources read by active passes that were last written by a now-inactive pass:
   - No barrier needed (resource is already in correct layout from last active write)
   - loadOp = LOAD (preserve existing content)
6. For persistent resources WRITTEN by active passes with a renderRegion:
   - loadOp = LOAD (preserve content outside the written region)
   - Scissor restricts actual writes to declared region
7. Standard dependency resolution, barrier insertion, aliasing on the active subgraph
```

### Compiled Graph Caching

The compiled graph depends on which passes are active. Since many submissions will have the same active set, cache compiled graphs by PassMask:

```java
public class FrameGraph {
    private final Map<PassMask, CompiledGraph> compiledCache = new HashMap<>();
    
    public CompiledGraph compile(PassMask mask) {
        return compiledCache.computeIfAbsent(mask, this::compileForMask);
    }
}
```

Common masks for the multi-rate example:
- `{center, left_top, present}` - even frames
- `{center, right_bottom, present}` - odd frames

Each is compiled once and reused indefinitely (until graph structure changes).

### Cache Invalidation

The cache is invalidated when:
- A pass is added or removed from the graph
- A resource's descriptor changes (resize, format change)
- Queue availability changes

Pass activation predicates changing does NOT invalidate the cache - it just selects a different cached entry.

## Region Tracking for Persistent Resources

### Per-Region Validity

For persistent resources with partial writes, the graph tracks:

```java
public class PersistentResourceState {
    private final GraphResource resource;
    
    // Track which regions have valid content and when they were last written
    private final List<RegionWrite> regionHistory;
    
    // Current image layout (persists across submissions)
    private VkImageLayout currentLayout;
    
    // Last submission that wrote ANY region
    private int lastWriteSubmission;
    
    public record RegionWrite(
        VkRect2D region,
        int submissionIndex,
        VkImageLayout layoutAfterWrite
    ) {}
    
    public boolean hasValidContent(VkRect2D queryRegion) {
        // Returns true if queryRegion is fully covered by previous writes
    }
    
    public VkAttachmentLoadOp loadOpFor(VkRect2D writeRegion) {
        // CLEAR if no prior content exists anywhere
        // LOAD if other regions have valid content that must be preserved
    }
}
```

### Barrier Optimization for Partial Writes

When a pass writes to a region of a persistent resource:

1. If the resource was last in `SHADER_READ_ONLY_OPTIMAL` (from a reader):
   - Transition to `COLOR_ATTACHMENT_OPTIMAL` for the write
   - This is a full-image transition (Vulkan doesn't support per-region layout transitions for color attachments)
   
2. If the resource is already in `COLOR_ATTACHMENT_OPTIMAL` (from a previous partial write this submission):
   - No transition needed
   - Just ensure write-after-write ordering if regions overlap (they shouldn't per validation)

3. After all writes complete and before readers:
   - Transition to `SHADER_READ_ONLY_OPTIMAL`
   - Single barrier covers all written regions

### Validation Rules for Regions

1. Two active passes in the same submission MUST NOT write overlapping regions of the same persistent resource (data race)
2. A pass that writes a persistent resource with a region MUST use `loadOp = LOAD` unless the resource has never been written (first submission)
3. Region dimensions must be within the resource's dimensions
4. Regions should be aligned to tile boundaries for optimal performance (warning if not)

## Integration with Temporal Resources

Temporal resources and multi-rate interact when a temporal resource is written by a conditionally-active pass:

### Scenario: TAA history written every frame, but GI accumulator written every 4th frame

```java
TemporalResource taaHistory = graph.temporal("taa_history").bufferCount(2).build();
TemporalResource giAccum = graph.temporal("gi_accumulator").bufferCount(2).build();

graph.addComputePass("gi_trace")
    .activateWhen(() -> submission % 4 == 0)  // every 4th frame
    .writesTemporalCurrent(giAccum)
    .record(...);

graph.addRenderPass("lighting")
    .activateWhen(() -> true)  // every frame
    .readsTemporalPrevious(giAccum)  // reads most recent GI, even if 3 frames old
    .writes(litColor)
    .record(...);

graph.addRenderPass("taa")
    .activateWhen(() -> true)
    .reads(litColor)
    .readsTemporalPrevious(taaHistory)
    .writesTemporalCurrent(taaHistory)
    .writes(output)
    .record(...);
```

**Behavior:**
- `taaHistory` flips every submission (written every frame)
- `giAccum` flips only on submissions where `gi_trace` is active (every 4th)
- `lighting` always reads the most recently written GI slot, which may be 0-3 submissions old
- The graph tracks per-temporal-resource flip counters independently

### Flip Counter Logic

```java
public class TemporalResource {
    private int writeCount = 0;  // incremented only when actually written
    
    public void onWriteExecuted() {
        writeCount++;
    }
    
    public PhysicalResource currentWriteSlot() {
        return physicalSlots[writeCount % bufferCount];
    }
    
    public PhysicalResource previousReadSlot() {
        // Most recent write, which may be from a previous submission
        return physicalSlots[(writeCount - 1 + bufferCount) % bufferCount];
    }
}
```

## Implementation Steps (Ordered)

1. **ResourceLifetime enum** - TRANSIENT, PERSISTENT, TEMPORAL
2. **PersistentResource class** - survives across submissions, tracks region validity, current layout
3. **Pass activation predicate API** - `activateWhen(BooleanSupplier)` on pass builder
4. **PassMask** - bitset of active passes, hashable for cache key
5. **Region descriptor on render passes** - `renderRegion(x, y, w, h)`, validation against resource dimensions
6. **Compiler pass elision** - skip inactive passes, handle their outputs per lifetime class
7. **Persistent resource loadOp logic** - LOAD vs CLEAR based on region validity state
8. **Compiled graph caching** - cache by PassMask, invalidation on structural changes
9. **Region overlap validation** - error if two active passes write overlapping regions of same persistent resource
10. **Temporal + activation interaction** - independent flip counters, "most recent write" tracking
11. **Integration with temporal unrolling** - temporal resources respect activation (don't flip when writer is inactive)

## Test Plan

### Unit Tests

1. **Pass activation basic** - 3 passes, middle one inactive:
   - Verify only 2 passes in compiled graph
   - Verify barriers skip the inactive pass
   - Verify transient resource of inactive pass is not allocated

2. **PassMask caching** - alternate between two masks over 100 submissions:
   - Verify compilation happens only twice (once per unique mask)
   - Verify correct compiled graph selected each submission

3. **Persistent resource retention** - pass writes persistent resource, then becomes inactive:
   - Verify resource retains content (no clear, no deallocation)
   - Verify downstream readers see the stale-but-valid data
   - Verify no barriers inserted for the retained resource when nothing touches it

4. **Region non-overlap validation** - two active passes with overlapping regions on same resource:
   - Verify validation error at compile time

5. **Region loadOp selection**:
   - First-ever write to persistent resource: loadOp = CLEAR
   - Subsequent partial write with other valid regions: loadOp = LOAD
   - Full-resource write (no region specified): loadOp = CLEAR or DONT_CARE

6. **Multi-rate alternation** - center every frame, left/right alternating:
   - Run 10 submissions
   - Verify center pass executes every submission
   - Verify left executes on even, right on odd
   - Verify composite output has valid content in all regions after submission 1

7. **Temporal + activation** - temporal resource written every 4th frame, read every frame:
   - Verify flip counter advances only on write frames
   - Verify reads always get most recent write (not stale slot)
   - Verify no validation errors about reading unwritten temporal data

### Integration Tests

8. **Full multi-rate pipeline** - the center/periphery example from API Design section:
   - Run 60 submissions
   - Verify no validation layer errors
   - Verify composite output is presentable every submission
   - Verify GPU timing shows reduced work on periphery-inactive submissions

9. **Dynamic activation change** - pass starts active, becomes inactive mid-run, then reactivates:
   - Verify smooth transition with no resource corruption
   - Verify cache hit when returning to previously-seen mask

10. **Activation + temporal + aliasing combined** - stress test:
    - 5 temporal resources with different activation rates
    - 10 transient resources
    - 3 persistent resources with partial regions
    - Verify correct aliasing decisions
    - Verify no memory corruption over 200 submissions
