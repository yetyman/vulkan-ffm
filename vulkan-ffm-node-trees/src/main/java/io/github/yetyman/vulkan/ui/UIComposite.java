package io.github.yetyman.vulkan.ui;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.ui.input.InputDispatchStrategy;
import io.github.yetyman.vulkan.ui.input.InputEvent;
import io.github.yetyman.vulkan.ui.input.UIInputDispatcher;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Orchestrates a set of UILayers: initialization, update, rendering, input dispatch, resize.
 * Layers are sorted by order() at build time. Adding/removing layers after build is not
 * supported (rebuild the composite if the layer set changes).
 *
 * Thread safety:
 *   - {@link #submitInput(InputEvent)} is safe to call from any thread. Events are queued
 *     in a lock-free ConcurrentLinkedQueue and drained on the dispatch thread via
 *     {@link #drainInput()}.
 *   - {@link #dispatchInput(InputEvent)} dispatches immediately and synchronously. Use this
 *     for same-thread dispatch (e.g., from GLFW callbacks on the main thread).
 *   - All other methods (update, render, resize) must be called from their owning thread.
 */
public class UIComposite implements AutoCloseable {

    private final List<UILayer> layers; // sorted by order(), lowest first
    private final InputDispatchStrategy inputStrategy;
    private final UIContext ctx;
    private final ConcurrentLinkedQueue<InputEvent> eventQueue = new ConcurrentLinkedQueue<>();

    private UIComposite(List<UILayer> layers, UIContext ctx, InputDispatchStrategy inputStrategy) {
        this.layers = layers;
        this.ctx = ctx;
        this.inputStrategy = inputStrategy;
    }

    public static Builder builder() { return new Builder(); }

    /** Initializes all layers in order (lowest first). */
    public void initialize() {
        for (UILayer layer : layers) {
            layer.initialize(ctx);
        }
    }

    /** Updates all layers that need it. */
    public void update(UIFrameContext frame) {
        for (UILayer layer : layers) {
            if (layer.needsUpdate()) {
                layer.update(frame);
            }
        }
    }

    /** Contributes all layers to the render graph (lowest order first = drawn first). */
    public void contributeToGraph(Object graphBuilder) {
        for (UILayer layer : layers) {
            layer.contributeToGraph(graphBuilder);
        }
    }

    /** Direct pre-render path - records all layers' pre-render commands in order (lowest first).
     * Must be called BEFORE the render pass begins. */
    public void preRender(VkCommandBuffer cmd, Arena frameArena) {
        for (UILayer layer : layers) {
            layer.preRender(cmd, frameArena);
        }
    }

    /** Direct render path - records all layers' commands in order (lowest first). */
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        for (UILayer layer : layers) {
            layer.render(cmd, frameArena);
        }
    }

    /**
     * Submits an input event for deferred dispatch. Thread-safe — can be called from any
     * thread (gamepad polling thread, network thread, audio control thread, etc.).
     * Events are dispatched in submission order when {@link #drainInput()} is called.
     */
    public void submitInput(InputEvent event) {
        eventQueue.add(event);
    }

    /**
     * Drains all queued input events and dispatches them through the strategy.
     * Call this once per frame on the dispatch thread (typically the main/update thread).
     * Events submitted during drain are processed in the next drain cycle (no reentrance).
     */
    public void drainInput() {
        InputEvent event;
        while ((event = eventQueue.poll()) != null) {
            inputStrategy.dispatch(event);
        }
    }

    /**
     * Dispatches an input event immediately and synchronously through the strategy.
     * Use for same-thread dispatch where queuing latency is undesirable (e.g., from
     * GLFW callbacks that already fire on the dispatch thread).
     */
    public void dispatchInput(InputEvent event) {
        inputStrategy.dispatch(event);
    }

    /** Notifies all layers of resize. */
    public void resize(int width, int height) {
        ctx.updateDimensions(width, height);
        for (UILayer layer : layers) {
            layer.resize(width, height);
        }
    }

    /** Closes all layers in reverse order (highest first). */
    @Override
    public void close() {
        for (int i = layers.size() - 1; i >= 0; i--) {
            try {
                layers.get(i).close();
            } catch (Exception e) {
                // log and continue - don't let one layer's failure prevent others from closing
            }
        }
    }

    /** @return the layers in this composite, sorted ascending by order(). */
    public List<UILayer> layers() { return layers; }

    /** @return the shared platform context. */
    public UIContext context() { return ctx; }

    public static class Builder {
        private final List<UILayer> layers = new ArrayList<>();
        private UIContext ctx;
        private InputDispatchStrategy strategy;

        private Builder() {}

        /** Adds a layer to the composite. Order among added layers is normalized by order() at build(). */
        public Builder layer(UILayer layer) { layers.add(layer); return this; }

        /** Sets the shared platform context passed to every layer's initialize(). */
        public Builder context(UIContext ctx) { this.ctx = ctx; return this; }

        /** Sets a custom input dispatch strategy. Defaults to capture/bubble (UIInputDispatcher). */
        public Builder inputStrategy(InputDispatchStrategy strategy) { this.strategy = strategy; return this; }

        public UIComposite build() {
            if (ctx == null) throw new IllegalStateException("UIContext not set");
            if (layers.isEmpty()) throw new IllegalStateException("No layers added");
            List<UILayer> sorted = new ArrayList<>(layers);
            sorted.sort(Comparator.comparingInt(UILayer::order));

            InputDispatchStrategy effectiveStrategy = (strategy != null) ? strategy : new UIInputDispatcher();
            UIComposite composite = new UIComposite(sorted, ctx, effectiveStrategy);

            // Bind strategy to a layer provider that filters for input-accepting layers
            effectiveStrategy.bind(() -> sorted.stream()
                .filter(UILayer::acceptsInput)
                .toList());

            return composite;
        }
    }
}
