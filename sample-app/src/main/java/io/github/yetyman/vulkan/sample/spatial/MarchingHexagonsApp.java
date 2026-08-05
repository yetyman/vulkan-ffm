package io.github.yetyman.vulkan.sample.spatial;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.helpers.math.Vec2;
import io.github.yetyman.helpers.math.spatial.isosurface.ContourOutput;
import io.github.yetyman.helpers.math.spatial.isosurface.MarchingHexagons;
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
import java.util.Random;

/**
 * Marching Hexagons 2D Demo — contour extraction on a hexagonal sampling grid.
 * Shows the hex grid, field sample points, and extracted contours.
 *
 * Controls: 1-3=field, +/-=hex size, R=randomize, Scroll=zoom, Drag=orbit
 */
public class MarchingHexagonsApp extends VulkanApplication {

    private static final int WIDTH = 1280, HEIGHT = 720;
    private static final String FONT_ID = "default";
    private static final String[] FONT_PATHS = {"C:/Windows/Fonts/consola.ttf","C:/Windows/Fonts/arial.ttf","/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf","/System/Library/Fonts/Menlo.ttc"};

    private float freqX = 2f, freqY = 1.8f, phase = 0f;
    private float fieldOffsetX = 0f, fieldOffsetY = 0f;
    private final ScalarField2D[] FIELDS = {
            (x, y) -> (float)(Math.sin((x+fieldOffsetX)*freqX+phase)*Math.cos((y+fieldOffsetY)*freqY) + Math.cos((x+fieldOffsetX)*0.9-(y+fieldOffsetY)*1.1)*0.4),
            (x, y) -> (float)Math.sqrt((x+fieldOffsetX)*(x+fieldOffsetX) + (y+fieldOffsetY)*(y+fieldOffsetY)) - 1.2f,
            (x, y) -> (float)(Math.sin((x+fieldOffsetX)*2.5+phase)*Math.sin((y+fieldOffsetY)*2.5+phase)*0.8)
    };
    private static final String[] FIELD_NAMES = {"Waves", "Circle", "Interference"};

    private final OrbitCamera camera = new OrbitCamera();
    private GraphicsLoop loop;
    private float hexSize = 0.25f;
    private int fieldIndex = 0;
    private ContourOutput contour;

    public MarchingHexagonsApp() { super("Marching Hexagons 2D", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem()); }

    @Override protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());
        camera.setDistance(8f); camera.setAspect((float)WIDTH/HEIGHT);
        camera.rotate(0, -1.4f);

        AssetRegistry assets = new AssetRegistry(); Arena uiArena = Arena.ofShared();
        VkCommandPool pool = VkCommandPool.builder().device(vulkanContext().device()).queueFamilyIndex(vulkanContext().graphicsQueueFamily()).build(vulkanContext().arena());
        FontRegistry fonts = new FontRegistry(vulkanContext().device(), vulkanContext().graphicsVkQueue(), pool);
        fonts.loadFont(FONT_ID, loadFont(), 1024, 1024); assets.register(FontRegistry.class, fonts);
        UIContext uiCtx = UIContext.builder().vulkan(vulkanContext()).assets(assets).dimensions(WIDTH,HEIGHT).applicationArena(uiArena).build();

        Scene3DOverlayLayer overlay = new Scene3DOverlayLayer();
        GPUDrivenTextLayer text = new GPUDrivenTextLayer(FONT_ID);
        OrbitCameraLayer input = new OrbitCameraLayer(camera);
        input.onPlus(() -> { hexSize = Math.min(1f, hexSize + 0.05f); rebuildContour(); });
        input.onMinus(() -> { hexSize = Math.max(0.1f, hexSize - 0.05f); rebuildContour(); });
        input.onR(() -> { Random r = new Random(); freqX = 1f+r.nextFloat()*3f; freqY = 1f+r.nextFloat()*3f; phase = r.nextFloat()*6.28f; rebuildContour(); });
        input.onArrowLeft(() -> { fieldOffsetX -= 0.1f; rebuildContour(); });
        input.onArrowRight(() -> { fieldOffsetX += 0.1f; rebuildContour(); });
        input.onArrowUp(() -> { fieldOffsetY -= 0.1f; rebuildContour(); });
        input.onArrowDown(() -> { fieldOffsetY += 0.1f; rebuildContour(); });

        rebuildContour();

        overlay.setFrameCallback(dl -> {
            // Draw hex grid
            float horizSp = hexSize * 1.5f;
            float vertSp = hexSize * (float)Math.sqrt(3.0);
            Vec2 ctr = new Vec2(0, 0);
            int hc = (int)Math.ceil(5f / horizSp) + 1;
            int hr = (int)Math.ceil(5f / vertSp) + 1;
            float[] gridCol = {0.25f, 0.25f, 0.3f, 0.35f};
            for (int col = -hc; col <= hc; col++) {
                for (int row = -hr; row <= hr; row++) {
                    float hx = col * horizSp;
                    float hy = row * vertSp + ((col & 1) != 0 ? vertSp * 0.5f : 0f);
                    if (hx < -2.8f || hx > 2.8f || hy < -2.8f || hy > 2.8f) continue;
                    // Draw hex outline (flat-top: 6 corners at 0,60,120,180,240,300 degrees)
                    for (int i = 0; i < 6; i++) {
                        float a1 = (float)(Math.PI / 3.0 * i);
                        float a2 = (float)(Math.PI / 3.0 * (i + 1));
                        float cx1 = hx + hexSize * (float)Math.cos(a1);
                        float cy1 = hy + hexSize * (float)Math.sin(a1);
                        float cx2 = hx + hexSize * (float)Math.cos(a2);
                        float cy2 = hy + hexSize * (float)Math.sin(a2);
                        dl.addLine(new float[]{cx1, 0, cy1}, new float[]{cx2, 0, cy2}, gridCol, DepthMode.DEPTH_TESTED);
                    }
                }
            }

            // Field sample points
            ScalarField2D field = FIELDS[fieldIndex];
            float sStep = 0.3f, crossSize = sStep * 0.1f;
            float minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
            for (float x = -2.5f; x <= 2.5f; x += sStep) for (float y = -2.5f; y <= 2.5f; y += sStep) { float v = field.sample(x,y); if(v<minV)minV=v; if(v>maxV)maxV=v; }
            float vr = maxV-minV; if(vr<0.001f)vr=1f;
            for (float x = -2.5f; x <= 2.5f; x += sStep) for (float y = -2.5f; y <= 2.5f; y += sStep) {
                float t = (field.sample(x,y)-minV)/vr;
                float[] ptCol = {t, 0.15f, 1f-t, 0.35f};
                dl.addLine(new float[]{x-crossSize,0.01f,y}, new float[]{x+crossSize,0.01f,y}, ptCol, DepthMode.DEPTH_TESTED);
                dl.addLine(new float[]{x,0.01f,y-crossSize}, new float[]{x,0.01f,y+crossSize}, ptCol, DepthMode.DEPTH_TESTED);
            }

            // Contour segments
            if (contour != null) {
                float[] contourCol = {0.2f, 1f, 0.4f, 0.9f};
                List<Vec2> verts = contour.vertices();
                for (int[] seg : contour.segments()) {
                    Vec2 a = verts.get(seg[0]), b = verts.get(seg[1]);
                    dl.addLine(new float[]{a.x, 0.02f, a.y}, new float[]{b.x, 0.02f, b.y}, contourCol, DepthMode.DEPTH_TESTED);
                }
            }

            dl.addWireBox(new float[]{-2.5f,-0.01f,-2.5f}, new float[]{2.5f,0.01f,2.5f}, new float[]{0.3f,0.3f,0.3f,0.3f}, DepthMode.DEPTH_TESTED);
        });
        text.setFrameCallback(b -> {
            b.drawText(FONT_ID, "Marching Hexagons: "+FIELD_NAMES[fieldIndex], 20, 30, 22, 1f,1f,1f,1f);
            b.drawText(FONT_ID, "Hex size: "+String.format("%.2f",hexSize)+"  Offset: ("+String.format("%.1f",fieldOffsetX)+","+String.format("%.1f",fieldOffsetY)+")", 20, 58, 16, 0.7f,0.8f,0.7f,1f);
            b.drawText(FONT_ID, "1-3=field +/-=size R=random Arrows=slide field Drag=orbit Scroll=zoom", 20, 85, 14, 0.5f,0.5f,0.5f,1f);
        });

        UIComposite comp = UIComposite.builder().context(uiCtx).layer(overlay).layer(text).layer(input).build();
        wireInput(comp);
        Scene3DOverlayExampleFrame frame = new Scene3DOverlayExampleFrame(vulkanContext().arena(), vulkanContext().device(), vulkanContext().graphicsVkQueue(), surface(), WIDTH, HEIGHT, comp);
        frame.init(vulkanContext().graphicsQueueFamily());
        loop = GraphicsLoop.builder().renderer(frame).driver(LoopDriver.uncapped()).shouldClose(()->windowSystem().shouldClose(window())).onResize(d->{frame.resize(d[0],d[1]);camera.setAspect((float)d[0]/d[1]);}).onFpsUpdate(m->Logger.info(m.summary())).build();
        loop.start();
        while (!windowSystem().shouldClose(window())) { windowSystem().pollEvents(); overlay.setCamera(camera.viewArray(),camera.projArray()); try{Thread.sleep(1);}catch(InterruptedException e){break;} }
        loop.stop();
    }

    private void rebuildContour() { contour = MarchingHexagons.extract(FIELDS[fieldIndex], new Vec2(-2.5f,-2.5f), new Vec2(2.5f,2.5f), 5, hexSize, 0f); }

    private void wireInput(UIComposite comp) {
        Arena a = Arena.global(); double[] mp = {0,0};
        GLFWCallbacks.setCursorPosCallback(window(), (w,x,y)->{float dx=(float)(x-mp[0]),dy=(float)(y-mp[1]);mp[0]=x;mp[1]=y;comp.dispatchInput(PointerInputData.mouseMove((float)x,(float)y,dx,dy));}, a);
        GLFWCallbacks.setMouseButtonCallback(window(), (w,b,act,m)->comp.dispatchInput(act!=0?PointerInputData.mousePress(b,(float)mp[0],(float)mp[1]):PointerInputData.mouseRelease(b,(float)mp[0],(float)mp[1])), a);
        GLFWCallbacks.setScrollCallback(window(), (w,dx,dy)->comp.dispatchInput(ScrollInputData.scroll((float)mp[0],(float)mp[1],(float)dx,(float)dy)), a);
        GLFWCallbacks.setKeyCallback(window(), (w,key,sc,act,mod)->{if(act==1){switch(key){case 49->{fieldIndex=0;rebuildContour();}case 50->{fieldIndex=1;rebuildContour();}case 51->{fieldIndex=2;rebuildContour();}}} comp.dispatchInput(act==1?KeyInputData.press(key,sc,mod):act==2?KeyInputData.repeat(key,sc,mod):KeyInputData.release(key,sc,mod));}, a);
    }

    @Override protected void onWindowResize(int w, int h) { if(loop!=null) loop.signalResize(w,h); }
    @Override protected void shutdown() {}
    private static byte[] loadFont(){for(String p:FONT_PATHS){Path path=Path.of(p);if(Files.exists(path))try{return Files.readAllBytes(path);}catch(IOException e){}}throw new IllegalStateException("No font");}
    public static void main(String[] args){new MarchingHexagonsApp().run();}
}
