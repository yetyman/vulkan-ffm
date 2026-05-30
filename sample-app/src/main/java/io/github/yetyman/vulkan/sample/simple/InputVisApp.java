package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.glfw.enums.GLFWKey;
import io.github.yetyman.structures.input.KeyboardState;
import io.github.yetyman.structures.input.MouseState;
import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.windowing.GLFWKeyboardAdapter;
import io.github.yetyman.vulkan.sample.windowing.GLFWMouseAdapter;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.util.Logger;

import java.lang.foreign.Arena;
import java.util.HashMap;
import java.util.Map;

public class InputVisApp extends VulkanApplication {

    private static final int W = 1400;
    private static final int H = 800;

    // Simulation — set true to feed synthetic circular motion instead of real input
    private static final boolean SIMULATE_CIRCLE = false;
    // Circle params: center in screen coords, radius in pixels, angular velocity in rad/s
    private static final double SIM_CX      = W / 2.0;
    private static final double SIM_CY      = H / 2.0;
    private static final double SIM_RADIUS  = 200.0;
    private static final double SIM_OMEGA   = 8.0; // rad/s
    private static final long   SIM_STEP_NS = 8_000_000L; // 8ms between synthetic events (~125Hz)

    private static final Map<Integer, String> KEY_NAMES = buildKeyNames();

    private MouseState    mouse;
    private KeyboardState keyboard;
    private InputVisFrame frame;
    private GraphicsLoop  loop;
    private Thread        simThread;

    public InputVisApp() {
        super("Input Visualizer", W, H, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());

        mouse    = new MouseState(5, 1.0, 0.007, 1.0, 500_000_000L);
        keyboard = new KeyboardState(InputVisFrame.ALL_KEYS);

        Arena callbackArena = Arena.global();
        if (SIMULATE_CIRCLE) {
            startCircleSimulation();
        } else {
            new GLFWMouseAdapter(window(), mouse, callbackArena);
            new GLFWKeyboardAdapter(window(), keyboard, KEY_NAMES::get, callbackArena);
        }

        frame = new InputVisFrame(
                vulkanContext().arena(), vulkanContext().device(),
                vulkanContext().graphicsVkQueue(), surface(), W, H,
                mouse, keyboard);
        frame.init(vulkanContext().graphicsQueueFamily());

        loop = GraphicsLoop.builder()
                .renderer(frame)
                .driver(LoopDriver.uncapped())
                .shouldClose(() -> windowSystem().shouldClose(window()))
                .onResize(dims -> frame.resize(dims[0], dims[1]))
                .onFpsUpdate(m -> Logger.info(m.summary()))
                .build();

        loop.start();

        while (!windowSystem().shouldClose(window())) {
            windowSystem().pollEvents();
            try { Thread.sleep(1); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); break;
            }
        }
        loop.stop();
        if (simThread != null) simThread.interrupt();
    }

    private void startCircleSimulation() {
        long startNs = System.nanoTime();
        simThread = new Thread(() -> {
            long next = System.nanoTime();
            while (!Thread.currentThread().isInterrupted()) {
                long now = System.nanoTime();
                double t = (now - startNs) * 1e-9;
                double angle = t * SIM_OMEGA;
                double x = SIM_CX + Math.cos(angle) * SIM_RADIUS;
                double y = SIM_CY + Math.sin(angle) * SIM_RADIUS;
                mouse.updatePosition(x, y, now);

                next += SIM_STEP_NS;
                long sleep = next - System.nanoTime();
                if (sleep > 0) {
                    try { Thread.sleep(sleep / 1_000_000, (int)(sleep % 1_000_000)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            }
        }, "circle-sim");
        simThread.setDaemon(true);
        simThread.start();
    }

    @Override protected void onWindowResize(int w, int h) { if (loop != null) loop.signalResize(w, h); }
    @Override protected void shutdown() { if (frame != null) frame.close(); }

    public static void main(String[] args) {
        try (InputVisApp app = new InputVisApp()) { app.run(); }
        catch (Exception e) { Logger.error("Fatal: " + e.getMessage()); e.printStackTrace(); }
    }

    private static Map<Integer, String> buildKeyNames() {
        Map<Integer, String> m = new HashMap<>();
        // Letters
        m.put(GLFWKey.GLFW_KEY_A.value(),"A"); m.put(GLFWKey.GLFW_KEY_B.value(),"B");
        m.put(GLFWKey.GLFW_KEY_C.value(),"C"); m.put(GLFWKey.GLFW_KEY_D.value(),"D");
        m.put(GLFWKey.GLFW_KEY_E.value(),"E"); m.put(GLFWKey.GLFW_KEY_F.value(),"F");
        m.put(GLFWKey.GLFW_KEY_G.value(),"G"); m.put(GLFWKey.GLFW_KEY_H.value(),"H");
        m.put(GLFWKey.GLFW_KEY_I.value(),"I"); m.put(GLFWKey.GLFW_KEY_J.value(),"J");
        m.put(GLFWKey.GLFW_KEY_K.value(),"K"); m.put(GLFWKey.GLFW_KEY_L.value(),"L");
        m.put(GLFWKey.GLFW_KEY_M.value(),"M"); m.put(GLFWKey.GLFW_KEY_N.value(),"N");
        m.put(GLFWKey.GLFW_KEY_O.value(),"O"); m.put(GLFWKey.GLFW_KEY_P.value(),"P");
        m.put(GLFWKey.GLFW_KEY_Q.value(),"Q"); m.put(GLFWKey.GLFW_KEY_R.value(),"R");
        m.put(GLFWKey.GLFW_KEY_S.value(),"S"); m.put(GLFWKey.GLFW_KEY_T.value(),"T");
        m.put(GLFWKey.GLFW_KEY_U.value(),"U"); m.put(GLFWKey.GLFW_KEY_V.value(),"V");
        m.put(GLFWKey.GLFW_KEY_W.value(),"W"); m.put(GLFWKey.GLFW_KEY_X.value(),"X");
        m.put(GLFWKey.GLFW_KEY_Y.value(),"Y"); m.put(GLFWKey.GLFW_KEY_Z.value(),"Z");
        // Numbers + symbols
        m.put(GLFWKey.GLFW_KEY_0.value(),"0"); m.put(GLFWKey.GLFW_KEY_1.value(),"1");
        m.put(GLFWKey.GLFW_KEY_2.value(),"2"); m.put(GLFWKey.GLFW_KEY_3.value(),"3");
        m.put(GLFWKey.GLFW_KEY_4.value(),"4"); m.put(GLFWKey.GLFW_KEY_5.value(),"5");
        m.put(GLFWKey.GLFW_KEY_6.value(),"6"); m.put(GLFWKey.GLFW_KEY_7.value(),"7");
        m.put(GLFWKey.GLFW_KEY_8.value(),"8"); m.put(GLFWKey.GLFW_KEY_9.value(),"9");
        m.put(GLFWKey.GLFW_KEY_GRAVE_ACCENT.value(),"`");
        m.put(GLFWKey.GLFW_KEY_MINUS.value(),"-");
        m.put(GLFWKey.GLFW_KEY_EQUAL.value(),"=");
        m.put(GLFWKey.GLFW_KEY_LEFT_BRACKET.value(),"[");
        m.put(GLFWKey.GLFW_KEY_RIGHT_BRACKET.value(),"]");
        m.put(GLFWKey.GLFW_KEY_BACKSLASH.value(),"\\");
        m.put(GLFWKey.GLFW_KEY_SEMICOLON.value(),";");
        m.put(GLFWKey.GLFW_KEY_APOSTROPHE.value(),"'");
        m.put(GLFWKey.GLFW_KEY_COMMA.value(),",");
        m.put(GLFWKey.GLFW_KEY_PERIOD.value(),".");
        m.put(GLFWKey.GLFW_KEY_SLASH.value(),"/");
        // Control keys
        m.put(GLFWKey.GLFW_KEY_ESCAPE.value(),"ESC");
        m.put(GLFWKey.GLFW_KEY_TAB.value(),"TAB");
        m.put(GLFWKey.GLFW_KEY_CAPS_LOCK.value(),"CAPS");
        m.put(GLFWKey.GLFW_KEY_BACKSPACE.value(),"BKSP");
        m.put(GLFWKey.GLFW_KEY_ENTER.value(),"ENTER");
        m.put(GLFWKey.GLFW_KEY_SPACE.value(),"SPACE");
        m.put(GLFWKey.GLFW_KEY_LEFT_SHIFT.value(),"LSHIFT");
        m.put(GLFWKey.GLFW_KEY_RIGHT_SHIFT.value(),"RSHIFT");
        m.put(GLFWKey.GLFW_KEY_LEFT_CONTROL.value(),"LCTRL");
        m.put(GLFWKey.GLFW_KEY_RIGHT_CONTROL.value(),"RCTRL");
        m.put(GLFWKey.GLFW_KEY_LEFT_ALT.value(),"LALT");
        m.put(GLFWKey.GLFW_KEY_RIGHT_ALT.value(),"RALT");
        m.put(GLFWKey.GLFW_KEY_LEFT_SUPER.value(),"LSUPER");
        m.put(GLFWKey.GLFW_KEY_RIGHT_SUPER.value(),"RSUPER");
        m.put(GLFWKey.GLFW_KEY_MENU.value(),"MENU");
        // F keys
        m.put(GLFWKey.GLFW_KEY_F1.value(),"F1");   m.put(GLFWKey.GLFW_KEY_F2.value(),"F2");
        m.put(GLFWKey.GLFW_KEY_F3.value(),"F3");   m.put(GLFWKey.GLFW_KEY_F4.value(),"F4");
        m.put(GLFWKey.GLFW_KEY_F5.value(),"F5");   m.put(GLFWKey.GLFW_KEY_F6.value(),"F6");
        m.put(GLFWKey.GLFW_KEY_F7.value(),"F7");   m.put(GLFWKey.GLFW_KEY_F8.value(),"F8");
        m.put(GLFWKey.GLFW_KEY_F9.value(),"F9");   m.put(GLFWKey.GLFW_KEY_F10.value(),"F10");
        m.put(GLFWKey.GLFW_KEY_F11.value(),"F11"); m.put(GLFWKey.GLFW_KEY_F12.value(),"F12");
        // Navigation / aux
        m.put(GLFWKey.GLFW_KEY_INSERT.value(),"INS");
        m.put(GLFWKey.GLFW_KEY_DELETE.value(),"DEL");
        m.put(GLFWKey.GLFW_KEY_HOME.value(),"HOME");
        m.put(GLFWKey.GLFW_KEY_END.value(),"END");
        m.put(GLFWKey.GLFW_KEY_PAGE_UP.value(),"PGUP");
        m.put(GLFWKey.GLFW_KEY_PAGE_DOWN.value(),"PGDN");
        // Arrows
        m.put(GLFWKey.GLFW_KEY_LEFT.value(),"LEFT");
        m.put(GLFWKey.GLFW_KEY_RIGHT.value(),"RIGHT");
        m.put(GLFWKey.GLFW_KEY_UP.value(),"UP");
        m.put(GLFWKey.GLFW_KEY_DOWN.value(),"DOWN");
        // System
        m.put(GLFWKey.GLFW_KEY_PRINT_SCREEN.value(),"PRTSC");
        m.put(GLFWKey.GLFW_KEY_SCROLL_LOCK.value(),"SCRLK");
        m.put(GLFWKey.GLFW_KEY_PAUSE.value(),"PAUSE");
        m.put(GLFWKey.GLFW_KEY_NUM_LOCK.value(),"NUMLK");
        // Numpad
        m.put(GLFWKey.GLFW_KEY_KP_0.value(),"NP0"); m.put(GLFWKey.GLFW_KEY_KP_1.value(),"NP1");
        m.put(GLFWKey.GLFW_KEY_KP_2.value(),"NP2"); m.put(GLFWKey.GLFW_KEY_KP_3.value(),"NP3");
        m.put(GLFWKey.GLFW_KEY_KP_4.value(),"NP4"); m.put(GLFWKey.GLFW_KEY_KP_5.value(),"NP5");
        m.put(GLFWKey.GLFW_KEY_KP_6.value(),"NP6"); m.put(GLFWKey.GLFW_KEY_KP_7.value(),"NP7");
        m.put(GLFWKey.GLFW_KEY_KP_8.value(),"NP8"); m.put(GLFWKey.GLFW_KEY_KP_9.value(),"NP9");
        m.put(GLFWKey.GLFW_KEY_KP_DECIMAL.value(),"NP.");
        m.put(GLFWKey.GLFW_KEY_KP_DIVIDE.value(),"NP/");
        m.put(GLFWKey.GLFW_KEY_KP_MULTIPLY.value(),"NP*");
        m.put(GLFWKey.GLFW_KEY_KP_SUBTRACT.value(),"NP-");
        m.put(GLFWKey.GLFW_KEY_KP_ADD.value(),"NP+");
        m.put(GLFWKey.GLFW_KEY_KP_ENTER.value(),"NPENT");
        return m;
    }
}
