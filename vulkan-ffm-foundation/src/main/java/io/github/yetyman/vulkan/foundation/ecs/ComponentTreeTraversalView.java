package io.github.yetyman.vulkan.foundation.ecs;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Per-component-type traversal list (Level 2 in the three-level model).
 *
 * Maintains a list of (Node, Component) pairs in traversal order, incrementally
 * maintained as the tree mutates. Provides dirty tracking so that Level 3 (caller-owned
 * dense backing arrays) can be updated efficiently via applyPatches().
 *
 * Registration: via Tree.getOrCreateComponentTraversal(type, order, key).
 *
 * Thread safety: mutated immediately and synchronously, assumed single-logical-thread.
 * Cross-thread handoff is the caller's responsibility (e.g., double-buffering).
 *
 * @param <C> the component type this view tracks
 */
public class ComponentTreeTraversalView<C extends Component> {

    private final Class<C> componentType;
    private final TraversalOrder order;

    // The traversal list entries
    private final List<Entry<C>> entries = new ArrayList<>();

    // Dirty tracking: indices that have changed since last applyPatches()
    private final BitSet dirtyIndices = new BitSet();
    private int dirtyCount = 0;

    // Tracking additions and removals for the patch report
    private final List<Addition<C>> pendingAdditions = new ArrayList<>();
    private final List<Removal<C>> pendingRemovals = new ArrayList<>();

    ComponentTreeTraversalView(Class<C> componentType, TraversalOrder order) {
        this.componentType = componentType;
        this.order = order;
    }

    /** @return the component type this view tracks. */
    public Class<C> componentType() { return componentType; }

    /** @return the traversal order. */
    public TraversalOrder order() { return order; }

    /** @return the number of live entries. */
    public int liveCount() { return entries.size(); }

    /**
     * Iterates all current (Node, Component) pairs in traversal order.
     * For callers that want to rebuild their array from scratch rather than patch incrementally.
     */
    public void forEach(BiConsumer<Node, C> visitor) {
        for (Entry<C> entry : entries) {
            visitor.accept(entry.node, entry.component);
        }
    }

    /**
     * Gets the entry at a specific index.
     *
     * @param index the index
     * @return the entry
     */
    public Entry<C> get(int index) {
        return entries.get(index);
    }

    /**
     * Consumes accumulated dirty state since the last call and reports it to the caller.
     *
     * The returned report tells the caller what changed:
     * - If changes are sparse relative to total count, returns specific dirty indices
     * - If changes exceed the rewrite threshold, recommends a full rewrite
     *
     * @return a report describing what changed
     */
    public DirtyReport<C> applyPatches() {
        if (dirtyCount == 0 && pendingAdditions.isEmpty() && pendingRemovals.isEmpty()) {
            return DirtyReport.clean();
        }

        // Threshold: if dirty count > totalCount / 3, recommend full rewrite
        boolean recommendFullRewrite = entries.size() > 0 &&
                (dirtyCount + pendingAdditions.size() + pendingRemovals.size()) * 3 > entries.size();

        DirtyReport<C> report;
        if (recommendFullRewrite) {
            report = DirtyReport.fullRewrite();
        } else {
            int[] indices = new int[dirtyCount];
            int idx = 0;
            for (int i = dirtyIndices.nextSetBit(0); i >= 0; i = dirtyIndices.nextSetBit(i + 1)) {
                if (idx < indices.length) {
                    indices[idx++] = i;
                }
            }
            report = new DirtyReport<>(
                    false,
                    Arrays.copyOf(indices, idx),
                    List.copyOf(pendingAdditions),
                    List.copyOf(pendingRemovals)
            );
        }

        // Clear dirty state
        dirtyIndices.clear();
        dirtyCount = 0;
        pendingAdditions.clear();
        pendingRemovals.clear();

        return report;
    }

    // --- Internal mutation methods (called by Tree) ---

    void addEntry(Node node, C component) {
        int index = entries.size();
        entries.add(new Entry<>(node, component));
        markDirtyIndex(index);
        pendingAdditions.add(new Addition<>(node, component, index));
    }

    void removeEntry(Node node, C component) {
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).node == node && entries.get(i).component == component) {
                index = i;
                break;
            }
        }
        if (index < 0) return;

        Entry<C> removed = entries.remove(index);
        pendingRemovals.add(new Removal<>(node, component, index));

        // If we used swap-remove, the moved element's index is dirty
        // For now using simple remove - the indices after the removal are all shifted
        // Mark the removal index as dirty
        markDirtyIndex(index);

        // All indices after the removed one shift down - mark them dirty
        for (int i = index; i < entries.size(); i++) {
            markDirtyIndex(i);
        }
    }

    void markDirty(Node node) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).node == node) {
                markDirtyIndex(i);
            }
        }
    }

    private void markDirtyIndex(int index) {
        if (!dirtyIndices.get(index)) {
            dirtyIndices.set(index);
            dirtyCount++;
        }
    }

    // --- Entry and report types ---

    /**
     * A (Node, Component) pair in the traversal list.
     */
    public record Entry<C extends Component>(Node node, C component) {}

    /**
     * Records an addition to the traversal list.
     */
    public record Addition<C extends Component>(Node node, C component, int index) {}

    /**
     * Records a removal from the traversal list.
     */
    public record Removal<C extends Component>(Node node, C component, int previousIndex) {}

    /**
     * Report of dirty state since the last applyPatches() call.
     */
    public record DirtyReport<C extends Component>(
            boolean fullRewriteRecommended,
            int[] dirtyIndices,
            List<Addition<C>> additions,
            List<Removal<C>> removals
    ) {
        /** No changes. */
        static <C extends Component> DirtyReport<C> clean() {
            return new DirtyReport<>(false, new int[0], List.of(), List.of());
        }

        /** Full rewrite recommended due to high churn. */
        static <C extends Component> DirtyReport<C> fullRewrite() {
            return new DirtyReport<>(true, new int[0], List.of(), List.of());
        }

        /** @return true if nothing has changed. */
        public boolean isClean() {
            return !fullRewriteRecommended && dirtyIndices.length == 0 &&
                    additions.isEmpty() && removals.isEmpty();
        }
    }
}
