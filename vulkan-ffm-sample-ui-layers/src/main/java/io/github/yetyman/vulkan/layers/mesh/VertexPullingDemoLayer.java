package io.github.yetyman.vulkan.layers.mesh;

import io.github.yetyman.helpers.math.Mat4;
import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPipeline;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferFactory;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.BufferWriteScope;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.command.VkDraw;
import io.github.yetyman.vulkan.command.VkSetState;
import io.github.yetyman.vulkan.enums.VkCompareOp;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.highlevel.VulkanContext;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.ComponentType;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.IndexStream;
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

import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdBindPipeline;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdPushConstants;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

/**
 * A sample {@link UILayer} demonstrating the vertex-pulling path with an oct-encoded normal
 * attribute that has no {@code VkFormat} and cannot be bound through {@code vkCmdBindVertexBuffers}.
 *
 * <p>This proves that shader-decoded attributes are genuinely first-class in the mesh module
 * rather than merely tolerated. The positions are stored as F32x3 in an SSBO, normals as
 * oct16-packed uint per vertex (2 bytes per normal, decoded in the shader), and indices in a
 * separate SSBO. No vertex input is used at all.</p>
 *
 * <p>This is the Phase 4 vertex-pulling validation sample described in the roadmap.</p>
 */
public class VertexPullingDemoLayer implements UILayer {

    private static final int ORDER = 51;

    /**
     * Oct16: a packed format with no VkFormat. Two signed 16-bit values (octahedral encoding)
     * packed into 4 bytes. The shader decodes them.
     */
    public static final AttributeFormat OCT16 = AttributeFormat.packed("oct16", 4);

    /** Layout: positions F32x3 in stream 0, oct16 normals in stream 1. */
    public static final MeshLayout LAYOUT = MeshLayout.builder()
            .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
            .stream(1).attribute(AttributeSemantic.NORMAL, OCT16)
            .build();

    private UIContext ctx;
    private VkDevice device;
    private VkQueue queue;
    private Arena layerArena;

    private VkPipeline pipeline;
    private DescriptorGroup descriptorGroup;
    private IBuffer positionBuffer;
    private IBuffer normalBuffer;
    private IBuffer indexBuffer;
    private int indexCount;
    private int width;
    private int height;
    private long startTime;

    private GeometrySource source;

    /**
     * @param source the geometry to render via vertex pulling
     */
    public VertexPullingDemoLayer(GeometrySource source) {
        this.source = source;
    }

    @Override
    public String name() {
        return "VertexPullingDemo";
    }

    @Override
    public int order() {
        return ORDER;
    }

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

        long vertexCount = source.elementCount();
        IndexStream idxStream = source.indices().orElseThrow(
                () -> new IllegalArgumentException("VertexPullingDemoLayer requires indexed geometry"));
        this.indexCount = (int) idxStream.indexCount();

        // Allocate SSBOs
        long posSize = vertexCount * 12; // F32x3
        long normalSize = vertexCount * 4; // oct16 = 4 bytes per vertex
        long idxSize = (long) indexCount * 4; // U32

        positionBuffer = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, posSize,
                BufferUsage.STORAGE, device, queue);
        normalBuffer = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, normalSize,
                BufferUsage.STORAGE, device, queue);
        indexBuffer = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, idxSize,
                BufferUsage.STORAGE, device, queue);

        // Upload positions
        MeshLayout posLayout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .build();
        try (BufferWriteScope scope = positionBuffer.acquireWrite(0, posSize, queue)) {
            source.stream(AttributeSemantic.POSITION).transcodeInto(
                    posLayout, scope.segment(), 0, 12, 0, vertexCount);
        }

        // Upload normals: convert from source F32x3 to oct16
        uploadOctEncodedNormals(vertexCount);

        // Upload indices
        try (BufferWriteScope scope = indexBuffer.acquireWrite(0, idxSize, queue)) {
            idxStream.transcodeInto(IndexWidth.U32, 0, scope.segment(), 0, 0, indexCount);
        }

        // Build pipeline (no vertex input at all)
        CompiledShader vert = ShaderLoader.compileShader("/shaders/mesh_pulling.vert");
        CompiledShader frag = ShaderLoader.compileShader("/shaders/mesh_pulling.frag");

        descriptorGroup = DescriptorGroup.builder()
                .device(device)
                .stageFlags(VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value())
                .storageBuffer(0, positionBuffer)
                .storageBuffer(1, normalBuffer)
                .storageBuffer(2, indexBuffer)
                .build(layerArena);

        pipeline = VkPipeline.builder()
                .device(device)
                .vertexShader(vert.getSpirV())
                .fragmentShader(frag.getSpirV())
                .dynamicViewport()
                .dynamicScissor()
                .depthTest(true)
                .depthCompareOp(VkCompareOp.VK_COMPARE_OP_LESS.value())
                .descriptorSetLayouts(descriptorGroup.layoutHandle())
                .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, 72)
                .dynamicRendering(
                        io.github.yetyman.vulkan.enums.VkFormat.VK_FORMAT_D32_SFLOAT.value(),
                        io.github.yetyman.vulkan.enums.VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value())
                .build(layerArena);

        source = null; // release reference, data is uploaded
    }

    @Override
    public void update(UIFrameContext frame) {
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        vkCmdBindPipeline(cmd.handle(), 0, pipeline.handle());
        VkSetState.setViewport(cmd, 0, 0, width, height);
        VkSetState.setScissor(cmd, 0, 0, width, height);

        descriptorGroup.set().bind(cmd.handle(), pipeline, 0, frameArena);

        // Push constants: mat4 mvp (64 bytes) + uint vertexBase (4) + uint indexBase (4) = 72
        float time = (System.nanoTime() - startTime) / 1_000_000_000f;
        Mat4 mvp = computeMVP(time);

        MemorySegment push = frameArena.allocate(72);
        mvp.writeTo(push, 0);
        push.set(JAVA_INT_UNALIGNED, 64, 0); // vertexBase
        push.set(JAVA_INT_UNALIGNED, 68, 0); // indexBase

        vkCmdPushConstants(cmd.handle(), pipeline.layout(),
                VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, 72, push);

        // Draw: index count vertices (shader pulls from SSBO via gl_VertexIndex)
        VkDraw.draw(cmd.handle(), indexCount, 1, 0, 0);
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
        if (descriptorGroup != null) descriptorGroup.close();
        if (pipeline != null) pipeline.close();
        if (positionBuffer != null) positionBuffer.close();
        if (normalBuffer != null) normalBuffer.close();
        if (indexBuffer != null) indexBuffer.close();
        if (layerArena != null) layerArena.close();
    }

    private void uploadOctEncodedNormals(long vertexCount) {
        // Read normals as F32x3 then encode to oct16
        MeshLayout nrmLayout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .build();

        try (Arena tempArena = Arena.ofConfined()) {
            long nrmStride = 12;
            MemorySegment normals = tempArena.allocate(nrmStride * vertexCount);
            source.stream(AttributeSemantic.NORMAL).transcodeInto(
                    nrmLayout, normals, 0, nrmStride, 0, vertexCount);

            try (BufferWriteScope scope = normalBuffer.acquireWrite(0, vertexCount * 4, queue)) {
                MemorySegment dst = scope.segment();
                for (long v = 0; v < vertexCount; v++) {
                    long srcOff = v * nrmStride;
                    float nx = normals.get(JAVA_FLOAT_UNALIGNED, srcOff);
                    float ny = normals.get(JAVA_FLOAT_UNALIGNED, srcOff + 4);
                    float nz = normals.get(JAVA_FLOAT_UNALIGNED, srcOff + 8);

                    // Octahedral encode
                    int packed = octEncode(nx, ny, nz);
                    dst.set(JAVA_INT_UNALIGNED, v * 4, packed);
                }
            }
        }
    }

    /**
     * Encodes a unit normal into oct16 (two snorm16 packed into a uint).
     */
    private static int octEncode(float nx, float ny, float nz) {
        // Project onto octahedron
        float len = Math.abs(nx) + Math.abs(ny) + Math.abs(nz);
        if (len < 1e-8f) len = 1.0f;
        float ox = nx / len;
        float oy = ny / len;

        // Wrap for lower hemisphere
        if (nz < 0.0f) {
            float tmpX = (1.0f - Math.abs(oy)) * (ox >= 0.0f ? 1.0f : -1.0f);
            float tmpY = (1.0f - Math.abs(ox)) * (oy >= 0.0f ? 1.0f : -1.0f);
            ox = tmpX;
            oy = tmpY;
        }

        // Quantize to signed 16-bit
        int ix = Math.max(-32767, Math.min(32767, Math.round(ox * 32767.0f)));
        int iy = Math.max(-32767, Math.min(32767, Math.round(oy * 32767.0f)));

        // Pack as two unsigned 16-bit values (the shader sign-extends)
        return (ix & 0xFFFF) | ((iy & 0xFFFF) << 16);
    }

    private Mat4 computeMVP(float time) {
        float aspect = (float) width / Math.max(height, 1);
        float fov = (float) Math.toRadians(60);
        float near = 0.1f, far = 100f;
        float tanHalf = (float) Math.tan(fov / 2);

        Mat4 proj = new Mat4();
        proj.m00 = 1f / (aspect * tanHalf);
        proj.m11 = -1f / tanHalf;
        proj.m22 = far / (near - far);
        proj.m32 = (near * far) / (near - far);
        proj.m23 = -1f;
        proj.m33 = 0f;

        Mat4 view = new Mat4();
        view.m32 = -4.0f;

        float angle = time * 0.5f;
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        Mat4 model = new Mat4();
        model.m00 = c;  model.m20 = s;
        model.m02 = -s; model.m22 = c;

        float tilt = 0.4f;
        float ct = (float) Math.cos(tilt);
        float st = (float) Math.sin(tilt);
        Mat4 tiltMat = new Mat4();
        tiltMat.m11 = ct; tiltMat.m21 = -st;
        tiltMat.m12 = st; tiltMat.m22 = ct;

        return proj.mulNew(view).mul(tiltMat).mul(model);
    }
}
