package io.github.yetyman.vulkan.sample.spatial;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.spatial.isosurface.MarchingTetrahedra;
import io.github.yetyman.helpers.math.spatial.isosurface.MeshOutput;
import io.github.yetyman.helpers.math.spatial.isosurface.ScalarField3D;
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
 * Marching Tetrahedra Demo — ambiguity-free isosurface extraction.
 * Same fields as MarchingCubesApp for comparison. No topology holes by construction.
 */
public class MarchingTetrahedraApp extends VulkanApplication {

    private static final int WIDTH = 1280, HEIGHT = 720;
    private static final String FONT_ID = "default";
    private static final String[] FONT_PATHS = {"C:/Windows/Fonts/consola.ttf","C:/Windows/Fonts/arial.ttf","/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf","/System/Library/Fonts/Menlo.ttc"};

    private static final ScalarField3D[] FIELDS = {
            (x, y, z) -> 1f - (float) Math.sqrt(x*x + y*y + z*z),
            (x, y, z) -> { float d1 = (float)Math.sqrt((x-0.5f)*(x-0.5f)+y*y+z*z); float d2 = (float)Math.sqrt((x+0.5f)*(x+0.5f)+y*y+z*z); return 0.5f/(d1+0.01f)+0.5f/(d2+0.01f)-2f; },
            (x, y, z) -> { float R = (float)Math.sqrt(x*x+z*z)-0.7f; return 0.3f-(float)Math.sqrt(R*R+y*y); },
            (x, y, z) -> (float)(Math.sin(x*4)+Math.sin(y*4)+Math.sin(z*4))/3f
    };
    private static final String[] FIELD_NAMES = {"Sphere","Metaballs","Torus","Sine Waves"};

    private final OrbitCamera camera = new OrbitCamera();
    private GraphicsLoop loop;
    private int resolution = 15, fieldIndex = 0;
    private float isoLevel = 0f;
    private boolean animateIso = true;
    private MeshOutput currentMesh;

    public MarchingTetrahedraApp() { super("Marching Tetrahedra Demo", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem()); }

    @Override protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());
        camera.setDistance(5f); camera.setAspect((float)WIDTH/HEIGHT);
        AssetRegistry assets = new AssetRegistry(); Arena uiArena = Arena.ofShared();
        VkCommandPool pool = VkCommandPool.builder().device(vulkanContext().device()).queueFamilyIndex(vulkanContext().graphicsQueueFamily()).build(vulkanContext().arena());
        FontRegistry fonts = new FontRegistry(vulkanContext().device(), vulkanContext().graphicsVkQueue(), pool);
        fonts.loadFont(FONT_ID, loadFont(), 1024, 1024); assets.register(FontRegistry.class, fonts);
        UIContext uiCtx = UIContext.builder().vulkan(vulkanContext()).assets(assets).dimensions(WIDTH,HEIGHT).applicationArena(uiArena).build();

        Scene3DOverlayLayer overlay = new Scene3DOverlayLayer();
        GPUDrivenTextLayer text = new GPUDrivenTextLayer(FONT_ID);
        OrbitCameraLayer input = new OrbitCameraLayer(camera);
        input.onPlus(() -> { resolution = Math.min(40, resolution+5); rebuildMesh(); });
        input.onMinus(() -> { resolution = Math.max(5, resolution-5); rebuildMesh(); });

        overlay.setFrameCallback(dl -> {
            if (currentMesh != null) {
                List<Vec3> v = currentMesh.vertices(); List<Integer> idx = currentMesh.indices();
                float[] c = {0.4f, 0.7f, 1f, 0.8f};
                for (int i = 0; i < idx.size(); i += 3) {
                    dl.addLine(toArr(v.get(idx.get(i))), toArr(v.get(idx.get(i+1))), c, DepthMode.DEPTH_TESTED);
                    dl.addLine(toArr(v.get(idx.get(i+1))), toArr(v.get(idx.get(i+2))), c, DepthMode.DEPTH_TESTED);
                    dl.addLine(toArr(v.get(idx.get(i+2))), toArr(v.get(idx.get(i))), c, DepthMode.DEPTH_TESTED);
                }
            }
            // Field sample points
            ScalarField3D field = FIELDS[fieldIndex];
            int sampleRes = Math.min(resolution, 10);
            float sampleStep = 3f / sampleRes;
            float crossSize = sampleStep * 0.12f;
            for (int x = 0; x <= sampleRes; x++) for (int y = 0; y <= sampleRes; y++) for (int z = 0; z <= sampleRes; z++) {
                float px = -1.5f + x * sampleStep, py = -1.5f + y * sampleStep, pz = -1.5f + z * sampleStep;
                float val = field.sample(px, py, pz);
                float[] ptColor = val > isoLevel ? new float[]{0.2f,0.3f,0.9f,0.35f} : new float[]{0.9f,0.2f,0.2f,0.45f};
                dl.addLine(new float[]{px-crossSize,py,pz}, new float[]{px+crossSize,py,pz}, ptColor, DepthMode.DEPTH_TESTED);
                dl.addLine(new float[]{px,py-crossSize,pz}, new float[]{px,py+crossSize,pz}, ptColor, DepthMode.DEPTH_TESTED);
            }
            dl.addWireBox(new float[]{-1.5f,-1.5f,-1.5f}, new float[]{1.5f,1.5f,1.5f}, new float[]{0.3f,0.3f,0.3f,0.3f}, DepthMode.DEPTH_TESTED);
        });
        text.setFrameCallback(b -> {
            b.drawText(FONT_ID, "Marching Tetrahedra: "+FIELD_NAMES[fieldIndex], 20, 30, 22, 1f,1f,1f,1f);
            b.drawText(FONT_ID, "Res: "+resolution+"  V: "+(currentMesh!=null?currentMesh.vertexCount():0)+"  T: "+(currentMesh!=null?currentMesh.indexCount()/3:0), 20, 58, 16, 0.7f,0.8f,0.7f,1f);
            b.drawText(FONT_ID, "1-4=field +/-=res I=animate", 20, 85, 14, 0.5f,0.5f,0.5f,1f);
        });

        UIComposite comp = UIComposite.builder().context(uiCtx).layer(overlay).layer(text).layer(input).build();
        wireInput(comp, overlay);
        Scene3DOverlayExampleFrame frame = new Scene3DOverlayExampleFrame(vulkanContext().arena(), vulkanContext().device(), vulkanContext().graphicsVkQueue(), surface(), WIDTH, HEIGHT, comp);
        frame.init(vulkanContext().graphicsQueueFamily());
        loop = GraphicsLoop.builder().renderer(frame).driver(LoopDriver.uncapped()).shouldClose(()->windowSystem().shouldClose(window())).onResize(d->{frame.resize(d[0],d[1]);camera.setAspect((float)d[0]/d[1]);}).onFpsUpdate(m->Logger.info(m.summary())).build();
        rebuildMesh(); loop.start();
        while (!windowSystem().shouldClose(window())) { windowSystem().pollEvents(); if(animateIso){isoLevel=(float)Math.sin((System.nanoTime()/1e9)*0.5)*0.5f;rebuildMesh();} overlay.setCamera(camera.viewArray(),camera.projArray()); try{Thread.sleep(1);}catch(InterruptedException e){break;} }
        loop.stop();
    }

    private void rebuildMesh() { currentMesh = MarchingTetrahedra.extract(FIELDS[fieldIndex], new Vec3(-1.5f,-1.5f,-1.5f), new Vec3(1.5f,1.5f,1.5f), resolution, resolution, resolution, isoLevel); }

    private void wireInput(UIComposite comp, Scene3DOverlayLayer overlay) {
        Arena a = Arena.global(); double[] mp = {0,0};
        GLFWCallbacks.setCursorPosCallback(window(), (w,x,y)->{float dx=(float)(x-mp[0]),dy=(float)(y-mp[1]);mp[0]=x;mp[1]=y;comp.dispatchInput(PointerInputData.mouseMove((float)x,(float)y,dx,dy));}, a);
        GLFWCallbacks.setMouseButtonCallback(window(), (w,b,act,m)->comp.dispatchInput(act!=0?PointerInputData.mousePress(b,(float)mp[0],(float)mp[1]):PointerInputData.mouseRelease(b,(float)mp[0],(float)mp[1])), a);
        GLFWCallbacks.setScrollCallback(window(), (w,dx,dy)->comp.dispatchInput(ScrollInputData.scroll((float)mp[0],(float)mp[1],(float)dx,(float)dy)), a);
        GLFWCallbacks.setKeyCallback(window(), (w,key,sc,act,mod)->{if(act==1){switch(key){case 49->{fieldIndex=0;rebuildMesh();}case 50->{fieldIndex=1;rebuildMesh();}case 51->{fieldIndex=2;rebuildMesh();}case 52->{fieldIndex=3;rebuildMesh();}case 73->animateIso=!animateIso;}} comp.dispatchInput(act==1?KeyInputData.press(key,sc,mod):act==2?KeyInputData.repeat(key,sc,mod):KeyInputData.release(key,sc,mod));}, a);
    }

    @Override protected void onWindowResize(int w, int h) { if(loop!=null) loop.signalResize(w,h); }
    @Override protected void shutdown() {}
    private static float[] toArr(Vec3 v){return new float[]{v.x,v.y,v.z};}
    private static byte[] loadFont(){for(String p:FONT_PATHS){Path path=Path.of(p);if(Files.exists(path))try{return Files.readAllBytes(path);}catch(IOException e){}}throw new IllegalStateException("No font");}
    public static void main(String[] args){new MarchingTetrahedraApp().run();}
}
