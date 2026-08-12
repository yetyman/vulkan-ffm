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
import io.github.yetyman.vulkan.mesh.process.NormalGenerator;
import io.github.yetyman.vulkan.mesh.processing.QemSimplifier;
import io.github.yetyman.vulkan.mesh.residency.DedicatedAllocator;
import io.github.yetyman.vulkan.mesh.source.AttributeStream;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.SegmentAttributeStream;
import io.github.yetyman.vulkan.mesh.source.SegmentGeometrySource;
import io.github.yetyman.vulkan.mesh.source.primitives.SphereSource;
import io.github.yetyman.vulkan.mesh.source.primitives.TorusSource;
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
 * UILayer that renders sphere and torus meshes at various QEM simplification levels side by side.
 *
 * <p>Layout: 2 rows (sphere top, torus bottom) x N columns (original + decimated at each ratio).
 * Each mesh is rendered with generated normals and lit with the mesh_sample shader for clear
 * visual inspection of simplification quality.</p>
 */
public class QemSimplifierDemoLayer implements UILayer {

    private static final float[] DECIMATION_RATIOS = {0.5f, 0.25f, 0.1f, 0.05f};

    // Layout: POSITION (stream 0), NORMAL (stream 1), UV (stream 2) - matches mesh_sample shaders
    private static final MeshLayout LAYOUT = MeshLayout.builder()
            .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
            .stream(1).attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
            .stream(2).attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
            .build();

    private static final Map<AttributeSemantic, Integer> LOCATIONS = Map.of(
            AttributeSemantic.POSITION, 0,
            AttributeSemantic.NORMAL, 1,
            AttributeSemantic.TEXCOORD(0), 2
    );

    private UIContext ctx;
    private VkDevice device;
    private VkQueue queue;
    private Arena layerArena;
    private VkPipeline pipeline;
    private DedicatedAllocator allocator;
    private int width;
    private int height;
    private long startTime;

    // Sphere row: original + decimated versions
    private final List<Mesh> sphereMeshes = new ArrayList<>();
    // Torus row: original + decimated versions
    private final List<Mesh> torusMeshes = new ArrayList<>();

    // Info for text labels
    private final List<String> triCountLabels = new ArrayList<>();
    private String errorInfo = "";

    public float[] ratios() { return DECIMATION_RATIOS; }

    public String[] triCountLabels() {
        return triCountLabels.toArray(new String[0]);
    }

    public String errorInfo() { return errorInfo; }

    @Override public String name() { return "QemSimplifierDemo"; }
    @Override public int order() { return 50; }
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
        this.startTime = System.nanoTime();

        allocator = new DedicatedAllocator(device, queue, MemoryStrategy.DEVICE_LOCAL);

        buildPipeline();
        buildMeshes();
    }

    @Override
    public void update(UIFrameContext frame) {
        // Static meshes - no per-frame update needed
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        if (sphereMeshes.isEmpty()) return;

        vkCmdBindPipeline(cmd.handle(), 0, pipeline.handle());
        VkSetState.setViewport(cmd, 0, 0, width, height);
        VkSetState.setScissor(cmd, 0, 0, width, height);

        float time = (System.nanoTime() - startTime) / 1_000_000_000f;
        int columns = sphereMeshes.size();

        // Draw sphere row (top half)
        for (int i = 0; i < sphereMeshes.size(); i++) {
            drawMesh(cmd, frameArena, sphereMeshes.get(i), i, columns, 0, time);
        }

        // Draw torus row (bottom half)
        for (int i = 0; i < torusMeshes.size(); i++) {
            drawMesh(cmd, frameArena, torusMeshes.get(i), i, columns, 1, time);
        }
    }

    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void close() {
        for (Mesh mesh : sphereMeshes) mesh.close();
        for (Mesh mesh : torusMeshes) mesh.close();
        sphereMeshes.clear();
        torusMeshes.clear();
        if (allocator != null) allocator.close();
        if (pipeline != null) pipeline.close();
        if (layerArena != null) layerArena.close();
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
    // Mesh construction (simplification + normal generation)
    // =========================================================================

    private void buildMeshes() {
        QemSimplifier simplifier = new QemSimplifier();
        StringBuilder errors = new StringBuilder();

        // Build sphere LODs
        SphereSource sphere = new SphereSource(layerArena, 1.0f, 24, 32);
        long sphereOrigTris = sphere.indices().get().indexCount() / 3;
        triCountLabels.add("S:" + sphereOrigTris + " T");
        sphereMeshes.add(uploadWithNormals(sphere));

        for (float ratio : DECIMATION_RATIOS) {
            GeometrySource simplified = simplifier.simplify(sphere, ratio, layerArena);
            long tris = simplified.indices().get().indexCount() / 3;
            float error = simplifier.lastError();
            triCountLabels.add("S:" + tris + " T");
            if (errors.length() > 0) errors.append(" | ");
            errors.append(String.format("S@%.0f%%: err=%.4f", ratio * 100, error));
            sphereMeshes.add(uploadWithNormals(simplified));
        }

        // Build torus LODs
        TorusSource torus = new TorusSource(layerArena, 1.0f, 0.4f, 32, 24);
        long torusOrigTris = torus.indices().get().indexCount() / 3;
        // Append torus counts after sphere counts -- we reuse the same labels array
        // but only display sphere counts in the column headers (torus is same ratio)
        torusMeshes.add(uploadWithNormals(torus));

        for (float ratio : DECIMATION_RATIOS) {
            GeometrySource simplified = simplifier.simplify(torus, ratio, layerArena);
            long tris = simplified.indices().get().indexCount() / 3;
            float error = simplifier.lastError();
            if (errors.length() > 0) errors.append(" | ");
            errors.append(String.format("T@%.0f%%: err=%.4f", ratio * 100, error));
            torusMeshes.add(uploadWithNormals(simplified));
        }

        errorInfo = errors.toString();
    }

    /**
     * Takes a position-only (or full) GeometrySource, generates normals, adds dummy UVs,
     * and uploads to GPU as a Mesh compatible with the mesh_sample pipeline layout.
     */
    private Mesh uploadWithNormals(GeometrySource source) {
        long vertexCount = source.elementCount();

        // Generate normals from position + indices
        AttributeStream normalStream = NormalGenerator.generate(source, layerArena);

        // Create dummy UV stream (all zeros) - required by the pipeline but not visually important
        long uvStride = 8; // F32x2
        MemorySegment uvData = layerArena.allocate(uvStride * vertexCount);
        uvData.fill((byte) 0);
        SegmentAttributeStream uvStream = new SegmentAttributeStream(
                AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2, vertexCount, uvData);

        // Build a composite source with all three attributes
        MeshLayout posLayout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .build();
        long posStride = 12;
        MemorySegment posData = layerArena.allocate(posStride * vertexCount);
        source.stream(AttributeSemantic.POSITION).transcodeInto(posLayout, posData, 0, posStride, 0, vertexCount);

        // Build a 3-stream SegmentGeometrySource
        MeshLayout fullLayout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .stream(1).attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .stream(2).attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
                .build();

        // Extract normal data segment
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

    private void drawMesh(VkCommandBuffer cmd, Arena frameArena, Mesh mesh,
                          int column, int totalColumns, int row, float time) {
        Mat4 mvp = computeMVP(column, totalColumns, row, time);
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

    private Mat4 computeMVP(int column, int totalColumns, int row, float time) {
        // Position each mesh in a grid: columns across X, rows across Y
        float spacing = 2.5f;
        float totalWidth = (totalColumns - 1) * spacing;
        float xOffset = -totalWidth / 2f + column * spacing;
        float yOffset = row == 0 ? 1.5f : -1.5f; // sphere above, torus below

        // Slow rotation around Y
        float angle = time * 0.5f;
        Mat4 model = Mat4.rotationY(angle);
        model.m30 = xOffset;
        model.m31 = yOffset;

        // Camera looking at center
        float cameraDistance = totalWidth * 0.55f + 3.5f;
        Mat4 view = Mat4.lookAt(
                new Vec3(0, 0.5f, cameraDistance),
                new Vec3(0, 0, 0),
                new Vec3(0, 1, 0));

        float aspect = (float) width / Math.max(height, 1);
        Mat4 proj = Mat4.perspective((float) Math.toRadians(50), aspect, 0.1f, 100f);

        return proj.mulNew(view).mul(model);
    }
}
