package io.github.yetyman.vulkan.foundation.ui;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.foundation.ui.input.UIInputDispatcher;
import io.github.yetyman.vulkan.foundation.ui.input.UIInputEvent;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates a set of UILayers: initialization, update, rendering, input dispatch, resize.
 * Layers are sorted by order() at build time. Adding/removing layers after build is not
 * supported (rebuild the composite if the layer set changes).
 */
public class UIComposite implements AutoCloseable {

    private final List<UILayer> layers; // sorted by order(), lowest first
    private final UIInputDispatcher inputDispatcher;
    private final UIContext ctx;

    private UIComposite(List<UILayer> layers, UIContext ctx) {
        this.layers = layers;
        this.ctx = ctx;
        this.inputDispatcher = new UIInputDispatcher();
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

    /** Direct render path - records all layers' commands in order (lowest first). */
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        for (UILayer layer : layers) {
            layer.render(cmd, frameArena);
        }
    }

    /** Dispatches an input event through capture/bubble phases across all layers. */
    public void dispatchInput(UIInputEvent event) {
        inputDispatcher.dispatch(event, layers);
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

        private Builder() {}

        /** Adds a layer to the composite. Order among added layers is normalized by order() at build(). */
        public Builder layer(UILayer layer) { layers.add(layer); return this; }

        /** Sets the shared platform context passed to every layer's initialize(). */
        public Builder context(UIContext ctx) { this.ctx = ctx; return this; }

        public UIComposite build() {
            if (ctx == null) throw new IllegalStateException("UIContext not set");
            if (layers.isEmpty()) throw new IllegalStateException("No layers added");
            List<UILayer> sorted = new ArrayList<>(layers);
            sorted.sort(Comparator.comparingInt(UILayer::order));
            return new UIComposite(sorted, ctx);
        }
    }
}
