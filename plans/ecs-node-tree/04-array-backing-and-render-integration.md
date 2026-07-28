# Array Backing and Render Integration

## The three levels, precisely

**Level 1 - the tree.** The single real `Node` parent/child structure. Mutated immediately,
synchronously. Assumed single-logical-thread.

**Level 2 - traversal views (`TraversalView<C>`).** Separate from the tree itself. Each view
is an incrementally-maintained doubly-linked list of nodes, optionally filtered by component
type. Maintained via O(1) operations as the tree mutates. Dirty tracking is per-entry
(boolean flag + singly-linked dirty chain). Views are keyed by string name on Tree.

**Level 3 - the dense backing array.** Owned and allocated by the CALLER (implementor), not the
framework. Updated only via explicit call to `view.applyPatches()`, which consumes accumulated
dirty state and reports it back. Nothing calls `applyPatches()` automatically.

## `TraversalView<C>` - the universal traversal primitive

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

- `addEntry`: O(1) — linked list insert (after parent's last descendant for correct DFS order)
- `removeEntry`: O(1) — IdentityHashMap lookup + doubly-linked unlink
- `markDirty`: O(1) — IdentityHashMap lookup + set flag (idempotent)
- `forEach`/`forEachNode`: O(n) linear walk, zero allocation
- `applyPatches`: O(dirty count) — walks dirty chain only

## Registration (string-keyed, not component-type-keyed)

```java
public class Tree {
    // Built-in all-nodes view, always maintained
    public TraversalView<Component> allNodes();

    // Custom views by name
    public <C extends Component> TraversalView<C> getOrCreateTraversalView(
            String key, Class<C> componentType, TraversalOrder order);

    public TraversalView<Component> getOrCreateTraversalView(
            String key, TraversalOrder order);  // unfiltered

    public void releaseTraversalView(String key);

    // Backward-compatible convenience
    public <C extends Component> TraversalView<C> getOrCreateComponentTraversal(
            Class<C> componentType, TraversalOrder order);
}
```

Views are keyed by string name. A view can be:
- **Unfiltered** (all nodes) — for input dispatch, tree-wide iteration
- **Filtered by component type** — for bulk rendering a specific component kind

The same view instance is returned on repeat calls with the same key. Views are lazily
populated with existing matching nodes on creation.

## DFS pre-order maintenance

New nodes are inserted at the correct DFS pre-order position: after the parent's last
existing descendant in the view. This is found by walking forward from the parent's entry
until a non-descendant is reached, then inserting after that point.

## Dirty tracking and applyPatches

The dirty tracking uses a singly-linked "dirty chain" (each dirty Entry points to the next
dirty Entry). `markDirty` is idempotent — marking an already-dirty entry is a no-op.

`applyPatches()` returns a `DirtyReport`:

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

**Full rewrite threshold:** if `(dirtyCount + additionCount + removalCount) * 3 > liveCount`,
the report recommends a full rewrite rather than enumerating individual dirty entries.

After `applyPatches()`, all dirty state is cleared. The next call returns clean until
further mutations occur.

## Tree-scoped components (`TreeComponent`)

Singletons on the tree. Symmetric lifecycle to node-scoped components. Registered via:

```java
tree.getOrRegisterTreeComponent(new MyBulkRendererComponent());
```

A tree-scoped component can register traversal views in its own lifecycle and use them
for bulk processing.

## Driving render from traversal views

Neither `Node` nor `Tree` drives rendering. A render-system-specific driver:

1. Gets a `TraversalView` for the component type(s) it cares about
2. Calls `view.applyPatches()` at whatever point in its frame sequencing it needs current data
3. Uses the dirty report + its own cached backing array to do bulk work (one draw call)

## Release policy

Views are NOT auto-destroyed when their live count reaches zero. The registering code decides
when to release via `tree.releaseTraversalView(key)`. The built-in `allNodes()` view is never
released.

## Thread safety model

- **Level 1 (tree) and Level 2 (traversal views):** mutated immediately, synchronously,
  single-logical-thread assumed. No locks, no atomics in the framework.
- **Level 3 (array) via `applyPatches()`:** this is where cross-thread handoff matters.
  The RECOMMENDED pattern (not enforced): double-buffer the backing array, swap pointers
  between frames. The framework provides no buffering — it's the caller's strategy.
- `Node.fireEvent` uses a static scratch buffer (safe under single-thread assumption).

## Claim registry

Claims are tracked per-tree (not global static state). The `Tree` owns an
`IdentityHashMap<Component, Map<Class<?>, ClaimStyle>>` that maps each claimed component
instance to its claim records. Claims are cleared when components are removed or the tree
is closed. No memory leak across tree lifetimes.

## Bulk render example (motivating usage pattern)

A `RectangleRenderComponent` type across many nodes, all wanting their vertex data in ONE
shared `FloatBuffer`/Vulkan buffer for one bulk draw call (not one draw call per node):

- The tree-scoped renderer component gets a `TraversalView<RectangleComponent>` over the
  DATA component it renders.
- Each `RectangleRenderComponent` caches its own slot index in the caller-owned backing array
  once (at `afterResolve()` time). Never re-looks-up its slot by hashmap on the hot path.
- On its data dependency's property change (via `PropertyNotifier`), performs a targeted write
  into its cached slot — not a full rebuild.
- Add/remove uses a swap-remove/free-list strategy to keep the array dense without shifting.
  This cost is paid ONLY on the Level 3 array side (at `applyPatches()` time). Level 1/2 are
  already O(1) via the tree/linked-list structure.

## Dirty tracking - design tension (still open for refinement)

The current implementation uses a singly-linked dirty chain with entry-level boolean flags.
This provides O(1) idempotent marking and O(dirty) iteration. The "full rewrite threshold"
(`(dirty + adds + removes) * 3 > liveCount`) is a simple heuristic.

**Still open for a dedicated design pass:**
- Whether the threshold constant (3) should be configurable per-view.
- How swap-remove interacts with the dirty model (does a swap count as one dirty entry or two?).
- Whether a bitset approach would be better for very large views with sparse changes.
