package io.github.yetyman.vulkan.sample.spatial;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.helpers.math.Vec2;
import io.github.yetyman.helpers.math.spatial.isosurface.ContourOutput;
import io.github.yetyman.helpers.math.spatial.isosurface.MarchingTriangles;
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
 * Marching Triangles 2D Demo — ambiguity-free contour extraction on a triangulated grid.
 * Shows field sample points + contour lines + the underlying triangle grid.
 *
 * Controls: 1-4=field, +/-=resolution, R=randomize field params, Scroll=zoom, Drag=orbit
 */
public class MarchingTrianglesApp extends VulkanApplication {

    private static final int WIDTH = 1280, HEIGHT = 720;
    private static final String FONT_ID = "default";
    private static final String[] FONT_PATHS = {"C:/Windows/Fonts/consola.ttf","C:/Windows/Fonts/arial.ttf","/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf","/System/Library/Fonts/Menlo.ttc"};

    private float freqX = 1.5f, freqY = 1.2f, phase = 0f;
    private float fieldOffsetX = 0f, fieldOffsetY = 0f;
    private final ScalarField2D[] FIELDS = {
            (x, y) -> (float)(Math.sin((x+fieldOffsetX)*freqX+phase)*Math.cos((y+fieldOffsetY)*freqY) + Math.sin((x+fieldOffsetX)*0.7+(y+fieldOffsetY)*0.9)*0.5),
            (x, y) -> (float)Math.sqrt((x+fieldOffsetX)*(x+fieldOffsetX) + (y+fieldOffsetY)*(y+fieldOffsetY)) - 1.5f,
            (x, y) -> (x+fieldOffsetX)*(x+fieldOffsetX) - (y+fieldOffsetY)*(y+fieldOffsetY),
            (x, y) -> (float)(Math.sin((x+fieldOffsetX)*3+phase) * Math.sin((y+fieldOffsetY)*3+phase))
    };
    private static final String[] FIELD_NAMES = {"Waves","Circle","Saddle","Grid Pattern"};

    private final OrbitCamera camera = new OrbitCamera();
    private GraphicsLoop loop;
    private int resolution = 30, fieldIndex = 0;
    private ContourOutput contour;
    private boolean showGrid = true;

    public MarchingTrianglesApp() { super("Marching Triangles 2D", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem()); }

    @Override protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());
        camera.setDistance(10f); camera.setAspect((float)WIDTH/HEIGHT);
        camera.rotate(0, -1.4f);

        AssetRegistry assets = new AssetRegistry(); Arena uiArena = Arena.ofShared();
        VkCommandPool pool = VkCommandPool.builder().device(vulkanContext().device()).queueFamilyIndex(vulkanContext().graphicsQueueFamily()).build(vulkanContext().arena());
        FontRegistry fonts = new FontRegistry(vulkanContext().device(), vulkanContext().graphicsVkQueue(), pool);
        fonts.loadFont(FONT_ID, loadFont(), 1024, 1024); assets.register(FontRegistry.class, fonts);
        UIContext uiCtx = UIContext.builder().vulkan(vulkanContext()).assets(assets).dimensions(WIDTH,HEIGHT).applicationArena(uiArena).build();

        Scene3DOverlayLayer overlay = new Scene3DOverlayLayer();
        GPUDrivenTextLayer text = new GPUDrivenTextLayer(FONT_ID);
        OrbitCameraLayer input = new OrbitCameraLayer(camera);
        input.onPlus(() -> { resolution = Math.min(80, resolution+5); rebuildContour(); });
        input.onMinus(() -> { resolution = Math.max(5, resolution-5); rebuildContour(); });
        input.onR(() -> { Random r = new Random(); freqX = 0.5f+r.nextFloat()*3f; freqY = 0.5f+r.nextFloat()*3f; phase = r.nextFloat()*6.28f; rebuildContour(); });
        input.onArrowLeft(() -> { fieldOffsetX -= 0.15f; rebuildContour(); });
        input.onArrowRight(() -> { fieldOffsetX += 0.15f; rebuildContour(); });
        input.onArrowUp(() -> { fieldOffsetY -= 0.15f; rebuildContour(); });
        input.onArrowDown(() -> { fieldOffsetY += 0.15f; rebuildContour(); });

        rebuildContour();

        overlay.setFrameCallback(dl -> {
            // Draw triangle grid faintly (matching the equilateral grid)
            if (showGrid) {
                float stepX = 6f / resolution;
                float triH = stepX * (float)(Math.sqrt(3.0) / 2.0);
                int gridRows = (int)(6f / triH);
                int gridCols = resolution;
                float[] gridCol = {0.2f, 0.2f, 0.25f, 0.3f};

                // Compute vertex positions matching the algorithm
                float[][] gx = new float[gridRows + 1][gridCols + 1];
                float[][] gy = new float[gridRows + 1][gridCols + 1];
                for (int r = 0; r <= gridRows; r++) {
                    float y = -3f + r * triH;
                    float offset = (r % 2 == 1) ? stepX * 0.5f : 0f;
                    for (int c = 0; c <= gridCols; c++) {
                        gx[r][c] = -3f + c * stepX + offset;
                        gy[r][c] = y;
                    }
                }

                // Draw all triangle edges
                for (int r = 0; r < gridRows; r++) {
                    for (int c = 0; c <= gridCols; c++) {
                        // Horizontal edge in current row
                        if (c < gridCols) {
                            dl.addLine(new float[]{gx[r][c],0,gy[r][c]}, new float[]{gx[r][c+1],0,gy[r][c+1]}, gridCol, DepthMode.DEPTH_TESTED);
                        }
                        // Edges to next row
                        if (r % 2 == 0) {
                            // Even row: vertex (r,c) connects down to (r+1,c) and (r+1,c-1) if exists
                            if (c <= gridCols) dl.addLine(new float[]{gx[r][c],0,gy[r][c]}, new float[]{gx[r+1][c],0,gy[r+1][c]}, gridCol, DepthMode.DEPTH_TESTED);
                            if (c > 0) dl.addLine(new float[]{gx[r][c],0,gy[r][c]}, new float[]{gx[r+1][c-1],0,gy[r+1][c-1]}, gridCol, DepthMode.DEPTH_TESTED);
                        } else {
                            // Odd row: vertex (r,c) connects down to (r+1,c) and (r+1,c+1) if exists
                            if (c <= gridCols) dl.addLine(new float[]{gx[r][c],0,gy[r][c]}, new float[]{gx[r+1][c],0,gy[r+1][c]}, gridCol, DepthMode.DEPTH_TESTED);
                            if (c < gridCols) dl.addLine(new float[]{gx[r][c],0,gy[r][c]}, new float[]{gx[r+1][c+1],0,gy[r+1][c+1]}, gridCol, DepthMode.DEPTH_TESTED);
                        }
                    }
                }
                // Last row horizontal edges
                for (int c = 0; c < gridCols; c++) {
                    dl.addLine(new float[]{gx[gridRows][c],0,gy[gridRows][c]}, new float[]{gx[gridRows][c+1],0,gy[gridRows][c+1]}, gridCol, DepthMode.DEPTH_TESTED);
                }
            }

            // Field sample points
            ScalarField2D field = FIELDS[fieldIndex];
            int sampleRes = Math.min(resolution, 20);
            float sStep = 6f / sampleRes, crossSize = sStep * 0.12f;
            float minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
            for (int x = 0; x <= sampleRes; x++) for (int y = 0; y <= sampleRes; y++) { float v = field.sample(-3f+x*sStep, -3f+y*sStep); if(v<minV)minV=v; if(v>maxV)maxV=v; }
            float vr = maxV-minV; if(vr<0.001f)vr=1f;
            for (int x = 0; x <= sampleRes; x++) for (int y = 0; y <= sampleRes; y++) {
                float px = -3f+x*sStep, pz = -3f+y*sStep;
                float t = (field.sample(px,pz)-minV)/vr;
                float[] ptCol = {t, 0.2f, 1f-t, 0.4f};
                dl.addLine(new float[]{px-crossSize,0.01f,pz}, new float[]{px+crossSize,0.01f,pz}, ptCol, DepthMode.DEPTH_TESTED);
                dl.addLine(new float[]{px,0.01f,pz-crossSize}, new float[]{px,0.01f,pz+crossSize}, ptCol, DepthMode.DEPTH_TESTED);
            }

            // Contour lines
            if (contour != null) {
                float[] contourCol = {1f, 0.9f, 0.2f, 0.9f};
                List<Vec2> verts = contour.vertices();
                for (int[] seg : contour.segments()) {
                    Vec2 a = verts.get(seg[0]), b = verts.get(seg[1]);
                    dl.addLine(new float[]{a.x, 0.02f, a.y}, new float[]{b.x, 0.02f, b.y}, contourCol, DepthMode.DEPTH_TESTED);
                }
            }

            dl.addWireBox(new float[]{-3,-0.01f,-3}, new float[]{3,0.01f,3}, new float[]{0.3f,0.3f,0.3f,0.3f}, DepthMode.DEPTH_TESTED);
        });
        text.setFrameCallback(b -> {
            b.drawText(FONT_ID, "Marching Triangles: "+FIELD_NAMES[fieldIndex], 20, 30, 22, 1f,1f,1f,1f);
            b.drawText(FONT_ID, "Res: "+resolution+"  Offset: ("+String.format("%.1f",fieldOffsetX)+","+String.format("%.1f",fieldOffsetY)+")", 20, 58, 16, 0.7f,0.8f,0.7f,1f);
            b.drawText(FONT_ID, "1-4=field +/-=res R=random G=grid Arrows=slide Drag=orbit Scroll=zoom", 20, 85, 14, 0.5f,0.5f,0.5f,1f);
        });

        UIComposite comp = UIComposite.builder().context(uiCtx).layer(overlay).layer(text).layer(input).build();
        wireInput(comp, overlay);
        Scene3DOverlayExampleFrame frame = new Scene3DOverlayExampleFrame(vulkanContext().arena(), vulkanContext().device(), vulkanContext().graphicsVkQueue(), surface(), WIDTH, HEIGHT, comp);
        frame.init(vulkanContext().graphicsQueueFamily());
        loop = GraphicsLoop.builder().renderer(frame).driver(LoopDriver.uncapped()).shouldClose(()->windowSystem().shouldClose(window())).onResize(d->{frame.resize(d[0],d[1]);camera.setAspect((float)d[0]/d[1]);}).onFpsUpdate(m->Logger.info(m.summary())).build();
        loop.start();
        while (!windowSystem().shouldClose(window())) { windowSystem().pollEvents(); overlay.setCamera(camera.viewArray(),camera.projArray()); try{Thread.sleep(1);}catch(InterruptedException e){break;} }
        loop.stop();
    }

    private void rebuildContour() { contour = MarchingTriangles.extract(FIELDS[fieldIndex], new Vec2(-3,-3), new Vec2(3,3), resolution, resolution, 0f); }

    private void wireInput(UIComposite comp, Scene3DOverlayLayer overlay) {
        Arena a = Arena.global(); double[] mp = {0,0};
        GLFWCallbacks.setCursorPosCallback(window(), (w,x,y)->{float dx=(float)(x-mp[0]),dy=(float)(y-mp[1]);mp[0]=x;mp[1]=y;comp.dispatchInput(PointerInputData.mouseMove((float)x,(float)y,dx,dy));}, a);
        GLFWCallbacks.setMouseButtonCallback(window(), (w,b,act,m)->comp.dispatchInput(act!=0?PointerInputData.mousePress(b,(float)mp[0],(float)mp[1]):PointerInputData.mouseRelease(b,(float)mp[0],(float)mp[1])), a);
        GLFWCallbacks.setScrollCallback(window(), (w,dx,dy)->comp.dispatchInput(ScrollInputData.scroll((float)mp[0],(float)mp[1],(float)dx,(float)dy)), a);
        GLFWCallbacks.setKeyCallback(window(), (w,key,sc,act,mod)->{if(act==1){switch(key){case 49->{fieldIndex=0;rebuildContour();}case 50->{fieldIndex=1;rebuildContour();}case 51->{fieldIndex=2;rebuildContour();}case 52->{fieldIndex=3;rebuildContour();}case 71->showGrid=!showGrid;}} comp.dispatchInput(act==1?KeyInputData.press(key,sc,mod):act==2?KeyInputData.repeat(key,sc,mod):KeyInputData.release(key,sc,mod));}, a);
    }

    @Override protected void onWindowResize(int w, int h) { if(loop!=null) loop.signalResize(w,h); }
    @Override protected void shutdown() {}
    private static byte[] loadFont(){for(String p:FONT_PATHS){Path path=Path.of(p);if(Files.exists(path))try{return Files.readAllBytes(path);}catch(IOException e){}}throw new IllegalStateException("No font");}
    public static void main(String[] args){new MarchingTrianglesApp().run();}
}
