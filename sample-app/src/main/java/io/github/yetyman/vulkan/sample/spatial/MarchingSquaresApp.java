package io.github.yetyman.vulkan.sample.spatial;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.helpers.math.Vec2;
import io.github.yetyman.helpers.math.spatial.isosurface.ContourOutput;
import io.github.yetyman.helpers.math.spatial.isosurface.MarchingSquares;
import io.github.yetyman.helpers.math.spatial.isosurface.ScalarField2D;
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

/**
 * Marching Squares 2D Contour Map — topographic-style multi-level contour lines.
 * Viewed top-down. Multiple iso-levels drawn simultaneously like a height map.
 *
 * Controls: 1-3=field, +/-=resolution, Scroll=zoom, Drag=pan
 */
public class MarchingSquaresApp extends VulkanApplication {

    private static final int WIDTH = 1280, HEIGHT = 720;
    private static final String FONT_ID = "default";
    private static final String[] FONT_PATHS = {"C:/Windows/Fonts/consola.ttf","C:/Windows/Fonts/arial.ttf","/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf","/System/Library/Fonts/Menlo.ttc"};

    private float fieldOffsetX = 0f, fieldOffsetY = 0f;

    private final ScalarField2D[] FIELDS = {
            // Perlin-like hills
            (x, y) -> (float)(Math.sin((x+fieldOffsetX)*1.5)*Math.cos((y+fieldOffsetY)*1.2) + Math.sin((x+fieldOffsetX)*0.7+(y+fieldOffsetY)*0.9)*0.5 + Math.cos((y+fieldOffsetY)*2.1)*0.3),
            // Concentric circles
            (x, y) -> (float)Math.sqrt((x+fieldOffsetX)*(x+fieldOffsetX) + (y+fieldOffsetY)*(y+fieldOffsetY)),
            // Saddle
            (x, y) -> (x+fieldOffsetX)*(x+fieldOffsetX) - (y+fieldOffsetY)*(y+fieldOffsetY)
    };
    private static final String[] FIELD_NAMES = {"Hills", "Circles", "Saddle"};
    private static final int CONTOUR_LEVELS = 10;

    // Colors for contour levels (gradient from blue to red)
    private static float[][] levelColors;
    static {
        levelColors = new float[CONTOUR_LEVELS][4];
        for (int i = 0; i < CONTOUR_LEVELS; i++) {
            float t = (float)i / (CONTOUR_LEVELS - 1);
            levelColors[i] = new float[]{t, 0.3f, 1f - t, 0.9f};
        }
    }

    private final OrbitCamera camera = new OrbitCamera();
    private GraphicsLoop loop;
    private int resolution = 40, fieldIndex = 0;
    private ContourOutput[] contours = new ContourOutput[CONTOUR_LEVELS];

    public MarchingSquaresApp() { super("Marching Squares 2D", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem()); }

    @Override protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());
        // Top-down view
        camera.setDistance(8f); camera.setAspect((float)WIDTH/HEIGHT);
        // Look straight down
        camera.rotate(0, -1.5f); // pitch down to look at XZ plane

        AssetRegistry assets = new AssetRegistry(); Arena uiArena = Arena.ofShared();
        VkCommandPool pool = VkCommandPool.builder().device(vulkanContext().device()).queueFamilyIndex(vulkanContext().graphicsQueueFamily()).build(vulkanContext().arena());
        FontRegistry fonts = new FontRegistry(vulkanContext().device(), vulkanContext().graphicsVkQueue(), pool);
        fonts.loadFont(FONT_ID, loadFont(), 1024, 1024); assets.register(FontRegistry.class, fonts);
        UIContext uiCtx = UIContext.builder().vulkan(vulkanContext()).assets(assets).dimensions(WIDTH,HEIGHT).applicationArena(uiArena).build();

        Scene3DOverlayLayer overlay = new Scene3DOverlayLayer();
        GPUDrivenTextLayer text = new GPUDrivenTextLayer(FONT_ID);
        OrbitCameraLayer input = new OrbitCameraLayer(camera);
        input.onPlus(() -> { resolution = Math.min(100, resolution+10); rebuildContours(); });
        input.onMinus(() -> { resolution = Math.max(10, resolution-10); rebuildContours(); });
        input.onArrowLeft(() -> { fieldOffsetX -= 0.2f; rebuildContours(); });
        input.onArrowRight(() -> { fieldOffsetX += 0.2f; rebuildContours(); });
        input.onArrowUp(() -> { fieldOffsetY -= 0.2f; rebuildContours(); });
        input.onArrowDown(() -> { fieldOffsetY += 0.2f; rebuildContours(); });

        overlay.setFrameCallback(dl -> {
            // Draw contours as lines on the XZ plane (Y=0)
            for (int level = 0; level < CONTOUR_LEVELS; level++) {
                ContourOutput c = contours[level];
                if (c == null) continue;
                float[] color = levelColors[level];
                List<Vec2> verts = c.vertices();
                List<int[]> segs = c.segments();
                for (int[] seg : segs) {
                    Vec2 a = verts.get(seg[0]), b = verts.get(seg[1]);
                    dl.addLine(new float[]{a.x, 0, a.y}, new float[]{b.x, 0, b.y}, color, DepthMode.DEPTH_TESTED);
                }
            }
            // Field sample points on XZ plane
            ScalarField2D field = FIELDS[fieldIndex];
            int sampleRes = Math.min(resolution, 25);
            float sampleStep = 6f / sampleRes;
            float crossSize = sampleStep * 0.15f;
            // Find value range for coloring
            float minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
            for (int x = 0; x <= sampleRes; x++) for (int y = 0; y <= sampleRes; y++) {
                float v = field.sample(-3f + x*sampleStep, -3f + y*sampleStep);
                if (v < minV) minV = v; if (v > maxV) maxV = v;
            }
            float valRange = maxV - minV; if (valRange < 0.001f) valRange = 1f;
            for (int x = 0; x <= sampleRes; x++) for (int y = 0; y <= sampleRes; y++) {
                float px = -3f + x * sampleStep, pz = -3f + y * sampleStep;
                float val = field.sample(px, pz);
                float t = (val - minV) / valRange; // 0=low, 1=high
                float[] ptColor = {t, 0.2f, 1f-t, 0.4f};
                dl.addLine(new float[]{px-crossSize, 0.01f, pz}, new float[]{px+crossSize, 0.01f, pz}, ptColor, DepthMode.DEPTH_TESTED);
                dl.addLine(new float[]{px, 0.01f, pz-crossSize}, new float[]{px, 0.01f, pz+crossSize}, ptColor, DepthMode.DEPTH_TESTED);
            }
            // Grid outline
            dl.addWireBox(new float[]{-3, -0.01f, -3}, new float[]{3, 0.01f, 3}, new float[]{0.3f,0.3f,0.3f,0.3f}, DepthMode.DEPTH_TESTED);
        });
        text.setFrameCallback(b -> {
            b.drawText(FONT_ID, "Marching Squares: "+FIELD_NAMES[fieldIndex], 20, 30, 22, 1f,1f,1f,1f);
            b.drawText(FONT_ID, "Resolution: "+resolution+"  Levels: "+CONTOUR_LEVELS, 20, 58, 16, 0.7f,0.8f,0.7f,1f);
            b.drawText(FONT_ID, "1-3=field  +/-=res  Drag=orbit  Scroll=zoom", 20, 85, 14, 0.5f,0.5f,0.5f,1f);
        });

        UIComposite comp = UIComposite.builder().context(uiCtx).layer(overlay).layer(text).layer(input).build();
        wireInput(comp, overlay);
        Scene3DOverlayExampleFrame frame = new Scene3DOverlayExampleFrame(vulkanContext().arena(), vulkanContext().device(), vulkanContext().graphicsVkQueue(), surface(), WIDTH, HEIGHT, comp);
        frame.init(vulkanContext().graphicsQueueFamily());
        loop = GraphicsLoop.builder().renderer(frame).driver(LoopDriver.uncapped()).shouldClose(()->windowSystem().shouldClose(window())).onResize(d->{frame.resize(d[0],d[1]);camera.setAspect((float)d[0]/d[1]);}).onFpsUpdate(m->Logger.info(m.summary())).build();
        rebuildContours(); loop.start();
        while (!windowSystem().shouldClose(window())) { windowSystem().pollEvents(); overlay.setCamera(camera.viewArray(),camera.projArray()); try{Thread.sleep(1);}catch(InterruptedException e){break;} }
        loop.stop();
    }

    private void rebuildContours() {
        ScalarField2D field = FIELDS[fieldIndex];
        // Sample field to find value range
        float minVal = Float.MAX_VALUE, maxVal = Float.MIN_VALUE;
        for (int x = 0; x <= 20; x++) for (int y = 0; y <= 20; y++) {
            float v = field.sample(-3f + x * 0.3f, -3f + y * 0.3f);
            minVal = Math.min(minVal, v); maxVal = Math.max(maxVal, v);
        }
        for (int i = 0; i < CONTOUR_LEVELS; i++) {
            float iso = minVal + (maxVal - minVal) * ((float)(i + 1) / (CONTOUR_LEVELS + 1));
            contours[i] = MarchingSquares.extract(field, new Vec2(-3, -3), new Vec2(3, 3), resolution, resolution, iso);
        }
    }

    private void wireInput(UIComposite comp, Scene3DOverlayLayer overlay) {
        Arena a = Arena.global(); double[] mp = {0,0};
        GLFWCallbacks.setCursorPosCallback(window(), (w,x,y)->{float dx=(float)(x-mp[0]),dy=(float)(y-mp[1]);mp[0]=x;mp[1]=y;comp.dispatchInput(PointerInputData.mouseMove((float)x,(float)y,dx,dy));}, a);
        GLFWCallbacks.setMouseButtonCallback(window(), (w,b,act,m)->comp.dispatchInput(act!=0?PointerInputData.mousePress(b,(float)mp[0],(float)mp[1]):PointerInputData.mouseRelease(b,(float)mp[0],(float)mp[1])), a);
        GLFWCallbacks.setScrollCallback(window(), (w,dx,dy)->comp.dispatchInput(ScrollInputData.scroll((float)mp[0],(float)mp[1],(float)dx,(float)dy)), a);
        GLFWCallbacks.setKeyCallback(window(), (w,key,sc,act,mod)->{if(act==1){switch(key){case 49->{fieldIndex=0;rebuildContours();}case 50->{fieldIndex=1;rebuildContours();}case 51->{fieldIndex=2;rebuildContours();}}} comp.dispatchInput(act==1?KeyInputData.press(key,sc,mod):act==2?KeyInputData.repeat(key,sc,mod):KeyInputData.release(key,sc,mod));}, a);
    }

    @Override protected void onWindowResize(int w, int h) { if(loop!=null) loop.signalResize(w,h); }
    @Override protected void shutdown() {}
    private static byte[] loadFont(){for(String p:FONT_PATHS){Path path=Path.of(p);if(Files.exists(path))try{return Files.readAllBytes(path);}catch(IOException e){}}throw new IllegalStateException("No font");}
    public static void main(String[] args){new MarchingSquaresApp().run();}
}
