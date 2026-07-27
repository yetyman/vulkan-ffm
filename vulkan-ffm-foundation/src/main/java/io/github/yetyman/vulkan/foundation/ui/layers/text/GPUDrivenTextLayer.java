package io.github.yetyman.vulkan.foundation.ui.layers.text;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.foundation.ui.UIContext;
import io.github.yetyman.vulkan.foundation.ui.UIFrameContext;
import io.github.yetyman.vulkan.foundation.ui.UILayer;
import io.github.yetyman.vulkan.foundation.ui.assets.FontRegistry;
import io.github.yetyman.vulkan.foundation.ui.input.InputEvent;

import java.lang.foreign.Arena;
import java.util.function.Consumer;

/**
 * UILayer that renders text using FontRegistry's rasterized glyph atlas via a GPU-driven
 * instanced-quad pipeline (TextRenderer). This is the minimal proof of the font pipeline
 * end-to-end: rasterization (FontRegistry/stb_truetype) -> atlas packing -> GPU upload ->
 * instanced draw.
 *
 * Usage:
 *   GPUDrivenTextLayer layer = new GPUDrivenTextLayer();
 *   layer.initialize(ctx); // registers/loads fonts via layer.fonts() beforehand
 *   layer.setFrameCallback(batch -> batch.drawText("default", "Hello", 50, 100, 32,
 *       1, 1, 1, 1));
 *
 * Text is drawn against a single font per frame (see TextRenderer for the reasoning);
 * calling drawText() with different fontIds across frames is fine, but mixing fonts within
 * one frame's batch is not supported by this minimal layer.
 */
public class GPUDrivenTextLayer implements UILayer {

    private static final int DEFAULT_ORDER = 900; // drawn late, near the top of the stack

    private final int order;
    private final String fontId;

    private UIContext ctx;
    private FontRegistry fonts;
    private TextRenderer renderer;
    private TextBatch batch;

    private Consumer<TextBatch> frameCallback;

    public GPUDrivenTextLayer(String fontId) {
        this(fontId, DEFAULT_ORDER);
    }

    public GPUDrivenTextLayer(String fontId, int order) {
        this.fontId = fontId;
        this.order = order;
    }

    /** Sets the per-frame callback that issues drawText() calls against the batch. */
    public void setFrameCallback(Consumer<TextBatch> callback) {
        this.frameCallback = callback;
    }

    @Override
    public String name() { return "gpu-driven-text"; }

    @Override
    public int order() { return order; }

    @Override
    public void initialize(UIContext ctx) {
        this.ctx = ctx;
        this.fonts = FontRegistry.from(ctx.assets());
        this.batch = new TextBatch(fonts);
        this.renderer = new TextRenderer(ctx);
        this.renderer.initialize();
    }

    @Override
    public void update(UIFrameContext frame) {
        batch.clear();
        if (frameCallback != null) {
            frameCallback.accept(batch);
        }
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        if (batch.isEmpty()) return;
        FontRegistry.FontAtlas atlas = fonts.getAtlas(fontId);
        if (atlas == null) {
            throw new IllegalStateException("No atlas registered for fontId: " + fontId
                + " - call FontRegistry.loadFont() before rendering");
        }
        renderer.render(cmd, frameArena, batch, atlas);
    }

    @Override
    public void resize(int width, int height) {
        // No size-dependent GPU resources - screen size is passed via push constant each frame.
    }

    @Override
    public boolean handleInput(InputEvent event) {
        return false; // this layer does not participate in input
    }

    @Override
    public boolean acceptsInput() { return false; }

    @Override
    public void close() {
        if (renderer != null) renderer.close();
    }
}
