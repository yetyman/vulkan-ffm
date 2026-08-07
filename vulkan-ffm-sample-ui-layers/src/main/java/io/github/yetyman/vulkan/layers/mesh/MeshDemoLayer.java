package io.github.yetyman.vulkan.layers.mesh;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPipeline;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.command.VkBind;
import io.github.yetyman.vulkan.command.VkDraw;
import io.github.yetyman.vulkan.command.VkDrawIndexed;
import io.github.yetyman.vulkan.command.VkSetState;
import io.github.yetyman.vulkan.enums.VkIndexType;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.highlevel.VkVertexFormat;
import io.github.yetyman.vulkan.highlevel.VulkanContext;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.Mesh;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.consume.GeometryBinding;
import io.github.yetyman.vulkan.mesh.consume.GeometryDrawRange;
import io.github.yetyman.vulkan.mesh.residency.DedicatedAllocator;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.shaders.CompiledShader;
import io.github.yetyman.vulkan.shaders.ShaderLoader;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.ui.UIFrameContext;
import io.github.yetyman.vulkan.ui.UILayer;
import io.github.yetyman.vulkan.ui.input.InputEvent;

import io.github.yetyman.helpers.math.Mat4;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdBindPipeline;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdPushConstants;

/**
 * A sample {@link UILayer} that renders any number of meshes from user-supplied
 * {@link GeometrySource}s through the mesh module's full pipeline.
 *
 * <p>Usage:
 * <pre>{@code
 * MeshDemoLayer meshLayer = new MeshDemoLayer();
 * meshLayer.addSource(new BoxSource(arena));
 * meshLayer.addSource(new SphereSource(arena, 0.5f, 16, 32));
 * meshLayer.addSource(new MeshOutputSource(marchingCubesResult));
 * // ... add to UIComposite, which calls initialize/render/etc.
 * }</pre>
 *
 * <p>Sources added before {@link #initialize} are uploaded during initialization. Sources added
 * after initialization are uploaded immediately.
 *
 * <p>Each source is placed at a different X position so they are visible side by side.
 */
public class MeshDemoLayer implements UILayer {

    private static final int ORDER = 50;

    /** The shared layout all sources are uploaded into. Position + Normal + UV, interleaved. */
    public static final MeshLayout LAYOUT = MeshLayout.builder()
            .stream(0)
            .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
            .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
            .attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
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

    private DedicatedAllocator allocator;
    private VkPipeline pipeline;
    private long startTime;
    private int width;
    private int height;

    private final List<GeometrySource> pendingSources = new ArrayList<>();
    private final List<Mesh> meshes = new ArrayList<>();
    private boolean initialized = false;

    /**
     * Adds a geometry source to be rendered. If the layer is already initialized, the mesh is
     * uploaded immediately; otherwise it is queued for initialization time.
     */
    public void addSource(GeometrySource source) {
        if (!initialized) {
            pendingSources.add(source);
        } else {
            meshes.add(uploadSource(source));
        }
    }

    @Override
    public String name() {
        return "MeshDemo";
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

        allocator = new DedicatedAllocator(device, queue, MemoryStrategy.DEVICE_LOCAL);

        // Build the pipeline
        VkVertexFormat vertexFormat = LAYOUT.toVertexFormat(LOCATIONS);
        CompiledShader vert = ShaderLoader.compileShader("/shaders/mesh_sample.vert");
        CompiledShader frag = ShaderLoader.compileShader("/shaders/mesh_sample.frag");

        VkPipeline.Builder pipelineBuilder = VkPipeline.builder()
                .device(device)
                .vertexShader(vert.getSpirV())
                .fragmentShader(frag.getSpirV())
                .dynamicViewport()
                .dynamicScissor()
                .cullMode(0) // VK_CULL_MODE_NONE - show all faces for arbitrary geometry
                .dynamicRendering(0, io.github.yetyman.vulkan.enums.VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value())
                .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, 64);

        var vertInput = pipelineBuilder.vertexInput();
        for (var binding : vertexFormat.getBindings()) {
            vertInput.binding(binding.binding(), binding.stride(), binding.inputRate());
        }
        for (var attr : vertexFormat.getAttributes()) {
            vertInput.attribute(attr.location(), attr.binding(), attr.format(), attr.offset());
        }
        vertInput.build();

        pipeline = pipelineBuilder.build(layerArena);

        // Upload any sources that were added before initialization
        for (GeometrySource source : pendingSources) {
            meshes.add(uploadSource(source));
        }
        pendingSources.clear();
        initialized = true;
    }

    @Override
    public void update(UIFrameContext frame) {
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        if (meshes.isEmpty()) return;

        vkCmdBindPipeline(cmd.handle(), 0, pipeline.handle());
        VkSetState.setViewport(cmd, 0, 0, width, height);
        VkSetState.setScissor(cmd, 0, 0, width, height);

        float time = (System.nanoTime() - startTime) / 1_000_000_000f;
        float spacing = 2.0f;
        float totalWidth = (meshes.size() - 1) * spacing;
        float startX = -totalWidth / 2f;

        for (int i = 0; i < meshes.size(); i++) {
            Mesh mesh = meshes.get(i);
            float xOffset = startX + i * spacing;

            Mat4 mvp = computeMVP(time, xOffset);
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
                int vkIdxType = switch (binding.indexWidth()) {
                    case U16 -> VkIndexType.VK_INDEX_TYPE_UINT16.value();
                    case U32 -> VkIndexType.VK_INDEX_TYPE_UINT32.value();
                    default -> VkIndexType.VK_INDEX_TYPE_UINT32.value();
                };
                VkBind.bindIndexBuffer(cmd.handle(), binding.indexBufferHandle().get(),
                        binding.indexBufferOffset(), vkIdxType);
                VkDrawIndexed.drawIndexed(cmd.handle(), draw.indexCount(), 1,
                        draw.firstIndex(), draw.vertexOffset(), 0);
            } else {
                VkDraw.draw(cmd.handle(), draw.indexCount(), 1, draw.firstIndex(), 0);
            }
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
        for (Mesh mesh : meshes) mesh.close();
        meshes.clear();
        if (allocator != null) allocator.close();
        if (pipeline != null) pipeline.close();
        if (layerArena != null) layerArena.close();
    }

    private Mesh uploadSource(GeometrySource source) {
        return Mesh.builder()
                .source(source)
                .layout(LAYOUT)
                .allocator(allocator)
                .queue(queue)
                .build();
    }

    private Mat4 computeMVP(float time, float xOffset) {
        float angle = time * 0.7f;
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        Mat4 model = new Mat4();
        model.m00 = c;  model.m20 = s;
        model.m02 = -s; model.m22 = c;
        model.m30 = xOffset;

        float tilt = 0.3f;
        float ct = (float) Math.cos(tilt);
        float st = (float) Math.sin(tilt);
        Mat4 tiltMat = new Mat4();
        tiltMat.m11 = ct; tiltMat.m21 = -st;
        tiltMat.m12 = st; tiltMat.m22 = ct;

        Mat4 view = new Mat4();
        view.m32 = -5.0f;

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

        return proj.mulNew(view).mul(tiltMat).mul(model);
    }
}
