package io.github.yetyman.vulkan.foundation.ecs;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.foundation.ui.UIContext;
import io.github.yetyman.vulkan.foundation.ui.UIFrameContext;
import io.github.yetyman.vulkan.foundation.ui.UILayer;
import io.github.yetyman.vulkan.foundation.ui.input.InputEvent;

import java.lang.foreign.Arena;

/**
 * A UILayer implementation that bridges the ECS node tree into the existing
 * multilayer rendering pipeline.
 *
 * This is the integration point between the UIComposite layer system and the ECS tree.
 * It owns a Tree and provides the update/render/input lifecycle bridging.
 *
 * Responsibilities:
 * - Holds a Tree instance and manages its lifecycle
 * - Bridges UIInputEvents into the tree's event system
 * - Provides update() calls for tree components to do per-frame processing
 * - Provides render() for tree components that record draw commands
 *
 * Hit-testing and spatial input dispatch are NOT handled here - that's the responsibility
 * of specific components within the tree (e.g., a future hit-test system component).
 * This layer bridges at the level of "this tree is a participant in the layer stack."
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
        return onInput(event);
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

    // --- Extension points for subclasses ---

    /**
     * Called during initialize() to populate the tree with initial nodes and components.
     * The tree is not yet initialized (initialize() has not been called on it).
     *
     * @param tree the tree to populate
     */
    protected abstract void buildTree(Tree tree);

    /**
     * Called after the tree has been built and initialized.
     * Register tree components, traversal views, etc. here.
     */
    protected void onInitialized(UIContext ctx) {}

    /**
     * Called once per frame for update logic.
     * Tree components should do their per-frame processing here.
     */
    protected void onUpdate(UIFrameContext frame) {}

    /**
     * Called once per frame to record render commands.
     * Tree components that render should use their traversal views here.
     */
    protected void onRender(VkCommandBuffer cmd, Arena frameArena) {}

    /**
     * Called on surface resize.
     */
    protected void onResize(int width, int height) {}

    /**
     * Called when an input event reaches this layer.
     *
     * Default implementation does nothing. Subclasses that want to bridge input
     * into the tree's event system should override this, perform hit-testing
     * (if spatial), select a target node, and call node.fireEvent() with an
     * appropriate ECS event.
     *
     * @return true if the event was consumed
     */
    protected boolean onInput(InputEvent event) { return false; }
}
