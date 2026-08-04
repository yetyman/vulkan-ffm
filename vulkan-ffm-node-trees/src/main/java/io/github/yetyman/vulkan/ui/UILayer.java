package io.github.yetyman.vulkan.ui;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.ui.input.InputEvent;
import java.lang.foreign.Arena;

/**
 * A self-contained UI rendering subsystem.
 *
 * Each layer owns its own pipelines, vertex formats, descriptor sets, and rendering approach.
 * Layers are composed vertically by UIComposite and share platform infrastructure (AssetRegistry)
 * but never share rendering internals.
 *
 * Lifecycle:
 *   1. Construction (layer-specific config)
 *   2. initialize(UIContext) - one-time GPU resource creation
 *   3. Per frame: update(UIFrameContext) then render or graph contribution
 *   4. resize(w, h) - on surface resize
 *   5. close() - GPU resource teardown
 */
public interface UILayer extends AutoCloseable {

    /** @return human-readable name for debug labels and render graph node naming. */
    String name();

    /**
     * Layer ordering - lower values drawn first (background), higher drawn last (foreground).
     * Input capture phase traverses highest-to-lowest. Bubble phase traverses lowest-to-highest.
     * Render order is lowest-to-highest (painter's algorithm at the layer level).
     */
    int order();

    /**
     * One-time initialization: create pipelines, allocate persistent buffers, load fonts/atlases.
     * Called once after UIComposite.build(). The UIContext remains valid for the layer's lifetime.
     */
    void initialize(UIContext ctx);

    /**
     * Per-frame update: process state changes, run layout, advance animations.
     * Called once per frame before render/graph contribution.
     * Must not record Vulkan commands - that happens in render() or contributeToGraph().
     */
    void update(UIFrameContext frame);

    /**
     * Contributes render graph nodes for this layer.
     * A layer may add any number of nodes (compute, transfer, graphics).
     * Called during graph construction. For static graphs, called once at startup.
     * For dynamic graphs, called when the graph is rebuilt.
     *
     * Layers that only use direct rendering may leave this as a no-op.
     *
     * @param graphBuilder graph builder instance; typed as Object until the render graph
     *                     builder interface's module location is finalized (see plan notes)
     */
    default void contributeToGraph(Object graphBuilder) {
        // Default no-op. Parameter type is Object until we decide where RenderGraphBuilder lives.
        // Will be replaced with proper type: RenderGraphBuilder
    }

    /**
     * Records draw commands into the provided command buffer.
     * Used in the direct rendering path (no render graph).
     * Called once per frame after update(). The layer should set its own viewport/scissor
     * and record all draw calls needed.
     *
     * Layers using exclusively the render graph path may leave this as a no-op.
     */
    default void render(VkCommandBuffer cmd, Arena frameArena) {}

    /** Handles resize of the rendering surface. Recreate size-dependent resources. */
    void resize(int width, int height);

    /**
     * Input handling - called during both capture and bubble phases.
     * The event's phase() indicates which pass is active.
     *
     * During capture (top-down): annotate event context, optionally stop propagation.
     * During bubble (bottom-up): react to event with full context, optionally consume.
     *
     * @return true if this layer consumed the event (stops propagation in current phase)
     */
    boolean handleInput(InputEvent event);

    /** @return whether this layer participates in input at all. False skips dispatch entirely. */
    default boolean acceptsInput() { return true; }

    /** @return whether this layer needs per-frame update calls. False skips update(). */
    default boolean needsUpdate() { return true; }
}
