package io.github.yetyman.vulkan.sample.ui;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.foundation.ui.UIContext;
import io.github.yetyman.vulkan.foundation.ui.UIFrameContext;
import io.github.yetyman.vulkan.foundation.ui.UILayer;
import io.github.yetyman.vulkan.foundation.ui.input.InputEventType;
import io.github.yetyman.vulkan.foundation.ui.input.InputPhase;
import io.github.yetyman.vulkan.foundation.ui.input.UIInputEvent;

import java.lang.foreign.Arena;

/**
 * Example-specific layer (lives in sample-app, not vulkan-ffm-foundation) whose only purpose is
 * to make UIComposite's capture/bubble input dispatch directly observable: it owns no GPU
 * resources and renders nothing itself. Placed at the top of the layer stack (highest order)
 * so it is first to see events during CAPTURE and last during BUBBLE.
 *
 * Behavior:
 *   - CAPTURE phase: logs the event and annotates propagation context with a capture timestamp,
 *     but never consumes - so lower layers still see the event during capture.
 *   - BUBBLE phase: logs the event, including any "hoveredWorldPos" annotation written by
 *     Scene3DOverlayLayer during CAPTURE (demonstrating cross-layer context passing - the 3D
 *     layer runs first in capture since it has a lower order() and CAPTURE traverses
 *     highest-to-lowest... actually order determines BUBBLE (lowest-to-highest) and CAPTURE
 *     (highest-to-lowest) traversal, so Scene3DOverlayLayer (order 100) runs AFTER this layer
 *     during CAPTURE and BEFORE it during BUBBLE - see UIInputDispatcher). On
 *     MOUSE_BUTTON_PRESS, toggles a "highlighted" boolean and calls stopPropagation() to
 *     demonstrate consumption.
 *
 * The toggled highlight state and last hovered world position are exposed via isHighlighted()/
 * lastHoveredWorldPos() so the combined example app can feed them into GPUDrivenTextLayer's HUD
 * text to make the cross-layer data flow visually confirmable, not just visible in the console log.
 */
public class HoverHighlightLayer implements UILayer {

    private static final int ORDER = 950; // topmost - first in capture, last in bubble

    private float mouseX;
    private float mouseY;
    private boolean highlighted = false;
    private float[] lastHoveredWorldPos;

    @Override
    public String name() { return "hover-highlight"; }

    @Override
    public int order() { return ORDER; }

    @Override
    public void initialize(UIContext ctx) {
        // No GPU resources owned by this layer.
    }

    @Override
    public void update(UIFrameContext frame) {
        // No per-frame state to advance beyond what handleInput() already tracks.
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        // This layer renders nothing itself - it exists purely to demonstrate input dispatch.
    }

    @Override
    public void resize(int width, int height) {
    }

    @Override
    public boolean handleInput(UIInputEvent event) {
        if (event.type() == InputEventType.MOUSE_MOVE) {
            mouseX = event.mouseX();
            mouseY = event.mouseY();
        }

        if (event.phase() == InputPhase.CAPTURE) {
            System.out.println("[HoverHighlightLayer] CAPTURE  " + event.type() + " (not consumed, annotating context)");
            event.propagation().put("captureTimestampNanos", event.timestampNanos());
            return false;
        }

        // BUBBLE phase
        float[] hoveredWorldPos = event.propagation().get("hoveredWorldPos", float[].class);
        if (hoveredWorldPos != null) {
            lastHoveredWorldPos = hoveredWorldPos;
            System.out.println("[HoverHighlightLayer] BUBBLE   " + event.type()
                + " - Scene3DOverlayLayer annotated hoveredWorldPos="
                + "(" + hoveredWorldPos[0] + ", " + hoveredWorldPos[1] + ", " + hoveredWorldPos[2] + ")");
        } else {
            System.out.println("[HoverHighlightLayer] BUBBLE   " + event.type()
                + " (captured at " + event.propagation().get("captureTimestampNanos", Long.class) + ")");
        }

        if (event.type() == InputEventType.MOUSE_BUTTON_PRESS) {
            highlighted = !highlighted;
            System.out.println("[HoverHighlightLayer] Click consumed - highlighted=" + highlighted
                + " - stopping propagation, no lower layer will see this click");
            event.stopPropagation();
            return true;
        }

        return false;
    }

    @Override
    public boolean acceptsInput() { return true; }

    @Override
    public boolean needsUpdate() { return false; }

    @Override
    public void close() {
        // No resources to release.
    }

    /** @return the last known cursor X position in screen pixels. */
    public float mouseX() { return mouseX; }

    /** @return the last known cursor Y position in screen pixels. */
    public float mouseY() { return mouseY; }

    /** @return whether the highlight has been toggled on by a click. */
    public boolean isHighlighted() { return highlighted; }

    /** @return the last world-space position annotated by Scene3DOverlayLayer during CAPTURE, or null if not currently hovering the pick target. */
    public float[] lastHoveredWorldPos() { return lastHoveredWorldPos; }
}
