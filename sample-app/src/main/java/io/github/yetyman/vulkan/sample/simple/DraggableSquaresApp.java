package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.glfw.enums.GLFWAction;
import io.github.yetyman.glfw.enums.GLFWKey;
import io.github.yetyman.glfw.enums.GLFWMouseButton;
import io.github.yetyman.glfw.enums.GLFWStandardCursor;
import io.github.yetyman.glfw.generated.GLFWFFM;
import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.util.Logger;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public class DraggableSquaresApp extends VulkanApplication {

    private GraphicsLoop loop;
    private DraggableSquaresGraphicsFrame frame;

    private MemorySegment handCursor;
    private MemorySegment arrowCursor;
    private boolean cursorIsHand = false;

    private volatile double mouseX, mouseY;
    private volatile boolean ctrlHeld = false;
    private volatile boolean shiftHeld = false;

    public DraggableSquaresApp() {
        super("Draggable Squares", 800, 600, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());

        frame = new DraggableSquaresGraphicsFrame(
                vulkanContext().arena(), vulkanContext().device(),
                vulkanContext().graphicsVkQueue(), surface(), 800, 600);
        frame.init(vulkanContext().graphicsQueueFamily());

        handCursor = GLFWFFM.glfwCreateStandardCursor(GLFWStandardCursor.GLFW_POINTING_HAND_CURSOR.value());
        arrowCursor = GLFWFFM.glfwCreateStandardCursor(GLFWStandardCursor.GLFW_ARROW_CURSOR.value());

        Arena callbackArena = Arena.global();

        GLFWCallbacks.setKeyCallback(window(), (w, key, scancode, action, mods) -> {
            boolean pressed = action != GLFWAction.GLFW_RELEASE.value();
            if (key == GLFWKey.GLFW_KEY_LEFT_CONTROL.value() || key == GLFWKey.GLFW_KEY_RIGHT_CONTROL.value())
                ctrlHeld = pressed;
            if (key == GLFWKey.GLFW_KEY_LEFT_SHIFT.value() || key == GLFWKey.GLFW_KEY_RIGHT_SHIFT.value())
                shiftHeld = pressed;
        }, callbackArena);

        GLFWCallbacks.setCursorPosCallback(window(), (w, x, y) -> {
            mouseX = x;
            mouseY = y;
            frame.onMouseMove(x, y);
        }, callbackArena);

        GLFWCallbacks.setMouseButtonCallback(window(), (w, button, action, mods) -> {
            if (button == GLFWMouseButton.GLFW_MOUSE_BUTTON_LEFT.value()) {
                boolean additive = ctrlHeld || shiftHeld;
                if (action == GLFWAction.GLFW_PRESS.value()) {
                    frame.onMousePress(mouseX, mouseY, additive);
                } else if (action == GLFWAction.GLFW_RELEASE.value()) {
                    frame.onMouseRelease(mouseX, mouseY, additive);
                }
            }
        }, callbackArena);

        loop = GraphicsLoop.builder()
                .renderer(frame)
                .driver(LoopDriver.uncapped())
                .shouldClose(() -> windowSystem().shouldClose(window()))
                .onResize(dims -> frame.resize(dims[0], dims[1]))
                .onFpsUpdate(fps -> Logger.info("FPS: " + fps))
                .build();

        loop.start();

        while (!windowSystem().shouldClose(window())) {
            windowSystem().pollEvents();
            updateCursor();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        loop.stop();
    }

    private void updateCursor() {
        boolean shouldBeHand = frame.isInteracting();
        if (shouldBeHand != cursorIsHand) {
            GLFWFFM.glfwSetCursor(window(), shouldBeHand ? handCursor : arrowCursor);
            cursorIsHand = shouldBeHand;
        }
    }

    @Override
    protected void onWindowResize(int w, int h) {
        if (loop != null) loop.signalResize(w, h);
    }

    @Override
    protected void shutdown() {
        if (frame != null) frame.close();
        if (!handCursor.equals(MemorySegment.NULL)) GLFWFFM.glfwDestroyCursor(handCursor);
        if (!arrowCursor.equals(MemorySegment.NULL)) GLFWFFM.glfwDestroyCursor(arrowCursor);
    }

    public static void main(String[] args) {
        try (DraggableSquaresApp app = new DraggableSquaresApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
