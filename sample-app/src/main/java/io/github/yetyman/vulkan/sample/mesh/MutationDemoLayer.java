package io.github.yetyman.vulkan.sample.mesh;

import io.github.yetyman.helpers.math.Mat4;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkComputePipeline;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPipeline;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferFactory;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.GpuCompletion;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.command.VkBind;
import io.github.yetyman.vulkan.command.VkDraw;
import io.github.yetyman.vulkan.command.VkDrawIndexed;
import io.github.yetyman.vulkan.command.VkSetState;
import io.github.yetyman.vulkan.enums.VkCompareOp;
import io.github.yetyman.vulkan.enums.VkIndexType;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.highlevel.VkVertexFormat;
import io.github.yetyman.vulkan.highlevel.VulkanContext;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.ElementWindow;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.Mesh;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.MeshOps;
import io.github.yetyman.vulkan.mesh.PrimitiveTopology;
import io.github.yetyman.vulkan.mesh.consume.GeometryBinding;
import io.github.yetyman.vulkan.mesh.consume.GeometryDrawRange;
import io.github.yetyman.vulkan.mesh.residency.DedicatedAllocator;
import io.github.yetyman.vulkan.mesh.residency.ExternalAllocation;
import io.github.yetyman.vulkan.mesh.residency.GeometryAllocation;
import io.github.yetyman.vulkan.mesh.residency.GeometryAllocator;
import io.github.yetyman.vulkan.mesh.residency.RetireQueue;
import io.github.yetyman.vulkan.mesh.residency.RingAllocator;
import io.github.yetyman.vulkan.mesh.residency.TransferBatchExecutor;
import io.github.yetyman.vulkan.mesh.residency.UploadExecutor;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.MutableGeometrySource;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.shaders.CompiledShader;
import io.github.yetyman.vulkan.shaders.DescriptorGroup;
import io.github.yetyman.vulkan.shaders.ShaderLoader;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.ui.UIFrameContext;
import io.github.yetyman.vulkan.ui.UILayer;
import io.github.yetyman.vulkan.ui.input.InputEvent;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Map;
import java.util.Set;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdBindPipeline;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdPushConstants;

/**
 * Single UILayer demonstrating all six mesh mutation categories rendered side by side.
 * Each category occupies one column of the viewport.
 */
public class MutationDemoLayer implements UILayer {

    // Shared layout: position (stream 0) + color (stream 1) - planar so updates are cheap
    private static final MeshLayout LAYOUT = MeshLayout.builder()
            .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
            .stream(1).attribute(AttributeSemantic.COLOR(0), AttributeFormat.F32x3)
            .build();

    private static final Map<AttributeSemantic, Integer> LOCATIONS = Map.of(
            AttributeSemantic.POSITION, 0,
            AttributeSemantic.COLOR(0), 1);

    private UIContext ctx;
    private VkDevice device;
    private VkQueue queue;
    private Arena layerArena;
    private VkPipeline pipeline;
    private int width;
    private int height;
    private long startTime;
    private String status = "";

    // Allocators and lifecycle
    private DedicatedAllocator dedicatedAllocator;
    private RingAllocator ringAllocator;
    private DedicatedAllocator ringBackingAllocator;
    private UploadExecutor executor;
    private RetireQueue retireQueue;

    // Category 1: Attribute paint
    private Mesh paintMesh;
    private PaintableGridSource paintSource;

    // Category 2: Growing stroke
    private Mesh strokeMesh;
    private GrowingStrokeSource strokeSource;

    // Category 3: Unbounded growth (reallocation)
    private Mesh growthMesh;
    private GrowingStrokeSource growthSource;
    private long growthCapacity;

    // Category 4: Topology swap
    private Mesh swapMesh;
    private int swapVariant;
    private float swapTimer;

    // Category 5: Compute deform
    private Mesh computeMesh;
    private IBuffer computePositionBuffer;
    private IBuffer computeColorBuffer;
    private IBuffer computeIndexBuffer;
    private DescriptorGroup computeDescriptorGroup;
    private CompiledShader deformShader;
    private VkComputePipeline computePipeline;
    private int computeVertexCount;

    // Category 6: Ring buffered
    private Mesh ringMesh;
    private PaintableGridSource ringSource;
    private GeometryAllocation ringAllocation;

    public String statusText() { return status; }

    @Override public String name() { return "MutationDemo"; }
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
        this.executor = new TransferBatchExecutor();
        this.retireQueue = new RetireQueue();

        dedicatedAllocator = new DedicatedAllocator(device, queue, MemoryStrategy.MAPPED);
        ringBackingAllocator = new DedicatedAllocator(device, queue, MemoryStrategy.MAPPED);
        ringAllocator = new RingAllocator(ringBackingAllocator, 2);

        buildPipeline();
        initCategory1();
        initCategory2();
        initCategory3();
        initCategory4();
        initCategory5();
        initCategory6();
    }

    @Override
    public void update(UIFrameContext frame) {
        float time = (System.nanoTime() - startTime) / 1_000_000_000f;
        float dt = (float) frame.deltaTime();

        retireQueue.drain();

        updateCategory1(time);
        updateCategory2(time);
        updateCategory3(time);
        updateCategory4(time, dt);
        // Category 5 is updated during render (compute dispatch in command buffer)
        updateCategory6(time);
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        vkCmdBindPipeline(cmd.handle(), 0, pipeline.handle());
        VkSetState.setViewport(cmd, 0, 0, width, height);
        VkSetState.setScissor(cmd, 0, 0, width, height);

        float time = (System.nanoTime() - startTime) / 1_000_000_000f;

        drawMesh(cmd, frameArena, paintMesh, 0, time);
        drawMesh(cmd, frameArena, strokeMesh, 1, time);
        drawMesh(cmd, frameArena, growthMesh, 2, time);
        drawMesh(cmd, frameArena, swapMesh, 3, time);
        drawMesh(cmd, frameArena, computeMesh, 4, time);
        drawRingMesh(cmd, frameArena, 5, time);
    }

    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void close() {
        if (retireQueue != null) retireQueue.close();
        if (paintMesh != null) paintMesh.close();
        if (strokeMesh != null) strokeMesh.close();
        if (growthMesh != null) growthMesh.close();
        if (swapMesh != null) swapMesh.close();
        if (computeMesh != null) computeMesh.close();
        if (computePositionBuffer != null) computePositionBuffer.close();
        if (computeColorBuffer != null) computeColorBuffer.close();
        if (computeIndexBuffer != null) computeIndexBuffer.close();
        if (computeDescriptorGroup != null) computeDescriptorGroup.close();
        if (computePipeline != null) computePipeline.close();
        if (ringMesh != null) ringMesh.close();
        if (dedicatedAllocator != null) dedicatedAllocator.close();
        if (ringBackingAllocator != null) ringBackingAllocator.close();
        if (pipeline != null) pipeline.close();
        if (layerArena != null) layerArena.close();
    }

    // =========================================================================
    // Pipeline
    // =========================================================================

    private void buildPipeline() {
        VkVertexFormat vertexFormat = LAYOUT.toVertexFormat(LOCATIONS);
        CompiledShader vert = ShaderLoader.compileShader("/shaders/mesh_mutation.vert");
        CompiledShader frag = ShaderLoader.compileShader("/shaders/mesh_mutation.frag");

        VkPipeline.Builder pb = VkPipeline.builder()
                .device(device)
                .vertexShader(vert.getSpirV())
                .fragmentShader(frag.getSpirV())
                .dynamicViewport()
                .dynamicScissor()
                .depthTest(false)
                .cullMode(0) // VK_CULL_MODE_NONE - show all faces
                .dynamicRendering(
                        0, // no depth attachment
                        io.github.yetyman.vulkan.enums.VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value())
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
    // Drawing helpers
    // =========================================================================

    private void drawMesh(VkCommandBuffer cmd, Arena frameArena, Mesh mesh, int column, float time) {
        if (mesh == null) return;
        Mat4 mvp = computeMVP(column, time);
        MemorySegment pushData = frameArena.allocate(64);
        mvp.writeTo(pushData, 0);
        vkCmdPushConstants(cmd.handle(), pipeline.layout(),
                VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, 64, pushData);

        GeometryBinding binding = mesh.binding();
        bindAndDraw(cmd, frameArena, binding, mesh.fullDrawRange());
    }

    private void drawRingMesh(VkCommandBuffer cmd, Arena frameArena, int column, float time) {
        if (ringMesh == null || ringAllocation == null) return;
        Mat4 mvp = computeMVP(column, time);
        MemorySegment pushData = frameArena.allocate(64);
        mvp.writeTo(pushData, 0);
        vkCmdPushConstants(cmd.handle(), pipeline.layout(),
                VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, 64, pushData);

        // Use the current slot's allocation for binding
        GeometryAllocation currentAlloc = ringAllocator.currentAllocation(ringAllocation);
        GeometryBinding binding = new GeometryBinding(LAYOUT, currentAlloc, ringMesh.indexWidth());
        bindAndDraw(cmd, frameArena, binding, ringMesh.fullDrawRange());
    }

    private void bindAndDraw(VkCommandBuffer cmd, Arena frameArena, GeometryBinding binding,
                             GeometryDrawRange draw) {
        MemorySegment[] handles = binding.vertexBufferHandles();
        long[] offsets = binding.vertexBufferOffsets();

        MemorySegment bufArray = frameArena.allocate(ValueLayout.ADDRESS, handles.length);
        MemorySegment offArray = frameArena.allocate(ValueLayout.JAVA_LONG, handles.length);
        for (int j = 0; j < handles.length; j++) {
            bufArray.setAtIndex(ValueLayout.ADDRESS, j, handles[j]);
            offArray.setAtIndex(ValueLayout.JAVA_LONG, j, offsets[j]);
        }
        VkBind.bindVertexBuffers(cmd.handle(), 0, handles.length, bufArray, offArray);

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

    private Mat4 computeMVP(int column, float time) {
        // 6 columns evenly spaced across a reasonable world width
        float meshSpacing = 2.2f;
        float totalWidth = (6 - 1) * meshSpacing; // distance from first center to last center
        float xOffset = -totalWidth / 2f + column * meshSpacing;

        // Spin around Y axis
        float angle = time * 0.5f;
        Mat4 model = Mat4.rotationY(angle);

        // For categories 1, 4, 5, 6 (grids/flat geometry), rotate 90 degrees X to face camera
        if (column == 0 || column == 3 || column == 4 || column == 5) {
            model = model.mulNew(Mat4.rotationX((float) Math.PI / 2f));
        }
        // Tilt category 2 slightly for a higher viewing angle
        if (column == 1) {
            model = model.mulNew(Mat4.rotationX((float) Math.toRadians(25)));
        }

        // Push category 3 forward so it's not clipped by near plane
        if (column == 2) {
            model.m32 = 1.0f;
        }

        model.m30 = xOffset;

        // Camera positioned to frame all 6 columns snugly
        float cameraDistance = totalWidth * 0.65f + 1.5f;
        Mat4 view = Mat4.lookAt(
                new Vec3(0, 0.5f, cameraDistance),  // eye slightly up
                new Vec3(0, 0, 0),                  // look at origin
                new Vec3(0, 1, 0));                 // up

        float aspect = (float) width / Math.max(height, 1);
        Mat4 proj = Mat4.perspective((float) Math.toRadians(55), aspect, 0.1f, 100f);

        return proj.mulNew(view).mul(model);
    }

    // =========================================================================
    // Category initialization and update - see continuation file
    // =========================================================================

    // =========================================================================
    // Category 1: Attribute Paint (in-place re-upload via MutableGeometrySource)
    // =========================================================================

    private void initCategory1() {
        paintSource = new PaintableGridSource(8);
        paintMesh = Mesh.builder()
                .source(paintSource)
                .layout(LAYOUT)
                .allocator(dedicatedAllocator)
                .executor(executor)
                .queue(queue)
                .build();
    }

    private void updateCategory1(float time) {
        paintSource.paintWave(time);
        paintMesh.updateDirty(executor, queue);
    }

    // =========================================================================
    // Category 2: Growing Stroke (count grows within capacity)
    // =========================================================================

    private void initCategory2() {
        strokeSource = new GrowingStrokeSource(500);
        // Allocate with large capacity: 500 segments * 6 verts each = 3000 verts
        strokeMesh = Mesh.builder()
                .source(strokeSource)
                .layout(LAYOUT)
                .allocator(dedicatedAllocator)
                .executor(executor)
                .queue(queue)
                .vertexCapacity(3000)
                .awaitUpload(true)
                .build();
    }

    private void updateCategory2(float time) {
        // Grow a spiral continuously - large capacity so it runs for a long time
        int targetSegments = (int) (time * 5);
        while (strokeSource.segmentCount() < targetSegments && strokeSource.segmentCount() < 500) {
            int i = strokeSource.segmentCount();
            float angle = i * 0.3f;
            float radius = 0.1f + i * 0.007f;
            float x = (float) Math.cos(angle) * radius;
            float z = (float) Math.sin(angle) * radius;
            float y = i * 0.008f - 0.5f;
            float t = i / 500f;
            strokeSource.addSpinePoint(x, y, z, t, 1.0f - t, 0.5f);
        }

        if (strokeSource.isDirty(AttributeSemantic.POSITION)) {
            strokeMesh.updateDirty(executor, queue);
            strokeMesh.setLiveCounts(strokeSource.liveCount(), 0);
        }
    }

    // =========================================================================
    // Category 3: Unbounded Growth (reallocation when capacity exceeded)
    // =========================================================================

    private void initCategory3() {
        growthSource = new GrowingStrokeSource(60);
        growthCapacity = 20 * 6; // Start small: 20 segments * 6 verts
        growthMesh = Mesh.builder()
                .source(growthSource)
                .layout(LAYOUT)
                .allocator(dedicatedAllocator)
                .executor(executor)
                .queue(queue)
                .vertexCapacity(growthCapacity)
                .awaitUpload(true)
                .build();
    }

    private void updateCategory3(float time) {
        // Category 3: grow continuously, reallocate when capacity is exceeded
        int targetSegments = (int) (time * 3);
        while (growthSource.segmentCount() < targetSegments) {
            int i = growthSource.segmentCount();
            float angle = i * 0.2f;
            float radius = 0.2f + i * 0.005f;
            float x = (float) Math.cos(angle) * radius;
            float z = (float) Math.sin(angle) * radius;
            float y = (float) Math.sin(i * 0.1f) * 0.5f;
            float t = (float) (Math.sin(i * 0.05f) * 0.5 + 0.5);
            growthSource.addSpinePoint(x, y, z, 1.0f - t, t, 0.3f);
        }

        if (growthSource.isDirty(AttributeSemantic.POSITION)) {
            long neededVerts = growthSource.liveCount();
            // Reallocate if needed (MAPPED strategy = no staging leak)
            if (neededVerts > growthCapacity) {
                long newCapacity = Math.max(growthCapacity * 2, neededVerts + 32);
                GpuCompletion completion = growthMesh.reallocate(
                        newCapacity, 0, dedicatedAllocator, executor, queue, retireQueue);
                completion.flush();
                completion.await();
                completion.close();
                growthCapacity = newCapacity;
                status = "Cat 3: reallocated to capacity " + growthCapacity;
            }
            growthMesh.updateDirty(executor, queue);
            growthMesh.setLiveCounts(neededVerts, 0);
        }
    }

    // =========================================================================
    // Category 4: Topology Swap (remesh via RetireQueue)
    // =========================================================================

    private void initCategory4() {
        swapVariant = 0;
        swapTimer = 0;
        swapMesh = buildSwapVariant(0);
    }

    private void updateCategory4(float time, float dt) {
        swapTimer += dt;
        if (swapTimer > 3.0f) { // Swap every 3 seconds
            swapTimer = 0;
            swapVariant = (swapVariant + 1) % 3;
            Mesh replacement = buildSwapVariant(swapVariant);
            MeshOps.swap(swapMesh, replacement, dedicatedAllocator, retireQueue,
                    GpuCompletion.completed());
        }
    }

    private Mesh buildSwapVariant(int variant) {
        PaintableGridSource source;
        switch (variant) {
            case 0 -> source = new PaintableGridSource(4);  // coarse grid
            case 1 -> source = new PaintableGridSource(8);  // medium grid
            default -> source = new PaintableGridSource(12); // fine grid
        }
        // Give it a distinguishing color
        float r = variant == 0 ? 1.0f : 0.3f;
        float g = variant == 1 ? 1.0f : 0.3f;
        float b = variant == 2 ? 1.0f : 0.3f;
        for (int i = 0; i < source.elementCount(); i++) {
            source.paintVertex(i, r, g, b);
        }
        source.clearDirty(AttributeSemantic.COLOR(0));
        return Mesh.builder()
                .source(source)
                .layout(LAYOUT)
                .allocator(dedicatedAllocator)
                .executor(executor)
                .queue(queue)
                .build();
    }

    // =========================================================================
    // Category 5: Compute Deform (GPU writes geometry, mesh wraps external allocation)
    // =========================================================================

    private void initCategory5() {
        // Create a small grid and upload its initial state into buffers we control
        int gridSize = 6;
        PaintableGridSource src = new PaintableGridSource(gridSize);
        computeVertexCount = (int) src.elementCount();
        int idxCount = (int) src.indices().get().indexCount();

        long posSize = (long) computeVertexCount * 12; // 3 floats * 4 bytes
        long colSize = (long) computeVertexCount * 12;
        long idxSize = (long) idxCount * 4; // U32 indices

        // Create MIXED buffers (storage for compute + vertex for draw + transfer)
        computePositionBuffer = BufferFactory.create(MemoryStrategy.MAPPED, null, posSize,
                BufferUsage.MIXED, device, queue);
        computeColorBuffer = BufferFactory.create(MemoryStrategy.MAPPED, null, colSize,
                BufferUsage.MIXED, device, queue);
        computeIndexBuffer = BufferFactory.create(MemoryStrategy.MAPPED, null, idxSize,
                BufferUsage.INDEX, device, queue);

        // Upload initial positions
        int vertsPerRow = gridSize + 1;
        try (var scope = computePositionBuffer.acquireWrite(0, posSize, queue)) {
            MemorySegment seg = scope.segment();
            float halfSize = gridSize / 2.0f;
            for (int i = 0; i < computeVertexCount; i++) {
                int row = i / vertsPerRow;
                int col = i % vertsPerRow;
                seg.set(JAVA_FLOAT_UNALIGNED, (long) i * 12, (col - halfSize) / gridSize * 2f);
                seg.set(JAVA_FLOAT_UNALIGNED, (long) i * 12 + 4, 0f);
                seg.set(JAVA_FLOAT_UNALIGNED, (long) i * 12 + 8, (row - halfSize) / gridSize * 2f);
            }
        }
        // Upload initial colors
        try (var scope = computeColorBuffer.acquireWrite(0, colSize, queue)) {
            MemorySegment seg = scope.segment();
            for (int i = 0; i < computeVertexCount; i++) {
                seg.set(JAVA_FLOAT_UNALIGNED, (long) i * 12, 0.3f);
                seg.set(JAVA_FLOAT_UNALIGNED, (long) i * 12 + 4, 0.7f);
                seg.set(JAVA_FLOAT_UNALIGNED, (long) i * 12 + 8, 1.0f);
            }
        }
        // Upload indices from the source
        try (var scope = computeIndexBuffer.acquireWrite(0, idxSize, queue)) {
            src.indices().get().transcodeInto(IndexWidth.U32, 0, scope.segment(), 0, 0, idxCount);
        }

        // Flush the initial upload
        io.github.yetyman.vulkan.buffers.TransferBatchManager.flush(device, queue).await();

        // Build a mesh wrapping these external buffers (adopt mode)
        DeviceRange posRange = new DeviceRange(computePositionBuffer, 0, posSize, 12);
        DeviceRange colRange = new DeviceRange(computeColorBuffer, 0, colSize, 12);
        DeviceRange idxRange = new DeviceRange(computeIndexBuffer, 0, idxSize, 4);
        ExternalAllocation extAlloc = ExternalAllocation.builder()
                .vertexRange(0, posRange)
                .vertexRange(1, colRange)
                .indexRange(idxRange)
                .build();

        computeMesh = Mesh.builder()
                .allocation(extAlloc)
                .layout(LAYOUT)
                .vertexCount(computeVertexCount)
                .indexCount(idxCount)
                .indexWidth(IndexWidth.U32)
                .topology(PrimitiveTopology.TRIANGLE_LIST)
                .bounds(new AABB(new Vec3(-1, -1, -1), new Vec3(1, 1, 1)))
                .build();

        // Note: The compute deformation shader (mesh_deform.comp) would be dispatched before
        // the draw pass via render graph integration. For now, the mesh demonstrates the
        // adopt-mode pattern with a static grid that could be deformed by any external writer.
    }

    // =========================================================================
    // Category 6: Ring Buffered (N-buffered write-while-reading)
    // =========================================================================

    private void initCategory6() {
        ringSource = new PaintableGridSource(6);

        // Allocate via the ring allocator - this creates 2 slots
        ringAllocation = ringAllocator.allocate(LAYOUT, ringSource.elementCount(),
                IndexWidth.U32, ringSource.indices().map(i -> i.indexCount()).orElse(0L));

        // Upload initial data into both slots
        for (int slot = 0; slot < 2; slot++) {
            GeometryAllocation slotAlloc = ringAllocator.slotAllocation(ringAllocation, slot);
            Mesh tempMesh = Mesh.builder()
                    .source(ringSource)
                    .allocation(slotAlloc)
                    .layout(LAYOUT)
                    .vertexCount(ringSource.elementCount())
                    .indexCount(ringSource.indices().map(i -> i.indexCount()).orElse(0L))
                    .topology(PrimitiveTopology.TRIANGLE_LIST)
                    .bounds(ringSource.bounds())
                    .build();
            // Upload into this slot
            var plan = new io.github.yetyman.vulkan.mesh.residency.UploadPlanner()
                    .plan(ringSource, LAYOUT, slotAlloc, ringAllocator);
            GpuCompletion c = executor.execute(plan, queue);
            c.flush();
            c.await();
            c.close();
        }

        // Build the reference mesh (uses slot 0's data for counts/bounds)
        GeometryAllocation slot0 = ringAllocator.currentAllocation(ringAllocation);
        ringMesh = Mesh.builder()
                .allocation(slot0)
                .layout(LAYOUT)
                .vertexCount(ringSource.elementCount())
                .indexCount(ringSource.indices().map(i -> i.indexCount()).orElse(0L))
                .indexWidth(IndexWidth.U32)
                .topology(PrimitiveTopology.TRIANGLE_LIST)
                .bounds(ringSource.bounds())
                .build();
    }

    private void updateCategory6(float time) {
        // Advance the ring to the next slot each frame
        ringAllocator.advance();

        // Paint a different pattern and upload into the current slot only
        ringSource.paintWave(time + 1.5f); // offset from category 1 for visual variety

        GeometryAllocation currentAlloc = ringAllocator.currentAllocation(ringAllocation);
        // Upload dirty color into the current slot
        var plan = new io.github.yetyman.vulkan.mesh.residency.UploadPlanner()
                .planUpdate(ringSource, LAYOUT, currentAlloc,
                        Set.of(AttributeSemantic.COLOR(0)),
                        ringSource.dirtyWindow(AttributeSemantic.COLOR(0)));
        GpuCompletion c = executor.execute(plan, queue);
        c.flush();
        ringSource.clearDirty(AttributeSemantic.COLOR(0));
    }
}
