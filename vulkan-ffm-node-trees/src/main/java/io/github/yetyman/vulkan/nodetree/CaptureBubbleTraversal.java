package io.github.yetyman.vulkan.nodetree;

/**
 * Encapsulates capture/bubble traversal over a TraversalView.
 *
 * Capture walks the view head→tail (typically root→leaves in DFS pre-order).
 * Bubble walks the view tail→head (leaves→root).
 *
 * This is a utility for any code that wants capture/bubble semantics over a set of nodes.
 * It is NOT tied to input handling specifically — it's just "walk forward, walk backward,
 * invoking handlers at each node."
 *
 * A UILayer that wants to dispatch events through its tree creates one of these from
 * a traversal view and calls handleEvent. A layer that doesn't want capture/bubble
 * just walks the view directly however it likes.
 *
 * Zero allocation per dispatch. Walks the linked list directly.
 */
public class CaptureBubbleTraversal {

    private final TraversalView<Component> view;

    /**
     * Creates a capture/bubble traversal over the given view.
     */
    public CaptureBubbleTraversal(TraversalView<Component> view) {
        this.view = view;
    }

    /**
     * Creates a capture/bubble traversal over a tree's built-in all-nodes view.
     */
    public CaptureBubbleTraversal(Tree tree) {
        this(tree.allNodes());
    }

    /**
     * Dispatches an event through the view using capture/bubble semantics.
     *
     * Capture: head→tail (each node's handlers invoked in forward order)
     * Bubble: tail→head (each node's handlers invoked in reverse order)
     *
     * stopPropagation() halts the current phase. Stopped resets between phases.
     *
     * @param event the event to dispatch
     * @return true if propagation was stopped during bubble (event consumed)
     */
    public boolean handleEvent(Event event) {
        handleEventCapture(event);
        event.resetForBubble();
        handleEventBubble(event);
        return event.isStopped();
    }

    /**
     * Capture phase only: walks the view head→tail.
     * Sets phase to CAPTURE and invokes handlers at each node in forward order.
     *
     * @param event the event to dispatch through capture
     */
    public void handleEventCapture(Event event) {
        event.setPhase(Event.Phase.CAPTURE);
        TraversalView.Entry<Component> entry = view.head();
        while (entry != null) {
            if (event.isStopped()) break;
            entry.node.dispatchToHandlers(event);
            entry = entry.next();
        }
    }

    /**
     * Bubble phase only: walks the view tail→head.
     * Sets phase to BUBBLE and invokes handlers at each node in reverse order.
     *
     * @param event the event to dispatch through bubble
     */
    public void handleEventBubble(Event event) {
        event.setPhase(Event.Phase.BUBBLE);
        TraversalView.Entry<Component> entry = view.tail();
        while (entry != null) {
            if (event.isStopped()) break;
            entry.node.dispatchToHandlers(event);
            entry = entry.prev();
        }
    }

    /** @return the view this traversal walks. */
    public TraversalView<Component> view() { return view; }
}
