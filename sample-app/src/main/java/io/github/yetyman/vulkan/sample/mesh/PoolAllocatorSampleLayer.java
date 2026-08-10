package io.github.yetyman.vulkan.sample.mesh;

import io.github.yetyman.helpers.math.Mat4;
import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkPipeline;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.command.VkBind;
import io.github.yetyman.vulkan.command.VkDrawIndexed;
import io.github.yetyman.vulkan.command.VkSetState;
import io.github.yetyman.vulkan.enums.VkFormat;
import io.github.yetyman.vulkan.enums.VkIndexType;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.enums.VkVertexInputRate;
import io.github.yetyman.vulkan.highlevel.VulkanContext;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.Mesh;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.consume.GeometryDrawRange;
import io.github.yetyman.vulkan.mesh.residency.IndexBaseMode;
import io.github.yetyman.vulkan.mesh.residency.PoolAllocator;
import io.github.yetyman.vulkan.shaders.CompiledShader;
import io.github.yetyman.vulkan.shaders.ShaderLoader;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.ui.UIFrameContext;
import io.github.yetyman.vulkan.ui.UILayer;
import io.github.yetyman.vulkan.ui.input.InputEvent;
import io.github.yetyman.vulkan.util.Logger;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdBindPipeline;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdPushConstants;

/**
 * Proves the Phase 5 falsification test from {@code plans/mesh/09-roadmap.md}: swapping
 * {@code DedicatedAllocator} for {@link PoolAllocator} requires no change above the
 * {@code GeometryAllocator} interface, and CPU recording time stays flat as partition count grows.
 *
 * <p>Builds a large number of small procedural grid meshes, all through one {@link PoolAllocator}
 * sharing one vertex buffer per stream and one index buffer for the whole scene. The vertex and
 * index buffers are bound exactly once per frame; every mesh after that costs one push-constant
 * write and one indexed draw call with a per-mesh {@code vertexOffset}/{@code firstIndex}, which is
 * exactly what {@link Mesh#fullDrawRange()} already computes from {@code GeometryAllocation}
 * without this layer doing any pool-specific bookkeeping.
 *
 * <p>Reports CPU command-recording time to {@link #statusText()} so the flat-scaling claim is
 * observable rather than asserted. Compare recording time at different {@code MESH_COUNT} values by
 * changing the constant and re-running; per Invariant 1, it should not grow with mesh count.
 */
public class PoolAllocatorSampleLayer implements UILayer {

    private static final int MESH_COUNT = 2000;
    private static final int GRID_SIZE = 3; // small grids so many fit in the pool cheaply

    private static final MeshLayout LAYOUT = MeshLayout.builder()
            .stream(0)
            .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
            .attribute(AttributeSemantic.COLOR(0), AttributeFormat.F32x3)
            .build();

    private VkDevice device;
    private VkQueue queue;
    private int width, height;

    private Arena layerArena;
    private PoolAllocator poolAllocator;
    private final List<Mesh> meshes = new ArrayList<>();
    private final List<Vec3> positions = new ArrayList<>();

    private CompiledShader vertShader;
    private CompiledShader fragShader;
    private VkPipeline pipeline;

    private long lastRecordNanos = 0;
    private long startTime;

    @Override
    public String name() { return "pool-allocator-sample"; }

    @Override
    public int order() { return 0; }

    @Override
    public void initialize(UIContext ctx) {
        VulkanContext vulkan = ctx.vulkan();
        this.device = vulkan.device();
        this.queue = vulkan.graphicsVkQueue();
        this.width = ctx.width();
        this.height = ctx.height();
        this.layerArena = Arena.ofShared();
        this.startTime = System.nanoTime();

        // One pool allocator for the whole scene: every mesh shares these buffers.
        long verticesPerMesh = (long) (GRID_SIZE + 1) * (GRID_SIZE + 1);
        long indicesPerMesh = (long) GRID_SIZE * GRID_SIZE * 6;
        poolAllocator = new PoolAllocator(device, queue, MemoryStrategy.MAPPED,
                LAYOUT, verticesPerMesh * MESH_COUNT, indicesPerMesh * MESH_COUNT,
                IndexWidth.U32, IndexBaseMode.RELATIVE_WITH_DRAW_OFFSET);

        int gridDim = (int) Math.ceil(Math.sqrt(MESH_COUNT));
        float spacing = 1.4f;
        for (int i = 0; i < MESH_COUNT; i++) {
            PaintableGridSource source = new PaintableGridSource(GRID_SIZE);
            Mesh mesh = Mesh.builder()
                    .source(source)
                    .layout(LAYOUT)
                    .allocator(poolAllocator)
                    .queue(queue)
                    .awaitUpload(false) // batch all uploads, wait once below
                    .build();
            meshes.add(mesh);

            int row = i / gridDim;
            int col = i % gridDim;
            positions.add(new Vec3(
                    (col - gridDim / 2f) * spacing,
                    0,
                    (row - gridDim / 2f) * spacing));
        }
        // Every mesh above was built with awaitUpload(false); TransferBatchManager batches them,
        // so wait once here instead of once per mesh.
        io.github.yetyman.vulkan.buffers.TransferBatchManager.flush(device, queue).await();

        Logger.info("PoolAllocatorSampleLayer: " + MESH_COUNT + " meshes, "
                + poolAllocator.allocationCount() + " live pool allocations, "
                + "1 shared vertex buffer + 1 shared index buffer for the whole scene");

        buildPipeline();
    }

    private void buildPipeline() {
        vertShader = ShaderLoader.compileShader("/shaders/mesh_mutation.vert");
        fragShader = ShaderLoader.compileShader("/shaders/mesh_mutation.frag");

        pipeline = VkPipeline.builder()
                .device(device)
                .vertexShader(vertShader.getSpirV())
                .fragmentShader(fragShader.getSpirV())
                .dynamicViewport()
                .dynamicScissor()
                .dynamicRendering(0, VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value())
                .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, 64)
                .vertexInput()
                    .binding(0, (int) LAYOUT.strideOf(0), VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX.value())
                    .attribute(0, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT.value(), 0)
                    .attribute(1, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT.value(), 12)
                    .build()
                .build(layerArena);
    }

    @Override
    public void update(UIFrameContext frame) {
        // Static scene: geometry never changes after initialize(). Nothing to update per frame.
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        long t0 = System.nanoTime();

        vkCmdBindPipeline(cmd.handle(), 0, pipeline.handle());
        VkSetState.setViewport(cmd, 0, 0, 0, width, height, 0.0f, 1.0f);
        VkSetState.setScissor(cmd, 0, 0, 0, width, height);

        // Bind the pool's shared buffers exactly once for the whole frame, regardless of mesh count.
        MemorySegment[] vertexHandles = { poolAllocator.vertexPool(0).handle() };
        long[] vertexOffsets = { 0L };
        MemorySegment bufArray = frameArena.allocate(ValueLayout.ADDRESS, vertexHandles.length);
        MemorySegment offArray = frameArena.allocate(ValueLayout.JAVA_LONG, vertexHandles.length);
        for (int j = 0; j < vertexHandles.length; j++) {
            bufArray.setAtIndex(ValueLayout.ADDRESS, j, vertexHandles[j]);
            offArray.setAtIndex(ValueLayout.JAVA_LONG, j, vertexOffsets[j]);
        }
        VkBind.bindVertexBuffers(cmd.handle(), 0, vertexHandles.length, bufArray, offArray);
        VkBind.bindIndexBuffer(cmd.handle(), poolAllocator.indexPool().handle(), 0,
                VkIndexType.VK_INDEX_TYPE_UINT32.value());

        float time = (float) ((System.nanoTime() - startTime) / 1e9);
        Mat4 view = Mat4.lookAt(new Vec3(0, 30, 40), new Vec3(0, 0, 0), new Vec3(0, 1, 0));
        Mat4 proj = Mat4.perspective((float) Math.toRadians(50),
                (float) width / Math.max(height, 1), 0.1f, 500f);
        Mat4 viewProj = proj.mulNew(view);

        for (int i = 0; i < meshes.size(); i++) {
            Mesh mesh = meshes.get(i);
            Vec3 pos = positions.get(i);

            Mat4 model = Mat4.rotationY(time * 0.2f + i * 0.01f);
            model.m30 = pos.x;
            model.m31 = pos.y;
            model.m32 = pos.z;
            Mat4 mvp = viewProj.mulNew(model);

            MemorySegment pushData = frameArena.allocate(64);
            mvp.writeTo(pushData, 0);
            vkCmdPushConstants(cmd.handle(), pipeline.layout(),
                    VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, 64, pushData);

            // Only this call and the push constant above are per-mesh; no per-mesh buffer bind.
            GeometryDrawRange draw = mesh.fullDrawRange();
            VkDrawIndexed.drawIndexed(cmd.handle(), draw.indexCount(), draw.instanceCount(),
                    draw.firstIndex(), draw.vertexOffset(), draw.firstInstance());
        }

        lastRecordNanos = System.nanoTime() - t0;
    }

    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public boolean handleInput(InputEvent event) { return false; }

    @Override
    public boolean acceptsInput() { return false; }

    /**
     * @return CPU command-recording time for the last frame, in the format the roadmap's
     * falsification test cares about: "must be flat" as {@link #MESH_COUNT} grows.
     */
    public String statusText() {
        return String.format("Pool allocator: %d meshes, %d live allocations, record time %.3f ms",
                meshes.size(), poolAllocator.allocationCount(), lastRecordNanos / 1e6);
    }

    @Override
    public void close() {
        for (Mesh mesh : meshes) mesh.close();
        meshes.clear();
        if (poolAllocator != null) poolAllocator.close();
        if (pipeline != null) pipeline.close();
        if (layerArena != null) layerArena.close();
    }
}
