package io.github.yetyman.vulkan.nodetree;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.ui.UIFrameContext;
import io.github.yetyman.vulkan.ui.UILayer;
import io.github.yetyman.vulkan.ui.input.InputEvent;

import java.lang.foreign.Arena;

/**
 * A UILayer that uses a Tree for internal organization.
 *
 * This is a convenience base class. It owns a Tree and runs its lifecycle alongside
 * the layer lifecycle. How (or whether) the subclass uses traversal views, dispatches
 * events through the tree, or renders from it is entirely up to the subclass.
 *
 * Trees are organizational — not event handlers. A subclass that wants capture/bubble
 * can create a CaptureBubbleTraversal from a view. A subclass that just uses the tree
 * for render data doesn't need to handle input through it at all.
 */
public abstract class TreeLayer implements UILayer {

    private final String name;
    private final int order;
    private Tree tree;
    private UIContext ctx;

    protected TreeLayer(String name, int order) {
        this.name = name;
        this.order = order;
    }

    // --- UILayer implementation ---

    @Override
    public String name() { return name; }

    @Override
    public int order() { return order; }

    @Override
    public void initialize(UIContext ctx) {
        this.ctx = ctx;
        this.tree = new Tree();
        buildTree(tree);
        tree.initialize();
        onInitialized(ctx);
    }

    @Override
    public void update(UIFrameContext frame) {
        onUpdate(frame);
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        onRender(cmd, frameArena);
    }

    @Override
    public void resize(int width, int height) {
        onResize(width, height);
    }

    @Override
    public boolean handleInput(InputEvent event) {
        return false; // Subclass decides if/how to handle input
    }

    @Override
    public void close() {
        if (tree != null) {
            tree.close();
            tree = null;
        }
    }

    // --- Access ---

    /** @return the tree owned by this layer. */
    protected Tree tree() { return tree; }

    /** @return the UI context. */
    protected UIContext context() { return ctx; }

    // --- Extension points ---

    /**
     * Called during initialize() to populate the tree with initial nodes and components.
     * The tree is not yet initialized (initialize() has not been called on it).
     */
    protected abstract void buildTree(Tree tree);

    /** Called after the tree has been built and initialized. */
    protected void onInitialized(UIContext ctx) {}

    /** Called once per frame for update logic. */
    protected void onUpdate(UIFrameContext frame) {}

    /** Called once per frame to record render commands. */
    protected void onRender(VkCommandBuffer cmd, Arena frameArena) {}

    /** Called on surface resize. */
    protected void onResize(int width, int height) {}
}
