# Frame Graph: Unfulfilled Features Plan

This document tracks features described in the design documentation that are not yet fully
implemented. Each section describes the gap, the target behavior, and implementation notes.

---

## 1. Subgraph Composition (Full Template System)

**Status:** Scaffold exists (`SubgraphTemplate.java`, 160 lines). Basic structure is present but
the full parameterized template stamping system is incomplete.

**Gap:**
- Template parameter declaration (typed parameters with `parameter(name, Class)`)
- Template body builder API (`body((params, builder) -> { ... })`)
- Input/output slot declaration and connection (`input()`, `output()`, `connectInput()`, `mapOutput()`)
- Instance-scoped resource naming (auto-suffix to prevent name collisions across stamps)
- Template-internal temporal resources (each instance gets own temporal slots)
- Validation: no recursion (template cannot reference itself transitively)
- Validation: input/output slot completeness at instantiation time

**Target API:**
```java
SubgraphTemplate cascade = SubgraphTemplate.define("shadow_cascade")
    .parameter("lightMatrix", float[].class)
    .parameter("cascadeIndex", int.class)
    .parameter("resolution", int.class)
    .input("scene_geometry")
    .output("shadow_map")
    .body((params, builder) -> {
        GraphResource depth = builder.createTransientResource("cascade_depth_" + params.get("cascadeIndex"))
            .format(VK_FORMAT_D32_SFLOAT)
            .size(params.get("resolution"), params.get("resolution"))
            .build();
        builder.addRenderPass("shadow_render_" + params.get("cascadeIndex"))
            .reads(builder.input("scene_geometry"))
            .writes(depth)
            .record((cmd, arena) -> { /* ... */ });
        builder.mapOutput("shadow_map", depth);
    });

// Stamping:
SubgraphInstance inst = graph.instantiate(cascade)
    .param("lightMatrix", matrices[i])
    .param("cascadeIndex", i)
    .param("resolution", 2048)
    .connectInput("scene_geometry", sceneBuffer)
    .build();
lightingPass.reads(inst.output("shadow_map"));
```

**Implementation Steps:**
1. Add typed parameter map to `SubgraphTemplate`
2. Create `SubgraphBodyBuilder` that wraps graph resource/pass creation with auto-naming
3. Add input/output slot registry with connection validation
4. Add `graph.instantiate(template)` factory returning `SubgraphInstance`
5. Wire `SubgraphInstance.output(name)` into downstream pass reads
6. Add recursion detection (maintain a "currently expanding" stack)
7. Integration with `PassGroup` for dynamic stamp count

---

## 2. Dynamic Pass Groups (Full Implementation)

**Status:** `PassGroup.java` exists (71 lines) with basic count-change tracking. Missing the
full runtime population API described in the docs.

**Gap:**
- `PassGroup.clear()` per-frame reset
- `PassGroup.addRenderPass(name)` / `addComputePass(name)` with full builder API
- `graph.addRenderPass(...).readsAllOutputsOf(group)` collective dependency
- Integration with subgraph templates: `group.instantiate(template)...build()`
- `maxCount` pre-allocation hint
- Warning when count exceeds maxCount
- Cache key includes group counts (not just PassMask)

**Target API:**
```java
PassGroup shadows = graph.addPassGroup("shadow_maps").maxCount(16).build();

// Per frame:
shadows.clear();
for (int i = 0; i < visibleLightCount; i++) {
    shadows.addRenderPass("shadow_" + i)
        .writes(shadowMaps[i])
        .reads(sceneGeometry)
        .record((cmd, arena) -> { /* ... */ });
}

graph.addRenderPass("lighting").readsAllOutputsOf(shadows).record(...);
```

**Implementation Steps:**
1. Add `clear()` method that removes all current-frame passes from the group
2. Add `addRenderPass`/`addComputePass` that creates nodes owned by the group
3. Add `readsAllOutputsOf(PassGroup)` on pass builder that creates edges to all group outputs
4. Extend compiled graph cache key to include `(PassMask, groupCountsHash)`
5. Add maxCount validation with warning log

---

## 3. Pass Failure Strategy (Execution-Time Recovery)

**Status:** `PassFailureStrategy.java` (74 lines) defines the interface and static factories.
No execution-time integration exists — the executor does not catch pass execution failures
or invoke recovery logic.

**Gap:**
- Executor `try/catch` around `node.execute(ctx)` with failure strategy dispatch
- `skipWithFallback()`: mark pass outputs as "use fallback", do not record commands
- `retryOnce()`: reinitialize pass resources, re-record
- `skipAndBackoff(N)`: disable pass for N future submissions via PassMask override
- `logAndContinue()`: log and continue without retry
- `PassFailure` record returned from execution for caller inspection
- Per-pass failure strategy override (falls back to graph default)
- Fallback data population for skipped passes (clear value or retained content)

**Implementation Steps:**
1. Wrap `node.execute(ctx)` in try/catch in both sequential and parallel recording paths
2. On catch: invoke configured `PassFailureStrategy` to determine action
3. For skip: populate outputs with fallback (reuse OptionalEdge fallback machinery)
4. For retry: reset transient resources for the pass, re-execute once
5. For backoff: set a per-node disable counter checked in PassMask evaluation
6. Collect `PassFailure` records and return from execute/submit
7. Add `failureStrategy(PassFailureStrategy)` to node builders and graph builder

---

## 4. Compiled Graph Introspection (DOT/JSON Export)

**Status:** `CompiledGraph.java` (149 lines) stores active nodes, buckets, lifetimes, aliasing.
`RenderGraphVisualizer` (137 lines) does ASCII visualization. The full DOT/JSON export and
queryable `PassInfo`/`ResourceInfo`/`EdgeInfo` records are not present.

**Gap:**
- `compiled.passes()` returning `List<PassInfo>` with name, type, bucket index, queue, reads, writes, active status, barrier count
- `compiled.resources()` returning `List<ResourceInfo>` with name, lifetime type, descriptor, first/last use, alias info, memory offset
- `compiled.edges()` returning `List<EdgeInfo>` with from/to pass, resource, edge type, cross-queue flag
- `compiled.exportDot()` — Graphviz DOT format with colored nodes by type, solid/dashed/dotted edges
- `compiled.exportJson()` — machine-readable JSON for external tooling
- `compiled.stats()` → `GraphStats` with total GPU time, barrier count, memory used, aliased memory saved, queue utilization

**Implementation Steps:**
1. Define `PassInfo`, `ResourceInfo`, `EdgeInfo` records
2. Add builder logic in `CompiledGraph` constructor to populate these from existing data
3. Implement `exportDot()` with DOT string formatting (nodes with shape/color by type, edges with style)
4. Implement `exportJson()` — can use simple StringBuilder, no external JSON lib needed
5. Add `GraphStats` aggregate record with memory/timing/utilization fields
6. Wire timestamp data from `RenderGraphStats` into `GraphStats` accessor

---

## 5. Temporal Resize Strategy (Execution-Time Logic)

**Status:** `TemporalResizeStrategy.java` (66 lines) defines the interface and static factories.
The actual resize execution path in `RenderGraph` or `TemporalResource` that applies these
strategies does not exist.

**Gap:**
- `RenderGraph.resize(width, height)` method that:
  1. Updates all transient image descriptors to new dimensions
  2. For each temporal resource, applies its configured `TemporalResizeStrategy`
  3. Reallocates physical resources at new dimensions
  4. Executes strategy (clear, scale via blit, lazyShrink countdown)
  5. Triggers incremental recompile (topology unchanged, resource bindings updated)
- `TemporalResource.resize(newWidth, newHeight, strategy, commandBuffer)` internal method
- `scale(filter)` strategy: record a blit from old physical slot to newly allocated slot
- `lazyShrink(N)` strategy: defer actual reallocation for N frames, track countdown
- `clear()` strategy: just reallocate and mark as needing initialState application
- `trim()` / `centerCopy()` strategies: blit with offset/crop

**Implementation Steps:**
1. Add `resize(int w, int h)` to `RenderGraph` that iterates resources
2. Add `resize(int w, int h, VkCommandBuffer)` to `TemporalResource`
3. Implement each strategy's `apply()` method with actual Vulkan blit/clear commands
4. Add incremental recompile path: `compiler.recompileFromLifetimes(...)` already exists
5. Handle transient resource reallocation via `TransientResourceAllocator.reallocate()`
6. Invalidate compiled graph cache on resize (aliasing may change)

---

## 6. Multi-Rate Region Descriptors

**Status:** The docs describe a full region-tracking system for partial-screen rendering.
No `renderRegion()` API exists on pass builders. No `PersistentResourceState` with
region validity tracking.

**Gap:**
- `renderRegion(x, y, w, h)` on `GraphicsPassNode.Builder`
- `PersistentResourceState` class tracking per-region write history
- `loadOpFor(region)` logic: CLEAR if first write, LOAD if other regions have content
- Region overlap validation between active passes writing same persistent resource
- Interaction with compiled graph: region metadata affects loadOp but not topology
- Tile alignment validation (warning if region not aligned to hardware tile size)

**Implementation Steps:**
1. Add `renderRegion(int x, int y, int w, int h)` to `GraphicsPassNode.Builder`
2. Create `PersistentResourceState` with region history list
3. Integrate into executor's auto-rendering: set viewport/scissor to declared region
4. Add loadOp selection logic based on region validity state
5. Add region overlap validation in compiler
6. Store region metadata in `GraphicsPassNode` for executor use

---

## 7. Adaptive Degradation (Budget-Aware Dropping)

**Status:** `DegradationStrategy.java` (64 lines) has the interface with `none()`,
`dropByPriority(budgetMs)`, `targetFrameRate(fps)` factories. The actual implementation
bodies are likely minimal — need to verify they actually compute cost estimates and
drop passes.

**Gap:**
- Cost estimation: use previous frame's `FrameStats` per-node GPU times to estimate
  total GPU cost of the candidate active set
- Drop logic: sort nodes by priority (LOW first), remove until estimated total fits budget
- `reduceQualityFirst(budgetMs)`: quality tier reduction before dropping (e.g. half-res flag on nodes)
- `dropWithHysteresis(budgetMs, stableFrames)`: hysteresis counter to avoid flickering
- Integration: `DegradationStrategy.apply()` is called in `executeInto()` — verify it
  actually returns a filtered node list, not just the input

**Implementation Steps:**
1. Verify/implement `dropByPriority`: sum GPU times from previous stats, remove LOW/MEDIUM nodes
2. Add hysteresis state (per-node counter tracking consecutive over/under budget frames)
3. Implement `targetFrameRate`: derive budget from target fps, delegate to dropByPriority
4. Add quality tier concept: `GraphicsPassNode.qualityTier(int)` with tier-specific resource descriptors
5. Implement `reduceQualityFirst`: reduce tiers before dropping; only drop if min-tier still exceeds budget

---

## 8. Feedback-Driven Adaptive Scheduling

**Status:** `AdaptiveSchedulingStrategy.java` (200 lines) and `AdaptiveFeedbackHandler.java`
(125 lines) exist. Need to verify the feedback loop is fully wired: timestamps collected ->
stats aggregated -> fed back to scheduler for queue assignment decisions.

**Gap (to verify):**
- `AdaptiveSchedulingStrategy` should use per-node GPU cost to decide graphics vs. async compute placement
- Threshold-based offloading: move compute nodes to async compute if graphics queue is saturated
- Verify `onFrameComplete()` in `RenderGraph` feeds `FrameStats` to the feedback handler
- Verify feedback handler output is consumed by scheduler on next compile

**Implementation Steps:**
1. Audit `AdaptiveFeedbackHandler.accept(FrameStats)` — does it compute per-node queue preference?
2. Audit `AdaptiveSchedulingStrategy.schedule()` — does it read feedback handler's recommendations?
3. If not wired: add `feedbackHandler.getRecommendations()` -> `schedulingStrategy.applyHints()`
4. Add configurable threshold: offload to compute only if estimated overlap benefit exceeds N ms
5. Add queue saturation metric: total GPU time assigned to queue / available budget

---

## 9. Full Determinism Guarantees

**Status:** The docs mandate stable topological sort, deterministic aliasing, deterministic
queue assignment. The implementation likely achieves this by using insertion-order data
structures, but there's no explicit verification or decision logging.

**Gap:**
- `DecisionLog` recording: every barrier, aliasing decision, queue assignment with reasoning
- Replay support: serialize `CompiledGraph` decisions for offline comparison
- Test coverage: same graph + same mask compiled twice yields identical `CompiledGraph`
- Verify no `HashMap` iteration-order dependency in scheduling or aliasing paths

**Implementation Steps:**
1. Add `compiled.enableDecisionLog(true)` flag
2. Create `DecisionLog` record class with entries for barrier, alias, and queue decisions
3. Log each decision with reasoning string during compile
4. Add unit test: compile same graph twice, assert `CompiledGraph.equals()`
5. Audit aliasing strategy for iteration-order sensitivity (use `LinkedHashMap` everywhere)

---

## 10. Incremental Recompile (Resize Path)

**Status:** `RenderGraphCompiler.recompileFromLifetimes()` exists (partial recompile skipping
validation/versioning). But the full incremental path where topology is preserved and only
resource bindings update is not exposed at the `RenderGraph` level.

**Gap:**
- `RenderGraph.rebindResources()` or equivalent public API
- Auto-detection: if only resource descriptors changed (size/format), use incremental path
- If topology changed (pass added/removed), fall back to full compile
- Cache invalidation: resize should invalidate aliasing layer but keep topology

**Implementation Steps:**
1. Add `rebindResources()` to `RenderGraph` that calls `compiler.recompileFromLifetimes()`
2. Track a "topologyVersion" counter incremented on add/remove pass
3. On resize: if topologyVersion unchanged, use incremental; else full compile
4. Clear only aliasing-related cache entries, keep PassMask -> topology mappings

---

## 11. External Semaphore Edge Execution

**Status:** `SemaphoreEdge.java` (42 lines) defines the edge type. Verify that the executor
actually wires external semaphore waits/signals into the correct queue's submit info.

**Gap (to verify):**
- External binary semaphore wait before a node's queue submission
- External timeline semaphore wait with value
- External signal after a node's queue submission
- Interaction with multi-queue: semaphore goes on the correct queue's submit
- The `wireExternalSemaphores(compiled)` call exists in executor — verify it does the right thing

**Implementation Steps:**
1. Audit `wireExternalSemaphores()` — confirm it iterates nodes, finds SemaphoreEdge declarations,
   and adds wait/signal entries to the corresponding `QueueSubmission`
2. Add test: node with external semaphore wait -> verify submit info contains the wait
3. Add timeline semaphore support if only binary is implemented

---

## 12. CompiledGraph.exportDot() and exportJson()

**Status:** Not implemented. `RenderGraphVisualizer` only does ASCII.

**Target:**
- DOT: nodes colored by type (blue=graphics, green=compute, orange=transfer, gray=CPU),
  edges styled by type (solid=resource, dashed=manual, dotted=temporal back-edge),
  queue assignment as subgraph clusters
- JSON: full topology, resources, edges, aliasing, timing data in a self-contained document

**Implementation Steps:**
1. Create `DotExporter` utility class
2. Map `NodeType` to DOT color/shape
3. Map edge types to DOT style (solid/dashed/dotted with labels)
4. Group nodes by assigned queue family into DOT subgraphs
5. Add resource lifetime bars as invisible nodes with rank constraints
6. Create `JsonExporter` utility class with StringBuilder-based JSON emission
7. Include all compiled data: passes, resources, edges, aliasing groups, timing if available

---

## 13. Quality-of-Life: Graph Builder Convenience Methods

**Status:** Graph construction requires manual node creation with full builder chains.
The docs show convenience methods that don't appear to all be present.

**Gap:**
- `graph.addRenderPass(name)` shorthand returning a fluent builder
- `graph.addComputePass(name)` shorthand
- `graph.addTransferPass(name)` shorthand
- `graph.createTransientResource(name)` shorthand
- `graph.createPersistentResource(name)` shorthand
- `graph.declareTerminalOutput(resource)` for explicit terminal declaration
- `graph.resourceAge(name)` for staleness query

**Implementation Steps:**
1. Add convenience factory methods to `RenderGraph.Builder` that delegate to node builders
2. Add `declareTerminalOutput()` that marks a resource as terminal (affects starting point validation)
3. Add `resourceAge(name)` that queries temporal resource staleness counter

---

## Priority Order

Based on impact and dependency relationships:

1. **Pass failure strategy execution** (3) — robustness, needed for production use
2. **Temporal resize execution** (5) — needed for window resize to work with temporal resources
3. **Incremental recompile** (10) — needed for resize to be fast
4. **Compiled graph introspection** (4) — critical for debugging during development
5. **DOT/JSON export** (12) — follows from introspection data structures
6. **Dynamic pass groups** (2) — critical for real-world variable-count workloads
7. **Adaptive degradation** (7) — important for production frame budget management
8. **Subgraph composition** (1) — convenience, not blocking
9. **Multi-rate region descriptors** (6) — niche optimization
10. **Feedback-driven scheduling** (8) — optimization, not blocking
11. **Determinism verification** (9) — correctness assurance, add tests early
12. **External semaphore audit** (11) — verify existing code, low effort
13. **Convenience methods** (13) — ergonomics, low priority
