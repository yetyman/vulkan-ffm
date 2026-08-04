package io.github.yetyman.vulkan.sample.ui.treedemo;

import io.github.yetyman.vulkan.nodetree.Component;
import io.github.yetyman.vulkan.nodetree.Dependency;
import io.github.yetyman.vulkan.nodetree.Node;
import io.github.yetyman.vulkan.ui2d.GestureEvent;
import io.github.yetyman.vulkan.ui2d.GestureRecognizer;
import io.github.yetyman.vulkan.ui2d.PointerEvent;
import io.github.yetyman.vulkan.ui2d.RectangleComponent;

import java.util.List;

/**
 * Per-node component that makes a rectangle draggable and highlightable.
 *
 * On hover: brightens color.
 * On press: brightens more.
 * On drag: moves this node's rect AND all descendant rects (parent-relative movement).
 *
 * Only responds to events where this node is the actual target — ignores events
 * bubbling up from children.
 */
public class DraggableComponent implements Component {

    private static final float HOVER_BRIGHTEN = 0.15f;
    private static final float PRESS_BRIGHTEN = 0.30f;

    private RectangleComponent rect;
    private GestureRecognizer gestures;
    private Node node;

    // Base color
    private float baseR, baseG, baseB;

    // Drag state
    private float dragStartRectX, dragStartRectY;

    @Override
    public List<Dependency<?>> requires() {
        return List.of(Dependency.selfRequired(RectangleComponent.class));
    }

    @Override
    public void onInit(Node node) {
        this.node = node;
    }

    @Override
    public void resolveDependencies(Node node) {
        this.rect = node.findComponent(RectangleComponent.class);
    }

    @Override
    public void afterResolve(Node node) {
        baseR = rect.r();
        baseG = rect.g();
        baseB = rect.b();

        gestures = node.tree().getTreeComponent(GestureRecognizer.class);

        node.addEventHandler(PointerEvent.ENTER, this::onPointerEnter);
        node.addEventHandler(PointerEvent.EXIT, this::onPointerExit);
        node.addEventHandler(GestureEvent.PRESS, this::onPress);
        node.addEventHandler(GestureEvent.DRAG_START, this::onDragStart);
        node.addEventHandler(GestureEvent.DRAG_MOVE, this::onDragMove);
        node.addEventHandler(GestureEvent.DRAG_END, this::onDragEnd);
    }

    private void onPointerEnter(PointerEvent event) {
        // Only react if this node is the direct target (not a bubble from child)
        if (event.target() != node) return;
        updateColor();
    }

    private void onPointerExit(PointerEvent event) {
        if (event.target() != node) return;
        updateColor();
    }

    private void onPress(GestureEvent event) {
        if (event.target() != node) return;
        updateColor();
    }

    private void onDragStart(GestureEvent event) {
        if (event.target() != node) return;
        dragStartRectX = rect.x();
        dragStartRectY = rect.y();
        updateColor();
    }

    private void onDragMove(GestureEvent event) {
        if (event.target() != node) return;

        // Compute delta from drag start
        float dx = event.x() - event.startX();
        float dy = event.y() - event.startY();

        // Move this rect
        float newX = dragStartRectX + dx;
        float newY = dragStartRectY + dy;
        float oldX = rect.x();
        float oldY = rect.y();
        rect.setPosition(newX, newY);

        // Move all descendant rects by the same delta
        float childDx = newX - oldX;
        float childDy = newY - oldY;
        moveDescendants(node, childDx, childDy);
    }

    private void onDragEnd(GestureEvent event) {
        if (event.target() != node) return;
        updateColor();
    }

    /**
     * Moves all descendant nodes' RectangleComponents by the given delta.
     */
    private void moveDescendants(Node parent, float dx, float dy) {
        for (Node child : parent.children()) {
            RectangleComponent childRect = child.findComponent(RectangleComponent.class);
            if (childRect != null) {
                childRect.setPosition(childRect.x() + dx, childRect.y() + dy);
            }
            moveDescendants(child, dx, dy);
        }
    }

    private void updateColor() {
        float brighten = 0;
        if (gestures != null) {
            if (gestures.isDragging(node) || gestures.isPressed(node)) {
                brighten = PRESS_BRIGHTEN;
            } else if (gestures.isHovered(node)) {
                brighten = HOVER_BRIGHTEN;
            }
        }
        rect.setColor(
                Math.min(1f, baseR + brighten),
                Math.min(1f, baseG + brighten),
                Math.min(1f, baseB + brighten),
                rect.a()
        );
    }

    public void setBaseColor(float r, float g, float b) {
        baseR = r;
        baseG = g;
        baseB = b;
        updateColor();
    }
}
