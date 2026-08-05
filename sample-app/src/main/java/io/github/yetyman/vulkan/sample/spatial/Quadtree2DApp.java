package io.github.yetyman.vulkan.sample.spatial;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.spatial.NodeVisitor;
import io.github.yetyman.helpers.math.spatial.quadtree.LinkedQuadtree;
import io.github.yetyman.helpers.math.spatial.quadtree.QuadtreeConfig;
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
 * Quadtree 2D Demo — top-down view of 2D spatial partitioning on the XZ plane.
 * Click to place objects, see the tree subdivide in real-time.
 *
 * Controls: Click=place object, R=randomize, C=clear, +/-=split threshold, Scroll=zoom, Drag=pan
 */
public class Quadtree2DApp extends VulkanApplication {

    private static final int WIDTH = 1280, HEIGHT = 720;
    private static final String FONT_ID = "default";
    private static final String[] FONT_PATHS = {"C:/Windows/Fonts/consola.ttf","C:/Windows/Fonts/arial.ttf","/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf","/System/Library/Fonts/Menlo.ttc"};
    private static final float WORLD = 20f;

    private final OrbitCamera camera = new OrbitCamera();
    private GraphicsLoop loop;
    private LinkedQuadtree<Integer> tree;
    private final List<AABB> objects = new ArrayList<>();
    private int splitThreshold = 4;
    private AABB queryBox = new AABB(new Vec3(-5, -1, -5), new Vec3(5, 1, 5));

    public Quadtree2DApp() { super("Quadtree 2D Demo", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem()); }

    @Override protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());
        camera.setDistance(35f); camera.setAspect((float)WIDTH/HEIGHT);
        camera.rotate(0, -1.4f); // top-down

        AssetRegistry assets = new AssetRegistry(); Arena uiArena = Arena.ofShared();
        VkCommandPool pool = VkCommandPool.builder().device(vulkanContext().device()).queueFamilyIndex(vulkanContext().graphicsQueueFamily()).build(vulkanContext().arena());
        FontRegistry fonts = new FontRegistry(vulkanContext().device(), vulkanContext().graphicsVkQueue(), pool);
        fonts.loadFont(FONT_ID, loadFont(), 1024, 1024); assets.register(FontRegistry.class, fonts);
        UIContext uiCtx = UIContext.builder().vulkan(vulkanContext()).assets(assets).dimensions(WIDTH,HEIGHT).applicationArena(uiArena).build();

        Scene3DOverlayLayer overlay = new Scene3DOverlayLayer();
        GPUDrivenTextLayer text = new GPUDrivenTextLayer(FONT_ID);
        OrbitCameraLayer input = new OrbitCameraLayer(camera);
        input.onR(() -> { randomize(); });
        input.onPlus(() -> { splitThreshold = Math.min(32, splitThreshold + 1); rebuild(); });
        input.onMinus(() -> { splitThreshold = Math.max(1, splitThreshold - 1); rebuild(); });

        randomize();

        overlay.setFrameCallback(dl -> {
            // Draw tree cells on XZ plane
            tree.visitNodes((bounds, depth, isLeaf, itemCount) -> {
                float t = Math.min(1f, depth * 0.2f);
                float alpha = 0.2f + t * 0.6f;
                float[] col = {0.3f + t * 0.4f, 0.7f, 0.3f, alpha};
                // Draw as flat rect on Y=0
                dl.addLine(new float[]{bounds.min.x, 0, bounds.min.z}, new float[]{bounds.max.x, 0, bounds.min.z}, col, DepthMode.DEPTH_TESTED);
                dl.addLine(new float[]{bounds.max.x, 0, bounds.min.z}, new float[]{bounds.max.x, 0, bounds.max.z}, col, DepthMode.DEPTH_TESTED);
                dl.addLine(new float[]{bounds.max.x, 0, bounds.max.z}, new float[]{bounds.min.x, 0, bounds.max.z}, col, DepthMode.DEPTH_TESTED);
                dl.addLine(new float[]{bounds.min.x, 0, bounds.max.z}, new float[]{bounds.min.x, 0, bounds.min.z}, col, DepthMode.DEPTH_TESTED);
            });
            // Draw objects as small filled squares
            List<Integer> hits = tree.query(queryBox);
            for (int i = 0; i < objects.size(); i++) {
                AABB b = objects.get(i);
                float[] col = hits.contains(i) ? new float[]{0.2f,1f,0.3f,0.9f} : new float[]{0.5f,0.5f,0.8f,0.8f};
                dl.addWireBox(new float[]{b.min.x, -0.1f, b.min.z}, new float[]{b.max.x, 0.1f, b.max.z}, col, DepthMode.DEPTH_TESTED);
            }
            // Query box
            dl.addWireBox(new float[]{queryBox.min.x, -0.2f, queryBox.min.z}, new float[]{queryBox.max.x, 0.2f, queryBox.max.z}, new float[]{1f,1f,0.2f,0.7f}, DepthMode.ALWAYS_ON_TOP);
        });
        text.setFrameCallback(b -> {
            b.drawText(FONT_ID, "Quadtree 2D", 20, 30, 22, 1f,1f,1f,1f);
            b.drawText(FONT_ID, "Objects: "+objects.size()+"  Split: "+splitThreshold, 20, 58, 16, 0.7f,0.8f,0.7f,1f);
            b.drawText(FONT_ID, "R=randomize  C=clear  +/-=threshold  Drag=orbit  Scroll=zoom", 20, 85, 14, 0.5f,0.5f,0.5f,1f);
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

    private void randomize() {
        objects.clear(); Random rng = new Random();
        // Clusters
        for (int i = 0; i < 30; i++) { float x = rng.nextFloat()*8-4, z = rng.nextFloat()*8-4; float s = 0.2f+rng.nextFloat()*0.5f; objects.add(new AABB(new Vec3(x,0,z), new Vec3(x+s,0.1f,z+s))); }
        for (int i = 0; i < 20; i++) { float x = 10+rng.nextFloat()*5, z = -5+rng.nextFloat()*10; float s = 0.3f+rng.nextFloat()*0.7f; objects.add(new AABB(new Vec3(x,0,z), new Vec3(x+s,0.1f,z+s))); }
        for (int i = 0; i < 15; i++) { float x = (rng.nextFloat()-0.5f)*WORLD*2, z = (rng.nextFloat()-0.5f)*WORLD*2; float s = 0.5f+rng.nextFloat()*1.5f; objects.add(new AABB(new Vec3(x,0,z), new Vec3(x+s,0.1f,z+s))); }
        rebuild();
    }

    private void rebuild() {
        LinkedQuadtree<Integer> newTree = new LinkedQuadtree<>(QuadtreeConfig.builder()
                .worldBounds(new AABB(new Vec3(-WORLD,-1,-WORLD), new Vec3(WORLD,1,WORLD)))
                .maxDepth(8).splitThreshold(splitThreshold).mergeThreshold(Math.max(1, splitThreshold/2)).build());
        List<AABB> snapshot = new ArrayList<>(objects);
        for (int i = 0; i < snapshot.size(); i++) newTree.insert(i, snapshot.get(i));
        tree = newTree;
    }

    private void wireInput(UIComposite comp, Scene3DOverlayLayer overlay) {
        Arena a = Arena.global(); double[] mp = {0,0};
        GLFWCallbacks.setCursorPosCallback(window(), (w,x,y)->{float dx=(float)(x-mp[0]),dy=(float)(y-mp[1]);mp[0]=x;mp[1]=y;comp.dispatchInput(PointerInputData.mouseMove((float)x,(float)y,dx,dy));}, a);
        GLFWCallbacks.setMouseButtonCallback(window(), (w,b,act,m)->comp.dispatchInput(act!=0?PointerInputData.mousePress(b,(float)mp[0],(float)mp[1]):PointerInputData.mouseRelease(b,(float)mp[0],(float)mp[1])), a);
        GLFWCallbacks.setScrollCallback(window(), (w,dx,dy)->comp.dispatchInput(ScrollInputData.scroll((float)mp[0],(float)mp[1],(float)dx,(float)dy)), a);
        GLFWCallbacks.setKeyCallback(window(), (w,key,sc,act,mod)->{if(act==1){switch(key){case 82->randomize();case 67->{objects.clear();rebuild();}}} comp.dispatchInput(act==1?KeyInputData.press(key,sc,mod):act==2?KeyInputData.repeat(key,sc,mod):KeyInputData.release(key,sc,mod));}, a);
    }

    @Override protected void onWindowResize(int w, int h) { if(loop!=null) loop.signalResize(w,h); }
    @Override protected void shutdown() {}
    private static byte[] loadFont(){for(String p:FONT_PATHS){Path path=Path.of(p);if(Files.exists(path))try{return Files.readAllBytes(path);}catch(IOException e){}}throw new IllegalStateException("No font");}
    public static void main(String[] args){new Quadtree2DApp().run();}
}
