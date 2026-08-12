package io.github.yetyman.vulkan.layers.lodscene;

import io.github.yetyman.helpers.math.Mat4;
import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPipeline;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.command.VkBind;
import io.github.yetyman.vulkan.command.VkDraw;
import io.github.yetyman.vulkan.command.VkDrawIndexed;
import io.github.yetyman.vulkan.command.VkSetState;
import io.github.yetyman.vulkan.enums.VkCompareOp;
import io.github.yetyman.vulkan.enums.VkFormat;
import io.github.yetyman.vulkan.enums.VkIndexType;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.highlevel.VkVertexFormat;
import io.github.yetyman.vulkan.highlevel.VulkanContext;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.Mesh;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.consume.GeometryBinding;
import io.github.yetyman.vulkan.mesh.consume.GeometryDrawRange;
import io.github.yetyman.vulkan.mesh.lod.LodContext;
import io.github.yetyman.vulkan.mesh.lod.LodSelection;
import io.github.yetyman.vulkan.mesh.lod.LodSelector;
import io.github.yetyman.vulkan.mesh.lod.RepresentationNode;
import io.github.yetyman.vulkan.mesh.lod.RepresentationStructure;
import io.github.yetyman.vulkan.mesh.process.NormalGenerator;
import io.github.yetyman.vulkan.mesh.processing.QemSimplifier;
import io.github.yetyman.vulkan.mesh.residency.DedicatedAllocator;
import io.github.yetyman.vulkan.mesh.source.AttributeStream;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.SegmentAttributeStream;
import io.github.yetyman.vulkan.mesh.source.SegmentGeometrySource;
import io.github.yetyman.vulkan.shaders.CompiledShader;
import io.github.yetyman.vulkan.shaders.ShaderLoader;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.ui.UIFrameContext;
import io.github.yetyman.vulkan.ui.UILayer;
import io.github.yetyman.vulkan.ui.input.InputEvent;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.yetyman.vulkan.generated.VulkanFFM.VK_CULL_MODE_BACK_BIT;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdBindPipeline;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdPushConstants;

/**
 * UILayer that renders a scene of positioned models, each with a runtime-generated LOD chain.
 * LOD selection is driven by screen-space error relative to the camera provided via
 * {@link #setCamera(Vec3, Mat4, Mat4)}.
 *
 * <p>This is a sample/provisional implementation. A production system would decouple the scene
 * graph from rendering, support instancing, GPU-driven LOD selection, and more sophisticated
 * transition modes. This exists to demonstrate the LOD infrastructure in an interactive setting
 * with minimal code.
 *
 * <p>Usage:
 * <pre>{@code
 * LodSceneLayer scene = new LodSceneLayer();
 * scene.addModel("Sphere", sphereSource, arena, new Mat4().translate(0, 0, 0));
 * scene.addModel("Box", boxSource, arena, new Mat4().translate(5, 0, 0));
 * // ... add to UIComposite alongside an OrbitCameraLayer
 * // In update loop: scene.setCamera(eye, viewMatrix, projMatrix);
 * }</pre>
 */
public class LodSceneLayer implements UILayer {

    private static final int ORDER = 50;
    private static final float[] DEFAULT_DECIMATION_RATIOS = {0.3f, 0.1f, 0.03f};
    private static final float ERROR_THRESHOLD_PIXELS = 2.0f;

    /** Shared mesh layout: position + normal + uv in separate streams. */
    public static final MeshLayout LAYOUT = MeshLayout.builder()
            .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
            .stream(1).attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
            .stream(2).attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
            .build();

    private static final Map<AttributeSemantic, Integer> LOCATIONS = Map.of(
            AttributeSemantic.POSITION, 0,
            AttributeSemantic.NORMAL, 1,
            AttributeSemantic.TEXCOORD(0), 2);

    // Vulkan state
    private UIContext ctx;
    private VkDevice device;
    private VkQueue queue;
    private Arena layerArena;
    private VkPipeline pipeline;
    private DedicatedAllocator allocator;
    private int width;
    private int height;
    private boolean initialized = false;

    // Camera state (set externally or via supplier)
    private Vec3 cameraEye = new Vec3(0, 0, 10);
    private Mat4 viewMatrix = Mat4.identity();
    private Mat4 projMatrix = Mat4.identity();
    private Mat4 vpMatrix = Mat4.identity();
    private CameraSource cameraSource;

    // Scene objects
    private final List<SceneObject> objects = new ArrayList<>();
    private final List<PendingModel> pendingModels = new ArrayList<>();
    private final List<PendingCustomModel> pendingCustomModels = new ArrayList<>();

    // LOD selection
    private LodSelector selector;

    // Stats (readable by text overlays)
    private long totalTrianglesThisFrame = 0;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Functional interface for providing camera state each frame. Avoids needing
     * an external pre-update callback — the layer pulls camera state at update time.
     */
    @FunctionalInterface
    public interface CameraSource {
        /** Called once per frame to supply camera state. */
        void apply(LodSceneLayer layer);
    }

    /**
     * Sets a camera source that is polled each frame at the start of update().
     * This is the recommended approach for integration with OrbitCamera or similar.
     *
     * @param source camera source supplier
     */
    public void setCameraSource(CameraSource source) {
        this.cameraSource = source;
    }

    /**
     * Sets the camera state for LOD selection and rendering. Call this each frame before update(),
     * or use {@link #setCameraSource(CameraSource)} for automatic polling.
     *
     * @param eye  camera world position
     * @param view view matrix (column-major)
     * @param proj projection matrix (column-major)
     */
    public void setCamera(Vec3 eye, Mat4 view, Mat4 proj) {
        this.cameraEye = eye;
        this.viewMatrix = view;
        this.projMatrix = proj;
        this.vpMatrix = proj.mulNew(view);
    }

    /**
     * Adds a model to the scene. The source is simplified into a LOD chain on the calling
     * thread. If the layer is already initialized, upload happens immediately; otherwise
     * it is deferred to initialization.
     *
     * @param name      display name for HUD/debugging
     * @param source    geometry source (must have POSITION; normals generated if missing)
     * @param arena     arena owning the source data (must outlive the call)
     * @param transform world transform for placement
     */
    public void addModel(String name, GeometrySource source, Arena arena, Mat4 transform) {
        addModel(name, source, arena, transform, DEFAULT_DECIMATION_RATIOS);
    }

    /**
     * Adds a model with custom decimation ratios for LOD generation.
     *
     * @param name             display name
     * @param source           geometry source
     * @param arena            arena owning the source data
     * @param transform        world transform
     * @param decimationRatios ratios for each simplified level (e.g. 0.5, 0.25, 0.1)
     */
    public void addModel(String name, GeometrySource source, Arena arena,
                         Mat4 transform, float[] decimationRatios) {
        if (!initialized) {
            pendingModels.add(new PendingModel(name, source, arena, transform, decimationRatios));
        } else {
            objects.add(buildSceneObject(name, source, arena, transform, decimationRatios));
        }
    }

    /**
     * Adds a model with explicit pre-built LOD sources. No automatic decimation is performed.
     * Each source becomes one LOD level (index 0 = finest). Error bounds are assigned
     * automatically based on level index and the bounds of the first source.
     *
     * @param name      display name
     * @param lodSources ordered list of geometry sources (finest first)
     * @param arena     arena owning the source data
     * @param transform world transform
     */
    public void addCustomLodModel(String name, List<GeometrySource> lodSources, Arena arena,
                                  Mat4 transform) {
        if (lodSources == null || lodSources.isEmpty()) {
            throw new IllegalArgumentException("at least one LOD source required");
        }
        if (!initialized) {
            pendingCustomModels.add(new PendingCustomModel(name, lodSources, arena, transform));
        } else {
            objects.add(buildCustomSceneObject(name, lodSources, arena, transform));
        }
    }

    /** @return unmodifiable view of current scene objects */
    public List<SceneObject> objects() { return List.copyOf(objects); }

    /** @return total triangles rendered last frame */
    public long totalTrianglesThisFrame() { return totalTrianglesThisFrame; }

    /** @return number of objects in the scene */
    public int objectCount() { return objects.size(); }

    // -------------------------------------------------------------------------
    // UILayer interface
    // -------------------------------------------------------------------------

    @Override public String name() { return "LodScene"; }
    @Override public int order() { return ORDER; }
    @Override public boolean handleInput(InputEvent event) { return false; }
    @Override public boolean acceptsInput() { return false; }

    @Override
    public void initialize(UIContext ctx) {
        this.ctx = ctx;
        VulkanContext vulkan = ctx.vulkan();
        this.device = vulkan.device();
        this.queue = vulkan.graphicsVkQueue();
        this.width = ctx.width();
        this.height = ctx.height();
        this.layerArena = Arena.ofShared();

        allocator = new DedicatedAllocator(device, queue, MemoryStrategy.DEVICE_LOCAL);
        buildPipeline();
        buildSelector();

        // Upload any models added before initialization
        for (PendingModel pm : pendingModels) {
            objects.add(buildSceneObject(pm.name, pm.source, pm.arena, pm.transform, pm.decimationRatios));
        }
        pendingModels.clear();
        for (PendingCustomModel pm : pendingCustomModels) {
            objects.add(buildCustomSceneObject(pm.name, pm.lodSources, pm.arena, pm.transform));
        }
        pendingCustomModels.clear();
        initialized = true;
    }

    @Override
    public void update(UIFrameContext frame) {
        // Poll camera source if configured
        if (cameraSource != null) {
            cameraSource.apply(this);
        }

        float dt = (float) frame.deltaTime();
        totalTrianglesThisFrame = 0;

        for (SceneObject obj : objects) {
            // Transform local bounds to world space for correct distance calculation
            AABB worldBounds = obj.baseBounds().transform(obj.transform());

            // Build per-object LOD context
            LodContext lodContext = LodContext.builder()
                    .cameraPosition(cameraEye)
                    .viewProjection(vpMatrix)
                    .projectionMatrix(projMatrix)
                    .screenHeight(height)
                    .errorThreshold(ERROR_THRESHOLD_PIXELS)
                    .objectTransform(obj.transform())
                    .objectBounds(worldBounds)
                    .deltaTime(dt)
                    .previousSelection(null)
                    .build();

            LodSelection selection = selector.select(obj.lodStructure(), lodContext);
            int desiredLod = selection.selectedNodeIndex();
            obj.requestLod(desiredLod, dt);
            totalTrianglesThisFrame += obj.currentTriangleCount();
        }

        selector.frameAdvance(dt);
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        if (objects.isEmpty()) return;

        vkCmdBindPipeline(cmd.handle(), 0, pipeline.handle());
        VkSetState.setViewport(cmd, 0, 0, width, height);
        VkSetState.setScissor(cmd, 0, 0, width, height);

        for (SceneObject obj : objects) {
            Mat4 mvp = vpMatrix.mulNew(obj.transform());
            drawMesh(cmd, frameArena, obj.currentMesh(), mvp);
        }
    }

    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void close() {
        for (SceneObject obj : objects) obj.close();
        objects.clear();
        if (allocator != null) allocator.close();
        if (pipeline != null) pipeline.close();
        if (layerArena != null) layerArena.close();
    }

    // -------------------------------------------------------------------------
    // LOD chain construction
    // -------------------------------------------------------------------------

    private SceneObject buildSceneObject(String name, GeometrySource source, Arena arena,
                                         Mat4 transform, float[] decimationRatios) {
        QemSimplifier simplifier = new QemSimplifier();
        List<Mesh> lodMeshes = new ArrayList<>();

        // Level 0: original (with generated normals)
        lodMeshes.add(uploadWithNormals(source, arena));

        // Simplified levels — skip if simplification produces degenerate geometry
        for (float ratio : decimationRatios) {
            GeometrySource simplified = simplifier.simplify(source, ratio, arena);
            if (simplified.elementCount() < 4) break; // too few verts for meaningful geometry
            if (simplified.indices().isEmpty()) break;
            if (simplified.indices().get().indexCount() < 12) break; // fewer than 4 triangles is degenerate
            lodMeshes.add(uploadWithNormals(simplified, arena));
        }

        // Build representation nodes with error bounds scaled for the geometry size
        int levels = lodMeshes.size();
        RepresentationNode[] nodes = new RepresentationNode[levels];
        AABB bounds = source.bounds();
        float extentSize = bounds.extents().length();

        for (int i = 0; i < levels; i++) {
            // Error bounds increase with coarseness: 0 for finest, progressively larger
            float errorBound = (i == 0) ? 0.0f : extentSize * 0.01f * i * i;
            long triCount = estimateTriCount(lodMeshes.get(i));
            nodes[i] = new RepresentationNode(
                    new int[]{i},
                    errorBound,
                    triCount,
                    bounds,
                    0);
        }

        RepresentationStructure.Flat structure = new RepresentationStructure.Flat(nodes);
        return new SceneObject(name, lodMeshes, structure, bounds, transform);
    }

    private long estimateTriCount(Mesh mesh) {
        GeometryDrawRange range = mesh.fullDrawRange();
        return range.indexCount() / 3;
    }

    /**
     * Builds a scene object from pre-built LOD sources (no decimation). Each source is uploaded
     * as a separate LOD level. Error bounds are spaced evenly based on geometry extent.
     */
    private SceneObject buildCustomSceneObject(String name, List<GeometrySource> lodSources,
                                               Arena arena, Mat4 transform) {
        List<Mesh> lodMeshes = new ArrayList<>();
        for (GeometrySource src : lodSources) {
            lodMeshes.add(uploadWithNormals(src, arena));
        }

        int levels = lodMeshes.size();
        RepresentationNode[] nodes = new RepresentationNode[levels];
        AABB bounds = lodSources.get(0).bounds();
        float extentSize = bounds.extents().length();

        for (int i = 0; i < levels; i++) {
            // Evenly spaced error bounds so each level gets a fair distance band
            float errorBound = (i == 0) ? 0.0f : extentSize * 0.05f * i;
            long triCount = estimateTriCount(lodMeshes.get(i));
            nodes[i] = new RepresentationNode(
                    new int[]{i},
                    errorBound,
                    triCount,
                    bounds,
                    0);
        }

        RepresentationStructure.Flat structure = new RepresentationStructure.Flat(nodes);
        return new SceneObject(name, lodMeshes, structure, bounds, transform);
    }

    // -------------------------------------------------------------------------
    // Selector
    // -------------------------------------------------------------------------

    private void buildSelector() {
        selector = new LodSelector() {
            @Override
            public LodSelection select(RepresentationStructure representations, LodContext context) {
                if (!(representations instanceof RepresentationStructure.Flat flat)) {
                    return LodSelection.None.EMPTY;
                }

                float distance = context.distanceTo(context.objectBounds());
                if (distance < 0.001f) distance = 0.001f;

                // Walk from finest (0) to coarsest. Pick the coarsest whose projected
                // screen error is still below the threshold.
                int selected = 0;
                for (int i = 1; i < flat.nodeCount(); i++) {
                    RepresentationNode node = flat.node(i);
                    if (!node.hasErrorBound()) continue;
                    float screenErr = context.projectError(node.errorBound(), distance);
                    if (screenErr <= context.errorThreshold()) {
                        selected = i;
                    } else {
                        break;
                    }
                }

                // We only use selectedNodeIndex() from the result; ranges are not consumed
                // because we render directly from the Mesh objects.
                return LodSelection.Explicit.of(List.of(), selected);
            }
        };
    }

    // -------------------------------------------------------------------------
    // Pipeline
    // -------------------------------------------------------------------------

    private void buildPipeline() {
        VkVertexFormat vertexFormat = LAYOUT.toVertexFormat(LOCATIONS);
        CompiledShader vert = ShaderLoader.compileShader("/shaders/mesh_sample.vert");
        CompiledShader frag = ShaderLoader.compileShader("/shaders/mesh_sample.frag");

        VkPipeline.Builder pb = VkPipeline.builder()
                .device(device)
                .vertexShader(vert.getSpirV())
                .fragmentShader(frag.getSpirV())
                .dynamicViewport()
                .dynamicScissor()
                .depthTest(true)
                .depthCompareOp(VkCompareOp.VK_COMPARE_OP_LESS.value())
                .cullMode(VK_CULL_MODE_BACK_BIT())
                .dynamicRendering(
                        VkFormat.VK_FORMAT_D32_SFLOAT.value(),
                        VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value())
                .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, 64);

        var vi = pb.vertexInput();
        for (var binding : vertexFormat.getBindings()) {
            vi.binding(binding.binding(), binding.stride(), binding.inputRate());
        }
        for (var attr : vertexFormat.getAttributes()) {
            vi.attribute(attr.location(), attr.binding(), attr.format(), attr.offset());
        }
        vi.build();

        pipeline = pb.build(layerArena);
    }

    // -------------------------------------------------------------------------
    // Mesh upload
    // -------------------------------------------------------------------------

    private Mesh uploadWithNormals(GeometrySource source, Arena arena) {
        long vertexCount = source.elementCount();
        AttributeStream normalStream = NormalGenerator.generate(source, arena);

        // Generate zeroed UVs if the source does not provide them
        long uvStride = 8;
        MemorySegment uvData = arena.allocate(uvStride * vertexCount);
        uvData.fill((byte) 0);

        // Transcode position data
        MeshLayout posLayout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3).build();
        long posStride = 12;
        MemorySegment posData = arena.allocate(posStride * vertexCount);
        source.stream(AttributeSemantic.POSITION).transcodeInto(posLayout, posData, 0, posStride, 0, vertexCount);

        // Transcode normal data
        MeshLayout fullLayout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .stream(1).attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .stream(2).attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
                .build();

        long normalStride = 12;
        MemorySegment normalData = arena.allocate(normalStride * vertexCount);
        normalStream.transcodeInto(fullLayout, normalData, 0, normalStride, 0, vertexCount);

        SegmentGeometrySource fullSource = SegmentGeometrySource.builder()
                .layout(fullLayout)
                .elementCount(vertexCount)
                .topology(source.topology())
                .bounds(source.bounds())
                .streamData(0, posData)
                .streamData(1, normalData)
                .streamData(2, uvData)
                .indices(source.indices().get())
                .build();

        return Mesh.builder()
                .source(fullSource)
                .layout(LAYOUT)
                .allocator(allocator)
                .queue(queue)
                .build();
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    private void drawMesh(VkCommandBuffer cmd, Arena frameArena, Mesh mesh, Mat4 mvp) {
        MemorySegment pushData = frameArena.allocate(64);
        mvp.writeTo(pushData, 0);
        vkCmdPushConstants(cmd.handle(), pipeline.layout(),
                VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, 64, pushData);

        GeometryBinding binding = mesh.binding();
        MemorySegment[] handles = binding.vertexBufferHandles();
        long[] offsets = binding.vertexBufferOffsets();

        MemorySegment bufArray = frameArena.allocate(ValueLayout.ADDRESS, handles.length);
        MemorySegment offArray = frameArena.allocate(ValueLayout.JAVA_LONG, handles.length);
        for (int j = 0; j < handles.length; j++) {
            bufArray.setAtIndex(ValueLayout.ADDRESS, j, handles[j]);
            offArray.setAtIndex(ValueLayout.JAVA_LONG, j, offsets[j]);
        }
        VkBind.bindVertexBuffers(cmd.handle(), 0, handles.length, bufArray, offArray);

        GeometryDrawRange draw = mesh.fullDrawRange();
        if (binding.indexBufferHandle().isPresent()) {
            int vkIdxType = binding.indexWidth() == IndexWidth.U16
                    ? VkIndexType.VK_INDEX_TYPE_UINT16.value()
                    : VkIndexType.VK_INDEX_TYPE_UINT32.value();
            VkBind.bindIndexBuffer(cmd.handle(), binding.indexBufferHandle().get(),
                    binding.indexBufferOffset(), vkIdxType);
            VkDrawIndexed.drawIndexed(cmd.handle(), draw.indexCount(), 1,
                    draw.firstIndex(), draw.vertexOffset(), 0);
        } else {
            VkDraw.draw(cmd.handle(), draw.indexCount(), 1, draw.firstIndex(), 0);
        }
    }

    // -------------------------------------------------------------------------
    // Pending model record
    // -------------------------------------------------------------------------

    private record PendingModel(String name, GeometrySource source, Arena arena,
                                Mat4 transform, float[] decimationRatios) {}

    private record PendingCustomModel(String name, List<GeometrySource> lodSources, Arena arena,
                                      Mat4 transform) {}
}
