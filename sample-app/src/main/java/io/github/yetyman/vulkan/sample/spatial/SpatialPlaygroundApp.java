package io.github.yetyman.vulkan.sample.spatial;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.geometry.Sphere;
import io.github.yetyman.helpers.math.spatial.SpatialStructure;
import io.github.yetyman.helpers.math.spatial.bvh.BVH;
import io.github.yetyman.helpers.math.spatial.grid.SparseGrid;
import io.github.yetyman.helpers.math.spatial.kdtree.KDTree;
import io.github.yetyman.helpers.math.spatial.octree.LinkedOctree;
import io.github.yetyman.helpers.math.spatial.octree.OctreeConfig;
import io.github.yetyman.helpers.math.spatial.quadtree.LinkedQuadtree;
import io.github.yetyman.helpers.math.spatial.quadtree.QuadtreeConfig;
import io.github.yetyman.helpers.math.spatial.rtree.RTree;
import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.assets.FontRegistry;
import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.layers.scene3d.Scene3DOverlayLayer;
import io.github.yetyman.vulkan.layers.scene3d.SpatialOverlayHelper;
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
 * Spatial Query Playground — interactive visualization of all spatial structures.
 *
 * Controls:
 *   Left-drag: orbit camera
 *   Right-drag: pan
 *   Scroll: zoom
 *   Space: cycle structure type
 *   R: randomize objects
 *   Q: cycle query type (AABB, Sphere)
 *   +/-: resize query region
 *   Arrow keys: move query center
 */
public class SpatialPlaygroundApp extends VulkanApplication {

    private static final int WIDTH = 1280, HEIGHT = 720;
    private static final int OBJECT_COUNT = 200;
    private static final float WORLD_SIZE = 50f;

    private enum StructureType { OCTREE, QUADTREE, BVH, RTREE, KDTREE, SPARSE_GRID }
    private enum QueryType { AABB, SPHERE }

    private StructureType currentStructure = StructureType.OCTREE;
    private QueryType currentQuery = QueryType.AABB;
    private SpatialStructure<Integer> structure;
    private final List<AABB> objectBounds = new ArrayList<>();
    private final OrbitCamera camera = new OrbitCamera();
    private SpatialOverlayHelper<Integer> overlayHelper;

    // Query state
    private final Vec3 queryCenter = new Vec3(0, 0, 0);
    private float querySize = 10f;

    // Graphics
    private GraphicsLoop loop;
    private Scene3DOverlayExampleFrame frame;
    private Scene3DOverlayLayer overlay;
    private GPUDrivenTextLayer textLayer;
    private UIComposite composite;
    private AssetRegistry assets;
    private Arena uiArena;
    private VkCommandPool textUploadPool;

    private static final String FONT_ID = "default";
    private static final String[] CANDIDATE_FONT_PATHS = {
            "C:/Windows/Fonts/consola.ttf",
            "C:/Windows/Fonts/arial.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
            "/System/Library/Fonts/Menlo.ttc"
    };

    public SpatialPlaygroundApp() {
        super("Spatial Playground", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());
        camera.setDistance(80f);
        camera.setAspect((float) WIDTH / HEIGHT);

        randomizeObjects();
        buildStructure();

        // Setup UI
        assets = new AssetRegistry();
        uiArena = Arena.ofShared();

        textUploadPool = VkCommandPool.builder()
                .device(vulkanContext().device())
                .queueFamilyIndex(vulkanContext().graphicsQueueFamily())
                .build(vulkanContext().arena());

        byte[] fontBytes = loadSystemFontBytes();
        FontRegistry fonts = new FontRegistry(
                vulkanContext().device(), vulkanContext().graphicsVkQueue(), textUploadPool);
        fonts.loadFont(FONT_ID, fontBytes, 1024, 1024);
        assets.register(FontRegistry.class, fonts);

        UIContext uiContext = UIContext.builder()
                .vulkan(vulkanContext())
                .assets(assets)
                .dimensions(WIDTH, HEIGHT)
                .applicationArena(uiArena)
                .build();

        overlay = new Scene3DOverlayLayer();
        overlayHelper = new SpatialOverlayHelper<>(structure, i -> objectBounds.get(i));
        overlay.setFrameCallback(overlayHelper.frameCallback());

        textLayer = new GPUDrivenTextLayer(FONT_ID);
        textLayer.setFrameCallback(batch -> {
            batch.drawText(FONT_ID, "Spatial Playground", 20, 30, 22, 1f, 1f, 1f, 1f);
            batch.drawText(FONT_ID, "Structure: " + currentStructure, 20, 60, 18, 0.8f, 0.9f, 1f, 1f);
            batch.drawText(FONT_ID, "Query: " + currentQuery + " (size=" + String.format("%.0f", querySize) + ")", 20, 85, 18, 0.8f, 0.9f, 1f, 1f);
            batch.drawText(FONT_ID, "Objects: " + structure.size(), 20, 110, 18, 0.7f, 0.8f, 0.7f, 1f);
            batch.drawText(FONT_ID, "", 20, 140, 16, 0.6f, 0.6f, 0.6f, 1f);
            batch.drawText(FONT_ID, "Space=structure  Q=query  R=randomize", 20, 160, 14, 0.5f, 0.5f, 0.5f, 1f);
            batch.drawText(FONT_ID, "+/-=size  Arrows=move  PgUp/Dn=Y  Drag=orbit  Scroll=zoom", 20, 180, 14, 0.5f, 0.5f, 0.5f, 1f);
        });

        // Input layer for orbit camera + key commands
        OrbitCameraLayer inputLayer = new OrbitCameraLayer(camera);
        inputLayer.onSpace(this::cycleStructure);
        inputLayer.onR(() -> { 
            randomizeObjects(); 
            buildStructure(); 
            Logger.info("Randomized " + OBJECT_COUNT + " objects"); 
        });
        inputLayer.onQ(this::cycleQuery);
        inputLayer.onPlus(() -> { querySize += 2f; Logger.info("Query size: " + querySize); });
        inputLayer.onMinus(() -> { querySize = Math.max(1f, querySize - 2f); Logger.info("Query size: " + querySize); });
        inputLayer.onArrowLeft(() -> queryCenter.x -= 3f);
        inputLayer.onArrowRight(() -> queryCenter.x += 3f);
        inputLayer.onArrowUp(() -> queryCenter.z -= 3f);
        inputLayer.onArrowDown(() -> queryCenter.z += 3f);

        // Add Y-axis movement: Page Up / Page Down
        inputLayer.onPageUp(() -> queryCenter.y += 3f);
        inputLayer.onPageDown(() -> queryCenter.y -= 3f);

        composite = UIComposite.builder()
                .context(uiContext)
                .layer(overlay)
                .layer(textLayer)
                .layer(inputLayer)
                .build();

        frame = new Scene3DOverlayExampleFrame(
                vulkanContext().arena(), vulkanContext().device(),
                vulkanContext().graphicsVkQueue(), surface(), WIDTH, HEIGHT, composite);
        frame.init(vulkanContext().graphicsQueueFamily());

        // Wire raw GLFW input into UIComposite dispatch
        wireInput(composite);

        loop = GraphicsLoop.builder()
                .renderer(frame)
                .driver(LoopDriver.uncapped())
                .shouldClose(() -> windowSystem().shouldClose(window()))
                .onResize(dims -> {
                    frame.resize(dims[0], dims[1]);
                    camera.setAspect((float) dims[0] / dims[1]);
                })
                .onFpsUpdate(m -> Logger.info(m.summary()))
                .build();

        loop.start();

        Logger.info("Spatial Playground: " + currentStructure + " (" + OBJECT_COUNT + " objects)");
        Logger.info("Controls: Space=cycle structure, R=randomize, Q=cycle query, +/-=resize, Arrows=move query");

        // Main thread: poll events, update query + camera
        while (!windowSystem().shouldClose(window())) {
            windowSystem().pollEvents();
            updateQuery();
            overlay.setCamera(camera.viewArray(), camera.projArray());
            try { Thread.sleep(1); } catch (InterruptedException e) { break; }
        }
        loop.stop();
    }

    private void wireInput(UIComposite composite) {
        Arena callbackArena = Arena.global();
        double[] lastMousePos = {0, 0};

        GLFWCallbacks.setCursorPosCallback(window(), (w, x, y) -> {
            float dx = (float) (x - lastMousePos[0]);
            float dy = (float) (y - lastMousePos[1]);
            lastMousePos[0] = x;
            lastMousePos[1] = y;
            composite.dispatchInput(PointerInputData.mouseMove((float) x, (float) y, dx, dy));
        }, callbackArena);

        GLFWCallbacks.setMouseButtonCallback(window(), (w, button, action, mods) -> {
            boolean pressed = action != 0;
            composite.dispatchInput(pressed
                    ? PointerInputData.mousePress(button, (float) lastMousePos[0], (float) lastMousePos[1])
                    : PointerInputData.mouseRelease(button, (float) lastMousePos[0], (float) lastMousePos[1]));
        }, callbackArena);

        GLFWCallbacks.setScrollCallback(window(), (w, dx, dy) ->
                composite.dispatchInput(ScrollInputData.scroll(
                        (float) lastMousePos[0], (float) lastMousePos[1], (float) dx, (float) dy)),
                callbackArena);

        GLFWCallbacks.setKeyCallback(window(), (w, key, scancode, action, mods) -> {
            InputEvent event = switch (action) {
                case 1 -> KeyInputData.press(key, scancode, mods);
                case 2 -> KeyInputData.repeat(key, scancode, mods);
                default -> KeyInputData.release(key, scancode, mods);
            };
            composite.dispatchInput(event);
        }, callbackArena);
    }

    @Override
    protected void onWindowResize(int w, int h) {
        if (loop != null) loop.signalResize(w, h);
    }

    @Override
    protected void shutdown() {
        if (frame != null) frame.close();
        if (assets != null) assets.close();
        if (textUploadPool != null) textUploadPool.close();
        if (uiArena != null) uiArena.close();
    }

    // --- Spatial logic ---

    private void randomizeObjects() {
        objectBounds.clear();
        Random rng = new Random();
        for (int i = 0; i < OBJECT_COUNT; i++) {
            float x = (rng.nextFloat() - 0.5f) * WORLD_SIZE * 2;
            float y = (rng.nextFloat() - 0.5f) * WORLD_SIZE * 2;
            float z = (rng.nextFloat() - 0.5f) * WORLD_SIZE * 2;
            float size = 0.5f + rng.nextFloat() * 2f;
            objectBounds.add(new AABB(new Vec3(x, y, z), new Vec3(x + size, y + size, z + size)));
        }
    }

    private void buildStructure() {
        // Snapshot bounds to avoid race with render thread
        List<AABB> snapshot = new ArrayList<>(objectBounds);
        SpatialStructure<Integer> newStructure = createStructure(currentStructure);
        for (int i = 0; i < snapshot.size(); i++) {
            newStructure.insert(i, snapshot.get(i));
        }
        structure = newStructure;
        if (overlayHelper != null) overlayHelper.setStructure(structure, i -> snapshot.get(i));
    }

    private SpatialStructure<Integer> createStructure(StructureType type) {
        return switch (type) {
            case OCTREE -> new LinkedOctree<>(OctreeConfig.builder()
                    .worldBounds(new AABB(new Vec3(-WORLD_SIZE, -WORLD_SIZE, -WORLD_SIZE),
                            new Vec3(WORLD_SIZE, WORLD_SIZE, WORLD_SIZE)))
                    .maxDepth(6).splitThreshold(8).build());
            case QUADTREE -> new LinkedQuadtree<>(QuadtreeConfig.builder()
                    .worldBounds(new AABB(new Vec3(-WORLD_SIZE, -WORLD_SIZE, -WORLD_SIZE),
                            new Vec3(WORLD_SIZE, WORLD_SIZE, WORLD_SIZE)))
                    .maxDepth(6).splitThreshold(8).build());
            case BVH -> new BVH<>();
            case RTREE -> new RTree<>();
            case KDTREE -> new KDTree<>();
            case SPARSE_GRID -> new SparseGrid<>(5f);
        };
    }

    private void updateQuery() {
        AABB queryAABB = new AABB(
                new Vec3(queryCenter.x - querySize, queryCenter.y - querySize, queryCenter.z - querySize),
                new Vec3(queryCenter.x + querySize, queryCenter.y + querySize, queryCenter.z + querySize));
        Sphere querySphere = new Sphere(new Vec3(queryCenter), querySize);

        List<Integer> results = switch (currentQuery) {
            case AABB -> structure.query(queryAABB);
            case SPHERE -> structure.query(querySphere);
        };

        overlayHelper.setQueryResults(results);
        switch (currentQuery) {
            case AABB -> overlayHelper.setQueryAABB(queryAABB);
            case SPHERE -> overlayHelper.setQuerySphere(querySphere);
        }
    }

    private void cycleStructure() {
        StructureType[] types = StructureType.values();
        currentStructure = types[(currentStructure.ordinal() + 1) % types.length];
        buildStructure();
        Logger.info("Structure: " + currentStructure);
    }

    private void cycleQuery() {
        QueryType[] types = QueryType.values();
        currentQuery = types[(currentQuery.ordinal() + 1) % types.length];
        Logger.info("Query: " + currentQuery);
    }

    public static void main(String[] args) {
        new SpatialPlaygroundApp().run();
    }

    private static byte[] loadSystemFontBytes() {
        for (String candidate : CANDIDATE_FONT_PATHS) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) {
                try { return Files.readAllBytes(path); }
                catch (IOException e) { Logger.error("Failed to read font: " + e.getMessage()); }
            }
        }
        throw new IllegalStateException("No system font found. Edit CANDIDATE_FONT_PATHS.");
    }
}
