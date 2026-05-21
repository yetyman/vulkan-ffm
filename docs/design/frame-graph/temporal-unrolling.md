# Cyclic Frame Graph with Temporal Unrolling

## Goal

The graph natively supports cycles via temporal edges. Cycles are automatically detected, unrolled, and backed by double/triple-buffered physical resources. No manual "import external resource" hacks. Cross-frame aliasing is supported for temporal physical slots with non-overlapping submission-local lifetimes.

## Core Model

A **temporal edge** is a dependency where a pass reads data written by a *previous* submission of the same graph. This creates a cycle in the logical graph but not in any single submission's execution.

```
Logical graph (has cycle):
  Render --> TAA Resolve --> Output
                ^    |
                |    v
              history (temporal feedback)

Unrolled per-submission (DAG):
  Render --> TAA Resolve --> Output
                ^
                |
           history[prev]  (read from previous submission's write)
              
  TAA Resolve also writes --> history[curr] (for next submission to read)
```

## Temporal Edge Declaration

```java
// Declare a temporal resource - the graph knows this participates in a cycle
TemporalResource history = graph.temporal("taa_history")
    .format(VK_FORMAT_R16G16B16A16_SFLOAT)
    .size(width, height)
    .bufferCount(2)  // double buffer (2 physical allocations)
    .initialState(InitialState.CLEAR)  // frame 0: reads get a cleared resource
    .build();

// Pass declares temporal read (previous frame) and temporal write (this frame)
graph.addRenderPass("taa_resolve")
    .reads(currentColor)
    .readsTemporalPrevious(history)   // <-- this is the back-edge
    .writesTemporalCurrent(history)   // <-- this closes the cycle
    .writes(output)
    .record((cmd, arena) -> { /* TAA resolve shader */ });
```

## Cycle Detection and Validation

During graph construction:

1. Build the full directed graph including temporal edges
2. Identify back-edges: any edge marked `readsTemporalPrevious` to `writesTemporalCurrent` on the same `TemporalResource`
3. **Validate:** every `TemporalResource` that is read must also be written somewhere in the graph (no dangling temporal reads)
4. **Validate:** no non-temporal cycles exist (those are genuine errors - circular dependency within a single submission)
5. **Validate:** temporal resources are not both read-previous and read-current in the same pass (ambiguous)
6. **Validate starting point soundness** - see next section

## Starting Point Resolution

### Problem Statement

On submission 0, every temporal read is a back-edge pointing to "the previous submission's output" — but there is no previous submission. The graph must guarantee at compile time that every pass reachable from any terminal output has all its inputs satisfied on the first submission.

This is NOT simply "every temporal resource must have an initialState." The required set of initial values depends on:
- Which passes are reachable from the terminal outputs
- Which temporal reads those reachable passes depend on (transitively)
- Which passes are active on submission 0 (pass mask for frame 0)
- Whether a temporal resource is reachable at all on frame 0

### Terminal Outputs

The graph may have multiple terminal outputs (multiple display targets, offscreen renders, file outputs, etc.). A terminal output is any resource that is written by the graph but not read by any other pass within the graph — it leaves the graph boundary.

```java
// Terminal outputs are auto-detected: resources written but never read within the graph
// Or explicitly declared:
graph.declareTerminalOutput(swapchainImage);
graph.declareTerminalOutput(offscreenCapture);
```

### Resolution Algorithm

```
Input: Graph G, terminal outputs T[], frame-0 pass mask M0
Output: Set of temporal resources that REQUIRE initial state, 
        OR compilation error with the full set of missing initial states

1. Identify terminal outputs:
   - Explicitly declared terminals
   - Auto-detected: resources written by graph passes but never read by any graph pass

2. Backward reachability from terminals under frame-0 mask:
   reachable = {}
   queue = [all passes that write to any terminal output AND are active in M0]
   while queue is not empty:
       pass P = queue.pop()
       reachable.add(P)
       for each input I of P:
           if I is a normal (non-temporal) resource:
               for each pass W that writes I:
                   if W is active in M0 AND W not in reachable:
                       queue.add(W)
           if I is a temporal read (back-edge):
               record I.temporalResource as "needed on frame 0"
               // Do NOT traverse backwards through the temporal edge -
               // that's the previous frame, which doesn't exist yet.
               // But DO check: does the temporal resource's WRITER 
               // have dependencies that also need initial state?
               // No - the writer runs THIS frame and writes for NEXT frame.
               // The reader needs initial state, not the writer's deps.

3. Collect required initial states:
   requiredInitials = set of all temporal resources marked "needed on frame 0"

4. For each resource R in requiredInitials:
   if R has no initialState defined:
       add to missingSet with diagnostic info

5. If missingSet is non-empty:
   COMPILE ERROR with full diagnostic (see Error Reporting below)
```

### Transitive Temporal Dependencies

Consider a chain: Pass A reads temporal X, and temporal X's writer (Pass B) reads temporal Y. On frame 0:
- Pass A needs temporal X's initial state (X is a back-edge read)
- Pass B writes temporal X for NEXT frame — it runs this frame, so its dependencies matter
- If Pass B reads temporal Y (back-edge), then Y ALSO needs initial state on frame 0

The algorithm handles this because Pass B is reachable from the terminal (it writes X which is read by A which feeds a terminal), and when we process Pass B's inputs, we find temporal Y as another back-edge read.

```
Example:
  Terminal <-- Present <-- Composite <-- TAA(reads history[prev]) <-- Lighting(reads GI[prev])
                                          |                            |
                                          writes history[curr]         writes GI[curr]

Frame 0 requires initial state for BOTH history AND GI, because:
  - Present is reachable (terminal writer)
  - Composite is reachable (Present reads it)
  - TAA is reachable (Composite reads its output) -> history needs initial state
  - Lighting is reachable (TAA reads its output) -> GI needs initial state
```

### Interaction with Pass Activation

The frame-0 pass mask may differ from steady-state. If a pass that reads a temporal resource is INACTIVE on frame 0, that temporal resource does NOT need initial state (it won't be read).

```
Example:
  GI accumulation pass: activateWhen(() -> submission % 4 == 0)  // active on frame 0
  GI refinement pass:   activateWhen(() -> submission % 4 != 0)  // INACTIVE on frame 0

  If GI refinement reads temporal "gi_refined[prev]", that resource does NOT need
  initial state because its reader is inactive on frame 0.
  
  But GI accumulation reads temporal "gi_raw[prev]", so gi_raw DOES need initial state.
```

The compiler evaluates the frame-0 pass mask to determine which temporal reads are actually reachable. This means the required initial state set is mask-dependent.

### Multiple Pass Masks at Startup

If the application uses multi-rate rendering where different pass masks apply to the first few submissions, the compiler must validate ALL of them:

```java
// The graph knows which masks can occur in the first N submissions
// For double-buffered temporal resources, only frame 0 matters
// For triple-buffered, frames 0 and 1 both need checking
// (frame 1 might activate a pass that was inactive on frame 0,
//  reading a temporal resource that was never written)

int framesToValidate = maxBufferCount - 1;  // for double-buffer: 1, triple: 2
for (int f = 0; f < framesToValidate; f++) {
    PassMask mask = evaluateActivationForSubmission(f);
    Set<TemporalResource> required = resolveRequiredInitials(mask, f);
    validate(required);
}
```

For frame 1 with double-buffered resources: the "previous" slot is whatever was written on frame 0. If a pass that writes temporal resource X was INACTIVE on frame 0, then frame 1's read of X[prev] gets... what? The initial state. So initial state must persist until the first actual write occurs.

### Error Reporting

When initial states are missing, the compiler produces a single comprehensive error listing ALL missing pieces, not just the first one found:

```
FrameGraph compilation failed: missing initial state for temporal resources required on frame 0.

The following temporal resources are read on the first submission but have no initialState defined:

  1. "taa_history" (R16G16B16A16_SFLOAT, 1920x1080)
     - Read by pass "taa_resolve" (active on frame 0)
     - Reachable from terminal "swapchain_present"
     - Suggested fix: .initialState(InitialState.CLEAR)
       (zero-cleared is safe for accumulation-style history)

  2. "velocity_buffer" (R16G16_SFLOAT, 1920x1080)
     - Read by pass "motion_blur" (active on frame 0)
     - Reachable from terminal "swapchain_present"  
     - Suggested fix: .initialState(InitialState.CLEAR)
       (zero velocity = no motion blur on first frame)

  3. "gi_accumulator" (R32G32B32A32_SFLOAT, 960x540)
     - Read by pass "gi_denoise" (active on frame 0)
     - Reachable from terminal "swapchain_present" via "lighting" -> "composite"
     - Suggested fix: .initialState(InitialState.CLEAR)
       (black GI on first frame, accumulates over time)

Dependency chain to terminal:
  swapchain_present <- composite <- lighting <- gi_denoise <- [gi_accumulator MISSING]
  swapchain_present <- composite <- motion_blur <- [velocity_buffer MISSING]
  swapchain_present <- taa_resolve <- [taa_history MISSING]
```

### Suggested Initial State Inference

The compiler can suggest appropriate initial states based on resource metadata:

| Resource Usage Pattern | Suggested InitialState | Reasoning |
|------------------------|----------------------|-----------|
| Accumulation (read+write same resource) | CLEAR (zeros) | Zero is identity for addition |
| History buffer (read prev, write curr) | CLEAR (zeros) | No history = no temporal effect |
| Velocity/motion (float2/float3) | CLEAR (zeros) | Zero velocity = static |
| Color buffer (RGBA) | CLEAR (black, alpha=1) | Black is safe default |
| Depth buffer | CLEAR (1.0) | Far plane = nothing rendered |
| Stencil buffer | CLEAR (0) | No stencil bits set |
| Generic/unknown | Cannot infer | User must specify |

The suggestion is based on:
- Resource format (float, int, depth, stencil)
- How it's used (the pass that reads it — is it additive? multiplicative? comparison?)
- Common patterns (anything named "history" or "accumulator" is likely additive)

### InitialState Options

```java
public sealed interface InitialState {
    /** Zero-clear the resource on first read */
    record Clear(float r, float g, float b, float a) implements InitialState {
        public static final Clear BLACK = new Clear(0, 0, 0, 0);
        public static final Clear BLACK_OPAQUE = new Clear(0, 0, 0, 1);
        public static final Clear WHITE = new Clear(1, 1, 1, 1);
    }
    
    /** Clear depth/stencil */
    record ClearDepthStencil(float depth, int stencil) implements InitialState {
        public static final ClearDepthStencil FAR = new ClearDepthStencil(1.0f, 0);
    }
    
    /** Application provides initial data before first submission */
    record Preloaded(/* marker - app must call graph.uploadInitialData(resource, data) */) 
        implements InitialState {}
    
    /** Explicitly undefined - shader handles the "no previous data" case internally,
     *  e.g. via a frame counter uniform that gates temporal reads */
    record Undefined(/* marker - compiler accepts this without warning */) 
        implements InitialState {}
}
```

### Validation Summary

The full validation sequence for temporal resources at compile time:

1. Every temporal resource that is READ must also be WRITTEN somewhere in the graph
2. No non-temporal cycles exist (single-frame circular dependency = error)
3. Temporal resources are not both read-previous and read-current in the same pass
4. **Starting point resolution:**
   a. Identify all terminal outputs (explicit + auto-detected)
   b. For each submission in `[0, maxBufferCount-1]`:
      - Evaluate pass mask for that submission
      - Walk backwards from terminals through active passes
      - Collect all temporal reads reachable from terminals
      - Verify each has initialState defined
   c. If any are missing: compile error with full diagnostic listing ALL missing resources, their dependency chains to terminals, and suggested fixes
5. If `InitialState.Preloaded` is used, verify that `graph.uploadInitialData()` was called before first `execute()` (runtime check, not compile-time)

## Unrolling Algorithm

```
Input: Graph G with temporal edges T
Output: Per-submission DAG D

1. Remove all temporal read edges from G --> G' (now a DAG)
2. For each temporal resource R with bufferCount N:
     physical[0..N-1] = allocate N physical resources matching R's descriptor
     readIndex(submission) = (submission - 1 + N) % N   // previous
     writeIndex(submission) = submission % N             // current
3. For submission S:
     For each pass P that readsTemporalPrevious(R):
       Bind P's temporal read to physical[readIndex(S)]
     For each pass P that writesTemporalCurrent(R):
       Bind P's temporal write to physical[writeIndex(S)]
4. If S == 0 (first submission):
     For each temporal read: bind to initial state resource (cleared/preloaded)
     The "previous" slot is initialized per initialState config
5. Compile G' as normal DAG with physical resource bindings
6. Insert layout transitions for temporal slots between submissions:
     - The "previous" slot transitions from SHADER_WRITE (last submission) to SHADER_READ (this submission)
     - The "current" slot transitions from SHADER_READ (last submission) to SHADER_WRITE (this submission)
```

## TemporalResource Class Design

```java
public class TemporalResource {
    private final String name;
    private final ResourceDescriptor descriptor;  // format, size, usage flags
    private final int bufferCount;                 // 2 = double buffer, 3 = triple
    private final InitialState initialState;       // CLEAR, PRELOADED, UNDEFINED
    
    // Allocated during graph compilation
    private PhysicalResource[] physicalSlots;
    
    // Per-submission state
    private int submissionCounter;
    
    // Lifetime tracking for cross-frame aliasing
    private int firstUseOrder;   // earliest pass index that touches this slot this submission
    private int lastUseOrder;    // latest pass index that touches this slot this submission
    private AccessType accessThisSubmission; // READ_ONLY, WRITE_ONLY, READ_WRITE
    
    public PhysicalResource currentWriteSlot() {
        return physicalSlots[submissionCounter % bufferCount];
    }
    
    public PhysicalResource previousReadSlot() {
        return physicalSlots[(submissionCounter - 1 + bufferCount) % bufferCount];
    }
    
    public void advanceSubmission() {
        submissionCounter++;
    }
}

public enum InitialState {
    CLEAR,       // first-frame reads get a zero-cleared resource
    PRELOADED,   // first-frame reads get application-provided initial data
    UNDEFINED    // first-frame reads are explicitly undefined (shader must handle)
}
```

## Cross-Frame Aliasing

The question: can `temporal_A[prev]` (read-only this submission) share physical memory with `temporal_B[curr]` (write-only this submission) if their usages don't overlap in time within the submission?

### Lifetime Analysis

Each physical resource slot has a **submission-local lifetime** determined by the topological order of passes that reference it:

- `temporal_A[prev]`: alive from first reader to last reader (read-only span)
- `temporal_B[curr]`: alive from first writer to last reader of the written data (write+read span)

If these spans don't overlap in the topologically-sorted execution order, they can alias physical memory.

### Aliasing Rules

1. A temporal slot marked `READ_ONLY` this submission (the "previous" slot) can alias with any resource whose lifetime starts AFTER the last reader of this slot
2. A temporal slot marked `WRITE_ONLY` this submission (the "current" slot being written for next frame) can alias with any resource whose lifetime ends BEFORE the first writer of this slot
3. Temporal slots of the SAME resource never alias with each other (they must coexist for the flip to work)
4. Format and size must be compatible for aliasing (same memory requirements)

### Implementation in GraphCompiler

```java
// During aliasing pass:
List<AliasCandidate> candidates = new ArrayList<>();

// Add transient resources
for (GraphResource r : transientResources) {
    candidates.add(new AliasCandidate(r, r.firstUseOrder, r.lastUseOrder, r.memoryRequirements));
}

// Add temporal physical slots with their submission-local lifetimes
for (TemporalResource t : temporalResources) {
    PhysicalResource prevSlot = t.previousReadSlot();
    candidates.add(new AliasCandidate(prevSlot, t.firstReadOrder, t.lastReadOrder, t.memoryRequirements));
    
    PhysicalResource currSlot = t.currentWriteSlot();
    candidates.add(new AliasCandidate(currSlot, t.firstWriteOrder, t.lastWriteOrder, t.memoryRequirements));
}

// Standard interval-graph coloring for non-overlapping lifetimes
aliasNonOverlapping(candidates);
```

## Iterative Passes (Convergence Loops)

For passes that run until convergence (e.g. iterative light bounces, fluid simulation steps):

### Declaration

```java
graph.addIterativePass("light_bounce")
    .queuePreference(QueueType.COMPUTE)
    .reads(sceneData)
    .readsAndWrites(bounceAccumulator)  // self-dependency: reads previous iteration, writes next
    .maxIterations(16)                   // safety cap
    .continueWhen(() -> !converged())    // predicate checked after each iteration
    .record((cmd, arena, iteration) -> {
        // dispatch bounce computation for iteration N
        // shader reads accumulator, writes to accumulator (ping-pong or atomic add)
    });
```

### Execution Model

At execution time, the graph handles iterative passes as:

1. Allocate ping-pong buffers if the pass reads-and-writes the same resource (or use atomic operations if the shader supports it)
2. Record iteration 0 commands into command buffer
3. Insert barrier: write -> read on the accumulator (COMPUTE_SHADER -> COMPUTE_SHADER)
4. Evaluate predicate (CPU-side readback or pre-determined iteration count)
5. If predicate true: record iteration 1, barrier, check again
6. Repeat until predicate false or maxIterations reached
7. Final state of accumulator is available to downstream passes

### Predicate Evaluation Strategies

| Strategy | Mechanism | Latency |
|----------|-----------|---------|
| Fixed count | `maxIterations` only, no predicate | Zero - known at compile time |
| CPU readback | Read convergence metric from GPU each iteration | High - GPU->CPU sync per iteration |
| Pre-determined | Application sets iteration count before submission | Zero - known at submission time |
| GPU conditional | `vkCmdBeginConditionalRenderingEXT` with GPU-written predicate buffer | Zero CPU latency, GPU evaluates |

For real-time rendering, **pre-determined** or **GPU conditional** are preferred. CPU readback per iteration is only acceptable for offline/non-interactive workloads.

### Ping-Pong for Self-Dependent Iterative Passes

When a pass reads and writes the same resource across iterations:

```java
// Graph compiler detects self-dependency in iterative pass
// Automatically allocates a ping-pong pair
PhysicalResource pingBuffer = allocate(descriptor);
PhysicalResource pongBuffer = allocate(descriptor);

// Iteration 0: read ping, write pong
// Iteration 1: read pong, write ping
// Iteration 2: read ping, write pong
// ...
// Final output is whichever buffer was last written
```

The application doesn't manage this - the graph compiler handles it when it sees `readsAndWrites` on an iterative pass.

## Barrier Insertion for Temporal Resources

Between submissions, temporal resources need layout transitions:

### Submission N ends:
- `history[curr]` was written (layout: COLOR_ATTACHMENT_OPTIMAL or GENERAL)
- `history[prev]` was read (layout: SHADER_READ_ONLY_OPTIMAL)

### Submission N+1 begins:
- `history[curr]` from N becomes `history[prev]` for N+1 (needs: SHADER_READ_ONLY_OPTIMAL)
- `history[prev]` from N becomes `history[curr]` for N+1 (needs: COLOR_ATTACHMENT_OPTIMAL or GENERAL)

### Implementation:
The graph inserts these transitions at the START of each submission, before any pass executes:
```java
// At submission start, for each temporal resource:
if (submissionCounter > 0) {
    // Transition the slot we're about to READ (was written last submission)
    insertBarrier(previousReadSlot, 
        srcStage: LAST_WRITE_STAGE,  // from last submission's writer
        dstStage: FIRST_READ_STAGE,  // this submission's first reader
        oldLayout: WRITE_LAYOUT,
        newLayout: READ_LAYOUT);
}
```

For submission 0, the initial state determines the starting layout (CLEAR implies TRANSFER_DST for the clear op, then transition to read layout).

## Implementation Steps (Ordered)

1. **ResourceDescriptor** - format, dimensions, usage flags, memory requirements
2. **TemporalResource class** - name, descriptor, bufferCount, initialState (sealed interface), physical slot array, submission counter, flip logic
3. **GraphPass temporal edge API** - `readsTemporalPrevious()`, `writesTemporalCurrent()`, `readsAndWrites()` on pass builder
4. **Terminal output declaration** - explicit `declareTerminalOutput()` + auto-detection (written but never read)
5. **Cycle detection in GraphCompiler** - identify back-edges via temporal declarations, validate no non-temporal cycles, validate all temporal resources are complete (both read and written)
6. **Starting point resolution in GraphCompiler** - backward reachability from terminals under frame-0 (and frame-1 for triple-buffer) pass masks, collect all required initial states, comprehensive error with full diagnostic for all missing pieces including dependency chains and suggestions
7. **Unrolling pass in GraphCompiler** - per-submission physical slot binding with index flip, initial state handling for submission 0
8. **Barrier insertion for temporal transitions** - correct layout transitions between submissions at submission boundaries
9. **Cross-frame aliasing in GraphCompiler** - lifetime analysis on physical temporal slots alongside transient resources, interval-graph coloring
10. **Iterative pass support** - loop node declaration, ping-pong detection, predicate evaluation, barrier stamping per iteration, maxIterations safety cap
11. **Integration with PassMask** - temporal resources written by conditionally-active passes track "most recent write submission" for correct read binding

## Test Plan

### Unit Tests

1. **TAA cycle correctness** - declare history temporal resource, verify:
   - Frame 0: temporal read gets cleared resource
   - Frame 1: temporal read gets frame 0's written output
   - Frame 2: temporal read gets frame 1's written output
   - Physical slots alternate correctly (A, B, A, B...)

2. **Triple-buffer rotation** - same as above with bufferCount=3, verify 3-slot rotation (A, B, C, A, B, C...)

3. **Cycle detection validation** - verify:
   - Temporal resource read without matching write -> error
   - Temporal resource write without matching read -> warning (unused temporal output)
   - Non-temporal cycle -> error with clear message identifying the cycle
   - Valid temporal cycle -> accepted, back-edge identified

4. **Starting point resolution - single temporal resource missing initial state:**
   - Graph: Render -> TAA(reads history[prev]) -> Present
   - history has NO initialState defined
   - Verify compile error naming "history" as missing
   - Verify error includes dependency chain: Present <- TAA <- [history MISSING]
   - Verify suggested fix is included (CLEAR for color format)

5. **Starting point resolution - transitive chain:**
   - Graph: Lighting(reads GI[prev]) -> Composite -> TAA(reads history[prev]) -> Present
   - history has initialState, GI does NOT
   - Verify compile error names "GI" (not history)
   - Verify dependency chain traces all the way: Present <- TAA <- Composite <- Lighting <- [GI MISSING]

6. **Starting point resolution - multiple missing, reports ALL:**
   - Graph with 3 temporal resources, 2 missing initialState
   - Verify compile error lists BOTH missing resources in one error
   - Verify each has its own dependency chain and suggestion

7. **Starting point resolution - unreachable temporal resource does NOT require initial state:**
   - Graph: PassA(reads tempX[prev], writes intermediate) -> Present
   - PassB(reads tempY[prev], writes unused_output) -- NOT connected to any terminal
   - tempX has no initialState, tempY has no initialState
   - Verify compile error ONLY mentions tempX (tempY is unreachable from terminals)

8. **Starting point resolution - inactive reader on frame 0 does NOT require initial state:**
   - Graph: PassA(reads tempX[prev], activateWhen frame > 0) -> Present
   - tempX has no initialState
   - Verify NO compile error (PassA is inactive on frame 0, so tempX isn't needed)
   - Verify that compiling with frame-1 mask DOES produce error (PassA active, tempX needed)

9. **Starting point resolution - triple buffer validates frames 0 AND 1:**
   - Temporal resource with bufferCount=3
   - PassA writes it (active every frame)
   - PassB reads it (active every frame)
   - No initialState defined
   - Verify compile error mentions both frame 0 and frame 1 need coverage
     (frame 0: no previous write exists; frame 1: only one previous write exists,
      but triple buffer's [prev] slot for frame 1 points to frame -1 which doesn't exist)

10. **Starting point resolution - Preloaded initial state without upload call:**
    - Temporal resource with InitialState.Preloaded
    - Compile succeeds (compile-time check passes)
    - First execute() without prior uploadInitialData() call -> runtime error

11. **Starting point resolution - Undefined initial state accepted without warning:**
    - Temporal resource with InitialState.Undefined
    - Compile succeeds, no warning
    - Shader is responsible for handling undefined data (e.g. frame counter check)

12. **Starting point resolution - multiple terminals, partial reachability:**
    - Terminal A reachable from tempX reader
    - Terminal B reachable from tempY reader
    - tempX has initialState, tempY does not
    - Verify error mentions tempY with chain to Terminal B (not Terminal A)

13. **Cross-frame aliasing** - two temporal resources with non-overlapping submission-local lifetimes:
   - Resource A: read in passes 1-3 (prev slot)
   - Resource B: written in passes 5-7 (curr slot)
   - Verify they share physical memory
   - Verify no data corruption across 100 submissions

14. **Cross-frame aliasing negative** - overlapping lifetimes must NOT alias:
   - Resource A: read in passes 1-5
   - Resource B: written in passes 3-7
   - Verify separate physical memory allocated

15. **Iterative convergence** - light bounce pass with maxIterations=8:
   - Predicate returns true for 4 iterations then false
   - Verify accumulator contains contributions from all 4 iterations
   - Verify only 4 sets of barriers inserted (not 8)

16. **Iterative ping-pong** - self-dependent iterative pass:
   - Verify ping-pong buffers allocated automatically
   - Verify correct read/write binding alternation per iteration
   - Verify final output is the last-written buffer

17. **Barrier correctness** - temporal resource transitions:
   - Verify layout transition from WRITE to READ at submission boundary
   - Verify no redundant barriers (don't transition a slot that wasn't touched)
   - Verify correct stage masks (last writer stage -> first reader stage)

### Integration Tests

18. **TAA full pipeline** - Render -> TAA Resolve (with history cycle) -> Output:
   - Run 10 submissions
   - Verify each submission's output incorporates previous frame's history
   - Verify no validation layer errors

19. **Mixed temporal + transient** - graph with both temporal cycles and transient intermediates:
    - Temporal: TAA history (double-buffered)
    - Transient: intermediate blur buffer (aliasable)
    - Verify transient buffer aliases with temporal slot when lifetimes permit
    - Verify no corruption

20. **Activation + temporal interaction** - temporal resource written by a conditionally-active pass:
    - Pass active on frames 0, 2, 4 (even frames only)
    - Temporal read on every frame
    - Frame 1 reads frame 0's output (correct - most recent write)
    - Frame 3 reads frame 2's output (correct)
    - Verify the graph tracks "most recent write submission" not just "previous submission"

21. **Multi-temporal-resource graph** - 3+ temporal resources with different buffer counts:
    - History (double-buffered)
    - Velocity (double-buffered)  
    - GI accumulator (triple-buffered)
    - Verify independent flip counters
    - Verify aliasing considers all temporal slots together
