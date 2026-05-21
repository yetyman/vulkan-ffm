# Frame Graph Addendum: Additional Capabilities

This document covers capabilities identified as gaps in the initial design documents.
Items 1-3 are in this file. Items 4-7 are in `addendum-capabilities-2.md`. Items 8-14 are in `addendum-capabilities-3.md`. Items 15-16 plus implementation priority and tests are in `addendum-capabilities-4.md`.

## Summary

| # | Topic | One-Line Scope | File |
|---|-------|---------------|------|
| 1 | Graph Instance Model | One graph per device; no multi-graph needed on single device | this |
| 2 | Subgraph Composition | Reusable pass templates stamped N times with parameters | this |
| 3 | Dynamic Pass Count | PassGroups for runtime-variable pass counts (N shadows, N materials) | this |
| 4 | Resource Resize | Incremental recompile on resize; strategy pattern for temporal history invalidation | part 2 |
| 5 | GPU Readback | Edge-based GPU-to-CPU data staging with latency-aware API | part 2 |
| 6 | External Resources | Importing resources not owned by the graph (swapchain, pre-existing buffers) | part 2 |
| 7 | Queue Selection Policy | Queue preference is a hint; fallback rules when preferred unavailable | part 2 |
| 8 | Manual Dependency Edges | Ordering constraints without resource involvement; removable edge handles | part 3 |
| 9 | Error Recovery | Configurable per-pass failure strategy (abort, skip, retry, backoff) | part 3 |
| 10 | Symbolic Graph / Introspection | Queryable compiled graph for visualization, profiling, DOT/JSON export | part 3 |
| 11 | Staleness Tracking | Age-since-last-write for persistent/temporal resources; staleness limits | part 3 |
| 12 | Priority / Degradation | Compile-time pass priority; budget-aware graceful pass dropping | part 3 |
| 13 | Optional Edges / Fallback Inputs | Edges with fallback values when source unavailable; unifies with initialState | part 3 |
| 14 | Multiple Writers | Mutually exclusive writers validated; future note for sequential multi-write | part 3 |
| 15 | Thread Safety Model | Single-writer construction; CompiledGraph is independent immutable snapshot | part 4 |
| 16 | Determinism | Stable topological sort, deterministic aliasing, no randomness | part 4 |

---

## Graph Instance Model

**One graph per device, always.**

A single graph on a single device sees all work and optimizes globally. Independent workloads (e.g. background video decode) that share zero resources with the render are simply passes with no edges to other passes — the compiler schedules them independently because there are no dependencies.

Multiple graphs only exist when multiple physical devices are involved (e.g. multi-GPU side-by-side comparison). Cross-device resource sharing is out of scope for initial implementation.

```java
// One graph per device
FrameGraph graph = FrameGraph.builder()
    .device(device)
    .queues(graphicsQueue, computeQueue, transferQueue)
    .build();

// Independent workloads are just unconnected subgraphs within the same graph
// The compiler sees no edges between them and schedules them independently
graph.addComputePass("video_decode")...;  // no edges to render passes
graph.addRenderPass("main_render")...;    // no edges to video decode
// These naturally parallelize across queues
```

---

## Subgraph Composition

Reusable subgraph templates that can be stamped multiple times with different parameters. No recursion.

### Declaration

```java
// Define a reusable subgraph template
SubgraphTemplate shadowCascade = SubgraphTemplate.define("shadow_cascade")
    .parameter("lightMatrix", float[].class)
    .parameter("cascadeIndex", int.class)
    .parameter("resolution", int.class)
    .input("scene_geometry")          // declared input slot
    .output("shadow_map")             // declared output slot
    .body((params, builder) -> {
        GraphResource depthTarget = builder.createTransientResource("cascade_depth_" + params.get("cascadeIndex"))
            .format(VK_FORMAT_D32_SFLOAT)
            .size(params.get("resolution"), params.get("resolution"))
            .build();
        
        builder.addRenderPass("shadow_render_" + params.get("cascadeIndex"))
            .reads(builder.input("scene_geometry"))
            .writes(depthTarget)
            .record((cmd, arena) -> { /* render shadow cascade */ });
        
        builder.mapOutput("shadow_map", depthTarget);
    });
```

### Instantiation

```java
// Stamp the template N times with different parameters
for (int i = 0; i < cascadeCount; i++) {
    SubgraphInstance cascade = graph.instantiate(shadowCascade)
        .param("lightMatrix", cascadeMatrices[i])
        .param("cascadeIndex", i)
        .param("resolution", cascadeResolutions[i])
        .connectInput("scene_geometry", sceneVertexBuffer)
        .build();
    
    // Use the output in downstream passes
    lightingPass.reads(cascade.output("shadow_map"));
}
```

### Rules

- Subgraph templates are defined once, instantiated many times
- Each instantiation produces unique pass names (template appends instance index or parameter-derived suffix)
- No recursion: a subgraph body cannot instantiate itself or any template that transitively references it
- Subgraph inputs/outputs are the connection points to the parent graph
- Internal resources of a subgraph instance are scoped to that instance (no accidental sharing)
- Subgraphs can contain temporal resources (each instance gets its own temporal slots)

---

## Dynamic Pass Count

The graph must handle variable numbers of passes determined at runtime without requiring full graph reconstruction every frame.

### Problem

Static graph declaration assumes you know all passes at build time. But:
- N shadow maps where N depends on visible lights this frame
- N material passes where N depends on active materials
- N draw passes for N chunks in a streaming world

### Solution: Pass Groups

```java
// Declare a dynamic pass group - the graph knows this section has variable membership
PassGroup shadowGroup = graph.addPassGroup("shadow_maps")
    .maxCount(16)  // upper bound for pre-allocation
    .build();

// Each frame, populate the group (clears previous frame's passes)
shadowGroup.clear();
for (int i = 0; i < visibleLightCount; i++) {
    shadowGroup.addRenderPass("shadow_" + i)
        .writes(shadowMaps[i])
        .reads(sceneGeometry)
        .record((cmd, arena) -> { /* render shadow for light i */ });
}

// Downstream passes depend on the group's outputs collectively
graph.addRenderPass("lighting")
    .readsAllOutputsOf(shadowGroup)  // depends on all passes in the group
    .record(...);
```

### Recompilation Behavior

- If the pass count changes between submissions, the graph recompiles for the new count
- Compiled variants are cached by (PassMask + group counts) — so "4 shadow maps" and "4 shadow maps" hit the same cache even if the specific lights differ
- The group's `maxCount` allows pre-allocating barrier slots and command buffer space
- If count exceeds maxCount, the graph recompiles with a larger allocation (and warns)

### Interaction with Subgraphs

Pass groups and subgraph templates compose naturally:

```java
PassGroup shadowGroup = graph.addPassGroup("shadow_maps").maxCount(16).build();

shadowGroup.clear();
for (int i = 0; i < visibleLightCount; i++) {
    shadowGroup.instantiate(shadowCascadeTemplate)
        .param("lightMatrix", lightMatrices[i])
        .param("cascadeIndex", i)
        .connectInput("scene_geometry", sceneGeometry)
        .build();
}
```
