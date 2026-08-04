# Traversal Views and the Three-Level Model

## The Three Levels

The Node Treesystem separates concerns into three distinct levels, each with different performance characteristics and ownership:

```
Level 1: THE TREE
  - The real Node parent/child structure
  - Mutated immediately, synchronously
  - Single-logical-thread assumed
  - O(1) child add/remove (ArrayList operations)

Level 2: TRAVERSAL VIEWS
  - Incrementally-maintained doubly-linked lists of nodes
  - Optionally filtered by component type
  - O(1) add/remove/markDirty via intrusive linked list
  - Dirty tracking via singly-linked dirty chain
  - Keyed by string name on Tree

Level 3: DENSE BACKING ARRAY (caller-owned)
  - GPU buffer, FloatBuffer, int[], whatever the consumer needs
  - Owned and allocated by the CALLER, not the framework
  - Updated only via explicit call to view.applyPatches()
  - Nothing calls applyPatches() automatically
```

The framework provides Levels 1 and 2. Level 3 is entirely the caller's responsibility — the framework gives you the change information, you decide how to apply it to your data structure.

---

## TraversalView

The universal iteration primitive. Any code that needs an ordered iteration of nodes (input dispatch, rendering, physics) uses a TraversalView:

```java
public class TraversalView<C extends Component> {
    // Intrusive doubly-linked list
    private Entry<C> head;
    private Entry<C> tail;
    private int liveCount;

    // O(1) node-to-entry lookup
    private final IdentityHashMap<Node, Entry<C>> nodeToEntry;

    // Dirty tracking: singly-linked dirty chain
    private Entry<C> dirtyHead;
    private int dirtyCount;

    public Class<C> componentType();   // null = unfiltered (all nodes)
    public boolean isUnfiltered();
    public int liveCount();

    public void forEach(BiConsumer<Node, C> visitor);  // O(n) walk
    public void forEachNode(Consumer<Node> visitor);   // O(n) walk, ignores component
    public Entry<C> head();                            // for direct linked-list walking
    public Entry<C> tail();                            // for reverse walking

    public DirtyReport<C> applyPatches();              // consumes dirty state
}
```

### Entry (intrusive linked list node)

```java
public static final class Entry<C extends Component> {
    public final Node node;
    public final C component;    // null for unfiltered views

    Entry<C> prev, next;         // doubly-linked traversal order
    boolean dirty;
    Entry<C> nextDirty;          // singly-linked dirty chain

    public Entry<C> next();      // public accessors for external iteration
    public Entry<C> prev();
}
```

### Performance characteristics

| Operation | Complexity | Notes |
|-----------|-----------|-------|
| addEntry | O(1) | Linked list insert after parent's last descendant |
| removeEntry | O(1) | IdentityHashMap lookup + doubly-linked unlink |
| markDirty | O(1) | IdentityHashMap lookup + set flag (idempotent) |
| forEach / forEachNode | O(n) | Linear walk, zero allocation |
| applyPatches | O(dirty count) | Walks dirty chain only |

---

## View Registration

Views are keyed by string name on the Tree:

```java
// Built-in all-nodes view, always maintained
tree.allNodes();

// Custom views by name
tree.getOrCreateTraversalView("renderables", RenderComponent.class, TraversalOrder.DFS_PRE);
tree.getOrCreateTraversalView("all-dfs", TraversalOrder.DFS_PRE);  // unfiltered

// Backward-compatible convenience
tree.getOrCreateComponentTraversal(RenderComponent.class, TraversalOrder.DFS_PRE);

// Release when no longer needed
tree.releaseTraversalView("renderables");
```

Views can be:
- **Unfiltered** (all nodes) — for input dispatch, tree-wide iteration
- **Filtered by component type** — for bulk rendering a specific component kind

The same view instance is returned on repeat calls with the same key. Views are lazily populated with existing matching nodes on creation.

### Release policy

Views are NOT auto-destroyed when their live count reaches zero. The registering code decides when to release via `tree.releaseTraversalView(key)`. The built-in `allNodes()` view is never released.

---

## DFS Pre-Order Maintenance

New nodes are inserted at the correct DFS pre-order position: after the parent's last existing descendant in the view. This is found by walking forward from the parent's entry until a non-descendant is reached, then inserting before that point.

This ensures that forEach traversal matches the tree's structural depth-first order without any sorting step.

---

## Dirty Tracking

The dirty tracking uses a singly-linked "dirty chain" — each dirty Entry points to the next dirty Entry, forming a linked list of only the dirty entries:

```
 Entry A (clean)
 Entry B (dirty) --> nextDirty --> Entry D
 Entry C (clean)
 Entry D (dirty) --> nextDirty --> null
 Entry E (clean)

 dirtyHead --> Entry B
 dirtyCount = 2
```

`markDirty` is idempotent — marking an already-dirty entry is a no-op (checks the boolean flag first).

### applyPatches and DirtyReport

```java
public record DirtyReport<C extends Component>(
    boolean fullRewriteRecommended,
    List<Entry<C>> dirtyEntries,
    int additionCount,
    int removalCount
) {
    public boolean isClean();
}
```

**Full rewrite threshold:** if `(dirtyCount + additionCount + removalCount) * 3 > liveCount`, the report recommends a full rewrite rather than enumerating individual dirty entries. This heuristic avoids the overhead of per-entry patching when a large fraction of the view has changed.

After `applyPatches()`, all dirty state is cleared. The next call returns clean until further mutations occur.

### Open design tension

The threshold constant (3) is currently fixed. Whether it should be configurable per-view, and how swap-remove in the backing array interacts with the dirty model (one dirty entry or two?), are recognized refinement opportunities. A bitset approach may be better for very large views with sparse changes. These are documented as future refinement, not blocking issues.

---

## Thread Safety Model

- **Level 1 (tree) and Level 2 (traversal views):** Mutated immediately, synchronously, single-logical-thread assumed. No locks, no atomics in the framework.
- **Level 3 (backing array) via `applyPatches()`:** This is where cross-thread handoff matters. The RECOMMENDED pattern (not enforced): double-buffer the backing array, swap pointers between frames. The framework provides no buffering — it is the caller's strategy.
- `Node.fireEvent` uses a static scratch buffer (safe under single-thread assumption).

---

## Driving Render from Traversal Views

Neither `Node` nor `Tree` drives rendering. A render-system-specific driver:

1. Gets a `TraversalView` for the component type(s) it cares about
2. Calls `view.applyPatches()` at whatever point in its frame sequencing it needs current data
3. Uses the dirty report + its own cached backing array to do bulk work (one draw call)

### Bulk render example

A `RectangleRenderComponent` type across many nodes, all wanting their vertex data in ONE shared FloatBuffer/Vulkan buffer for one bulk draw call:

```java
// Tree-scoped renderer component
public class RectangleBatchRenderer implements TreeComponent {
    private TraversalView<RectangleComponent> view;
    private FloatVkBuffer vertexBuffer;
    private int[] slotMap;         // node index -> buffer slot
    private int[] freeSlots;       // free-list for dense packing

    @Override
    public void onInit(Tree tree) {
        view = tree.getOrCreateTraversalView(
            "rectangle-render", RectangleComponent.class, TraversalOrder.DFS_PRE);
        vertexBuffer = new FloatVkBuffer(/* ... */);
    }

    public void flush(VkCommandBuffer cmd) {
        DirtyReport<RectangleComponent> report = view.applyPatches();

        if (report.isClean()) return;  // nothing changed

        if (report.fullRewriteRecommended()) {
            // Rebuild entire buffer from scratch
            rebuildAll();
        } else {
            // Patch only dirty entries
            for (var entry : report.dirtyEntries()) {
                int slot = slotMap[entry.node.id()];
                writeVertexData(slot, entry.component);
            }
        }

        // One draw call for all rectangles
        vertexBuffer.bind(cmd);
        Vulkan.cmdDraw(cmd.handle(), view.liveCount() * 6, 1, 0, 0);
    }
}
```

Key patterns:
- The tree-scoped renderer component gets a TraversalView over the DATA component it renders.
- Each render component caches its own slot index in the caller-owned backing array once (at `afterResolve()` time). Never re-looks-up its slot by hashmap on the hot path.
- On its data dependency's property change (via PropertyNotifier), marks itself dirty in the view — not a full rebuild.
- Add/remove uses a swap-remove/free-list strategy to keep the array dense without shifting. This cost is paid ONLY on the Level 3 array side (at `applyPatches()` time). Levels 1 and 2 are already O(1).

---

## Claim Registry Location

Claims are tracked per-tree (not global static state). The `Tree` owns an `IdentityHashMap<Component, Map<Class<?>, ClaimStyle>>` that maps each claimed component instance to its claim records. Claims are cleared when components are removed or the tree is closed. No memory leak across tree lifetimes.

See [dependency-injection.md](dependency-injection.md) for claim semantics.
