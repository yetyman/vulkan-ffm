package io.github.yetyman.vulkan.sample.mesh;

import io.github.yetyman.helpers.math.Mat4;
import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.VkBufferBarrier;
import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkComputePipeline;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPipeline;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferFactory;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.command.VkBind;
import io.github.yetyman.vulkan.command.VkDispatch;
import io.github.yetyman.vulkan.command.VkDrawIndexedIndirectCount;
import io.github.yetyman.vulkan.command.VkFillBuffer;
import io.github.yetyman.vulkan.command.VkSetState;
import io.github.yetyman.vulkan.enums.VkAccessFlagBits;
import io.github.yetyman.vulkan.enums.VkFormat;
import io.github.yetyman.vulkan.enums.VkIndexType;
import io.github.yetyman.vulkan.enums.VkPipelineStageFlagBits;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.enums.VkVertexInputRate;
import io.github.yetyman.vulkan.highlevel.VulkanContext;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.Mesh;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.consume.GeometryTable;
import io.github.yetyman.vulkan.mesh.consume.IndirectDrawEncoder;
import io.github.yetyman.vulkan.mesh.partition.GeometryPartition;
import io.github.yetyman.vulkan.mesh.partition.PartitionSet;
import io.github.yetyman.vulkan.mesh.residency.IndexBaseMode;
import io.github.yetyman.vulkan.mesh.residency.PoolAllocator;
import io.github.yetyman.vulkan.shaders.CompiledShader;
import io.github.yetyman.vulkan.shaders.DescriptorGroup;
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
 * Proves the Phase 5 falsification claim from {@code plans/mesh/09-roadmap.md}: CPU
 * command-recording time stays flat as scene size grows, because the CPU records exactly one
 * indirect draw call regardless of how many partitions exist or survive culling.
 *
 * <p>This is the other half of Phase 5 alongside {@link PoolAllocatorSampleLayer}. That layer
 * proves the CPU-loop-of-draws case stays flat when every draw shares one bind; this layer proves
 * the fully GPU-driven case, where the CPU does not even loop over partitions. A compute shader
 * ({@code geometry_table_cull.comp}) reads {@link GeometryTable} directly and writes surviving draw
 * commands plus a count; the CPU issues one {@code vkCmdDrawIndexedIndirectCount} and nothing else
 * is scene-size-dependent.
 *
 * <p>Per {@code plans/mesh/05-consumption.md}, the cull shader embodies one specific, opinionated
 * culling policy (single-camera distance cull, residency-flag check only). That policy belongs
 * here, in application/sample code, not in {@code vulkan-ffm-mesh}: {@link GeometryTable} itself is
 * the unbiased, mandatory infrastructure (the SSBO, its record layout, dirty tracking), while this
 * layer is one specific consumer of it, exactly parallel to how {@code GeometryBinding} (data) is
 * core but {@code Drawable} (a rendering paradigm) is deliberately absent from the module. Any other
 * culling scheme, LOD selection pass, or BLAS-build feeder can read the same table and write its own
 * shader against the same public {@link GeometryTable#buffer()} without needing anything privileged.
 */
public class GeometryTableCullingSampleLayer implements UILayer {

    private static final int MESH_COUNT = 5000;
    private static final int GRID_SIZE = 3; // small grids so many fit in the pool cheaply
    private static final int CULL_WORKGROUP_SIZE = 64; // matches geometry_table_cull.comp local_size_x
    private static final float CULL_DISTANCE = 60.0f;

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

    private GeometryTable table;
    private IBuffer commandsBuffer;
    private IBuffer countBuffer;

    private CompiledShader cullShader;
    private VkComputePipeline cullPipeline;
    private DescriptorGroup cullDescriptors;
    private int cullPushConstantSize;

    private CompiledShader vertShader;
    private CompiledShader fragShader;
    private VkPipeline pipeline;

    private long lastRecordNanos = 0;
    private long startTime;

    @Override
    public String name() { return "geometry-table-culling-sample"; }

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

        buildScene();
        buildTable();
        buildCullResources();
        buildGraphicsPipeline();

        Logger.info("GeometryTableCullingSampleLayer: " + MESH_COUNT + " partitions registered in "
                + "GeometryTable, 1 shared vertex buffer + 1 shared index buffer + 1 compute cull "
                + "pass + 1 vkCmdDrawIndexedIndirectCount for the whole scene");
    }

    private void buildScene() {
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
    }

    /**
     * Registers every mesh's whole-mesh partition in the table, with bounds offset by its instance
     * position, then flushes once. Positions are baked into the partition's world-space bounds here
     * rather than passed as a per-instance transform because {@link GeometryTable} carries no
     * instancing concept of its own; a real scene would either bake world-space bounds per placement
     * (as done here, one partition per placed instance) or add an app-side instance table mapping to
     * shared partition indices. Both are paradigm-specific decisions that belong in application code,
     * not in {@code vulkan-ffm-mesh}.
     */
    private void buildTable() {
        table = new GeometryTable(device, queue, MESH_COUNT);

        for (int i = 0; i < meshes.size(); i++) {
            Mesh mesh = meshes.get(i);
            Vec3 pos = positions.get(i);
            PartitionSet parts = mesh.partitions();
            GeometryPartition local = parts.get(0);

            AABB worldBounds = AABB.fromMinMax(
                    local.bounds().min.addNew(pos),
                    local.bounds().max.addNew(pos));

            GeometryPartition worldPartition = new GeometryPartition(
                    local.name(),
                    local.firstIndex(),
                    local.primitiveCount(),
                    local.vertexCount(),
                    local.topology(),
                    worldBounds,
                    local.tag(),
                    local.sortKey());

            table.register(mesh.allocation(), worldPartition);
        }

        table.flush(queue).await();
    }

    private void buildCullResources() {
        commandsBuffer = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null,
                (long) MESH_COUNT * IndirectDrawEncoder.INDEXED_STRIDE,
                BufferUsage.STORAGE_INDIRECT, device, queue);
        countBuffer = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null,
                Integer.BYTES, BufferUsage.STORAGE_INDIRECT, device, queue);

        cullShader = ShaderLoader.compileShader("/shaders/geometry_table_cull.comp");

        List<ShaderLoader.PushConstantBlockInfo> blocks = cullShader.getReflection().getPushConstantBlocks();
        // vec3 cameraPos (padded to 16 by GLSL default alignment) + float cullDistance (4) +
        // uint partitionCount (4) = 24, but confirm from reflection rather than guessing.
        cullPushConstantSize = blocks.isEmpty() ? 24 : blocks.get(0).size();

        cullDescriptors = DescriptorGroup.builder()
                .device(device)
                .stageFlags(VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value())
                .storageBuffer(0, table.buffer())
                .storageBuffer(1, commandsBuffer)
                .storageBuffer(2, countBuffer)
                .build(layerArena);

        cullPipeline = VkComputePipeline.builder()
                .device(device)
                .computeShader(cullShader.getSpirV())
                .descriptorSetLayouts(cullDescriptors.layoutHandle())
                .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value(),
                        0, cullPushConstantSize)
                .build(layerArena);
    }

    private void buildGraphicsPipeline() {
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
        // Static scene: geometry and the table never change after initialize(). Nothing to update.
    }

    @Override
    public void preRender(VkCommandBuffer cmd, Arena frameArena) {
        Vec3 cameraPos = new Vec3(0, 30, 40);
        recordCullPass(cmd, frameArena, cameraPos);
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        long t0 = System.nanoTime();

        float time = (float) ((System.nanoTime() - startTime) / 1e9);

        recordDrawPass(cmd, frameArena, time);

        lastRecordNanos = System.nanoTime() - t0;
    }

    /**
     * Zeroes the draw count, dispatches the culling compute shader over every partition, then
     * barriers compute-write to indirect-command-read before the draw pass reads either buffer.
     *
     * <p>The count must be zeroed by the CPU (a fill), never from inside the shader: {@code
     * barrier()} in GLSL only synchronizes invocations within one workgroup, so resetting the
     * counter from inside the shader would race against every other workgroup's {@code atomicAdd}
     * with no ordering guarantee between them. See {@code geometry_table_cull.comp}'s own comment.
     */
    private void recordCullPass(VkCommandBuffer cmd, Arena frameArena, Vec3 cameraPos) {
        VkFillBuffer.fillBuffer(cmd, countBuffer.handle(), 0, countBuffer.size(), 0);

        VkBufferBarrier fillToComputeBarrier = VkBufferBarrier.builder()
                .buffer(countBuffer.handle())
                .srcAccess(VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT.value())
                .dstAccess(VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value()
                        | VkAccessFlagBits.VK_ACCESS_SHADER_WRITE_BIT.value())
                .build(frameArena);
        fillToComputeBarrier.execute(cmd.handle(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT.value(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT.value());

        cullPipeline.bind(cmd.handle());
        cullDescriptors.set().bind(cmd.handle(), cullPipeline, 0, frameArena);

        MemorySegment pushData = frameArena.allocate(cullPushConstantSize);
        pushData.set(ValueLayout.JAVA_FLOAT, 0, cameraPos.x);
        pushData.set(ValueLayout.JAVA_FLOAT, 4, cameraPos.y);
        pushData.set(ValueLayout.JAVA_FLOAT, 8, cameraPos.z);
        pushData.set(ValueLayout.JAVA_FLOAT, 12, CULL_DISTANCE);
        pushData.set(ValueLayout.JAVA_INT, 16, MESH_COUNT);
        cullPipeline.pushConstants(cmd.handle(), 0, pushData, cullPushConstantSize);

        int groupCountX = (MESH_COUNT + CULL_WORKGROUP_SIZE - 1) / CULL_WORKGROUP_SIZE;
        VkDispatch.dispatch(cmd.handle(), groupCountX);

        VkBufferBarrier commandsBarrier = VkBufferBarrier.builder()
                .buffer(commandsBuffer.handle())
                .srcAccess(VkAccessFlagBits.VK_ACCESS_SHADER_WRITE_BIT.value())
                .dstAccess(VkAccessFlagBits.VK_ACCESS_INDIRECT_COMMAND_READ_BIT.value())
                .build(frameArena);
        VkBufferBarrier countBarrier = VkBufferBarrier.builder()
                .buffer(countBuffer.handle())
                .srcAccess(VkAccessFlagBits.VK_ACCESS_SHADER_WRITE_BIT.value())
                .dstAccess(VkAccessFlagBits.VK_ACCESS_INDIRECT_COMMAND_READ_BIT.value())
                .build(frameArena);
        commandsBarrier.execute(cmd.handle(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT.value(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT.value());
        countBarrier.execute(cmd.handle(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT.value(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT.value());
    }

    /**
     * Records the graphics pass: bind the pool's shared vertex/index buffers once, then issue
     * exactly one {@code vkCmdDrawIndexedIndirectCount}. This is the line that proves the Phase 5
     * claim; nothing here loops over partitions or meshes.
     */
    private void recordDrawPass(VkCommandBuffer cmd, Arena frameArena, float time) {
        vkCmdBindPipeline(cmd.handle(), 0, pipeline.handle());
        VkSetState.setViewport(cmd, 0, 0, 0, width, height, 0.0f, 1.0f);
        VkSetState.setScissor(cmd, 0, 0, 0, width, height);

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

        Mat4 view = Mat4.lookAt(new Vec3(0, 30, 40), new Vec3(0, 0, 0), new Vec3(0, 1, 0));
        Mat4 proj = Mat4.perspective((float) Math.toRadians(50),
                (float) width / Math.max(height, 1), 0.1f, 500f);
        Mat4 mvp = proj.mulNew(view);

        MemorySegment pushData = frameArena.allocate(64);
        mvp.writeTo(pushData, 0);
        vkCmdPushConstants(cmd.handle(), pipeline.layout(),
                VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, 64, pushData);

        // Exactly one indirect draw call, regardless of MESH_COUNT or how many survive culling.
        VkDrawIndexedIndirectCount.drawIndexedIndirectCount(cmd, commandsBuffer.handle(), 0,
                countBuffer.handle(), 0, MESH_COUNT, IndirectDrawEncoder.INDEXED_STRIDE);
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
     * falsification test cares about: "must be flat" as {@link #MESH_COUNT} grows, unlike
     * {@link PoolAllocatorSampleLayer#statusText()} which loops over meshes once per frame.
     */
    public String statusText() {
        return String.format("GeometryTable culling: %d partitions, record time %.3f ms",
                meshes.size(), lastRecordNanos / 1e6);
    }

    @Override
    public void close() {
        for (Mesh mesh : meshes) mesh.close();
        meshes.clear();
        if (table != null) table.close();
        if (commandsBuffer != null) commandsBuffer.close();
        if (countBuffer != null) countBuffer.close();
        if (poolAllocator != null) poolAllocator.close();
        if (cullDescriptors != null) cullDescriptors.close();
        if (cullPipeline != null) cullPipeline.close();
        if (pipeline != null) pipeline.close();
        if (layerArena != null) layerArena.close();
    }
}
