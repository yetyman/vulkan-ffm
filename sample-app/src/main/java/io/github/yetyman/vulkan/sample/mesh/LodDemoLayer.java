package io.github.yetyman.vulkan.sample.mesh;

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
import io.github.yetyman.vulkan.mesh.lod.LodChannels;
import io.github.yetyman.vulkan.mesh.lod.LodContext;
import io.github.yetyman.vulkan.mesh.lod.LodSelection;
import io.github.yetyman.vulkan.mesh.lod.LodSelector;
import io.github.yetyman.vulkan.mesh.lod.RepresentationNode;
import io.github.yetyman.vulkan.mesh.lod.RepresentationStructure;
import io.github.yetyman.vulkan.mesh.lod.ResidencyQuery;
import io.github.yetyman.vulkan.mesh.lod.TransitionMode;
import io.github.yetyman.vulkan.mesh.lod.TransitionState;
import io.github.yetyman.vulkan.mesh.partition.FloatChannelKey;
import io.github.yetyman.vulkan.mesh.partition.PartitionMetadata;
import io.github.yetyman.vulkan.mesh.process.NormalGenerator;
import io.github.yetyman.vulkan.mesh.processing.QemSimplifier;
import io.github.yetyman.vulkan.mesh.residency.DedicatedAllocator;
import io.github.yetyman.vulkan.mesh.source.AttributeStream;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.SegmentAttributeStream;
import io.github.yetyman.vulkan.mesh.source.SegmentGeometrySource;
import io.github.yetyman.vulkan.mesh.source.primitives.SphereSource;
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
 * UILayer demonstrating the LOD system with a real-time camera distance selector.
 *
 * <p>A sphere is built at 4 LOD levels (100%, 50%, 25%, 10%). The LOD selector picks
 * the appropriate level based on simulated camera distance. All 4 levels are shown in a
 * row for reference, with the actively-selected one highlighted and enlarged in the center.
 *
 * <p>The camera distance oscillates automatically to demonstrate LOD transitions in real time.
 */
public class LodDemoLayer implements UILayer {

    private static final float[] DECIMATION_RATIOS = {0.3f, 0.1f, 0.03f};
    private static final MeshLayout LAYOUT = MeshLayout.builder()
            .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
            .stream(1).attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
            .stream(2).attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
            .build();
    private static final Map<AttributeSemantic, Integer> LOCATIONS = Map.of(
            AttributeSemantic.POSITION, 0,
            AttributeSemantic.NORMAL, 1,
            AttributeSemantic.TEXCOORD(0), 2);

    private UIContext ctx;
    private VkDevice device;
    private VkQueue queue;
    private Arena layerArena;
    private VkPipeline pipeline;
    private DedicatedAllocator allocator;
    private int width;
    private int height;
    private long startTime;

    // LOD data
    private final List<Mesh> lodMeshes = new ArrayList<>();
    private RepresentationStructure.Flat lodStructure;
    private LodSelector selector;
    private int currentSelectedNode = 0;
    private int renderNode = 0; // the node actually rendered this frame (stable during transitions)
    private TransitionState activeTransition;
    private float simulatedDistance = 5.0f;

    // Info for text overlay
    private String statusLine = "";
    private long[] triCounts;
    private float[] errorBounds;

    @Override public String name() { return "LodDemo"; }
    @Override public int order() { return 50; }
    @Override public boolean handleInput(InputEvent event) { return false; }
    @Override public boolean acceptsInput() { return false; }

    public String statusLine() { return statusLine; }
    public int currentSelectedNode() { return currentSelectedNode; }
    public int levelCount() { return lodMeshes.size(); }
    public long[] triCounts() { return triCounts; }
    public float[] errorBounds() { return errorBounds; }
    public float simulatedDistance() { return simulatedDistance; }
    public TransitionState activeTransition() { return activeTransition; }

    @Override
    public void initialize(UIContext ctx) {
        this.ctx = ctx;
        VulkanContext vulkan = ctx.vulkan();
        this.device = vulkan.device();
        this.queue = vulkan.graphicsVkQueue();
        this.width = ctx.width();
        this.height = ctx.height();
        this.layerArena = Arena.ofShared();
        this.startTime = System.nanoTime();

        allocator = new DedicatedAllocator(device, queue, MemoryStrategy.DEVICE_LOCAL);
        buildPipeline();
        buildLodChain();
        buildSelector();
    }

    @Override
    public void update(UIFrameContext frame) {
        float time = (System.nanoTime() - startTime) / 1_000_000_000f;
        float dt = (float) frame.deltaTime();

        // Oscillate camera distance between 2 and 20 (slow enough to observe transitions)
        simulatedDistance = 11.0f + 9.0f * (float) Math.sin(time * 0.3);
        simulatedDistance = Math.max(2.0f, simulatedDistance);

        // Run LOD selection
        LodContext lodContext = LodContext.builder()
                .cameraPosition(new Vec3(0, 0, simulatedDistance))
                .viewProjection(Mat4.perspective((float) Math.toRadians(50),
                        (float) width / Math.max(height, 1), 0.1f, 100f))
                .screenHeight(height)
                .errorThreshold(2.0f) // 2 pixel threshold
                .objectTransform(Mat4.identity())
                .objectBounds(lodMeshes.getFirst().bounds())
                .deltaTime(dt)
                .previousSelection(null)
                .build();

        LodSelection selection = selector.select(lodStructure, lodContext);

        int desiredNode = selection.selectedNodeIndex();

        // Guard against blank frames: only switch when the transition from old to new is
        // complete. During transition, keep rendering the OLD level (fromNode). The central
        // render uses renderNode which stays stable.
        if (desiredNode != currentSelectedNode && desiredNode >= 0) {
            if (activeTransition == null) {
                // Start a new transition; keep rendering the old node until it completes
                activeTransition = new TransitionState(
                        new TransitionMode.Dither(0.3f),
                        currentSelectedNode, desiredNode);
            }
        }

        // Advance transition
        if (activeTransition != null) {
            activeTransition.advance(dt);
            if (activeTransition.isComplete()) {
                // Transition finished: commit to the new node
                currentSelectedNode = activeTransition.toNodeIndex();
                activeTransition = null;
            }
        }

        // The node we actually render this frame (old node during transition, new after)
        renderNode = currentSelectedNode;

        // Update status
        statusLine = String.format("Distance: %.1f | LOD: %d/%d | Tris: %d | Error: %.4f%s",
                simulatedDistance,
                currentSelectedNode, lodMeshes.size() - 1,
                triCounts[currentSelectedNode],
                errorBounds[currentSelectedNode],
                activeTransition != null ?
                        String.format(" | Transition: %d->%d %.0f%%",
                                activeTransition.fromNodeIndex(), activeTransition.toNodeIndex(),
                                activeTransition.factor() * 100) : "");

        selector.frameAdvance(dt);
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        if (lodMeshes.isEmpty()) return;

        vkCmdBindPipeline(cmd.handle(), 0, pipeline.handle());
        VkSetState.setViewport(cmd, 0, 0, width, height);
        VkSetState.setScissor(cmd, 0, 0, width, height);

        float time = (System.nanoTime() - startTime) / 1_000_000_000f;
        float aspect = (float) width / Math.max(height, 1);

        // One shared camera
        Mat4 view = Mat4.lookAt(
                new Vec3(0, 0, 8f),
                new Vec3(0, 0, 0),
                new Vec3(0, 1, 0));
        Mat4 proj = Mat4.perspective((float) Math.toRadians(50), aspect, 0.1f, 100f);
        Mat4 vp = proj.mulNew(view);

        // Top row: all LOD levels side by side (small), selected one highlighted larger
        int levels = lodMeshes.size();
        float topSpacing = 2.2f;
        for (int i = 0; i < levels; i++) {
            float xOff = -((levels - 1) * topSpacing) / 2f + i * topSpacing;
            float scale = (i == currentSelectedNode) ? 0.65f : 0.4f;
            Mat4 model = Mat4.rotationY(time * 0.5f);
            model.m00 *= scale; model.m10 *= scale; model.m20 *= scale;
            model.m01 *= scale; model.m11 *= scale; model.m21 *= scale;
            model.m02 *= scale; model.m12 *= scale; model.m22 *= scale;
            model.m30 = xOff;
            model.m31 = 2.0f;
            drawMeshWithMVP(cmd, frameArena, lodMeshes.get(i), vp.mulNew(model));
        }

        // Center-left: the currently selected LOD level rendered large (fixed size)
        float centralScale = 1.3f;
        Mat4 centralModel = Mat4.rotationY(time * 0.5f);
        centralModel.m00 *= centralScale; centralModel.m10 *= centralScale; centralModel.m20 *= centralScale;
        centralModel.m01 *= centralScale; centralModel.m11 *= centralScale; centralModel.m21 *= centralScale;
        centralModel.m02 *= centralScale; centralModel.m12 *= centralScale; centralModel.m22 *= centralScale;
        centralModel.m30 = -2.0f;
        centralModel.m31 = -1.0f;
        drawMeshWithMVP(cmd, frameArena, lodMeshes.get(renderNode), vp.mulNew(centralModel));

        // Center-right: same selected LOD but scaled inversely with distance (simulates real camera)
        float distScale = 4.0f / simulatedDistance; // closer = bigger, farther = smaller
        Mat4 distModel = Mat4.rotationY(time * 0.5f);
        distModel.m00 *= distScale; distModel.m10 *= distScale; distModel.m20 *= distScale;
        distModel.m01 *= distScale; distModel.m11 *= distScale; distModel.m21 *= distScale;
        distModel.m02 *= distScale; distModel.m12 *= distScale; distModel.m22 *= distScale;
        distModel.m30 = 2.0f;
        distModel.m31 = -1.0f;
        drawMeshWithMVP(cmd, frameArena, lodMeshes.get(renderNode), vp.mulNew(distModel));
    }

    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void close() {
        for (Mesh mesh : lodMeshes) mesh.close();
        lodMeshes.clear();
        if (allocator != null) allocator.close();
        if (pipeline != null) pipeline.close();
        if (layerArena != null) layerArena.close();
    }

    // =========================================================================
    // LOD chain construction
    // =========================================================================

    private void buildLodChain() {
        QemSimplifier simplifier = new QemSimplifier();
        SphereSource sphere = new SphereSource(layerArena, 1.0f, 32, 48);

        // Level 0: original
        lodMeshes.add(uploadWithNormals(sphere));

        List<Long> triList = new ArrayList<>();
        triList.add(sphere.indices().get().indexCount() / 3);

        // Simplified levels
        for (float ratio : DECIMATION_RATIOS) {
            GeometrySource simplified = simplifier.simplify(sphere, ratio, layerArena);
            triList.add(simplified.indices().get().indexCount() / 3);
            lodMeshes.add(uploadWithNormals(simplified));
        }

        // Build representation nodes with artificial error bounds scaled for visibility.
        // Real QEM errors are tiny for a unit sphere; we scale them to produce LOD transitions
        // at distances that are interesting in the demo (2-20 units).
        // Error bounds increase with coarseness: finest=0, coarsest=large.
        int levels = lodMeshes.size();
        errorBounds = new float[levels];
        triCounts = new long[levels];
        RepresentationNode[] nodes = new RepresentationNode[levels];

        // Assign error bounds that spread the transitions across the demo distance range.
        // With threshold=2px, screenHeight=800, fov=50deg:
        //   screenError = (worldError / distance) * 858
        //   switch happens when screenError > threshold, i.e. distance < worldError * 429
        // We want transitions at roughly dist 5, 10, 16:
        //   level 1: error=0.012 -> switches at dist ~5.1
        //   level 2: error=0.024 -> switches at dist ~10.3
        //   level 3: error=0.04  -> switches at dist ~17.2
        float[] artificialErrors = {0.0f, 0.012f, 0.024f, 0.04f};

        for (int i = 0; i < levels; i++) {
            errorBounds[i] = artificialErrors[i];
            triCounts[i] = triList.get(i);
            nodes[i] = new RepresentationNode(
                    new int[]{i},
                    errorBounds[i],
                    triCounts[i],
                    sphere.bounds(),
                    0);
        }

        lodStructure = new RepresentationStructure.Flat(nodes);
    }

    // =========================================================================
    // Selector: screen-error based, picks finest level whose error is acceptable
    // =========================================================================

    private void buildSelector() {
        selector = new LodSelector() {
            @Override
            public LodSelection select(RepresentationStructure representations, LodContext context) {
                if (!(representations instanceof RepresentationStructure.Flat flat)) {
                    return LodSelection.None.EMPTY;
                }

                float distance = context.distanceTo(context.objectBounds());
                if (distance < 0.001f) distance = 0.001f;

                // Compute screen-error projection factor directly.
                // screenError = (worldError / distance) * (screenHeight / 2) * cot(fovY/2)
                float fovY = (float) Math.toRadians(50);
                float cotHalfFov = 1.0f / (float) Math.tan(fovY / 2.0f);
                float factor = (context.screenHeight() * 0.5f) * cotHalfFov;

                // Walk from finest (0) to coarsest (last). Pick the coarsest whose projected
                // screen error is still below the threshold.
                int selected = 0;
                for (int i = 1; i < flat.nodeCount(); i++) {
                    RepresentationNode node = flat.node(i);
                    if (!node.hasErrorBound()) continue;
                    float screenErr = (node.errorBound() / distance) * factor;
                    if (screenErr <= context.errorThreshold()) {
                        // This coarser level is still acceptable
                        selected = i;
                    } else {
                        // This level exceeds threshold; stop
                        break;
                    }
                }

                Mesh mesh = lodMeshes.get(selected);
                GeometryDrawRange range = mesh.fullDrawRange();
                return LodSelection.Explicit.of(List.of(range), selected);
            }
        };
    }

    // =========================================================================
    // Pipeline
    // =========================================================================

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

    // =========================================================================
    // Mesh upload (same pattern as QemSimplifierDemoLayer)
    // =========================================================================

    private Mesh uploadWithNormals(GeometrySource source) {
        long vertexCount = source.elementCount();
        AttributeStream normalStream = NormalGenerator.generate(source, layerArena);

        long uvStride = 8;
        MemorySegment uvData = layerArena.allocate(uvStride * vertexCount);
        uvData.fill((byte) 0);
        SegmentAttributeStream uvStream = new SegmentAttributeStream(
                AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2, vertexCount, uvData);

        MeshLayout posLayout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3).build();
        long posStride = 12;
        MemorySegment posData = layerArena.allocate(posStride * vertexCount);
        source.stream(AttributeSemantic.POSITION).transcodeInto(posLayout, posData, 0, posStride, 0, vertexCount);

        MeshLayout fullLayout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .stream(1).attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .stream(2).attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
                .build();

        long normalStride = 12;
        MemorySegment normalData = layerArena.allocate(normalStride * vertexCount);
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

    // =========================================================================
    // Drawing
    // =========================================================================

    private void drawMeshWithMVP(VkCommandBuffer cmd, Arena frameArena, Mesh mesh, Mat4 mvp) {
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

}
