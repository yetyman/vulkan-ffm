package io.github.yetyman.vulkan.foundation.ecs;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * An incrementally-maintained ordered list of nodes in a tree.
 *
 * This is the universal traversal primitive. It maintains a doubly-linked list of nodes
 * in traversal order, updated incrementally as the tree mutates. Any code that needs to
 * iterate nodes in order uses one of these — input dispatch, rendering, physics, anything.
 *
 * A view can optionally filter by component type, but it doesn't have to. An unfiltered
 * view contains all nodes in the tree.
 *
 * Registration is by string key on Tree. Multiple views can coexist with different filters
 * or orderings.
 *
 * Performance characteristics:
 * - addEntry: O(1) append (linked list tail insert)
 * - removeEntry: O(1) via direct node-to-entry lookup (doubly-linked unlink)
 * - markDirty: O(1) via direct node-to-entry lookup
 * - forEach: O(n) linear walk of linked list
 * - applyPatches: O(dirty count)
 *
 * Thread safety: assumed single-logical-thread.
 *
 * @param <C> the component type this view tracks (use Component for unfiltered)
 */
public class TraversalView<C extends Component> {

    private final Class<C> componentType; // null means unfiltered (all nodes)
    private final TraversalOrder order;

    // Intrusive doubly-linked list
    private Entry<C> head;
    private Entry<C> tail;
    private int liveCount = 0;

    // Per-node direct lookup for O(1) operations
    private final IdentityHashMap<Node, Entry<C>> nodeToEntry = new IdentityHashMap<>();

    // Dirty tracking
    private int dirtyCount = 0;
    private Entry<C> dirtyHead; // singly-linked dirty chain for O(dirty) iteration
    private int additionCount = 0;
    private int removalCount = 0;

    /**
     * Creates a filtered view (tracks nodes that have the given component type).
     */
    TraversalView(Class<C> componentType, TraversalOrder order) {
        this.componentType = componentType;
        this.order = order;
    }

    /**
     * Creates an unfiltered view (tracks all nodes).
     */
    @SuppressWarnings("unchecked")
    static TraversalView<Component> unfiltered(TraversalOrder order) {
        return new TraversalView<>(null, order);
    }

    /** @return the component type filter, or null if unfiltered. */
    public Class<C> componentType() { return componentType; }

    /** @return whether this is an unfiltered (all-nodes) view. */
    public boolean isUnfiltered() { return componentType == null; }

    /** @return the traversal order. */
    public TraversalOrder order() { return order; }

    /** @return the number of live entries. */
    public int liveCount() { return liveCount; }

    /**
     * Iterates all current (Node, Component) pairs in linked list order.
     * For unfiltered views, the component is null.
     */
    public void forEach(BiConsumer<Node, C> visitor) {
        Entry<C> current = head;
        while (current != null) {
            visitor.accept(current.node, current.component);
            current = current.next;
        }
    }

    /**
     * Iterates all nodes in linked list order (ignores component).
     */
    public void forEachNode(Consumer<Node> visitor) {
        Entry<C> current = head;
        while (current != null) {
            visitor.accept(current.node);
            current = current.next;
        }
    }

    /** @return the head entry (first in traversal order), or null if empty. */
    public Entry<C> head() { return head; }

    /** @return the tail entry (last in traversal order), or null if empty. */
    public Entry<C> tail() { return tail; }

    /**
     * Gets an entry by index (O(n) - prefer forEach for bulk access).
     */
    public Entry<C> get(int index) {
        Entry<C> current = head;
        for (int i = 0; i < index && current != null; i++) {
            current = current.next;
        }
        return current;
    }

    /**
     * Checks whether this view accepts a given node (based on its component filter).
     */
    boolean accepts(Node node) {
        if (componentType == null) return true; // unfiltered
        return node.findComponent(componentType) != null;
    }

    /**
     * Consumes accumulated dirty state since the last call and reports it to the caller.
     */
    public DirtyReport<C> applyPatches() {
        if (dirtyCount == 0 && additionCount == 0 && removalCount == 0) {
            return DirtyReport.clean();
        }

        boolean recommendFullRewrite = liveCount > 0 &&
                (dirtyCount + additionCount + removalCount) * 3 > liveCount;

        DirtyReport<C> report;
        if (recommendFullRewrite) {
            report = DirtyReport.fullRewrite();
        } else {
            List<Entry<C>> dirtyEntries = new ArrayList<>(dirtyCount);
            Entry<C> current = dirtyHead;
            while (current != null) {
                dirtyEntries.add(current);
                Entry<C> next = current.nextDirty;
                current.dirty = false;
                current.nextDirty = null;
                current = next;
            }
            report = new DirtyReport<>(false, dirtyEntries, additionCount, removalCount);
        }

        clearDirtyState();
        return report;
    }

    // --- Internal mutation methods (called by Tree) ---

    void addEntry(Node node, C component) {
        Entry<C> entry = new Entry<>(node, component);
        nodeToEntry.put(node, entry);

        if (tail == null) {
            head = tail = entry;
        } else {
            entry.prev = tail;
            tail.next = entry;
            tail = entry;
        }

        liveCount++;
        additionCount++;
        markEntryDirty(entry);
    }

    /**
     * Inserts a node into the list immediately after the given reference node's entry.
     * Used to maintain DFS pre-order: a new child is inserted after its parent's
     * last descendant already in the list.
     */
    void insertAfter(Node node, C component, Node afterNode) {
        Entry<C> afterEntry = nodeToEntry.get(afterNode);
        if (afterEntry == null) {
            // Fallback to tail append if reference not found
            addEntry(node, component);
            return;
        }

        Entry<C> entry = new Entry<>(node, component);
        nodeToEntry.put(node, entry);

        // Insert after afterEntry in the linked list
        entry.prev = afterEntry;
        entry.next = afterEntry.next;
        if (afterEntry.next != null) {
            afterEntry.next.prev = entry;
        } else {
            tail = entry;
        }
        afterEntry.next = entry;

        liveCount++;
        additionCount++;
        markEntryDirty(entry);
    }

    void removeEntry(Node node, C component) {
        Entry<C> entry = nodeToEntry.remove(node);
        if (entry == null) return;

        // Unlink from doubly-linked list - O(1)
        if (entry.prev != null) {
            entry.prev.next = entry.next;
        } else {
            head = entry.next;
        }
        if (entry.next != null) {
            entry.next.prev = entry.prev;
        } else {
            tail = entry.prev;
        }

        if (entry.dirty) {
            dirtyCount--;
        }

        liveCount--;
        removalCount++;
    }

    void markDirty(Node node) {
        Entry<C> entry = nodeToEntry.get(node);
        if (entry != null) {
            markEntryDirty(entry);
        }
    }

    /** @return true if the given node has an entry in this view. */
    boolean contains(Node node) {
        return nodeToEntry.containsKey(node);
    }

    /** @return the entry for the given node, or null. Package-private. */
    Entry<C> getEntry(Node node) {
        return nodeToEntry.get(node);
    }

    private void markEntryDirty(Entry<C> entry) {
        if (!entry.dirty) {
            entry.dirty = true;
            entry.nextDirty = dirtyHead;
            dirtyHead = entry;
            dirtyCount++;
        }
    }

    private void clearDirtyState() {
        Entry<C> current = dirtyHead;
        while (current != null) {
            Entry<C> next = current.nextDirty;
            current.dirty = false;
            current.nextDirty = null;
            current = next;
        }
        dirtyHead = null;
        dirtyCount = 0;
        additionCount = 0;
        removalCount = 0;
    }

    // --- Entry ---

    /**
     * A (Node, Component) pair in the traversal list.
     * Intrusive doubly-linked list node for O(1) operations.
     */
    public static final class Entry<C extends Component> {
        public final Node node;
        public final C component; // null for unfiltered views

        Entry<C> prev;
        Entry<C> next;

        boolean dirty;
        Entry<C> nextDirty;

        Entry(Node node, C component) {
            this.node = node;
            this.component = component;
        }

        /** @return the next entry in traversal order, or null. */
        public Entry<C> next() { return next; }

        /** @return the previous entry in traversal order, or null. */
        public Entry<C> prev() { return prev; }
    }

    /**
     * Report of dirty state since the last applyPatches() call.
     */
    public record DirtyReport<C extends Component>(
            boolean fullRewriteRecommended,
            List<Entry<C>> dirtyEntries,
            int additionCount,
            int removalCount
    ) {
        static <C extends Component> DirtyReport<C> clean() {
            return new DirtyReport<>(false, List.of(), 0, 0);
        }

        static <C extends Component> DirtyReport<C> fullRewrite() {
            return new DirtyReport<>(true, List.of(), 0, 0);
        }

        public boolean isClean() {
            return !fullRewriteRecommended && dirtyEntries.isEmpty() &&
                    additionCount == 0 && removalCount == 0;
        }
    }
}
