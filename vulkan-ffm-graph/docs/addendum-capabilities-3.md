# Frame Graph Addendum: Capabilities (Part 3)

Continuation of addendum-capabilities-2.md.

---

## Manual Dependency Edges

Sometimes ordering is needed that isn't captured by resource dependencies (GPU timer queries, debug markers, external API calls between passes).

### Declaration

```java
// Create an explicit ordering edge with no resource involvement
DependencyEdge timerOrder = graph.addDependency()
    .from(passA)
    .to(passB)
    .reason("GPU timer query must bracket passB")  // for debugging/introspection
    .build();

// The edge is a first-class object that can be removed
timerOrder.remove();

// Or conditionally active (respects pass activation)
DependencyEdge conditionalOrder = graph.addDependency()
    .from(passA)
    .to(passB)
    .activateWhen(() -> profilingEnabled)
    .build();
```

### Behavior

- Manual edges participate in topological sort like resource edges
- They do NOT insert barriers (no resource to transition)
- They DO affect queue assignment (if A and B are on different queues, a semaphore is inserted)
- They are visible in the symbolic graph for introspection
- Removing an edge triggers recompilation if it changes the topological order

### Validation

- Circular manual dependencies are detected and reported as errors (same as non-temporal resource cycles)
- A manual dependency from an inactive pass is ignored (the edge is elided with the pass)

---

## Error Recovery and Failure Strategy

### Problem

What happens when a pass fails at execution time? Shader compilation error, out-of-memory during resource allocation, device lost, etc.

### Failure Strategy (Configurable)

```java
public interface PassFailureStrategy {
    /** Throw immediately, abort the submission */
    static PassFailureStrategy abort() { ... }
    
    /** Skip the failed pass, use fallback data for its outputs, continue */
    static PassFailureStrategy skipWithFallback() { ... }
    
    /** Retry once with reinitialized resources, then abort if still failing */
    static PassFailureStrategy retryOnce() { ... }
    
    /** Retry with validation enabled for diagnostics, then abort */
    static PassFailureStrategy retryWithValidation() { ... }
    
    /** Skip and disable the pass for N future submissions (backoff), then retry */
    static PassFailureStrategy skipAndBackoff(int disableFrames) { ... }
    
    /** Log the failure silently and continue (for truly optional/cosmetic passes) */
    static PassFailureStrategy logAndContinue() { ... }
    
    /** Custom handler */
    static PassFailureStrategy custom(FailureHandler handler) { ... }
}

// Set per-graph default
FrameGraph graph = FrameGraph.builder()
    .device(device)
    .failureStrategy(PassFailureStrategy.retryOnce())
    .build();

// Override per-pass
graph.addRenderPass("optional_effect")
    .failureStrategy(PassFailureStrategy.skipWithFallback())
    .record(...);
```

### Skip-with-Fallback Behavior

When a pass is skipped due to failure:
- Its transient outputs are filled with fallback data (clear color, or copied from a previous successful frame if temporal)
- Its persistent outputs retain their previous content (same as if the pass were inactive)
- Its temporal outputs do NOT advance (same as inactive pass behavior)
- Downstream passes receive the fallback data and continue

This reuses the same machinery as pass activation — a failed pass is treated as if it became inactive mid-submission.

### Retry Behavior

1. First attempt fails
2. Graph reinitializes the failed pass's resources (reallocate, clear)
3. Second attempt with fresh resources
4. If `retryWithValidation`: enable Vulkan validation layers for the retry frame only, capture diagnostics
5. If retry succeeds: continue normally, log warning
6. If retry fails: fall through to abort or skip depending on configuration

### Error Reporting

```java
public record PassFailure(
    String passName,
    FailureType type,          // SHADER_ERROR, OUT_OF_MEMORY, DEVICE_LOST, TIMEOUT, etc.
    String message,
    int submissionIndex,
    boolean recovered          // true if retry/skip succeeded
) {}

// Query after execution
List<PassFailure> failures = compiled.execute(queues, arena);
if (!failures.isEmpty()) {
    for (PassFailure f : failures) {
        logger.warn("Pass '{}' failed: {} (recovered: {})", f.passName(), f.message(), f.recovered());
    }
}
```

---

## Symbolic Graph and Introspection

The compiled graph is a queryable structure exposing all scheduling decisions for visualization and profiling.

### Introspection API

```java
CompiledGraph compiled = graph.compile(mask);

// Query topology
List<PassInfo> passes = compiled.passes();           // in execution order
List<ResourceInfo> resources = compiled.resources(); // all resources with lifetimes
List<EdgeInfo> edges = compiled.edges();             // all dependencies (resource + manual)
List<AliasGroup> aliasGroups = compiled.aliasing();  // which resources share memory

// Per-pass info
public record PassInfo(
    String name,
    PassType type,              // RENDER, COMPUTE, TRANSFER, ITERATIVE
    int executionOrder,         // topological index
    QueueType assignedQueue,    // actual queue assignment (after policy resolution)
    List<String> reads,         // resource names read
    List<String> writes,        // resource names written
    boolean active,             // whether active in current mask
    int estimatedBarrierCount   // barriers inserted before this pass
) {}

// Per-resource info
public record ResourceInfo(
    String name,
    ResourceLifetime lifetime,  // TRANSIENT, PERSISTENT, TEMPORAL, EXTERNAL
    ResourceDescriptor descriptor,
    int firstUseOrder,
    int lastUseOrder,
    String aliasedWith,         // null if not aliased, or name of alias partner
    long memoryOffset           // offset in aliased memory block
) {}

// Per-edge info
public record EdgeInfo(
    String fromPass,
    String toPass,
    String resourceName,        // null for manual dependencies
    EdgeType type,              // RESOURCE_READ, RESOURCE_WRITE, TEMPORAL, MANUAL
    boolean crossQueue          // true if this edge crosses queue families
) {}
```

### Graph Export for Visualization

```java
// Export as DOT format for Graphviz
String dot = compiled.exportDot();

// Export as JSON for custom tooling
String json = compiled.exportJson();

// Export includes:
// - All passes as nodes (colored by type: render=blue, compute=green, transfer=orange)
// - All edges (solid=resource, dashed=manual, dotted=temporal back-edge)
// - Resource lifetime bars
// - Aliasing groups highlighted
// - Queue assignment lanes
// - Barrier insertion points marked
```

### Profiling Hooks

```java
// Enable GPU timestamp queries on all passes
compiled.enableProfiling(true);

// After execution, query timings
for (PassInfo pass : compiled.passes()) {
    PassTiming timing = compiled.getPassTiming(pass.name());
    // timing.gpuStartNs(), timing.gpuEndNs(), timing.gpuDurationNs()
    // timing.barrierWaitNs() (time spent waiting on barriers)
}

// Aggregate stats
GraphStats stats = compiled.stats();
// stats.totalGpuTimeNs()
// stats.totalBarrierCount()
// stats.totalMemoryUsed()
// stats.aliasedMemorySaved()
// stats.queueUtilization(QueueType.GRAPHICS) -> 0.0-1.0
```

---

## Staleness Tracking

For persistent and temporal resources that may not be written every submission, track how old the data is.

### API

```java
// Query staleness of a resource
int age = graph.resourceAge("gi_accumulator");  // submissions since last write

// Pass can query staleness of its inputs
graph.addRenderPass("lighting")
    .reads(giAccumulator)
    .record((cmd, arena, context) -> {
        int giAge = context.inputAge("gi_accumulator");
        // Adjust temporal blend weight based on staleness
        float blendWeight = Math.min(1.0f, giAge * 0.1f);
        pushConstants.set("temporalWeight", blendWeight);
    });
```

### Staleness Limits

```java
// Declare a maximum acceptable staleness
graph.addRenderPass("motion_blur")
    .reads(velocityBuffer)
    .maxStaleness(velocityBuffer, 2)  // error/warning if velocity is >2 submissions old
    .record(...);
```

If staleness exceeds the limit:
- Default: log warning, continue with stale data
- Configurable: force the writer pass to activate (override its activation predicate)
- Configurable: use fallback input instead (see Optional Edges below)

---

## Priority and Graceful Degradation

### Compile-Time Priority

Passes have a priority that determines drop order when the frame budget is exceeded:

```java
graph.addRenderPass("main_geometry")
    .priority(Priority.CRITICAL)    // never dropped
    .record(...);

graph.addRenderPass("ssao")
    .priority(Priority.HIGH)        // dropped only under extreme pressure
    .record(...);

graph.addComputePass("gi_refinement")
    .priority(Priority.MEDIUM)      // dropped when budget is tight
    .record(...);

graph.addRenderPass("volumetric_fog")
    .priority(Priority.LOW)         // first to be dropped
    .record(...);
```

### Priority Levels

```java
public enum Priority {
    CRITICAL,   // never dropped (present, core geometry, depth)
    HIGH,       // dropped only as last resort
    MEDIUM,     // standard optional effects
    LOW,        // first candidates for dropping
    BACKGROUND  // only runs when frame budget has headroom
}
```

### Degradation Strategy

```java
public interface DegradationStrategy {
    /** No degradation - run everything regardless of budget */
    static DegradationStrategy none() { ... }
    
    /** Drop lowest-priority passes until estimated time fits budget */
    static DegradationStrategy dropByPriority(float budgetMs) { ... }
    
    /** Reduce quality tier (e.g. half-res passes, fewer iterations) before dropping */
    static DegradationStrategy reduceQualityFirst(float budgetMs) { ... }
    
    /** Drop by priority but with hysteresis (don't flicker passes on/off at boundary) */
    static DegradationStrategy dropWithHysteresis(float budgetMs, int stableFrames) { ... }
    
    /** Target a specific frame rate, auto-adjust budget */
    static DegradationStrategy targetFrameRate(float targetFps) { ... }
    
    /** Custom logic decides which passes to deactivate */
    static DegradationStrategy custom(DegradationHandler handler) { ... }
}

// Applied at submission time
graph.setDegradationStrategy(DegradationStrategy.dropByPriority(16.0f));
```

### Interaction with Pass Activation

Priority-based dropping is implemented as an additional activation filter:
1. Evaluate user-defined `activateWhen()` predicates → candidate active set
2. If degradation strategy is active: estimate total cost of candidate set
3. If over budget: deactivate lowest-priority passes until within budget
4. Result is the final PassMask

This means priority dropping uses the same machinery as manual activation — the graph doesn't need special handling for "dropped" passes.

---

## Optional Edges and Fallback Inputs

An edge can be marked optional with a fallback value. If the source is unavailable (stale, invalid, writer inactive), the fallback is used instead.

### Declaration

```java
graph.addRenderPass("lighting")
    .reads(giAccumulator)                              // required input
    .readsOptional(ssaoBuffer, fallback(clear(1.0f)))  // optional: white if SSAO disabled
    .readsOptional(ssrBuffer, fallback(clear(0.0f)))   // optional: black if SSR disabled
    .readsTemporalPrevious(history, fallback(InitialState.Clear.BLACK))  // temporal with explicit fallback
    .record(...);
```

### Fallback Sources

```java
public sealed interface Fallback {
    /** Constant clear value */
    record ClearValue(float r, float g, float b, float a) implements Fallback {}
    
    /** Another resource in the graph (must be available when fallback triggers) */
    record AlternateResource(GraphResource alternate) implements Fallback {}
    
    /** The resource's own previous valid content (for persistent resources) */
    record RetainPrevious() implements Fallback {}
    
    /** Application-provided data uploaded before execution */
    record Preloaded(Object data) implements Fallback {}
}
```

### When Fallback Triggers

An optional edge uses its fallback when:
1. The source resource's writer is inactive this submission (pass deactivated)
2. The source resource exceeds its staleness limit
3. The source resource was invalidated (resize with clear strategy)
4. The source resource's writer failed and was skipped

### Unification with Initial State

Starting point resolution (temporal resources on frame 0) is a special case of fallback:
- `initialState` IS the fallback for temporal reads when no previous write exists
- This means `readsTemporalPrevious(history)` with no explicit fallback uses the temporal resource's `initialState` as its fallback
- Explicit fallback on a temporal read overrides the resource's initialState for that specific reader

```java
// These are equivalent:
TemporalResource history = graph.temporal("history")
    .initialState(InitialState.Clear.BLACK)
    .build();
graph.addRenderPass("taa").readsTemporalPrevious(history)...;

// Same as:
TemporalResource history = graph.temporal("history").build();  // no initialState
graph.addRenderPass("taa")
    .readsTemporalPrevious(history, fallback(ClearValue(0,0,0,0)))...;
```

The starting point resolution algorithm checks: does every reachable temporal read have EITHER a resource-level initialState OR a per-edge fallback? If yes, frame 0 is sound.

---

## Multiple Writers to Temporal Resources

### Rule: At Most One Active Writer Per Submission

Multiple passes may declare `writesTemporalCurrent(resource)` on the same temporal resource, but they MUST be mutually exclusive via activation predicates. The graph validates this at compile time.

```java
// Valid: mutually exclusive writers
graph.addRenderPass("taa_high_quality")
    .activateWhen(() -> qualitySetting == HIGH)
    .writesTemporalCurrent(taaHistory)
    .record(...);

graph.addRenderPass("taa_low_quality")
    .activateWhen(() -> qualitySetting != HIGH)
    .writesTemporalCurrent(taaHistory)
    .record(...);
```

### Validation

At compile time, for each PassMask:
- Count active writers to each temporal resource
- If count > 1: compile error listing the conflicting passes and suggesting mutual exclusion

```
FrameGraph compilation error: temporal resource "taa_history" has multiple active writers
in pass mask [taa_high_quality, taa_low_quality, ...]:

  - "taa_high_quality" (active)
  - "taa_low_quality" (active)

These passes must be mutually exclusive. Add activation predicates that prevent
both from being active in the same submission.
```

**NOTE:** This strict mutual-exclusion rule may be relaxed in the future to support
sequential multi-writer patterns (e.g. ordered accumulation from multiple passes into
the same temporal resource within one submission). For now, single-writer-per-submission
is enforced. If relaxed later, the graph would need to validate write ordering and insert
appropriate barriers between sequential writers.

### Sequential Writes (Non-Temporal)

For non-temporal (persistent/transient) resources, multiple writers ARE allowed if they write to non-overlapping regions (validated by region descriptors) or if they have an explicit ordering dependency between them:

```java
// Valid: sequential writes to same persistent resource with explicit ordering
graph.addRenderPass("clear_pass")
    .writes(compositeOutput)
    .record(...);

graph.addDependency().from("clear_pass").to("draw_pass").build();

graph.addRenderPass("draw_pass")
    .writes(compositeOutput)  // writes after clear, ordered by manual dependency
    .record(...);
```
