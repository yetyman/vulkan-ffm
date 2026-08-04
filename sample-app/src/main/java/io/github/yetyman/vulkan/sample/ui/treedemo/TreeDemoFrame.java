package io.github.yetyman.vulkan.sample.ui.treedemo;

import io.github.yetyman.vulkan.nodetree.Node;
import io.github.yetyman.vulkan.nodetree.Tree;
import io.github.yetyman.vulkan.ui2d.*;

import java.util.Random;

/**
 * Builds and manages the node tree for the draggable squares demo.
 *
 * Scene structure:
 * - Root node (no visual)
 *   - Several parent rectangles (large, draggable)
 *     - Each contains child rectangles (smaller, also draggable)
 *
 * Components per rectangle node:
 * - RectangleComponent (data: position, size, color)
 * - DraggableComponent (behavior: responds to gesture events, updates color/position)
 *
 * Tree-scoped components:
 * - GestureRecognizer (watches pointer events, fires gesture events, tracks state)
 * - SpatialGrid (hit-testing acceleration)
 * - NineSliceRenderer (bulk vertex buffer for all rects)
 */
public class TreeDemoFrame {

    private final Tree tree;
    private final GestureRecognizer gestures;
    private final SpatialGrid spatialGrid;
    private final NineSliceRenderer renderer;

    private int width;
    private int height;

    public TreeDemoFrame(int width, int height) {
        this.width = width;
        this.height = height;
        this.tree = new Tree();

        // Register GestureRecognizer first — DraggableComponents need it during afterResolve
        this.gestures = tree.getOrRegisterTreeComponent(new GestureRecognizer(8.0f));

        // Build the scene (creates nodes with components)
        buildScene();

        // Initialize the tree (runs lifecycle for all node-scoped components)
        tree.initialize();

        // Register renderer and spatial grid AFTER nodes are initialized
        this.spatialGrid = tree.getOrRegisterTreeComponent(new SpatialGrid(width, height, 50));
        this.renderer = tree.getOrRegisterTreeComponent(new NineSliceRenderer(8192));
    }

    private void buildScene() {
        Random rng = new Random(42);
        Node root = tree.root();

        // 30x10 grid of panels, each with 6 children, each child with 3 grandchildren
        // Total: 300 panels + 1800 children + 5400 grandchildren = 7500 rects
        int cols = 30;
        int rows = 10;
        float panelW = 60, panelH = 50;
        float gapX = 5, gapY = 5;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                float px = 5 + col * (panelW + gapX);
                float py = 5 + row * (panelH + gapY);

                Node panel = root.createChild();
                RectangleComponent panelRect = panel.addComponent(new RectangleComponent(px, py, panelW, panelH));
                panelRect.setColor(0.15f + rng.nextFloat() * 0.15f,
                        0.15f + rng.nextFloat() * 0.15f,
                        0.25f + rng.nextFloat() * 0.2f);
                panelRect.setUniformInset(2);
                panel.addComponent(new DraggableComponent());

                // 6 children per panel (2x3 grid inside)
                for (int ci = 0; ci < 6; ci++) {
                    float cx = px + 3 + (ci % 2) * 27;
                    float cy = py + 3 + (ci / 2) * 15;
                    float cw = 25, ch = 13;

                    Node child = panel.createChild();
                    RectangleComponent childRect = child.addComponent(new RectangleComponent(cx, cy, cw, ch));
                    childRect.setColor(0.3f + rng.nextFloat() * 0.4f,
                            0.25f + rng.nextFloat() * 0.4f,
                            0.2f + rng.nextFloat() * 0.3f);
                    childRect.setUniformInset(1);
                    child.addComponent(new DraggableComponent());

                    // 3 grandchildren per child (stacked horizontally inside)
                    for (int gi = 0; gi < 3; gi++) {
                        float gx = cx + 2 + gi * 7;
                        float gy = cy + 2;
                        float gw = 6, gh = 9;

                        Node grandchild = child.createChild();
                        RectangleComponent gcRect = grandchild.addComponent(new RectangleComponent(gx, gy, gw, gh));
                        gcRect.setColor(0.5f + rng.nextFloat() * 0.5f,
                                0.4f + rng.nextFloat() * 0.5f,
                                0.3f + rng.nextFloat() * 0.4f);
                        grandchild.addComponent(new DraggableComponent());
                    }
                }
            }
        }
    }

    // --- Input dispatch (called by the app on input events) ---

    /**
     * Handles a pointer move. Hit-tests, fires enter/exit and move events.
     */
    public void onPointerMove(float x, float y, int pointerId) {
        Node hit = spatialGrid.hitTest(x, y);
        Node prevHover = gestures.hoveredNode();

        // Fire exit on previous hover if changed
        if (prevHover != null && prevHover != hit) {
            prevHover.fireEvent(new PointerEvent(PointerEvent.EXIT, x, y, -1, pointerId, prevHover));
        }

        // Fire enter on new hover if changed
        if (hit != null && hit != prevHover) {
            hit.fireEvent(new PointerEvent(PointerEvent.ENTER, x, y, -1, pointerId, hit));
        }

        // Fire move — on capture target if dragging, otherwise on hit
        Node moveTarget = gestures.captureTarget(pointerId);
        if (moveTarget == null) moveTarget = hit;
        if (moveTarget != null) {
            moveTarget.fireEvent(new PointerEvent(PointerEvent.MOVE, x, y, -1, pointerId, moveTarget));
        }
    }

    /**
     * Handles pointer down. Hit-tests and fires down event.
     */
    public void onPointerDown(float x, float y, int button, int pointerId) {
        Node hit = spatialGrid.hitTest(x, y);
        if (hit != null) {
            hit.fireEvent(new PointerEvent(PointerEvent.DOWN, x, y, button, pointerId, hit));
        }
    }

    /**
     * Handles pointer up. Fires up event on capture target or hit.
     */
    public void onPointerUp(float x, float y, int button, int pointerId) {
        Node target = gestures.captureTarget(pointerId);
        if (target == null) {
            target = spatialGrid.hitTest(x, y);
        }
        if (target != null) {
            target.fireEvent(new PointerEvent(PointerEvent.UP, x, y, button, pointerId, target));
        }
    }

    // --- Rendering access ---

    /** @return the renderer's vertex data for GPU upload. */
    public float[] vertexData() { return renderer.vertexData(); }

    /** @return total vertex count for the draw call. */
    public int vertexCount() { return renderer.vertexCount(); }

    /** @return true if vertex data has changed since last frame. */
    public boolean isDirty() { return renderer.isDirty(); }

    /** Clears dirty flags after GPU upload. */
    public void clearDirty() { renderer.clearDirty(); }

    /** @return the NineSliceRenderer for direct access if needed. */
    public NineSliceRenderer renderer() { return renderer; }

    /** @return the tree. */
    public Tree tree() { return tree; }

    /** @return whether any element is being interacted with (for cursor changes). */
    public boolean isInteracting() {
        return gestures.hoveredNode() != null || gestures.captureTarget(0) != null;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        spatialGrid.resize(width, height, 100);
        spatialGrid.markStructureDirty();
    }

    public void close() {
        tree.close();
    }
}
