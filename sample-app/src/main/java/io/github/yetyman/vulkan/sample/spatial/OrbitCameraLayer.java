package io.github.yetyman.vulkan.sample.spatial;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.ui.UIFrameContext;
import io.github.yetyman.vulkan.ui.UILayer;
import io.github.yetyman.vulkan.ui.input.InputEvent;
import io.github.yetyman.vulkan.ui.input.InputEventType;
import io.github.yetyman.vulkan.ui.input.InputPhase;
import io.github.yetyman.vulkan.ui.input.types.KeyInputData;
import io.github.yetyman.vulkan.ui.input.types.PointerInputData;
import io.github.yetyman.vulkan.ui.input.types.ScrollInputData;

import java.lang.foreign.Arena;

/**
 * Input-only UILayer that drives an OrbitCamera from mouse/scroll/key events.
 * Does not render anything — just consumes input to move the camera.
 *
 * Left-drag: orbit (rotate yaw/pitch)
 * Right-drag: pan
 * Scroll: zoom
 * Arrow keys: move query center (dispatched via callback)
 * Space/R/Q/+/-: dispatched via keyCallback
 */
public class OrbitCameraLayer implements UILayer {

    private final OrbitCamera camera;
    private boolean leftDown, rightDown;
    private Runnable onSpace, onR, onQ, onPlus, onMinus, onV;
    private Runnable onLeft, onRight, onUp, onDown;
    private Runnable onPageUp, onPageDown;

    public OrbitCameraLayer(OrbitCamera camera) {
        this.camera = camera;
    }

    public void onSpace(Runnable r) { this.onSpace = r; }
    public void onR(Runnable r) { this.onR = r; }
    public void onQ(Runnable r) { this.onQ = r; }
    public void onPlus(Runnable r) { this.onPlus = r; }
    public void onMinus(Runnable r) { this.onMinus = r; }
    public void onV(Runnable r) { this.onV = r; }
    public void onArrowLeft(Runnable r) { this.onLeft = r; }
    public void onArrowRight(Runnable r) { this.onRight = r; }
    public void onArrowUp(Runnable r) { this.onUp = r; }
    public void onArrowDown(Runnable r) { this.onDown = r; }
    public void onPageUp(Runnable r) { this.onPageUp = r; }
    public void onPageDown(Runnable r) { this.onPageDown = r; }

    @Override public String name() { return "orbit-camera-input"; }
    @Override public int order() { return 1000; } // highest priority for input capture
    @Override public void initialize(UIContext ctx) {}
    @Override public void update(UIFrameContext frame) {}
    @Override public void render(VkCommandBuffer cmd, Arena frameArena) {}
    @Override public void resize(int width, int height) {}
    @Override public void close() {}
    @Override public boolean needsUpdate() { return false; }

    @Override
    public boolean handleInput(InputEvent event) {
        // Only handle during bubble phase to avoid double-firing
        if (event.phase() == InputPhase.CAPTURE) return false;

        switch (event.type()) {
            case POINTER_DOWN -> {
                PointerInputData data = event.data(PointerInputData.class);
                if (data.button == 0) leftDown = true;
                if (data.button == 1) rightDown = true;
                return true;
            }
            case POINTER_UP -> {
                PointerInputData data = event.data(PointerInputData.class);
                if (data.button == 0) leftDown = false;
                if (data.button == 1) rightDown = false;
                return true;
            }
            case POINTER_MOVE -> {
                PointerInputData data = event.data(PointerInputData.class);
                float dx = data.deltaX;
                float dy = data.deltaY;
                if (leftDown) {
                    camera.rotate(-dx * 0.005f, -dy * 0.005f);
                    return true;
                }
                if (rightDown) {
                    camera.pan(dx, dy);
                    return true;
                }
            }
            case SCROLL -> {
                ScrollInputData data = event.data(ScrollInputData.class);
                camera.zoom(data.scrollY * 2f);
                return true;
            }
            case KEY_PRESS -> {
                KeyInputData data = event.data(KeyInputData.class);
                int key = data.keyCode;
                // GLFW key constants
                if (key == 32 && onSpace != null) { onSpace.run(); return true; } // SPACE
                if (key == 82 && onR != null) { onR.run(); return true; } // R
                if (key == 81 && onQ != null) { onQ.run(); return true; } // Q
                if (key == 61 && onPlus != null) { onPlus.run(); return true; } // =+
                if (key == 45 && onMinus != null) { onMinus.run(); return true; } // -
                if (key == 86 && onV != null) { onV.run(); return true; } // V
                if (key == 263 && onLeft != null) { onLeft.run(); return true; } // LEFT
                if (key == 262 && onRight != null) { onRight.run(); return true; } // RIGHT
                if (key == 265 && onUp != null) { onUp.run(); return true; } // UP
                if (key == 264 && onDown != null) { onDown.run(); return true; } // DOWN
                if (key == 266 && onPageUp != null) { onPageUp.run(); return true; } // PAGE_UP
                if (key == 267 && onPageDown != null) { onPageDown.run(); return true; } // PAGE_DOWN
            }
            default -> {}
        }
        return false;
    }
}
