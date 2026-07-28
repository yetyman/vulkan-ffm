package io.github.yetyman.vulkan.ui2d;

import io.github.yetyman.vulkan.foundation.ecs.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Tree-scoped gesture recognizer.
 *
 * Watches pointer events flowing through the tree and maintains gesture state per pointer.
 * Synthesizes higher-level events: Press, DragStart, DragMove, DragEnd.
 * Also tracks which node is currently hovered, focused, and pressed.
 *
 * Individual nodes query this component for their interaction state rather than
 * storing it themselves — avoids per-node state propagation and traversal view updates
 * on every focus/hover change.
 *
 * State machine per pointer:
 *   IDLE → (pointer down on node A) → PRESSED
 *   PRESSED → (move > dragThreshold) → DRAGGING, fire DragStart on A
 *   PRESSED → (pointer up, still on A) → IDLE, fire Press on A
 *   PRESSED → (pointer up, off A) → IDLE (cancel, no press fired)
 *   DRAGGING → (pointer move) → fire DragMove on A
 *   DRAGGING → (pointer up) → IDLE, fire DragEnd on A
 */
public class GestureRecognizer implements TreeComponent {

    /** Distance threshold (in pixels) before a press becomes a drag. */
    private float dragThreshold = 5.0f;

    // Per-pointer tracking
    private final Map<Integer, PointerState> pointers = new HashMap<>();

    // Current hover state (not per-pointer — just the latest pointer position)
    private Node hoveredNode;

    // Keyboard focus (separate from pointer)
    private Node focusedNode;

    // Tree reference for firing events
    private Tree tree;

    public GestureRecognizer() {}

    public GestureRecognizer(float dragThreshold) {
        this.dragThreshold = dragThreshold;
    }

    // --- TreeComponent lifecycle ---

    @Override
    public void onInit(Tree tree) {
        this.tree = tree;
    }

    @Override
    public void afterResolve(Tree tree) {
        // Register receive-all handler on the tree root to observe pointer events.
        // We listen on root so we see all events regardless of target.
        tree.root().addReceiveAllHandler(this::onEvent);
    }

    @Override
    public void close(Tree tree) {
        pointers.clear();
        hoveredNode = null;
        focusedNode = null;
    }

    // --- Event handling ---

    private void onEvent(Event event) {
        if (event instanceof PointerEvent pe) {
            EventType<?> type = pe.eventType();
            if (type == PointerEvent.DOWN) onPointerDown(pe);
            else if (type == PointerEvent.MOVE) onPointerMove(pe);
            else if (type == PointerEvent.UP) onPointerUp(pe);
            else if (type == PointerEvent.ENTER) onPointerEnter(pe);
            else if (type == PointerEvent.EXIT) onPointerExit(pe);
        }
    }

    private void onPointerDown(PointerEvent event) {
        PointerState state = new PointerState();
        state.captureTarget = event.target();
        state.startX = event.x();
        state.startY = event.y();
        state.currentX = event.x();
        state.currentY = event.y();
        state.button = event.button();
        state.phase = GesturePhase.PRESSED;
        pointers.put(event.pointerId(), state);
    }

    private void onPointerMove(PointerEvent event) {
        PointerState state = pointers.get(event.pointerId());
        if (state == null) {
            // No active pointer — just update hover
            updateHover(event.target());
            return;
        }

        state.currentX = event.x();
        state.currentY = event.y();

        if (state.phase == GesturePhase.PRESSED) {
            // Check drag threshold
            float dx = state.currentX - state.startX;
            float dy = state.currentY - state.startY;
            if (dx * dx + dy * dy > dragThreshold * dragThreshold) {
                state.phase = GesturePhase.DRAGGING;
                fireDragStart(state, event);
            }
        } else if (state.phase == GesturePhase.DRAGGING) {
            fireDragMove(state, event);
        }

        updateHover(event.target());
    }

    private void onPointerUp(PointerEvent event) {
        PointerState state = pointers.remove(event.pointerId());
        if (state == null) return;

        if (state.phase == GesturePhase.PRESSED) {
            // Was still in pressed state (no drag) — check if pointer is still on capture target
            if (event.target() == state.captureTarget) {
                firePress(state, event);
            }
            // else: pointer moved off the target before up — cancel, no press
        } else if (state.phase == GesturePhase.DRAGGING) {
            fireDragEnd(state, event);
        }
    }

    private void onPointerEnter(PointerEvent event) {
        updateHover(event.target());
    }

    private void onPointerExit(PointerEvent event) {
        if (hoveredNode == event.target()) {
            hoveredNode = null;
        }
    }

    // --- Synthetic event firing ---

    private void firePress(PointerState state, PointerEvent source) {
        GestureEvent press = new GestureEvent(GestureEvent.PRESS,
                source.x(), source.y(), state.startX, state.startY,
                state.button, source.pointerId(), state.captureTarget);
        state.captureTarget.fireEvent(press);
    }

    private void fireDragStart(PointerState state, PointerEvent source) {
        GestureEvent drag = new GestureEvent(GestureEvent.DRAG_START,
                state.currentX, state.currentY, state.startX, state.startY,
                state.button, source.pointerId(), state.captureTarget);
        state.captureTarget.fireEvent(drag);
    }

    private void fireDragMove(PointerState state, PointerEvent source) {
        GestureEvent drag = new GestureEvent(GestureEvent.DRAG_MOVE,
                state.currentX, state.currentY, state.startX, state.startY,
                state.button, source.pointerId(), state.captureTarget);
        state.captureTarget.fireEvent(drag);
    }

    private void fireDragEnd(PointerState state, PointerEvent source) {
        GestureEvent drag = new GestureEvent(GestureEvent.DRAG_END,
                source.x(), source.y(), state.startX, state.startY,
                state.button, source.pointerId(), state.captureTarget);
        state.captureTarget.fireEvent(drag);
    }

    // --- Hover/focus management ---

    private void updateHover(Node newHover) {
        if (newHover != hoveredNode) {
            hoveredNode = newHover;
        }
    }

    // --- State queries (called by node render components) ---

    /** @return true if the given node is currently under the pointer. */
    public boolean isHovered(Node node) {
        return node == hoveredNode;
    }

    /** @return true if any pointer is currently pressed down on this node. */
    public boolean isPressed(Node node) {
        for (PointerState state : pointers.values()) {
            if (state.captureTarget == node && state.phase == GesturePhase.PRESSED) {
                return true;
            }
        }
        return false;
    }

    /** @return true if this node is currently being dragged. */
    public boolean isDragging(Node node) {
        for (PointerState state : pointers.values()) {
            if (state.captureTarget == node && state.phase == GesturePhase.DRAGGING) {
                return true;
            }
        }
        return false;
    }

    /** @return true if this node has keyboard focus. */
    public boolean isFocused(Node node) {
        return node == focusedNode;
    }

    /** @return the currently hovered node, or null. */
    public Node hoveredNode() { return hoveredNode; }

    /** @return the currently focused node, or null. */
    public Node focusedNode() { return focusedNode; }

    /**
     * Sets keyboard focus to the given node (or null to clear focus).
     */
    public void setFocus(Node node) {
        this.focusedNode = node;
    }

    /**
     * @return the node that has pointer capture for the given pointer ID, or null.
     */
    public Node captureTarget(int pointerId) {
        PointerState state = pointers.get(pointerId);
        return state != null ? state.captureTarget : null;
    }

    /** Sets the drag distance threshold. */
    public void setDragThreshold(float threshold) {
        this.dragThreshold = threshold;
    }

    public float dragThreshold() { return dragThreshold; }

    // --- Internal state ---

    private enum GesturePhase {
        PRESSED, DRAGGING
    }

    private static class PointerState {
        Node captureTarget;
        float startX, startY;
        float currentX, currentY;
        int button;
        GesturePhase phase;
    }
}
