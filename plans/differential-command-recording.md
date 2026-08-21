# Differential Command Recording Plan

## Summary

This is sill strongly under consideration and needs to be discussed and refined.


Define a generalized pattern and reusable utility for differential GPU updates: given a dynamic
set of objects tracked by `TraversalView`, maintain a GPU buffer (SSBO, indirect draw buffer,
instance buffer) that reflects the current set with minimal per-frame work. In steady state,
zero CPU work. On change, O(dirty count) patching with coalesced multi-region transfers.

This plan sits between two existing systems:
- **Below**: `ManagedBuffer` + `DirtyStrategy` (from mirroring-and-dirty-tracking-plan.md)
  provides the physical "which bytes are stale and how to flush them" layer.
- **Above**: Render graph UI layers (from render-graph-ui-layers.md) and any other system with
  a dynamic GPU-driven instance set consumes this pattern.

The deliverable is a reusable `TraversalBufferBridge<C, G>` utility plus the protocols and
conventions for using it.

---

## Goals

1. Generalized reusable bridge from `TraversalView<C>` to GPU buffer, usable by any subsystem.
2. Zero per-object CPU work in steady state (no changes = no writes, no transfers, no recording).
3. O(dirty count) patching when objects change (not O(total count)).
4. Slot management with free-list compaction to avoid fragmentation without full rewrites.
5. Integration with indirect draw for zero command re-recording even when object count changes.
6. Clear protocol for how render graph nodes consume the bridge's output.

## Non-Goals

- Replacing `TraversalView` or `DirtyStrategy` -- this composes them, not replaces them.
- Defining buffer allocation strategy -- callers choose their own `MemoryStrategy`.
- Implementing GPU-side culling or LOD -- those are consumers of this bridge's output.
- Thread safety of the bridge itself -- assumed single-logical-thread (same as TraversalView).

## Dependencies

- `TraversalView<C>` and `DirtyReport` from `vulkan-ffm-node-trees`
- `ManagedBuffer`, `DirtyStrategy`, `BufferWritable` from `vulkan-core/buffers`
- `TransferBatch` for coalesced multi-region copies from `vulkan-core/buffers`

---

## Part 1: The Problem

Any system that renders a dynamic set of GPU-driven instances faces the same bookkeeping:

1. **Object appears**: allocate a slot in the GPU buffer, write initial data
2. **Object changes**: patch its slot in the GPU buffer
3. **Object disappears**: reclaim its slot (mark dead, add to free list)
4. **Nothing changes**: do nothing (ideally zero CPU instructions in the hot path)
5. **Many things change at once**: batch all updates into minimal transfers
6. **Draw command**: issue a single indirect draw whose count reflects current live objects

Without a pattern, every layer/system hand-rolls this. The result is duplicated logic for:
- Slot allocation / free-list management
- Dirty detection and conditional patching
- Full-rewrite vs. incremental-patch decision
- Indirect draw buffer maintenance
- Integration with the buffer transfer system

---

## Part 2: Architecture

```
+------------------+
| Node Tree        |   (dynamic objects: add, remove, modify via PropertyNotifier)
+------------------+
        |
        v
+------------------+
| TraversalView<C> |   (incrementally maintained ordered list, dirty tracking)
+------------------+
        |
        | applyPatches() -> DirtyReport
        v
+-------------------------------+
| TraversalBufferBridge<C, G>   |   <-- THIS PLAN
+-------------------------------+
        |                 |
        v                 v
+---------------+   +------------------+
| ManagedBuffer |   | IndirectBuffer   |   (optional, for zero command re-recording)
| (instance     |   | (draw args)      |
|  SSBO/UBO)    |   |                  |
+---------------+   +------------------+
        |                 |
        | flush()         | flush()
        v                 v
+---------------------------------------+
| Graph Node execute()                  |   (reads buffers, issues vkCmdDrawIndirect)
+---------------------------------------+
```

### Responsibilities

| Component | Responsibility |
|-----------|---------------|
| TraversalView | Track which objects exist; report adds/removes/dirties |
| TraversalBufferBridge | Slot management, GPU data mapping, dirty consumption, buffer writes |
| ManagedBuffer + DirtyStrategy | Track which byte ranges are stale, coalesce, transfer |
| IndirectDrawBuffer (optional) | Maintain draw args reflecting current live count |
| Graph Node | Record draw commands referencing the now-current buffers |

---

## Part 3: TraversalBufferBridge API

```java
package io.github.yetyman.vulkan.ui.graph;

import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.buffers.BufferWritable;
import io.github.yetyman.vulkan.nodetree.Component;
import io.github.yetyman.vulkan.nodetree.Node;
import io.github.yetyman.vulkan.nodetree.TraversalView;

import java.util.function.Function;

/**
 * Bridges a TraversalView to a GPU buffer with differential updates.
 *
 * Manages slot allocation, free-list compaction, dirty consumption, and buffer writes.
 * In steady state (no changes), sync() is a no-op. On change, only dirty entries are
 * patched with O(dirty count) work.
 *
 * Usage:
 *   bridge.sync();              // consume dirty report, patch buffer
 *   bridge.flush(queue);        // transfer dirty byte ranges to GPU
 *   // ... graph node executes, reads the buffer ...
 *
 * @param <C> component type tracked by the traversal view
 * @param <G> GPU data type (must implement BufferWritable)
 */
public class TraversalBufferBridge<C extends Component, G extends BufferWritable> {

    private final TraversalView<C> view;
    private final ManagedBuffer buffer;
    private final Function<C, G> toGpuData;
    private final int elementSize;
    private final int maxElements;

    // Slot management
    private final IdentityHashMap<Node, Integer> nodeToSlot;
    private final int[] freeSlots;   // circular free list
    private int freeHead, freeTail, freeCount;
    private int highWaterMark;       // highest slot ever assigned (for compaction decisions)

    // State
    private boolean lastSyncWasFullRewrite;
    private int liveCount;

    // Optional: indirect draw integration
    private IndirectDrawBuffer indirectDraw; // null if not using indirect

    private TraversalBufferBridge(Builder<C, G> b) { ... }

    public static <C extends Component, G extends BufferWritable> Builder<C, G> builder() {
        return new Builder<>();
    }

    // --- Core API ---

    /**
     * Consumes the TraversalView's dirty report and patches the buffer.
     *
     * @return a SyncResult indicating what work was done
     */
    public SyncResult sync() { ... }

    /**
     * Flushes dirty byte ranges from CPU mirror to GPU.
     * Delegates to ManagedBuffer.flushDirty(). No-op if nothing was written by sync().
     */
    public void flush(MemorySegment queue) { ... }

    /**
     * @return the number of currently live (non-free) slots
     */
    public int liveCount() { return liveCount; }

    /**
     * @return the buffer handle for binding in descriptor sets or vertex input
     */
    public MemorySegment bufferHandle() { return buffer.handle(); }

    /**
     * @return the managed buffer (for direct access if needed)
     */
    public ManagedBuffer buffer() { return buffer; }

    /**
     * @return the indirect draw buffer, or null if not configured
     */
    public IndirectDrawBuffer indirectDraw() { return indirectDraw; }

    /**
     * Forces a full rewrite of the buffer from the current TraversalView state.
     * Used after initial setup or when the buffer is reallocated (resize).
     */
    public void forceFullRewrite() { ... }

    /**
     * Releases all resources. The bridge does NOT close the managed buffer
     * (caller owns buffer lifetime).
     */
    public void close() { ... }

    // --- Sync Result ---

    public enum SyncResult {
        /** Nothing changed. Zero work performed. */
        CLEAN,
        /** Incremental patch applied. Only dirty entries written. */
        PATCHED,
        /** Full buffer rewrite performed (too many changes for incremental). */
        FULL_REWRITE
    }

    // --- Builder ---

    public static class Builder<C extends Component, G extends BufferWritable> {

        /** The traversal view to consume dirty reports from. Required. */
        public Builder<C, G> view(TraversalView<C> view) { ... }

        /** The GPU buffer to write into. Required. Must be in deferred mode. */
        public Builder<C, G> buffer(ManagedBuffer buffer) { ... }

        /** Maps a component to its GPU representation. Required. */
        public Builder<C, G> mapper(Function<C, G> toGpuData) { ... }

        /** Size in bytes of one GPU element. Required. */
        public Builder<C, G> elementSize(int size) { ... }

        /** Maximum number of elements the buffer can hold. Required. */
        public Builder<C, G> maxElements(int max) { ... }

        /**
         * Enables indirect draw integration. The bridge will maintain an indirect
         * draw buffer alongside the instance buffer.
         */
        public Builder<C, G> indirectDraw(IndirectDrawBuffer indirect) { ... }

        public TraversalBufferBridge<C, G> build() { ... }
    }
}
```

---

## Part 4: Slot Management Strategy

### Allocation

When a node is added to the TraversalView:
1. Check free list. If non-empty, pop a free slot.
2. Otherwise, assign `highWaterMark++` (append).
3. Map node -> slot in `nodeToSlot`.
4. Write initial GPU data at `slot * elementSize`.

### Removal

When a node is removed from the TraversalView:
1. Look up slot from `nodeToSlot`, remove mapping.
2. Push slot onto free list.
3. Write a "dead" marker at that slot (zero bytes or a sentinel) so the GPU skips it.
4. Decrement `liveCount`.

### Compaction

Free-list fragmentation grows over time (slots scattered with dead entries between live ones).
This matters for:
- Cache coherence on the GPU (scattered reads are slower)
- Indirect draw (can't skip dead entries without GPU-side culling)

**Compaction trigger**: when `freeCount > liveCount * 0.5` (more than half the allocated range
is dead), perform a full compaction:
1. Walk the TraversalView in order
2. Assign contiguous slots 0..liveCount-1
3. Rewrite the entire buffer
4. Reset free list to empty, highWaterMark to liveCount

This is O(n) but rare. The heuristic ensures it only fires when fragmentation is severe enough
that the compaction cost is justified by the GPU cache improvement.

### Alternative: Stable-Index Mode

For systems that need stable slot indices (e.g., other buffers reference objects by slot index),
compaction is disabled. Dead entries are left in place and the GPU skips them via:
- A visibility bit in the per-instance data (GPU reads it, skips draw if dead)
- Or `vkCmdDrawIndexedIndirectCount` with a count buffer that excludes dead entries

This is opt-in via `Builder.stableIndices(true)`.

---

## Part 5: Sync Protocol

```java
public SyncResult sync() {
    TraversalView.DirtyReport<C> report = view.applyPatches();

    if (report.isClean()) {
        return SyncResult.CLEAN;
    }

    if (report.fullRewriteRecommended()) {
        rewriteAll();
        return SyncResult.FULL_REWRITE;
    }

    // Process removals first (frees slots for additions)
    // Note: removals are implicit in DirtyReport -- entries that were in nodeToSlot
    // but are no longer in the view. We detect this by checking nodeToSlot against
    // the view's current entries.
    processRemovals(report);

    // Process additions (new entries in dirty list that have no slot)
    // Process modifications (existing entries in dirty list that have a slot)
    for (TraversalView.Entry<C> entry : report.dirtyEntries()) {
        Integer existingSlot = nodeToSlot.get(entry.node);
        if (existingSlot == null) {
            // Addition
            int slot = allocateSlot();
            nodeToSlot.put(entry.node, slot);
            G gpuData = toGpuData.apply(entry.component);
            writeSlot(slot, gpuData);
            liveCount++;
        } else {
            // Modification
            G gpuData = toGpuData.apply(entry.component);
            writeSlot(existingSlot, gpuData);
        }
    }

    // Update indirect draw buffer if configured
    if (indirectDraw != null) {
        indirectDraw.updateCount(liveCount);
    }

    return SyncResult.PATCHED;
}

private void writeSlot(int slot, G gpuData) {
    long offset = (long) slot * elementSize;
    buffer.writeAt(offset, gpuData);
    // DirtyStrategy on the buffer automatically tracks this byte range
}
```

### Removal Detection

The `DirtyReport` from `TraversalView` reports `removalCount` but doesn't give you the removed
entries (they're already unlinked). Two approaches:

**Option A: Track removals via TraversalView callback**

Add a removal listener to the view that the bridge registers. When the view calls
`removeEntry(node, component)`, the bridge immediately:
1. Looks up the slot
2. Pushes it to the free list
3. Writes dead marker
4. Decrements liveCount

This means removals are handled eagerly (before `sync()` is called). `sync()` only handles
additions and modifications from the dirty report.

**Option B: Detect removals by diffing nodeToSlot against the view**

During `sync()`, iterate `nodeToSlot` keys and check which are no longer in the view.
This is O(nodeToSlot.size()) which defeats the purpose of differential updates for large sets.

**Decision: Option A.** The bridge registers a removal callback with the TraversalView.
This requires adding a `onRemoval(BiConsumer<Node, C>)` hook to TraversalView (small addition
to the node-trees module).

---

## Part 6: IndirectDrawBuffer

```java
package io.github.yetyman.vulkan.ui.graph;

/**
 * Maintains an indirect draw command buffer that reflects the current live instance count.
 * Updated by TraversalBufferBridge when objects are added or removed.
 *
 * Supports both:
 * - VkDrawIndirectCommand (non-indexed)
 * - VkDrawIndexedIndirectCommand (indexed)
 *
 * For indexed draws, the caller provides per-draw geometry bindings.
 * For instanced draws, the buffer contains a single draw command with instanceCount = liveCount.
 */
public class IndirectDrawBuffer implements AutoCloseable {

    private final ManagedBuffer buffer;
    private final IndirectKind kind;
    private final int maxDrawCount;

    // Current state
    private int drawCount;

    public static Builder builder() { return new Builder(); }

    /**
     * Updates the instance count for a single-draw instanced scenario.
     * The draw command is: draw(vertexCount, instanceCount=liveCount, 0, 0)
     */
    public void updateCount(int instanceCount) { ... }

    /**
     * Sets a specific draw command at the given index.
     * For multi-draw scenarios (one draw per material/geometry group).
     */
    public void setDraw(int index, int indexCount, int instanceCount,
                        int firstIndex, int vertexOffset, int firstInstance) { ... }

    /**
     * Removes a draw command (shifts remaining or marks dead depending on mode).
     */
    public void removeDraw(int index) { ... }

    /** @return the buffer handle for vkCmdDrawIndexedIndirect */
    public MemorySegment handle() { return buffer.handle(); }

    /** @return current number of draw commands */
    public int drawCount() { return drawCount; }

    /** Flushes dirty ranges to GPU. */
    public void flush(MemorySegment queue) { buffer.flushDirty(queue); }

    public enum IndirectKind { NON_INDEXED, INDEXED }

    public static class Builder {
        public Builder kind(IndirectKind kind) { ... }
        public Builder maxDrawCount(int max) { ... }
        public Builder device(VkDevice device) { ... }
        public IndirectDrawBuffer build(Arena arena) { ... }
    }
}
```

### Single-Draw vs. Multi-Draw

**Single-draw (instanced)**: One draw command, instanceCount tracks liveCount. All instances
use the same geometry. This is the simple case (gizmo layer, particle system).

```
vkCmdDrawIndexedIndirect(cmd, indirectBuf, 0, 1, stride)
// The single command says: draw N instances of this geometry
```

**Multi-draw (per-object geometry)**: One draw command per unique geometry binding. Each draw
has its own indexCount, firstIndex, vertexOffset. instanceCount is typically 1 per draw (or
batched by material). This is the scene mesh layer case.

```
vkCmdDrawIndexedIndirect(cmd, indirectBuf, 0, drawCount, stride)
// drawCount commands, each describing one mesh's draw args
```

In multi-draw mode, the `TraversalBufferBridge` slot index corresponds to the indirect draw
command index. Adding an object adds a draw command; removing an object removes one.

---

## Part 7: Integration with Render Graph

### The Contract

A render graph node that uses a `TraversalBufferBridge` follows this protocol:

```java
// In RenderGraphLayer.prepareFrame():
SyncResult result = bridge.sync();
if (result != SyncResult.CLEAN) {
    bridge.flush(queue);
}

// In the GraphicsPassNode's execute lambda:
ctx -> {
    VkCommandBuffer cmd = ctx.commandBuffer();
    // Bind pipeline, descriptor set (pointing to bridge.bufferHandle())
    if (bridge.indirectDraw() != null) {
        vkCmdDrawIndexedIndirect(cmd.handle(), bridge.indirectDraw().handle(),
            0, bridge.indirectDraw().drawCount(), stride);
    } else {
        vkCmdDraw(cmd.handle(), vertexCount, bridge.liveCount(), 0, 0);
    }
}
```

### Transfer Scheduling

The bridge's `flush()` records copy commands into the current `TransferBatch`. The render graph
must ensure these transfers complete before the graphics pass reads the buffer. Two options:

1. **Explicit transfer node**: The layer creates a `TransferNode` that calls `bridge.flush()`.
   The graph inserts barriers between this node and the graphics pass via resource edges.

2. **MirrorFlushPass integration**: The graph's existing `MirrorFlushPass` can auto-detect
   deferred mirrored buffers and flush them (per the mirroring plan). If the layer's buffer is
   declared as a graph resource with the `DEFERRED_MIRRORED` flag, the graph handles it.

**Recommendation**: Option 1 for explicit control. The layer knows when it wrote data and
creates a transfer node for it. This is more predictable and debuggable than auto-detection.

---

## Part 8: TraversalView Addition -- Removal Callback

To support Option A from Part 5, add to `TraversalView`:

```java
// In TraversalView<C>:

private BiConsumer<Node, C> onRemovalCallback;

/**
 * Registers a callback fired immediately when an entry is removed from this view.
 * The callback receives the node and component being removed.
 * Only one callback is supported (last registration wins).
 */
public void onRemoval(BiConsumer<Node, C> callback) {
    this.onRemovalCallback = callback;
}

// In removeEntry():
void removeEntry(Node node, C component) {
    Entry<C> entry = nodeToEntry.remove(node);
    if (entry == null) return;

    // Fire callback BEFORE unlinking (node/component still valid)
    if (onRemovalCallback != null) {
        onRemovalCallback.accept(node, component);
    }

    // ... existing unlink logic ...
}
```

This is a minimal, backward-compatible addition. Existing code that doesn't register a callback
sees no behavior change.

---

## Part 9: Full Rewrite Path

When `DirtyReport.fullRewriteRecommended()` is true (more than 1/3 of entries changed), or
when `forceFullRewrite()` is called explicitly:

```java
private void rewriteAll() {
    // Reset slot management
    nodeToSlot.clear();
    freeHead = freeTail = freeCount = 0;
    highWaterMark = 0;
    liveCount = 0;

    // Walk the view in order, assign contiguous slots
    view.forEach((node, component) -> {
        int slot = highWaterMark++;
        nodeToSlot.put(node, slot);
        G gpuData = toGpuData.apply(component);
        writeSlot(slot, gpuData);
        liveCount++;
    });

    // Update indirect draw
    if (indirectDraw != null) {
        indirectDraw.updateCount(liveCount);
    }

    lastSyncWasFullRewrite = true;
}
```

After a full rewrite, the buffer's `DirtyStrategy` will report the entire written range as
dirty, and `flush()` will transfer it in one large copy. This is optimal -- a single
`vkCmdCopyBuffer` with one region covering the contiguous range.

---

## Part 10: Steady-State Analysis

### Frame with no changes

```
sync():
  view.applyPatches() -> DirtyReport.clean()
  return SyncResult.CLEAN

flush():
  buffer.flushDirty() -> no-op (nothing dirty)

Graph node execute():
  Same vkCmdDrawIndexedIndirect as last frame
  Same buffer contents
  Same descriptor sets
```

**CPU cost: near zero.** One method call to `applyPatches()` which checks `dirtyCount == 0`
and returns a static singleton. One branch in `sync()`. One no-op in `flush()`. Done.

### Frame with 3 transforms changed (out of 10000 objects)

```
sync():
  view.applyPatches() -> DirtyReport with 3 dirty entries
  For each: look up slot, map component -> GPU data, write 3 slots
  return SyncResult.PATCHED

flush():
  buffer.flushDirty() -> 3 dirty regions coalesced by DirtyStrategy
  Multi-region vkCmdCopyBuffer with 1-3 regions (depending on coalescing)

Graph node execute():
  Same vkCmdDrawIndexedIndirect (draw count unchanged)
  Buffer contents updated at 3 slots
```

**CPU cost: O(3).** Three slot lookups, three data mappings, three buffer writes. The
multi-region copy is a single Vulkan command regardless of region count.

### Frame with 500 objects added

```
sync():
  view.applyPatches() -> DirtyReport with 500 dirty entries (additions)
  For each: allocate slot (from free list or highWaterMark), write initial data
  Update indirect draw count
  return SyncResult.PATCHED (or FULL_REWRITE if threshold hit)

flush():
  500 new slots written -> large dirty region (likely contiguous at end)
  Single-region vkCmdCopyBuffer

Graph node execute():
  Same vkCmdDrawIndexedIndirect but draw count now includes 500 new objects
  No command re-recording needed
```

**CPU cost: O(500).** Proportional to change size, not total size.

---

## Part 11: Where It Lives

### Module Placement

`io.github.yetyman.vulkan.ui.graph.TraversalBufferBridge` in `vulkan-ffm-sample-ui-layers`.

**Rationale**: It composes `TraversalView` (from node-trees) with `ManagedBuffer` (from
vulkan-core). The sample-ui-layers module depends on both. It's a concrete integration utility,
not a core primitive.

If it proves universally useful beyond the sample layers, it could be promoted to a shared
utility module. But for now, keeping it in sample-ui-layers avoids premature abstraction.

### The TraversalView.onRemoval callback addition

Lives in `vulkan-ffm-node-trees` (where TraversalView is). This is the only cross-module
change required.

### IndirectDrawBuffer

Lives in `vulkan-ffm-sample-ui-layers` alongside the bridge. It's a convenience wrapper,
not core infrastructure.

---

## Part 12: Implementation Order

### Phase 1: TraversalView Enhancement

1. Add `onRemoval(BiConsumer<Node, C>)` to `TraversalView`
2. Fire callback in `removeEntry()` before unlinking
3. Unit test: register callback, remove a node, verify callback fires with correct args

### Phase 2: TraversalBufferBridge Core

4. Implement `TraversalBufferBridge` with:
   - Slot allocation (free list + highWaterMark)
   - `sync()` consuming DirtyReport (additions, modifications)
   - Removal handling via onRemoval callback
   - `forceFullRewrite()`
   - `flush()` delegating to ManagedBuffer.flushDirty
5. Implement compaction trigger and full-compaction path
6. Unit test: add/remove/modify objects, verify buffer contents and slot assignments

### Phase 3: IndirectDrawBuffer

7. Implement `IndirectDrawBuffer` with single-draw and multi-draw modes
8. Integrate with bridge (bridge updates indirect buffer on add/remove)
9. Unit test: verify draw count tracks liveCount correctly

### Phase 4: Integration Tests

10. Create a test that:
    - Builds a Tree with MeshComponent nodes
    - Registers a TraversalView<MeshComponent>
    - Creates a TraversalBufferBridge
    - Adds/removes/modifies nodes
    - Verifies SyncResult values (CLEAN, PATCHED, FULL_REWRITE)
    - Verifies buffer contents via mirror read
    - Verifies indirect draw count

### Phase 5: Graph Integration Example

11. Create a minimal RenderGraphLayer that uses the bridge:
    - One GraphicsPassNode with indirect draw
    - One TransferNode that calls bridge.flush()
    - Demonstrate steady-state zero-work frame
    - Demonstrate incremental-patch frame

---

## Open Questions

1. **Compaction during animation**: If objects are continuously added and removed (particle
   system), the free list grows and shrinks. Should compaction be time-gated (at most once
   per N seconds) to avoid compaction during high-churn frames?
   **Recommendation**: Yes. Add `minCompactionIntervalMs` to builder (default 1000ms).
   Compaction only fires if both the fragmentation threshold AND the time gate are met.

2. **Multi-draw slot correspondence**: In multi-draw mode, should slot index == draw command
   index, or should they be decoupled (allowing multiple instances per draw)?
   **Recommendation**: Decoupled. The bridge manages instance slots. A separate
   `DrawCommandManager` (layer-specific, not in the bridge) groups instances into draw commands
   by material/geometry. The bridge provides `liveCount` and slot data; the layer decides how
   to batch draws.

3. **Buffer resize**: If liveCount exceeds maxElements, what happens?
   **Recommendation**: Throw. The caller must provision a large enough buffer at construction.
   If dynamic resize is needed, the caller can detect the approaching limit via
   `bridge.liveCount() > bridge.maxElements() * 0.9` and rebuild with a larger buffer. This is
   a rare, expensive operation (full rewrite) and should not be hidden inside the bridge.

4. **Multiple views per bridge**: Can one bridge serve multiple TraversalViews?
   **Recommendation**: No. One bridge = one view = one buffer. If a layer needs multiple
   buffers (e.g., separate transform buffer and material buffer), it creates multiple bridges
   over the same view with different mappers. Each bridge writes to its own buffer.

5. **Stable-index mode and GPU culling**: When stable indices are enabled (no compaction),
   should the bridge provide a visibility bitmask buffer for GPU-side culling?
   **Recommendation**: Deferred. GPU culling is its own system. The bridge provides the raw
   data; a culling compute pass can read the visibility bits from the instance data.

6. what is a valid example application for this capability? where is thisi truly difficult otherwise and what use does 
   it bring to the dev to be represented in an example app?