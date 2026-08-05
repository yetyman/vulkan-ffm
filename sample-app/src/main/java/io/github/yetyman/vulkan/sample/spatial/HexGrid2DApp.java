package io.github.yetyman.vulkan.sample.spatial;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.helpers.math.Vec2;
import io.github.yetyman.helpers.math.spatial.hex.HexCoord;
import io.github.yetyman.helpers.math.spatial.hex.HexGrid;
import io.github.yetyman.helpers.math.spatial.hex.HexLayout;
import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.assets.FontRegistry;
import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.layers.scene3d.DepthMode;
import io.github.yetyman.vulkan.layers.scene3d.Scene3DOverlayLayer;
import io.github.yetyman.vulkan.layers.text.GPUDrivenTextLayer;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.ui.Scene3DOverlayExampleFrame;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.ui.UIComposite;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.ui.assets.AssetRegistry;
import io.github.yetyman.vulkan.ui.input.InputEvent;
import io.github.yetyman.vulkan.ui.input.types.KeyInputData;
import io.github.yetyman.vulkan.ui.input.types.PointerInputData;
import io.github.yetyman.vulkan.ui.input.types.ScrollInputData;
import io.github.yetyman.vulkan.util.Logger;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/**
 * Hex Grid 2D Demo — flat-top hexagonal grid rendered on the XZ plane.
 * Paint terrain types, show neighbors, range queries, and pathfinding.
 *
 * Controls: 1-4=paint terrain, N=show neighbors of center, R=range query, Scroll=zoom, Drag=orbit
 */
public class HexGrid2DApp extends VulkanApplication {

    private static final int WIDTH = 1280, HEIGHT = 720;
    private static final String FONT_ID = "default";
    private static final String[] FONT_PATHS = {"C:/Windows/Fonts/consola.ttf","C:/Windows/Fonts/arial.ttf","/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf","/System/Library/Fonts/Menlo.ttc"};
    private static final int GRID_RADIUS = 6;
    private static final float HEX_SIZE = 1f;

    private enum Terrain { GRASS, WATER, MOUNTAIN, SAND }
    private static final float[][] TERRAIN_COLORS = {
            {0.2f, 0.8f, 0.2f, 0.7f}, // grass
            {0.2f, 0.4f, 0.9f, 0.7f}, // water
            {0.5f, 0.4f, 0.3f, 0.7f}, // mountain
            {0.9f, 0.85f, 0.5f, 0.7f}  // sand
    };

    private final OrbitCamera camera = new OrbitCamera();
    private GraphicsLoop loop;
    private final HexGrid<Terrain> grid = new HexGrid<>();
    private final HexLayout layout = new HexLayout(HexLayout.Orientation.FLAT_TOP, HEX_SIZE, new Vec2(0, 0));
    private HexCoord selectedHex = new HexCoord(0, 0);
    private int queryRadius = 2;
    private boolean showNeighbors = false;

    public HexGrid2DApp() { super("Hex Grid 2D Demo", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem()); }

    @Override protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());
        camera.setDistance(18f); camera.setAspect((float)WIDTH/HEIGHT);
        camera.rotate(0, -1.4f);

        // Fill grid with random terrain
        Random rng = new Random(42);
        HexCoord center = new HexCoord(0, 0);
        for (HexCoord c : center.range(GRID_RADIUS)) {
            Terrain t = Terrain.values()[rng.nextInt(4)];
            grid.set(c, t);
        }

        AssetRegistry assets = new AssetRegistry(); Arena uiArena = Arena.ofShared();
        VkCommandPool pool = VkCommandPool.builder().device(vulkanContext().device()).queueFamilyIndex(vulkanContext().graphicsQueueFamily()).build(vulkanContext().arena());
        FontRegistry fonts = new FontRegistry(vulkanContext().device(), vulkanContext().graphicsVkQueue(), pool);
        fonts.loadFont(FONT_ID, loadFont(), 1024, 1024); assets.register(FontRegistry.class, fonts);
        UIContext uiCtx = UIContext.builder().vulkan(vulkanContext()).assets(assets).dimensions(WIDTH,HEIGHT).applicationArena(uiArena).build();

        Scene3DOverlayLayer overlay = new Scene3DOverlayLayer();
        GPUDrivenTextLayer text = new GPUDrivenTextLayer(FONT_ID);
        OrbitCameraLayer input = new OrbitCameraLayer(camera);
        input.onPlus(() -> { queryRadius = Math.min(GRID_RADIUS, queryRadius+1); });
        input.onMinus(() -> { queryRadius = Math.max(0, queryRadius-1); });

        overlay.setFrameCallback(dl -> {
            // Draw all hex cells
            HexCoord c = new HexCoord(0, 0);
            for (HexCoord hex : c.range(GRID_RADIUS)) {
                Terrain t = grid.get(hex);
                if (t == null) t = Terrain.GRASS;
                float[] color = TERRAIN_COLORS[t.ordinal()];
                drawHexOutline(dl, hex, color);
            }

            // Highlight selected hex
            drawHexOutline(dl, selectedHex, new float[]{1f, 1f, 1f, 1f});

            // Show range query
            List<Terrain> rangeResults = grid.queryRange(selectedHex, queryRadius);
            for (HexCoord hex : selectedHex.range(queryRadius)) {
                if (!hex.equals(selectedHex)) {
                    drawHexOutline(dl, hex, new float[]{1f, 0.8f, 0.2f, 0.6f});
                }
            }

            // Show neighbors if enabled
            if (showNeighbors) {
                for (HexCoord n : selectedHex.neighbors()) {
                    drawHexOutline(dl, n, new float[]{0.2f, 1f, 1f, 0.9f});
                }
            }
        });
        text.setFrameCallback(b -> {
            b.drawText(FONT_ID, "Hex Grid 2D", 20, 30, 22, 1f,1f,1f,1f);
            b.drawText(FONT_ID, "Selected: ("+selectedHex.q+","+selectedHex.r+")  Range: "+queryRadius, 20, 58, 16, 0.7f,0.8f,0.7f,1f);
            b.drawText(FONT_ID, "Arrows=move  N=neighbors  +/-=range  R=randomize", 20, 85, 14, 0.5f,0.5f,0.5f,1f);
        });

        input.onArrowLeft(() -> selectedHex = selectedHex.neighbor(3));
        input.onArrowRight(() -> selectedHex = selectedHex.neighbor(0));
        input.onArrowUp(() -> selectedHex = selectedHex.neighbor(5));
        input.onArrowDown(() -> selectedHex = selectedHex.neighbor(2));
        input.onR(() -> { Random r = new Random(); for (HexCoord h : new HexCoord(0,0).range(GRID_RADIUS)) grid.set(h, Terrain.values()[r.nextInt(4)]); });

        UIComposite comp = UIComposite.builder().context(uiCtx).layer(overlay).layer(text).layer(input).build();
        wireInput(comp);
        Scene3DOverlayExampleFrame frame = new Scene3DOverlayExampleFrame(vulkanContext().arena(), vulkanContext().device(), vulkanContext().graphicsVkQueue(), surface(), WIDTH, HEIGHT, comp);
        frame.init(vulkanContext().graphicsQueueFamily());
        loop = GraphicsLoop.builder().renderer(frame).driver(LoopDriver.uncapped()).shouldClose(()->windowSystem().shouldClose(window())).onResize(d->{frame.resize(d[0],d[1]);camera.setAspect((float)d[0]/d[1]);}).onFpsUpdate(m->Logger.info(m.summary())).build();
        loop.start();
        while (!windowSystem().shouldClose(window())) { windowSystem().pollEvents(); overlay.setCamera(camera.viewArray(),camera.projArray()); try{Thread.sleep(1);}catch(InterruptedException e){break;} }
        loop.stop();
    }

    private void drawHexOutline(io.github.yetyman.vulkan.layers.scene3d.OverlayDrawList dl, HexCoord hex, float[] color) {
        Vec2[] corners = layout.hexCorners(hex);
        for (int i = 0; i < 6; i++) {
            Vec2 a = corners[i], b = corners[(i + 1) % 6];
            dl.addLine(new float[]{a.x, 0, a.y}, new float[]{b.x, 0, b.y}, color, DepthMode.DEPTH_TESTED);
        }
    }

    private void wireInput(UIComposite comp) {
        Arena a = Arena.global(); double[] mp = {0,0};
        GLFWCallbacks.setCursorPosCallback(window(), (w,x,y)->{float dx=(float)(x-mp[0]),dy=(float)(y-mp[1]);mp[0]=x;mp[1]=y;comp.dispatchInput(PointerInputData.mouseMove((float)x,(float)y,dx,dy));}, a);
        GLFWCallbacks.setMouseButtonCallback(window(), (w,b,act,m)->comp.dispatchInput(act!=0?PointerInputData.mousePress(b,(float)mp[0],(float)mp[1]):PointerInputData.mouseRelease(b,(float)mp[0],(float)mp[1])), a);
        GLFWCallbacks.setScrollCallback(window(), (w,dx,dy)->comp.dispatchInput(ScrollInputData.scroll((float)mp[0],(float)mp[1],(float)dx,(float)dy)), a);
        GLFWCallbacks.setKeyCallback(window(), (w,key,sc,act,mod)->{if(act==1&&key==78)showNeighbors=!showNeighbors; comp.dispatchInput(act==1?KeyInputData.press(key,sc,mod):act==2?KeyInputData.repeat(key,sc,mod):KeyInputData.release(key,sc,mod));}, a);
    }

    @Override protected void onWindowResize(int w, int h) { if(loop!=null) loop.signalResize(w,h); }
    @Override protected void shutdown() {}
    private static byte[] loadFont(){for(String p:FONT_PATHS){Path path=Path.of(p);if(Files.exists(path))try{return Files.readAllBytes(path);}catch(IOException e){}}throw new IllegalStateException("No font");}
    public static void main(String[] args){new HexGrid2DApp().run();}
}
