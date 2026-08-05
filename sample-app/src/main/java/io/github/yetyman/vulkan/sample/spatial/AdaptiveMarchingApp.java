package io.github.yetyman.vulkan.sample.spatial;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.spatial.isosurface.AdaptiveMarchingCubes;
import io.github.yetyman.helpers.math.spatial.isosurface.MeshOutput;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Adaptive Marching Cubes Demo — point cloud to mesh via density-based octree subdivision.
 * Dense clusters get fine resolution, sparse areas get coarse resolution.
 *
 * Controls:
 *   1-4: point cloud preset
 *   R: randomize
 *   +/-: kernel radius
 *   T: iso threshold
 *   Drag: orbit, Scroll: zoom
 */
public class AdaptiveMarchingApp extends VulkanApplication {

    private static final int WIDTH = 1280, HEIGHT = 720;
    private static final String FONT_ID = "default";
    private static final String[] FONT_PATHS = {"C:/Windows/Fonts/consola.ttf","C:/Windows/Fonts/arial.ttf","/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf","/System/Library/Fonts/Menlo.ttc"};

    private final OrbitCamera camera = new OrbitCamera();
    private GraphicsLoop loop;
    private List<Vec3> points = new ArrayList<>();
    private List<Float> weights = new ArrayList<>();
    private MeshOutput currentMesh;
    private float surfaceRadius = 1.0f;
    private float pointWeight = 1.0f;
    private int presetIndex = 0;
    private long lastRebuildMs = 0;

    public AdaptiveMarchingApp() { super("Adaptive Marching Cubes", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem()); }

    @Override protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());
        camera.setDistance(12f); camera.setAspect((float)WIDTH/HEIGHT);

        AssetRegistry assets = new AssetRegistry(); Arena uiArena = Arena.ofShared();
        VkCommandPool pool = VkCommandPool.builder().device(vulkanContext().device()).queueFamilyIndex(vulkanContext().graphicsQueueFamily()).build(vulkanContext().arena());
        FontRegistry fonts = new FontRegistry(vulkanContext().device(), vulkanContext().graphicsVkQueue(), pool);
        fonts.loadFont(FONT_ID, loadFont(), 1024, 1024); assets.register(FontRegistry.class, fonts);
        UIContext uiCtx = UIContext.builder().vulkan(vulkanContext()).assets(assets).dimensions(WIDTH,HEIGHT).applicationArena(uiArena).build();

        Scene3DOverlayLayer overlay = new Scene3DOverlayLayer();
        GPUDrivenTextLayer text = new GPUDrivenTextLayer(FONT_ID);
        OrbitCameraLayer input = new OrbitCameraLayer(camera);
        input.onPlus(() -> { surfaceRadius += 0.1f; rebuildMesh(); });
        input.onMinus(() -> { surfaceRadius = Math.max(0.1f, surfaceRadius - 0.1f); rebuildMesh(); });
        input.onR(() -> { generatePoints(presetIndex); rebuildMesh(); });
        input.onArrowUp(() -> { pointWeight += 0.2f; rebuildMesh(); });
        input.onArrowDown(() -> { pointWeight = Math.max(0.2f, pointWeight - 0.2f); rebuildMesh(); });

        generatePoints(0);
        rebuildMesh();

        overlay.setFrameCallback(dl -> {
            // Draw points as small crosses
            float[] ptColor = {0.8f, 0.8f, 0.3f, 0.6f};
            float cs = 0.05f;
            for (Vec3 p : points) {
                dl.addLine(new float[]{p.x-cs,p.y,p.z}, new float[]{p.x+cs,p.y,p.z}, ptColor, DepthMode.DEPTH_TESTED);
                dl.addLine(new float[]{p.x,p.y-cs,p.z}, new float[]{p.x,p.y+cs,p.z}, ptColor, DepthMode.DEPTH_TESTED);
                dl.addLine(new float[]{p.x,p.y,p.z-cs}, new float[]{p.x,p.y,p.z+cs}, ptColor, DepthMode.DEPTH_TESTED);
            }
            // Draw mesh wireframe
            if (currentMesh != null) {
                List<Vec3> v = currentMesh.vertices(); List<Integer> idx = currentMesh.indices();
                float[] mc = {0.3f, 0.9f, 0.5f, 0.7f};
                for (int i = 0; i < idx.size(); i += 3) {
                    dl.addLine(toArr(v.get(idx.get(i))), toArr(v.get(idx.get(i+1))), mc, DepthMode.DEPTH_TESTED);
                    dl.addLine(toArr(v.get(idx.get(i+1))), toArr(v.get(idx.get(i+2))), mc, DepthMode.DEPTH_TESTED);
                    dl.addLine(toArr(v.get(idx.get(i+2))), toArr(v.get(idx.get(i))), mc, DepthMode.DEPTH_TESTED);
                }
            }
        });
        text.setFrameCallback(b -> {
            b.drawText(FONT_ID, "Adaptive Marching Cubes", 20, 30, 22, 1f,1f,1f,1f);
            b.drawText(FONT_ID, "Points: "+points.size()+"  Surface R: "+String.format("%.2f",surfaceRadius)+"  Weight: "+String.format("%.1f",pointWeight), 20, 58, 16, 0.7f,0.8f,0.7f,1f);
            b.drawText(FONT_ID, "Verts: "+(currentMesh!=null?currentMesh.vertexCount():0)+"  Tris: "+(currentMesh!=null?currentMesh.indexCount()/3:0)+"  Build: "+lastRebuildMs+"ms", 20, 80, 16, 0.7f,0.8f,0.7f,1f);
            b.drawText(FONT_ID, "1-4=preset R=random +/-=radius Up/Dn=weight Drag=orbit Scroll=zoom", 20, 110, 14, 0.5f,0.5f,0.5f,1f);
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

    private void rebuildMesh() {
        long t0 = System.currentTimeMillis();
        AdaptiveMarchingCubes amc = new AdaptiveMarchingCubes(surfaceRadius, 4, 5);
        List<Vec3> snapshot = new ArrayList<>(points);
        MeshOutput newMesh = amc.extract(snapshot, pointWeight);
        currentMesh = newMesh;
        lastRebuildMs = System.currentTimeMillis() - t0;
        Logger.info("Rebuilt: " + currentMesh.vertexCount() + " verts, " + currentMesh.indexCount()/3 + " tris in " + lastRebuildMs + "ms");
    }

    private void generatePoints(int preset) {
        points.clear();
        Random rng = new Random();
        switch (preset) {
            case 0 -> {
                // Sphere shell
                for (int i = 0; i < 200; i++) {
                    float theta = rng.nextFloat() * 6.28f;
                    float phi = (float)Math.acos(2*rng.nextFloat()-1);
                    float r = 3f + (rng.nextFloat()-0.5f)*0.3f;
                    points.add(new Vec3(r*(float)Math.sin(phi)*(float)Math.cos(theta), r*(float)Math.sin(phi)*(float)Math.sin(theta), r*(float)Math.cos(phi)));
                }
            }
            case 1 -> {
                // Two dense clusters + sparse scatter
                for (int i = 0; i < 80; i++) points.add(new Vec3(-2+rng.nextFloat()*1.5f, rng.nextFloat()*1.5f-0.75f, rng.nextFloat()*1.5f-0.75f));
                for (int i = 0; i < 80; i++) points.add(new Vec3(2+rng.nextFloat()*1f, rng.nextFloat()*1f-0.5f, rng.nextFloat()*1f-0.5f));
                for (int i = 0; i < 40; i++) points.add(new Vec3((rng.nextFloat()-0.5f)*8, (rng.nextFloat()-0.5f)*4, (rng.nextFloat()-0.5f)*4));
            }
            case 2 -> {
                // Torus point cloud
                for (int i = 0; i < 300; i++) {
                    float theta = rng.nextFloat() * 6.28f;
                    float phi = rng.nextFloat() * 6.28f;
                    float R = 3f, r = 1f;
                    float noise = (rng.nextFloat()-0.5f)*0.2f;
                    points.add(new Vec3((R+(r+noise)*(float)Math.cos(phi))*(float)Math.cos(theta), (r+noise)*(float)Math.sin(phi), (R+(r+noise)*(float)Math.cos(phi))*(float)Math.sin(theta)));
                }
            }
            case 3 -> {
                // Line + blob
                for (int i = 0; i < 60; i++) points.add(new Vec3(-4+i*0.13f, (rng.nextFloat()-0.5f)*0.3f, (rng.nextFloat()-0.5f)*0.3f));
                for (int i = 0; i < 100; i++) { float a = rng.nextFloat()*6.28f; float r = rng.nextFloat()*1.5f; points.add(new Vec3(r*(float)Math.cos(a), 2+rng.nextFloat()*1.5f, r*(float)Math.sin(a))); }
            }
        }
        presetIndex = preset;
    }

    private void wireInput(UIComposite comp, Scene3DOverlayLayer overlay) {
        Arena a = Arena.global(); double[] mp = {0,0};
        GLFWCallbacks.setCursorPosCallback(window(), (w,x,y)->{float dx=(float)(x-mp[0]),dy=(float)(y-mp[1]);mp[0]=x;mp[1]=y;comp.dispatchInput(PointerInputData.mouseMove((float)x,(float)y,dx,dy));}, a);
        GLFWCallbacks.setMouseButtonCallback(window(), (w,b,act,m)->comp.dispatchInput(act!=0?PointerInputData.mousePress(b,(float)mp[0],(float)mp[1]):PointerInputData.mouseRelease(b,(float)mp[0],(float)mp[1])), a);
        GLFWCallbacks.setScrollCallback(window(), (w,dx,dy)->comp.dispatchInput(ScrollInputData.scroll((float)mp[0],(float)mp[1],(float)dx,(float)dy)), a);
        GLFWCallbacks.setKeyCallback(window(), (w,key,sc,act,mod)->{if(act==1){switch(key){case 49->{generatePoints(0);rebuildMesh();}case 50->{generatePoints(1);rebuildMesh();}case 51->{generatePoints(2);rebuildMesh();}case 52->{generatePoints(3);rebuildMesh();}}} comp.dispatchInput(act==1?KeyInputData.press(key,sc,mod):act==2?KeyInputData.repeat(key,sc,mod):KeyInputData.release(key,sc,mod));}, a);
    }

    @Override protected void onWindowResize(int w, int h) { if(loop!=null) loop.signalResize(w,h); }
    @Override protected void shutdown() {}
    private static float[] toArr(Vec3 v){return new float[]{v.x,v.y,v.z};}
    private static byte[] loadFont(){for(String p:FONT_PATHS){Path path=Path.of(p);if(Files.exists(path))try{return Files.readAllBytes(path);}catch(IOException e){}}throw new IllegalStateException("No font");}
    public static void main(String[] args){new AdaptiveMarchingApp().run();}
}
