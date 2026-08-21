package io.github.yetyman.vulkan.sample.bufferdemo;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.ui.UIFrameContext;
import io.github.yetyman.vulkan.ui.UILayer;
import io.github.yetyman.vulkan.ui.input.InputEvent;
import io.github.yetyman.vulkan.ui.input.InputEventType;
import io.github.yetyman.vulkan.ui.input.InputPhase;
import io.github.yetyman.vulkan.ui.input.types.PointerInputData;

import java.lang.foreign.Arena;

/**
 * A UILayer that tracks the current mouse position and publishes it into the
 * event propagation context under the key "mousePos" (a float[2] in pixel coords).
 *
 * This layer never consumes events - it only annotates them during the CAPTURE phase
 * so that downstream layers can read the mouse position during BUBBLE.
 *
 * Additionally exposes the latest mouse position via {@link #mouseX()} and {@link #mouseY()}
 * for layers that poll rather than react to events.
 */
public class MouseInputLayer implements UILayer {

    public static final String MOUSE_POS_KEY = "mousePos";

    private static final int ORDER = 10; // early capture, processes input first

    private volatile float mouseX;
    private volatile float mouseY;

    @Override
    public String name() {
        return "mouse-input";
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void initialize(UIContext ctx) {
        // No GPU resources needed
    }

    @Override
    public void update(UIFrameContext frame) {
        // No per-frame work
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        // No rendering
    }

    @Override
    public void resize(int width, int height) {
        // No size-dependent resources
    }

    @Override
    public boolean handleInput(InputEvent event) {
        if (event.phase() == InputPhase.CAPTURE) {
            if (event.type() == InputEventType.POINTER_MOVE
                    || event.type() == InputEventType.POINTER_DOWN
                    || event.type() == InputEventType.POINTER_UP) {
                PointerInputData pointer = event.data(PointerInputData.class);
                mouseX = pointer.x;
                mouseY = pointer.y;
                event.propagation().put(MOUSE_POS_KEY, new float[]{mouseX, mouseY});
            }
        }
        return false; // never consume, only annotate
    }

    @Override
    public boolean acceptsInput() {
        return true;
    }

    @Override
    public boolean needsUpdate() {
        return false;
    }

    /** @return latest mouse X in pixel coordinates. */
    public float mouseX() {
        return mouseX;
    }

    /** @return latest mouse Y in pixel coordinates. */
    public float mouseY() {
        return mouseY;
    }

    @Override
    public void close() {
        // No resources to release
    }
}
