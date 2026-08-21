package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.buffers.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.shaders.ShaderLoader;

import java.lang.foreign.*;

/**
 * Animated spline frame demonstrating deferred dirty tracking with multi-region GPU upload.
 *
 * <p>The buffer system features exercised here:
 * <ul>
 *   <li>{@code DEVICE_LOCAL_MIRRORED} buffer with deferred mode enabled</li>
 *   <li>Scattered CPU writes each frame via {@code acquireWrite} on small sub-ranges</li>
 *   <li>{@link DirtyStrategy} (RangeCoalescing for this buffer size) automatically tracks
 *       which byte ranges were touched</li>
 *   <li>A single {@code flushDirty} call per frame issues one {@code vkCmdCopyBuffer} with
 *       multiple regions — only the changed control points are transferred</li>
 *   <li>The GPU renders the full spline each frame from device-local memory at optimal bandwidth</li>
 * </ul>
 *
 * <p>Animation pattern: multiple independent "wave groups" propagate through the control point
 * array at different speeds and phases. Each group affects a contiguous run of ~32-128 points.
 * On any given frame, 3-6 groups are active, producing 3-6 dirty regions that the strategy
 * coalesces and flushes as a multi-region copy.
 *
 * <p>The control point layout is:
 * <pre>
 * struct ControlPoint {
 *     vec2 pos;    // 8 bytes
 *     vec4 color;  // 16 bytes
 * };               // 24 bytes total
 * </pre>
 */
public class SplineDirtyTrackingFrame extends SimpleGraphicsFrame {

    // Control point struct under std430 rules:
    //   vec2 pos;   // offset 0, 8 bytes
    //   (padding)   // offset 8, 8 bytes (vec4 requires 16-byte alignment)
    //   vec4 color; // offset 16, 16 bytes
    // Total: 32 bytes per control point
    private static final int CP_STRIDE = 32;
    private static final int CP_POS_OFFSET = 0;
    private static final int CP_COLOR_OFFSET = 16; // after 8-byte pad
    private static final int POINT_COUNT = 8192;
    private static final long BUF_SIZE = (long) POINT_COUNT * CP_STRIDE;

    // Wave animation parameters
    private static final int NUM_WAVE_GROUPS = 5;
    private static final int POINTS_PER_GROUP = 64;

    private ManagedBuffer controlPointBuffer;

    private VkDescriptorSetLayout descriptorLayout;
    private VkDescriptorPool descriptorPool;
    private VkDescriptorSet descriptorSet;

    // Animation state
    private long frameCount = 0;
    private final float[] wavePhases = new float[NUM_WAVE_GROUPS];
    private final float[] waveSpeeds = new float[NUM_WAVE_GROUPS];
    private final int[] waveOffsets = new int[NUM_WAVE_GROUPS]; // base index in point array

    // Stats for display
    private volatile String lastFlushStats = "";

    public SplineDirtyTrackingFrame(Arena arena, VkDevice device, VkQueue queue,
                                    MemorySegment surface, int width, int height) {
        super(arena, device, queue, surface, width, height, 3);
    }

    /**
     * @return stats about the last dirty flush (region count, bytes transferred).
     */
    public String getLastFlushStats() {
        return lastFlushStats;
    }

    @Override
    protected void initializeResources(int queueFamilyIndex) {
        // Create the control point buffer as DEVICE_LOCAL_MIRRORED with deferred mode.
        // At 8192 points * 24 bytes = 196,608 bytes (~192KB), DirtyStrategy.forSize() selects
        // RangeCoalescingDirtyStrategy, which coalesces adjacent dirty ranges with a 256-byte gap.
        controlPointBuffer = (ManagedBuffer) BufferFactory.create(
                MemoryStrategy.DEVICE_LOCAL_MIRRORED, null, BUF_SIZE,
                BufferUsage.STORAGE, device, queue);

        // Enable deferred mode: writes go to mirror, tracked by dirty strategy, flushed explicitly
        controlPointBuffer.setDeferred(true);

        // Initialize all control points as a grid of horizontal lines (multiple spline rows)
        // Each row is POINT_COUNT / NUM_ROWS points, distributed across the screen
        initializeControlPoints();

        // Flush the initial full-buffer write to the GPU
        controlPointBuffer.flushDirty(queue);

        // Initialize wave animation parameters
        for (int g = 0; g < NUM_WAVE_GROUPS; g++) {
            wavePhases[g] = (float) (g * Math.PI * 2.0 / NUM_WAVE_GROUPS);
            waveSpeeds[g] = 0.02f + g * 0.008f;
            // Distribute wave groups across the buffer so they hit different regions
            waveOffsets[g] = (POINT_COUNT / NUM_WAVE_GROUPS) * g + 100;
        }

        // Descriptor setup (vertex shader reads control points as SSBO)
        int vertStage = VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value();

        descriptorLayout = VkDescriptorSetLayout.builder()
                .device(device)
                .storageBuffer(0, vertStage)
                .build(arena);

        descriptorPool = VkDescriptorPool.builder()
                .device(device)
                .maxSets(1)
                .storageBuffers(1)
                .build(arena);

        descriptorSet = descriptorPool.allocateDescriptorSet(descriptorLayout);
        try (Arena tmp = Arena.ofConfined()) {
            descriptorSet.bind(0, controlPointBuffer.vkBuffer(), tmp);
        }

        super.initializeResources(queueFamilyIndex);
    }

    /**
     * Seeds the control points as a single continuous spiral that fills the screen.
     * The spiral progresses outward from the center, giving a dense continuous curve
     * rendered as one line strip with no topology breaks.
     */
    private void initializeControlPoints() {
        try (BufferWriteScope scope = controlPointBuffer.acquireWrite(0, BUF_SIZE, queue)) {
            MemorySegment mem = scope.segment();
            for (int i = 0; i < POINT_COUNT; i++) {
                float t = (float) i / POINT_COUNT;
                // Spiral: radius grows linearly, angle grows faster
                float radius = 0.05f + t * 0.85f;
                float angle = t * (float) (Math.PI * 2.0 * 24.0); // 24 full revolutions
                float x = radius * (float) Math.cos(angle);
                float y = radius * (float) Math.sin(angle);

                // Color: hue follows progress along the spiral
                float hue = t;
                float r = hueToR(hue);
                float g = hueToG(hue);
                float b = hueToB(hue);

                long base = (long) i * CP_STRIDE;
                mem.set(ValueLayout.JAVA_FLOAT, base + CP_POS_OFFSET, x);         // pos.x
                mem.set(ValueLayout.JAVA_FLOAT, base + CP_POS_OFFSET + 4, y);     // pos.y
                mem.set(ValueLayout.JAVA_FLOAT, base + CP_COLOR_OFFSET, r);       // color.r
                mem.set(ValueLayout.JAVA_FLOAT, base + CP_COLOR_OFFSET + 4, g);   // color.g
                mem.set(ValueLayout.JAVA_FLOAT, base + CP_COLOR_OFFSET + 8, b);   // color.b
                mem.set(ValueLayout.JAVA_FLOAT, base + CP_COLOR_OFFSET + 12, 1.0f); // color.a
            }
        }
    }

    @Override
    protected VkPipeline createPipeline() {
        VkPipeline.Builder pb = VkPipeline.builder()
                .device(device)
                .vertexShader(ShaderLoader.builder("/shaders/spline.vert").compile())
                .fragmentShader(ShaderLoader.builder("/shaders/spline.frag").compile())
                .lineStripTopology()
                .dynamicViewport()
                .dynamicScissor()
                .descriptorSetLayouts(descriptorLayout.handle());

        if (useDynamicRendering) {
            pb.dynamicRendering(0, VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value());
        } else {
            pb.renderPass(renderPass.handle());
        }

        return pb.build(arena);
    }

    @Override
    protected void beforeRenderPass(VkCommandBuffer commandBuffer, SegmentAllocator frameAllocator) {
        // Animate scattered groups of control points, then flush dirty ranges to GPU.
        // This is where the dirty tracking earns its keep: only the changed sub-ranges
        // are copied to the GPU, not the entire 192KB buffer.
        animateWaveGroups();
        flushAndRecordStats();
    }

    /**
     * Animates several independent groups of control points. Each group is a contiguous run
     * of points whose positions are displaced radially by a sine wave. The groups are scattered
     * across the buffer, producing multiple distinct dirty regions.
     *
     * <p>Uses {@code acquireWrite} on the sub-range for each group. In deferred mode, this:
     * <ol>
     *   <li>Returns a slice of the mirror's mapped memory (zero-cost, no staging alloc)</li>
     *   <li>On scope close, marks the range dirty via the buffer's DirtyStrategy</li>
     *   <li>No GPU copy is issued — that happens later in {@code flushDirty}</li>
     * </ol>
     *
     * <p>The caller does not need to know about observability, mirrors, or dirty strategies.
     * It just writes bytes through the buffer's normal API.
     */
    private void animateWaveGroups() {
        frameCount++;

        for (int g = 0; g < NUM_WAVE_GROUPS; g++) {
            wavePhases[g] += waveSpeeds[g];

            int startIdx = waveOffsets[g] % POINT_COUNT;
            int endIdx = Math.min(startIdx + POINTS_PER_GROUP, POINT_COUNT);
            int actualSize = endIdx - startIdx;
            if (actualSize <= 0) continue;

            long byteOffset = (long) startIdx * CP_STRIDE;
            long byteSize = (long) actualSize * CP_STRIDE;

            // acquireWrite in deferred mode: returns mirror memory, marks dirty on close
            try (BufferWriteScope scope = controlPointBuffer.acquireWrite(byteOffset, byteSize, queue)) {
                MemorySegment mem = scope.segment();
                for (int i = 0; i < actualSize; i++) {
                    int idx = startIdx + i;
                    float t = (float) idx / POINT_COUNT;
                    float baseRadius = 0.05f + t * 0.85f;
                    float angle = t * (float) (Math.PI * 2.0 * 24.0);

                    // Sine displacement along the radius
                    float localT = (float) i / POINTS_PER_GROUP;
                    float displacement = 0.04f * (float) Math.sin(wavePhases[g] + localT * Math.PI * 4.0);
                    float r = baseRadius + displacement;

                    float x = r * (float) Math.cos(angle);
                    float y = r * (float) Math.sin(angle);

                    long local = (long) i * CP_STRIDE;
                    mem.set(ValueLayout.JAVA_FLOAT, local + CP_POS_OFFSET, x);
                    mem.set(ValueLayout.JAVA_FLOAT, local + CP_POS_OFFSET + 4, y);

                    // Pulse alpha to visualize which points are currently being updated
                    float alpha = 0.4f + 0.6f * (float) Math.abs(Math.sin(wavePhases[g] + localT * Math.PI * 2.0));
                    mem.set(ValueLayout.JAVA_FLOAT, local + CP_COLOR_OFFSET + 12, alpha);
                }
            }
        }

        // Drift wave group offsets and restore previously-displaced points to base positions
        if (frameCount % 60 == 0) {
            for (int g = 0; g < NUM_WAVE_GROUPS; g++) {
                int oldStart = waveOffsets[g] % POINT_COUNT;
                int oldEnd = Math.min(oldStart + POINTS_PER_GROUP, POINT_COUNT);
                int oldSize = oldEnd - oldStart;

                // Move the group
                waveOffsets[g] = (waveOffsets[g] + POINTS_PER_GROUP / 2) % POINT_COUNT;

                // Restore the old region to base spiral positions
                if (oldSize > 0) {
                    long oldByteOffset = (long) oldStart * CP_STRIDE;
                    long oldByteSize = (long) oldSize * CP_STRIDE;

                    try (BufferWriteScope scope = controlPointBuffer.acquireWrite(oldByteOffset, oldByteSize, queue)) {
                        MemorySegment mem = scope.segment();
                        for (int i = 0; i < oldSize; i++) {
                            int idx = oldStart + i;
                            float t = (float) idx / POINT_COUNT;
                            float baseRadius = 0.05f + t * 0.85f;
                            float angle = t * (float) (Math.PI * 2.0 * 24.0);

                            float x = baseRadius * (float) Math.cos(angle);
                            float y = baseRadius * (float) Math.sin(angle);

                            long local = (long) i * CP_STRIDE;
                            mem.set(ValueLayout.JAVA_FLOAT, local + CP_POS_OFFSET, x);
                            mem.set(ValueLayout.JAVA_FLOAT, local + CP_POS_OFFSET + 4, y);
                            mem.set(ValueLayout.JAVA_FLOAT, local + CP_COLOR_OFFSET + 12, 1.0f);
                        }
                    }
                }
            }
        }
    }

    /**
     * Flushes dirty ranges to the GPU and records statistics about the transfer.
     */
    private void flushAndRecordStats() {
        DirtyStrategy dirty = controlPointBuffer.cpuDirtyStrategy();
        if (!dirty.isDirty()) {
            lastFlushStats = "no dirty regions";
            return;
        }

        int regionCount = dirty.dirtyRegionCount();
        long totalBytes = 0;
        DirtyRegionIterator it = dirty.dirtyRegions();
        while (it.hasNext()) {
            it.next();
            totalBytes += it.size();
        }

        // Flush: this issues one vkCmdCopyBuffer with regionCount regions
        controlPointBuffer.flushDirty(queue);

        float pct = (float) totalBytes / BUF_SIZE * 100.0f;
        lastFlushStats = String.format("dirty: %d regions, %d bytes (%.1f%% of buffer)",
                regionCount, totalBytes, pct);
    }

    @Override
    protected void onDraw(VkCommandBuffer commandBuffer, SegmentAllocator frameAllocator) {
        descriptorSet.bind(commandBuffer, pipeline, 0, frameAllocator);
    }

    @Override
    protected int vertexCount() {
        return POINT_COUNT;
    }

    @Override
    protected void cleanupResources() {
        super.cleanupResources();
        if (descriptorPool != null) descriptorPool.close();
        if (descriptorLayout != null) descriptorLayout.close();
        if (controlPointBuffer != null) controlPointBuffer.close();
    }

    // -- Simple HSV hue to RGB helpers --

    private static float hueToR(float h) {
        return hueComponent(h, 0.0f);
    }

    private static float hueToG(float h) {
        return hueComponent(h, 1.0f / 3.0f);
    }

    private static float hueToB(float h) {
        return hueComponent(h, 2.0f / 3.0f);
    }

    private static float hueComponent(float h, float offset) {
        float k = (h + offset) % 1.0f;
        if (k < 0) k += 1.0f;
        if (k < 1.0f / 6.0f) return k * 6.0f;
        if (k < 0.5f) return 1.0f;
        if (k < 2.0f / 3.0f) return (2.0f / 3.0f - k) * 6.0f;
        return 0.0f;
    }
}
