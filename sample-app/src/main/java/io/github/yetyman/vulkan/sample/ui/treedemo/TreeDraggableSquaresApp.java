package io.github.yetyman.vulkan.sample.ui.treedemo;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.glfw.enums.GLFWAction;
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

/**
 * Tree-based draggable squares demo.
 *
 * Same visuals as the original DraggableSquaresApp but using the full node tree:
 * - Hierarchical nested rectangles (parent panels with child buttons)
 * - GestureRecognizer for press/drag detection via events
 * - SpatialGrid for O(1) hit-testing
 * - NineSliceRenderer for bulk rendering
 * - DraggableComponent per node handles highlight/drag via event handlers
 *
 * Demonstrates the complete node treepipeline from input to render.
 */
public class TreeDraggableSquaresApp extends VulkanApplication {

    private GraphicsLoop loop;
    private TreeDemoFrame demoFrame;
    private TreeDemoGraphicsFrame graphicsFrame;

    private MemorySegment handCursor;
    private MemorySegment arrowCursor;
    private boolean cursorIsHand = false;

    private volatile double mouseX, mouseY;

    public TreeDraggableSquaresApp() {
        super("Tree Draggable Squares", 800, 600, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());

        // Create the node tree demo
        demoFrame = new TreeDemoFrame(800, 600);

        // Create the Vulkan graphics frame (handles GPU resources and rendering)
        graphicsFrame = new TreeDemoGraphicsFrame(
                vulkanContext().arena(), vulkanContext().device(),
                vulkanContext().graphicsVkQueue(), surface(), 800, 600, demoFrame);
        graphicsFrame.init(vulkanContext().graphicsQueueFamily());

        handCursor = GLFWFFM.glfwCreateStandardCursor(GLFWStandardCursor.GLFW_POINTING_HAND_CURSOR.value());
        arrowCursor = GLFWFFM.glfwCreateStandardCursor(GLFWStandardCursor.GLFW_ARROW_CURSOR.value());

        Arena callbackArena = Arena.global();

        GLFWCallbacks.setCursorPosCallback(window(), (w, x, y) -> {
            mouseX = x;
            mouseY = y;
            demoFrame.onPointerMove((float) x, (float) y, 0);
        }, callbackArena);

        GLFWCallbacks.setMouseButtonCallback(window(), (w, button, action, mods) -> {
            if (button == GLFWMouseButton.GLFW_MOUSE_BUTTON_LEFT.value()) {
                if (action == GLFWAction.GLFW_PRESS.value()) {
                    demoFrame.onPointerDown((float) mouseX, (float) mouseY, 0, 0);
                } else if (action == GLFWAction.GLFW_RELEASE.value()) {
                    demoFrame.onPointerUp((float) mouseX, (float) mouseY, 0, 0);
                }
            }
        }, callbackArena);

        loop = GraphicsLoop.builder()
                .renderer(graphicsFrame)
                .driver(LoopDriver.uncapped())
                .shouldClose(() -> windowSystem().shouldClose(window()))
                .onResize(dims -> {
                    demoFrame.resize(dims[0], dims[1]);
                    graphicsFrame.resize(dims[0], dims[1]);
                })
                .onFpsUpdate(m -> Logger.info(m.summary()))
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
        boolean shouldBeHand = demoFrame.isInteracting();
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
        if (demoFrame != null) demoFrame.close();
        if (graphicsFrame != null) graphicsFrame.close();
        if (!handCursor.equals(MemorySegment.NULL)) GLFWFFM.glfwDestroyCursor(handCursor);
        if (!arrowCursor.equals(MemorySegment.NULL)) GLFWFFM.glfwDestroyCursor(arrowCursor);
    }

    public static void main(String[] args) {
        try (TreeDraggableSquaresApp app = new TreeDraggableSquaresApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
