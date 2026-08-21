package io.github.yetyman.vulkan.sample.bufferdemo;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDescriptorPool;
import io.github.yetyman.vulkan.VkDescriptorSet;
import io.github.yetyman.vulkan.VkDescriptorSetLayout;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPipeline;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferFactory;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.BufferWriteScope;
import io.github.yetyman.vulkan.buffers.DirtyRegionIterator;
import io.github.yetyman.vulkan.buffers.DirtyStrategy;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.command.VkBind;
import io.github.yetyman.vulkan.command.VkDrawIndexed;
import io.github.yetyman.vulkan.command.VkPushConstantsCmd;
import io.github.yetyman.vulkan.command.VkSetState;
import io.github.yetyman.vulkan.enums.VkFormat;
import io.github.yetyman.vulkan.enums.VkIndexType;
import io.github.yetyman.vulkan.enums.VkPipelineBindPoint;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.enums.VkVertexInputRate;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.shaders.ShaderInstance;
import io.github.yetyman.vulkan.shaders.ShaderLoader;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.ui.UIFrameContext;
import io.github.yetyman.vulkan.ui.UILayer;
import io.github.yetyman.vulkan.ui.input.InputEvent;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * UILayer that renders 4 quad grids side-by-side, each backed by a different buffer
 * memory strategy. The grids are deformed each frame by the current mouse position
 * (XY displacement falling off with distance from cursor).
 *
 * <p>Grid layout: 4 columns, each 32 quads wide x 80 quads tall, with spacing between.
 * Each grid uses vertex pulling from an SSBO containing vec2 positions.
 *
 * <p>Buffer strategies demonstrated:
 * <ol>
 *   <li>DEVICE_LOCAL_MIRRORED - GPU-resident, CPU reads from mirror buffer</li>
 *   <li>MAPPED - HOST_VISIBLE|HOST_COHERENT, CPU reads directly (inherent observability)</li>
 *   <li>MAPPED_CACHED - HOST_VISIBLE|HOST_CACHED, CPU reads directly with cache</li>
 *   <li>REBAR - Direct CPU-to-VRAM mapping (falls back to MAPPED if ReBAR unavailable)</li>
 * </ol>
 */
public class QuadGridMeshLayer implements UILayer {

    private static final int ORDER = 50;

    // Grid dimensions: 32 quads wide x 80 quads tall
    // Each quad has 4 unique vertices (not shared) to allow spacing between quads
    public static final int QUAD_COLS = 32;
    public static final int QUAD_ROWS = 80;
    public static final int VERTS_PER_QUAD = 4;
    public static final int VERTEX_COUNT = QUAD_COLS * QUAD_ROWS * VERTS_PER_QUAD; // 10240
    public static final int VERTEX_STRIDE = 8; // vec2 = 2 floats = 8 bytes
    public static final long BUFFER_SIZE = (long) VERTEX_COUNT * VERTEX_STRIDE;

    // Index buffer: 2 triangles per quad, 6 indices per quad
    private static final int INDEX_COUNT = QUAD_COLS * QUAD_ROWS * 6;

    // Spacing between quads as fraction of cell size
    private static final float QUAD_INSET = 0.08f;

    public static final int GRID_COUNT = 4;

    // Deformation parameters
    private static final float DEFORM_RADIUS = 0.15f; // in normalized grid coords
    private static final float DEFORM_STRENGTH = 0.04f;

    private UIContext ctx;
    private VkDevice device;
    private VkQueue queue;
    private Arena layerArena;

    private ManagedBuffer[] vertexBuffers;
    private IBuffer indexBuffer;
    private VkDescriptorSetLayout descriptorLayout;
    private VkDescriptorPool descriptorPool;
    private VkDescriptorSet[] descriptorSets;
    private VkPipeline pipeline;

    private final MouseInputLayer mouseInput;
    private int width;
    private int height;

    // Per-grid tracking of which quads were deformed last frame (for differential restore)
    private boolean[][] previouslyDeformed;

    // Per-grid stats
    private final long[] lastWrittenBytes = new long[GRID_COUNT];  // bytes CPU wrote this frame
    private final int[] lastWrittenQuads = new int[GRID_COUNT];    // quads touched this frame
    private final long[] lastTransferBytes = new long[GRID_COUNT]; // bytes actually sent to GPU
    private final int[] lastTransferRegions = new int[GRID_COUNT]; // transfer regions

    // Strategy labels for display
    private static final String[] STRATEGY_LABELS = {
            "DEVICE_LOCAL_MIRRORED",
            "MAPPED (coherent)",
            "MAPPED_CACHED",
            "REBAR (fallback: MAPPED)"
    };

    private static final MemoryStrategy[] STRATEGIES = {
            MemoryStrategy.DEVICE_LOCAL_MIRRORED,
            MemoryStrategy.MAPPED,
            MemoryStrategy.MAPPED_CACHED,
            MemoryStrategy.REBAR
    };

    public QuadGridMeshLayer(MouseInputLayer mouseInput) {
        this.mouseInput = mouseInput;
    }

    /** @return the vertex buffer for the given grid index. */
    public ManagedBuffer vertexBuffer(int gridIndex) {
        return vertexBuffers[gridIndex];
    }

    /** @return the strategy label for the given grid index. */
    public String strategyLabel(int gridIndex) {
        return STRATEGY_LABELS[gridIndex];
    }

    /**
     * Computes the screen-space bounding rectangle for a given grid column.
     * @return [x, y, width, height] in pixels
     */
    public float[] gridBounds(int gridIndex) {
        float totalWidth = width;
        float totalHeight = height;
        float spacing = totalWidth * 0.02f;
        float gridWidth = (totalWidth - spacing * (GRID_COUNT + 1)) / GRID_COUNT;
        float gridHeight = totalHeight * 0.9f;
        float x = spacing + gridIndex * (gridWidth + spacing);
        float y = totalHeight * 0.05f;
        return new float[]{x, y, gridWidth, gridHeight};
    }

    @Override
    public String name() {
        return "quad-grid-mesh";
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void initialize(UIContext ctx) {
        this.ctx = ctx;
        this.device = ctx.vulkan().device();
        this.queue = ctx.vulkan().graphicsVkQueue();
        this.width = ctx.width();
        this.height = ctx.height();
        this.layerArena = Arena.ofShared();

        if (!VulkanCapabilities.dynamicRendering) {
            throw new IllegalStateException("QuadGridMeshLayer requires dynamic rendering support");
        }

        // Create vertex buffers for each strategy
        vertexBuffers = new ManagedBuffer[GRID_COUNT];
        previouslyDeformed = new boolean[GRID_COUNT][QUAD_COLS * QUAD_ROWS];
        for (int i = 0; i < GRID_COUNT; i++) {
            MemoryStrategy strategy = STRATEGIES[i];
            try {
                vertexBuffers[i] = (ManagedBuffer) BufferFactory.create(
                        strategy, null, BUFFER_SIZE, BufferUsage.STORAGE, device, queue);
            } catch (Exception e) {
                // ReBAR may not be available - fall back to MAPPED
                vertexBuffers[i] = (ManagedBuffer) BufferFactory.create(
                        MemoryStrategy.MAPPED, null, BUFFER_SIZE, BufferUsage.STORAGE, device, queue);
            }
            vertexBuffers[i].setDeferred(true);
            initializeGrid(i);
            vertexBuffers[i].flushDirty(queue);
        }

        // Create shared index buffer
        createIndexBuffer();

        // Descriptor setup
        int vertStage = VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value();

        descriptorLayout = VkDescriptorSetLayout.builder()
                .device(device)
                .storageBuffer(0, vertStage)
                .build(layerArena);

        descriptorPool = VkDescriptorPool.builder()
                .device(device)
                .maxSets(GRID_COUNT)
                .storageBuffers(GRID_COUNT)
                .build(layerArena);

        descriptorSets = new VkDescriptorSet[GRID_COUNT];
        for (int i = 0; i < GRID_COUNT; i++) {
            descriptorSets[i] = descriptorPool.allocateDescriptorSet(descriptorLayout);
            try (Arena tmp = Arena.ofConfined()) {
                descriptorSets[i].bind(0, vertexBuffers[i].vkBuffer(), tmp);
            }
        }

        // Create pipeline
        pipeline = VkPipeline.builder()
                .device(device)
                .vertexShader(ShaderLoader.builder("/shaders/quad_grid.vert").compile())
                .fragmentShader(ShaderLoader.builder("/shaders/quad_grid.frag").compile())
                .triangleTopology()
                .dynamicViewport()
                .dynamicScissor()
                .dynamicRendering(0, VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value())
                .descriptorSetLayouts(descriptorLayout.handle())
                .pushConstantRange(vertStage, 0, 24) // vec2 offset + vec2 scale + int cols + int rows = 24 bytes
                .build(layerArena);
    }

    @Override
    public void update(UIFrameContext frame) {
        // Deform all grids based on mouse position (differential: only write affected quads)
        float mx = mouseInput.mouseX();
        float my = mouseInput.mouseY();

        for (int i = 0; i < GRID_COUNT; i++) {
            int quadsWritten = deformGridDifferential(i, mx, my);
            lastWrittenBytes[i] = (long) quadsWritten * VERTS_PER_QUAD * VERTEX_STRIDE;
            lastWrittenQuads[i] = quadsWritten;

            // For DEVICE_LOCAL_MIRRORED: dirty state was accumulated by acquireWrite.
            // Check what will actually be transferred before flushing.
            DirtyStrategy dirty = vertexBuffers[i].cpuDirtyStrategy();
            if (dirty.isDirty()) {
                lastTransferRegions[i] = dirty.dirtyRegionCount();
                long totalBytes = 0;
                DirtyRegionIterator it = dirty.dirtyRegions();
                while (it.hasNext()) {
                    it.next();
                    totalBytes += it.size();
                }
                lastTransferBytes[i] = totalBytes;
            } else {
                lastTransferBytes[i] = 0;
                lastTransferRegions[i] = 0;
            }

            // Flush: for DEVICE_LOCAL_MIRRORED this issues vkCmdCopyBuffer for dirty regions.
            // For MAPPED/CACHED/REBAR this is a no-op (writes are already GPU-visible).
            vertexBuffers[i].flushDirty(queue);
        }
    }

    /** @return bytes written by CPU last frame for grid i. */
    public long lastWrittenBytes(int gridIndex) {
        return lastWrittenBytes[gridIndex];
    }

    /** @return quads touched last frame for grid i. */
    public int lastWrittenQuads(int gridIndex) {
        return lastWrittenQuads[gridIndex];
    }

    /** @return bytes actually transferred to GPU last frame for grid i (0 for direct-mapped). */
    public long lastTransferBytes(int gridIndex) {
        return lastTransferBytes[gridIndex];
    }

    /** @return number of transfer regions last frame for grid i. */
    public int lastTransferRegions(int gridIndex) {
        return lastTransferRegions[gridIndex];
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        VkBind.bindPipeline(cmd.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), pipeline.handle());

        // Use full-window viewport and scissor so deformed vertices can extend beyond grid bounds
        VkSetState.setViewport(cmd, 0, 0, 0, width, height, 0.0f, 1.0f);
        VkSetState.setScissor(cmd, 0, 0, 0, width, height);

        for (int i = 0; i < GRID_COUNT; i++) {
            float[] bounds = gridBounds(i);

            // Push constants: map grid-local [0,1] to NDC using full-window coordinates
            // Grid pixel rect [bounds.x, bounds.x+bounds.w] maps to NDC x range
            // NDC.x = (pixelX / windowWidth) * 2 - 1
            // So grid-local [0,1] -> pixel [bounds.x, bounds.x+w] -> NDC
            // gridOffset = (bounds.x / width) * 2 - 1 (maps grid-local 0 to correct NDC)
            // gridScale = (bounds.w / width) * 2 (maps grid-local [0,1] span to NDC span)
            float ndcOffsetX = (bounds[0] / width) * 2.0f - 1.0f;
            float ndcOffsetY = (bounds[1] / height) * 2.0f - 1.0f;
            float ndcScaleX = (bounds[2] / width) * 2.0f;
            float ndcScaleY = (bounds[3] / height) * 2.0f;

            MemorySegment pc = frameArena.allocate(24);
            pc.set(ValueLayout.JAVA_FLOAT, 0, ndcOffsetX);
            pc.set(ValueLayout.JAVA_FLOAT, 4, ndcOffsetY);
            pc.set(ValueLayout.JAVA_FLOAT, 8, ndcScaleX);
            pc.set(ValueLayout.JAVA_FLOAT, 12, ndcScaleY);
            pc.set(ValueLayout.JAVA_INT, 16, QUAD_COLS);
            pc.set(ValueLayout.JAVA_INT, 20, QUAD_ROWS);

            VkPushConstantsCmd.pushConstants(cmd.handle(), pipeline.layout(),
                    VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, pc, 24);

            // Bind descriptor set for this grid's vertex buffer
            descriptorSets[i].bind(cmd, pipeline, 0, frameArena);

            // Bind index buffer and draw
            VkBind.bindIndexBuffer(cmd.handle(), indexBuffer.handle(), 0,
                    VkIndexType.VK_INDEX_TYPE_UINT32.value());
            VkDrawIndexed.drawIndexed(cmd.handle(), INDEX_COUNT, 1, 0, 0, 0);
        }
    }

    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public boolean handleInput(InputEvent event) {
        return false;
    }

    @Override
    public boolean acceptsInput() {
        return false;
    }

    @Override
    public void close() {
        if (pipeline != null) pipeline.close();
        if (descriptorPool != null) descriptorPool.close();
        if (descriptorLayout != null) descriptorLayout.close();
        if (indexBuffer != null) indexBuffer.close();
        if (vertexBuffers != null) {
            for (ManagedBuffer buf : vertexBuffers) {
                if (buf != null) buf.close();
            }
        }
        if (layerArena != null) layerArena.close();
    }

    /**
     * Initializes grid i with separated quads (4 vertices per quad with inset spacing).
     * Each quad occupies a cell in the grid with a small gap between adjacent quads.
     */
    private void initializeGrid(int gridIndex) {
        ManagedBuffer buf = vertexBuffers[gridIndex];
        try (BufferWriteScope scope = buf.acquireWrite(0, BUFFER_SIZE, queue)) {
            MemorySegment mem = scope.segment();
            float cellW = 1.0f / QUAD_COLS;
            float cellH = 1.0f / QUAD_ROWS;
            float insetX = cellW * QUAD_INSET;
            float insetY = cellH * QUAD_INSET;

            for (int row = 0; row < QUAD_ROWS; row++) {
                for (int col = 0; col < QUAD_COLS; col++) {
                    int quadIdx = row * QUAD_COLS + col;
                    int baseVert = quadIdx * VERTS_PER_QUAD;

                    float left = col * cellW + insetX;
                    float right = (col + 1) * cellW - insetX;
                    float top = row * cellH + insetY;
                    float bottom = (row + 1) * cellH - insetY;

                    // TL, TR, BL, BR
                    long offset = (long) baseVert * VERTEX_STRIDE;
                    mem.set(ValueLayout.JAVA_FLOAT, offset, left);
                    mem.set(ValueLayout.JAVA_FLOAT, offset + 4, top);
                    mem.set(ValueLayout.JAVA_FLOAT, offset + 8, right);
                    mem.set(ValueLayout.JAVA_FLOAT, offset + 12, top);
                    mem.set(ValueLayout.JAVA_FLOAT, offset + 16, left);
                    mem.set(ValueLayout.JAVA_FLOAT, offset + 20, bottom);
                    mem.set(ValueLayout.JAVA_FLOAT, offset + 24, right);
                    mem.set(ValueLayout.JAVA_FLOAT, offset + 28, bottom);
                }
            }
        }
    }

    /**
     * Differentially deforms the grid: only writes quads whose vertices are within
     * the deform radius OR that were deformed last frame but are now outside (restore).
     * This produces minimal dirty regions for the dirty tracking strategy to coalesce.
     *
     * @return number of quads written this frame
     */
    private int deformGridDifferential(int gridIndex, float screenMouseX, float screenMouseY) {
        float[] bounds = gridBounds(gridIndex);
        float localMX = (screenMouseX - bounds[0]) / bounds[2];
        float localMY = (screenMouseY - bounds[1]) / bounds[3];

        float cellW = 1.0f / QUAD_COLS;
        float cellH = 1.0f / QUAD_ROWS;
        float insetX = cellW * QUAD_INSET;
        float insetY = cellH * QUAD_INSET;
        float halfDiag = (float) Math.sqrt(cellW * cellW + cellH * cellH) * 0.5f;
        float affectRadius = DEFORM_RADIUS + halfDiag;

        ManagedBuffer buf = vertexBuffers[gridIndex];
        boolean[] prevDeformed = previouslyDeformed[gridIndex];
        int quadsWritten = 0;

        for (int row = 0; row < QUAD_ROWS; row++) {
            float quadCenterY = (row + 0.5f) * cellH;
            float dy = quadCenterY - localMY;

            // Early row skip: if the entire row is too far vertically
            if (Math.abs(dy) > affectRadius) {
                for (int col = 0; col < QUAD_COLS; col++) {
                    int quadIdx = row * QUAD_COLS + col;
                    if (prevDeformed[quadIdx]) {
                        writeQuadBase(buf, quadIdx, col, row, cellW, cellH, insetX, insetY);
                        prevDeformed[quadIdx] = false;
                        quadsWritten++;
                    }
                }
                continue;
            }

            for (int col = 0; col < QUAD_COLS; col++) {
                int quadIdx = row * QUAD_COLS + col;

                float quadCenterX = (col + 0.5f) * cellW;
                float dx = quadCenterX - localMX;
                float distToCenter = (float) Math.sqrt(dx * dx + dy * dy);

                boolean affected = distToCenter < affectRadius;

                if (!affected && !prevDeformed[quadIdx]) {
                    continue;
                }

                int baseVert = quadIdx * VERTS_PER_QUAD;
                long byteOffset = (long) baseVert * VERTEX_STRIDE;
                long byteSize = (long) VERTS_PER_QUAD * VERTEX_STRIDE;

                float left = col * cellW + insetX;
                float right = (col + 1) * cellW - insetX;
                float top = row * cellH + insetY;
                float bottom = (row + 1) * cellH - insetY;

                try (BufferWriteScope scope = buf.acquireWrite(byteOffset, byteSize, queue)) {
                    MemorySegment mem = scope.segment();
                    writeDeformedVertex(mem, 0, left, top, localMX, localMY, affected);
                    writeDeformedVertex(mem, 8, right, top, localMX, localMY, affected);
                    writeDeformedVertex(mem, 16, left, bottom, localMX, localMY, affected);
                    writeDeformedVertex(mem, 24, right, bottom, localMX, localMY, affected);
                }

                prevDeformed[quadIdx] = affected;
                quadsWritten++;
            }
        }
        return quadsWritten;
    }

    private void writeDeformedVertex(MemorySegment mem, long offset, float baseX, float baseY,
                                     float mouseX, float mouseY, boolean affected) {
        float finalX = baseX;
        float finalY = baseY;
        if (affected) {
            float vdx = baseX - mouseX;
            float vdy = baseY - mouseY;
            float dist = (float) Math.sqrt(vdx * vdx + vdy * vdy);
            if (dist < DEFORM_RADIUS && dist > 0.001f) {
                float t = 1.0f - dist / DEFORM_RADIUS;
                float factor = DEFORM_STRENGTH * t * t * (3.0f - 2.0f * t);
                finalX += (vdx / dist) * factor;
                finalY += (vdy / dist) * factor;
            }
        }
        mem.set(ValueLayout.JAVA_FLOAT, offset, finalX);
        mem.set(ValueLayout.JAVA_FLOAT, offset + 4, finalY);
    }

    /** Writes a quad back to its base (undeformed) position. */
    private void writeQuadBase(ManagedBuffer buf, int quadIdx, int col, int row,
                               float cellW, float cellH, float insetX, float insetY) {
        int baseVert = quadIdx * VERTS_PER_QUAD;
        long byteOffset = (long) baseVert * VERTEX_STRIDE;
        long byteSize = (long) VERTS_PER_QUAD * VERTEX_STRIDE;

        float left = col * cellW + insetX;
        float right = (col + 1) * cellW - insetX;
        float top = row * cellH + insetY;
        float bottom = (row + 1) * cellH - insetY;

        try (BufferWriteScope scope = buf.acquireWrite(byteOffset, byteSize, queue)) {
            MemorySegment mem = scope.segment();
            mem.set(ValueLayout.JAVA_FLOAT, 0, left);
            mem.set(ValueLayout.JAVA_FLOAT, 4, top);
            mem.set(ValueLayout.JAVA_FLOAT, 8, right);
            mem.set(ValueLayout.JAVA_FLOAT, 12, top);
            mem.set(ValueLayout.JAVA_FLOAT, 16, left);
            mem.set(ValueLayout.JAVA_FLOAT, 20, bottom);
            mem.set(ValueLayout.JAVA_FLOAT, 24, right);
            mem.set(ValueLayout.JAVA_FLOAT, 28, bottom);
        }
    }

    /**
     * Creates the shared index buffer for separated quads.
     * Each quad has 4 unique vertices (TL=0, TR=1, BL=2, BR=3 within quad),
     * 2 triangles = 6 indices per quad.
     */
    private void createIndexBuffer() {
        long indexBufSize = (long) INDEX_COUNT * 4; // uint32 indices
        indexBuffer = BufferFactory.create(
                MemoryStrategy.MAPPED, null, indexBufSize, BufferUsage.INDEX, device, queue);

        ByteBuffer indices = ByteBuffer.allocate((int) indexBufSize).order(ByteOrder.LITTLE_ENDIAN);
        for (int q = 0; q < QUAD_COLS * QUAD_ROWS; q++) {
            int base = q * VERTS_PER_QUAD;
            int tl = base;      // 0
            int tr = base + 1;  // 1
            int bl = base + 2;  // 2
            int br = base + 3;  // 3

            // Triangle 1: TL, BL, TR
            indices.putInt(tl);
            indices.putInt(bl);
            indices.putInt(tr);

            // Triangle 2: TR, BL, BR
            indices.putInt(tr);
            indices.putInt(bl);
            indices.putInt(br);
        }
        indices.flip();
        indexBuffer.write(indices, 0, queue);
    }
}
