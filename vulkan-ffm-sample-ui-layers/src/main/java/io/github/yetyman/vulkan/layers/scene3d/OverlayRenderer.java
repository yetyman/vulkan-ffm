package io.github.yetyman.vulkan.layers.scene3d;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPipeline;
import io.github.yetyman.vulkan.buffers.BufferFactory;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.command.VkBind;
import io.github.yetyman.vulkan.command.VkDraw;
import io.github.yetyman.vulkan.command.VkPushConstantsCmd;
import io.github.yetyman.vulkan.enums.VkCompareOp;
import io.github.yetyman.vulkan.enums.VkFormat;
import io.github.yetyman.vulkan.enums.VkPipelineBindPoint;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.enums.VkVertexInputRate;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.shaders.ShaderInstance;
import io.github.yetyman.vulkan.shaders.ShaderLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Vulkan rendering backend for 3D overlay primitives (debug lines, gizmos, annotations).
 *
 * Four pipeline variants, all sharing the same vertex/fragment shaders and vertex format
 * (OverlayVertex: vec3 pos, vec4 color = 28 bytes), differing only in topology and depth test:
 *   - line, depth-tested   (occluded by scene geometry)
 *   - line, always-on-top  (ignores depth, drawn over everything)
 *   - triangle, depth-tested
 *   - triangle, always-on-top
 *
 * Vertex data for all four is uploaded into two MAPPED buffers (one for lines, one for
 * triangles) re-written each frame - overlay vertex counts are small (hundreds to low
 * thousands) so a full re-upload per frame is simpler and fast enough, avoiding the
 * bookkeeping of a persistent vertex buffer with partial updates.
 *
 * Push constant: view-projection matrix (mat4, 64 bytes), vertex stage.
 *
 * Requires dynamic rendering (VulkanCapabilities.dynamicRendering), consistent with
 * TextRenderer's scope decision for this minimal layer set - render-pass-based path is not
 * implemented.
 *
 * Note: this renderer does not itself read or write a depth buffer - the DEPTH_TESTED variant
 * pipelines have depth test/write enabled, but actually occluding overlay geometry against
 * scene depth requires the caller's render pass/dynamic rendering setup to attach a shared
 * depth image. In the minimal example app (no 3D scene depth buffer), the DEPTH_TESTED and
 * ALWAYS_ON_TOP pipelines behave identically since there is no scene depth to test against.
 */
public class OverlayRenderer implements AutoCloseable {

    private static final int INITIAL_VERTEX_CAPACITY = 4096;

    private final UIContext ctx;
    private final VkDevice device;

    private ShaderInstance vertShader;
    private ShaderInstance fragShader;

    private VkPipeline lineDepthTestedPipeline;
    private VkPipeline lineOnTopPipeline;
    private VkPipeline triDepthTestedPipeline;
    private VkPipeline triOnTopPipeline;

    private IBuffer lineVertexBuffer;
    private IBuffer triVertexBuffer;
    private int lineVertexCapacity;
    private int triVertexCapacity;

    public OverlayRenderer(UIContext ctx) {
        this.ctx = ctx;
        this.device = ctx.vulkan().device();
    }

    public void initialize() {
        if (!VulkanCapabilities.dynamicRendering) {
            throw new IllegalStateException(
                "OverlayRenderer requires dynamic rendering support - renderPass-based path not implemented (minimal scene3d overlay scope)");
        }

        vertShader = ShaderLoader.load("/shaders/overlay.vert", device);
        fragShader = ShaderLoader.load("/shaders/overlay.frag", device);

        lineDepthTestedPipeline = buildPipeline(true, true);
        lineOnTopPipeline = buildPipeline(true, false);
        triDepthTestedPipeline = buildPipeline(false, true);
        triOnTopPipeline = buildPipeline(false, false);

        allocateLineBuffer(INITIAL_VERTEX_CAPACITY);
        allocateTriBuffer(INITIAL_VERTEX_CAPACITY);
    }

    private VkPipeline buildPipeline(boolean lines, boolean depthTested) {
        VkPipeline.Builder builder = VkPipeline.builder()
            .device(device)
            .vertexShader(vertShader)
            .fragmentShader(fragShader)
            .dynamicViewport()
            .dynamicScissor()
            .dynamicRendering(0, VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value())
            .vertexInput()
                .binding(0, OverlayVertex.SIZE_BYTES, VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX.value())
                .attribute(0, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT.value(), 0)
                .attribute(1, 0, VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT.value(), 12)
                .build();

        if (lines) {
            builder.lineTopology();
        } else {
            builder.triangleTopology();
        }

        if (depthTested) {
            builder.depthTest(true).depthWrite(true).depthCompareOp(VkCompareOp.VK_COMPARE_OP_LESS_OR_EQUAL.value());
        }

        return builder.build(ctx.applicationArena());
    }

    /**
     * Tessellates and renders the accumulated overlay draw list: depth-tested primitives first,
     * then always-on-top primitives, lines before triangles within each depth group.
     */
    public void render(VkCommandBuffer cmd, Arena frameArena, OverlayDrawList drawList, float[] viewProjection) {
        if (drawList.isEmpty()) return;

        List<OverlayVertex> depthTestedLines = drawList.depthTestedLines();
        List<OverlayVertex> onTopLines = drawList.onTopLines();
        List<OverlayVertex> depthTestedTris = drawList.depthTestedTris();
        List<OverlayVertex> onTopTris = drawList.onTopTris();

        int totalLineVerts = depthTestedLines.size() + onTopLines.size();
        int totalTriVerts = depthTestedTris.size() + onTopTris.size();
        ensureLineCapacity(totalLineVerts);
        ensureTriCapacity(totalTriVerts);

        uploadCombined(lineVertexBuffer, depthTestedLines, onTopLines);
        uploadCombined(triVertexBuffer, depthTestedTris, onTopTris);

        MemorySegment vp = frameArena.allocate(64);
        for (int i = 0; i < 16; i++) {
            vp.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, i * 4L, viewProjection[i]);
        }

        if (!depthTestedLines.isEmpty()) {
            drawSegment(cmd, frameArena, lineDepthTestedPipeline, lineVertexBuffer, vp, 0, depthTestedLines.size());
        }
        if (!depthTestedTris.isEmpty()) {
            drawSegment(cmd, frameArena, triDepthTestedPipeline, triVertexBuffer, vp, 0, depthTestedTris.size());
        }
        if (!onTopLines.isEmpty()) {
            drawSegment(cmd, frameArena, lineOnTopPipeline, lineVertexBuffer, vp, depthTestedLines.size(), onTopLines.size());
        }
        if (!onTopTris.isEmpty()) {
            drawSegment(cmd, frameArena, triOnTopPipeline, triVertexBuffer, vp, depthTestedTris.size(), onTopTris.size());
        }
    }

    private void drawSegment(VkCommandBuffer cmd, Arena frameArena, VkPipeline pipeline,
                              IBuffer vertexBuffer, MemorySegment viewProjection, int firstVertex, int vertexCount) {
        VkBind.bindPipeline(cmd.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), pipeline.handle());
        VkPushConstantsCmd.pushConstants(cmd.handle(), pipeline.layout(),
            VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, viewProjection, 64);
        VkBind.bindVertexBuffers(cmd.handle(), 0, vertexBuffer.handle(), 0);
        VkDraw.draw(cmd.handle(), vertexCount, 1, firstVertex, 0);
    }

    @Override
    public void close() {
        if (lineDepthTestedPipeline != null) lineDepthTestedPipeline.close();
        if (lineOnTopPipeline != null) lineOnTopPipeline.close();
        if (triDepthTestedPipeline != null) triDepthTestedPipeline.close();
        if (triOnTopPipeline != null) triOnTopPipeline.close();
        if (vertShader != null) vertShader.close();
        if (fragShader != null) fragShader.close();
        if (lineVertexBuffer != null) lineVertexBuffer.close();
        if (triVertexBuffer != null) triVertexBuffer.close();
    }

    private void allocateLineBuffer(int capacity) {
        if (lineVertexBuffer != null) lineVertexBuffer.close();
        lineVertexCapacity = capacity;
        lineVertexBuffer = BufferFactory.create(
            MemoryStrategy.MAPPED, null, (long) capacity * OverlayVertex.SIZE_BYTES,
            BufferUsage.VERTEX, device, ctx.vulkan().graphicsVkQueue());
    }

    private void allocateTriBuffer(int capacity) {
        if (triVertexBuffer != null) triVertexBuffer.close();
        triVertexCapacity = capacity;
        triVertexBuffer = BufferFactory.create(
            MemoryStrategy.MAPPED, null, (long) capacity * OverlayVertex.SIZE_BYTES,
            BufferUsage.VERTEX, device, ctx.vulkan().graphicsVkQueue());
    }

    private void ensureLineCapacity(int required) {
        if (required <= lineVertexCapacity) return;
        int newCapacity = Math.max(lineVertexCapacity, 1);
        while (newCapacity < required) newCapacity *= 2;
        allocateLineBuffer(newCapacity);
    }

    private void ensureTriCapacity(int required) {
        if (required <= triVertexCapacity) return;
        int newCapacity = Math.max(triVertexCapacity, 1);
        while (newCapacity < required) newCapacity *= 2;
        allocateTriBuffer(newCapacity);
    }

    private void uploadCombined(IBuffer buffer, List<OverlayVertex> first, List<OverlayVertex> second) {
        int total = first.size() + second.size();
        if (total == 0) return;
        ByteBuffer buf = ByteBuffer.allocate(total * OverlayVertex.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        writeVertices(buf, first);
        writeVertices(buf, second);
        buf.flip();
        buffer.write(buf, 0, ctx.vulkan().graphicsVkQueue());
    }

    private void writeVertices(ByteBuffer buf, List<OverlayVertex> vertices) {
        for (OverlayVertex v : vertices) {
            buf.putFloat(v.x()).putFloat(v.y()).putFloat(v.z());
            buf.putFloat(v.r()).putFloat(v.g()).putFloat(v.b()).putFloat(v.a());
        }
    }
}
