# Frame Graph Addendum: Capabilities (Part 4)

Continuation of addendum-capabilities-3.md.

---

## Thread Safety Model

### Principle: Single-Writer, Immutable-Once-Compiled

The graph is NOT internally synchronized for concurrent mutation. Instead, the lifecycle has clear phases with simple rules:

```
Phase 1: CONSTRUCTION (single thread)
  - Add/remove passes, resources, edges
  - Modify activation predicates
  - No compilation or execution happening

Phase 2: COMPILATION (single thread, graph locked)
  - Graph is read-only during compilation
  - Produces an immutable CompiledGraph
  - No modifications allowed (boolean flag check, throws if violated)

Phase 3: EXECUTION (CompiledGraph is immutable, safe to use from any thread)
  - Execute the compiled graph on GPU
  - Meanwhile, the graph definition can be modified for next frame's compilation
  - CompiledGraph holds no mutable references back to the graph definition
```

### Concurrent Safety Checks

```java
public class FrameGraph {
    private volatile boolean compiling = false;
    
    public void addRenderPass(...) {
        if (compiling) throw new IllegalStateException(
            "Cannot modify graph during compilation. " +
            "Modifications are allowed after compile() returns.");
        // ...
    }
    
    public CompiledGraph compile(PassMask mask) {
        compiling = true;
        try {
            // ... produce immutable CompiledGraph ...
            return compiledGraph;
        } finally {
            compiling = false;
        }
    }
}
```

### Execute While Compiling Next Frame

This is safe without cloning because:
- `CompiledGraph` is a fully independent immutable object
- It contains: execution order, physical resource handles, barrier commands, queue assignments
- It does NOT reference the mutable `FrameGraph` definition
- Modifying the `FrameGraph` for frame N+1 while executing frame N's `CompiledGraph` is safe

```java
// Frame loop (single thread, but illustrates the independence)
CompiledGraph currentFrame = graph.compile(currentMask);

// Execute current frame on GPU (non-blocking submit)
currentFrame.execute(queues, arena);

// Immediately start modifying graph for next frame
shadowGroup.clear();
shadowGroup.addRenderPass(...);  // safe - currentFrame doesn't reference graph definition

// Compile next frame (can overlap with GPU execution of current frame)
CompiledGraph nextFrame = graph.compile(nextMask);

// Wait for current frame's GPU work to finish
currentFrame.awaitCompletion();

// Now execute next frame
nextFrame.execute(queues, arena);
```

### What IS Thread-Safe

- Reading a `CompiledGraph` (introspection, profiling queries) from any thread
- Executing a `CompiledGraph` (it's immutable)
- Calling `resourceAge()` and other read-only queries on the graph (atomic counters)

### What Is NOT Thread-Safe

- Concurrent `addPass` / `removePass` / `addDependency` calls (single-writer only)
- Calling `compile()` from multiple threads simultaneously
- Modifying the graph while `compile()` is in progress

### No Cloning Needed

The key insight: `compile()` reads the graph definition and produces a fully independent
`CompiledGraph` object. The `CompiledGraph` contains copies of everything it needs
(pass order, barrier specs, resource binding info) — it does NOT hold references back
to the mutable `FrameGraph`. So after `compile()` returns:

- The `CompiledGraph` is frozen and self-contained
- The `FrameGraph` can be freely mutated for the next frame
- No deep copy of the graph structure is needed
- No reference counting or copy-on-write
- The compiled graph is naturally independent by construction

This is not a complex concurrency scheme — it's just good separation between
"mutable definition" and "immutable execution plan." Design it this way from the start
and the thread safety question becomes trivial.

---

## Determinism

### Guarantee

Given identical inputs (same graph structure, same pass mask, same resource descriptors), the graph produces identical output (same execution order, same barrier placement, same aliasing decisions, same queue assignments).

### How It's Achieved

1. **Topological sort** — uses stable tie-breaking by pass insertion order. When multiple passes have no dependency between them, they execute in the order they were added to the graph.

2. **Aliasing** — uses a deterministic greedy algorithm. Resources are considered for aliasing in a fixed order (by first-use topological index, then by name for ties). The first compatible non-overlapping candidate wins.

3. **Queue assignment** — when multiple queues are equally valid, the pass goes to the queue with the lowest index (deterministic tie-break).

4. **Pass group ordering** — passes within a dynamic PassGroup execute in the order they were added to the group that submission.

5. **No randomness** — no random number generators, no hash-map iteration order dependencies, no system-time-dependent decisions.

### Replay Support

Because the graph is deterministic, you can:
- Record a sequence of PassMasks and replay them for debugging
- Compare two runs and get identical barrier/aliasing output
- Serialize a CompiledGraph's decisions for offline analysis

```java
// Record decisions for debugging
compiled.enableDecisionLog(true);
compiled.execute(queues, arena);
DecisionLog log = compiled.getDecisionLog();
// log contains: every barrier inserted, every aliasing decision, every queue assignment
// with reasoning (which dependency caused this barrier, which lifetime overlap prevented aliasing)
```

---

## Implementation Priority for Addendum Items

Ordered by dependency and importance to core functionality:

1. **External resources** — needed immediately (swapchain is external)
2. **Queue selection policy** — needed for multi-queue scheduling
3. **Manual dependency edges** — simple, enables important use cases
4. **Dynamic pass count (PassGroup)** — critical for real-world usage
5. **Symbolic graph / introspection** — valuable for debugging during development
6. **GPU readback** — needed for convergence-driven iteration
7. **Optional edges and fallback inputs** — unifies with starting point resolution
8. **Staleness tracking** — builds on temporal resource infrastructure
9. **Resource resize + strategies** — needed for window resize
10. **Subgraph composition** — convenience, not blocking
11. **Error recovery** — robustness, can start with simple abort
12. **Priority / degradation** — optimization, not blocking
13. **Thread safety checks** — simple boolean flags, add early
14. **Determinism** — design for it from the start, verify with tests

## Test Additions for Addendum Capabilities

### External Resources

1. Import swapchain image, verify initial/final layout transitions inserted
2. Import buffer with existing access state, verify correct barrier at first use
3. Missing bind before execute -> runtime error with clear message

### Dynamic Pass Count

4. PassGroup with 0 passes -> no-op, downstream passes get no input
5. PassGroup count changes between submissions -> recompile triggered
6. PassGroup count same as previous -> cache hit
7. PassGroup exceeds maxCount -> recompile + warning

### Manual Dependencies

8. Manual edge creates ordering without barrier
9. Manual edge across queues inserts semaphore
10. Remove manual edge -> recompile, ordering may change
11. Circular manual dependency -> compile error

### Readback

12. Readback available one frame after write
13. ON_DEMAND readback only copies when requested
14. Partial readback (region) copies only specified bytes
15. Readback of temporal resource reads correct slot

### Optional Edges / Fallback

16. Optional input with inactive writer -> fallback value used
17. Optional input with active writer -> normal resource used
18. Temporal read with fallback overrides resource initialState
19. Staleness limit exceeded -> fallback triggered

### Resize

20. Resource resize triggers incremental recompile (topology unchanged)
21. Temporal resize with scale strategy -> history scaled to new dimensions
22. Temporal resize with clear strategy -> history reset to initialState
23. Resize during multi-rate -> only active passes see new dimensions immediately

### Introspection

24. Exported DOT graph has correct node/edge count
25. Pass timing queries return non-zero values after profiled execution
26. Aliasing stats report correct memory savings

### Thread Safety

27. Modify graph during compile -> IllegalStateException
28. Execute CompiledGraph while modifying graph definition -> no error
29. Two concurrent compile() calls -> IllegalStateException on second

### Determinism

30. Same graph + same mask compiled twice -> identical CompiledGraph
31. Same graph with passes added in same order -> same execution order across runs
