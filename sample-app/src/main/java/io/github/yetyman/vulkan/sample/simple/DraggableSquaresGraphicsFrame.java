package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDescriptorPool;
import io.github.yetyman.vulkan.VkDescriptorSet;
import io.github.yetyman.vulkan.VkDescriptorSetLayout;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPipeline;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.command.VkPushConstantsCmd;
import io.github.yetyman.vulkan.enums.VkDescriptorType;
import io.github.yetyman.vulkan.enums.VkFormat;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.shaders.ShaderLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class DraggableSquaresGraphicsFrame extends SimpleGraphicsFrame {

    public static final int NUM_SQUARES = 1_000;
    public static final int SQUARE_SIZE = 80;
    public static final float CORNER_RADIUS = 12.0f;

    private static final int FLOATS_PER_SQ = 6; // x, y, r, g, b, radius
    private static final long BUF_SIZE = (long) NUM_SQUARES * FLOATS_PER_SQ * Float.BYTES;
    private static final int SLICES_PER_SQ = 9;
    private static final int PC_SIZE = 8;
    private static final float SELECT_BRIGHTEN = 0.25f;
    private static final float DRAG_BRIGHTEN = 0.15f; // on top of select

    private VkBuffer squareBuf;
    private VkDescriptorSetLayout descLayout;
    private VkDescriptorPool descPool;
    private VkDescriptorSet descSet;

    // GPU-facing square data: x, y, r, g, b, radius
    private final float[] squareData = new float[NUM_SQUARES * FLOATS_PER_SQ];
    // Base colors, never modified after init — source of truth for color
    private final float[] baseColors = new float[NUM_SQUARES * 3]; // r, g, b per square

    // Per-square dirty flags — set on input thread, cleared on render thread
    private final boolean[] dirty = new boolean[NUM_SQUARES];

    // Selection and drag state — written on input thread, read on render thread
    private final Set<Integer> selectedSquares = new HashSet<>();
    private final AtomicInteger hoveredSquare = new AtomicInteger(-1);
    private final AtomicInteger primaryDrag = new AtomicInteger(-1); // square the mouse is anchored to

    // Drag offset relative to the primaryDrag square's top-left
    private volatile float dragOffsetX, dragOffsetY;
    // Last mouse position during drag, for computing delta
    private volatile double lastDragX, lastDragY;
    private volatile boolean dragging = false;

    public DraggableSquaresGraphicsFrame(Arena arena, VkDevice device, VkQueue queue,
                                         MemorySegment surface, int width, int height) {
        super(arena, device, queue, surface, width, height, 3);
    }

    @Override
    protected void initializeResources(int queueFamilyIndex) {
        squareBuf = VkBuffer.builder()
                .device(device)
                .size(BUF_SIZE)
                .storageBuffer()
                .hostVisible()
                .build(arena);

        initSquareData();
        uploadAllSquares();

        int vertStage = VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value();
        descLayout = VkDescriptorSetLayout.builder()
                .device(device)
                .storageBuffer(0, vertStage)
                .build(arena);

        descPool = VkDescriptorPool.builder()
                .device(device)
                .maxSets(1)
                .storageBuffers(1)
                .build(arena);

        descSet = descPool.allocateDescriptorSet(descLayout);
        try (Arena tmp = Arena.ofConfined()) {
            descSet.updateBuffer(0,
                    VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER.value(),
                    squareBuf.handle(), 0, BUF_SIZE, tmp);
        }

        super.initializeResources(queueFamilyIndex);
    }

    private void initSquareData() {
        Random rng = new Random(42);
        int margin = 20;
        placeSquare(0, margin, margin, rng);
        placeSquare(1, width - margin - SQUARE_SIZE, margin, rng);
        placeSquare(2, margin, height - margin - SQUARE_SIZE, rng);
        placeSquare(3, width - margin - SQUARE_SIZE, height - margin - SQUARE_SIZE, rng);
        for (int i = 4; i < NUM_SQUARES; i++) {
            float x = rng.nextFloat() * (width - SQUARE_SIZE);
            float y = rng.nextFloat() * (height - SQUARE_SIZE);
            placeSquare(i, x, y, rng);
        }
    }

    private void placeSquare(int i, float x, float y, Random rng) {
        float r = rng.nextFloat() * 0.7f + 0.2f;
        float g = rng.nextFloat() * 0.7f + 0.2f;
        float b = rng.nextFloat() * 0.7f + 0.2f;
        baseColors[i * 3] = r;
        baseColors[i * 3 + 1] = g;
        baseColors[i * 3 + 2] = b;
        int base = i * FLOATS_PER_SQ;
        squareData[base] = x;
        squareData[base + 1] = y;
        squareData[base + 2] = r;
        squareData[base + 3] = g;
        squareData[base + 4] = b;
        squareData[base + 5] = CORNER_RADIUS;
    }

    /**
     * Recomputes display color for square i from base color + selection/drag state and marks dirty.
     */
    private void refreshColor(int i) {
        boolean selected = selectedSquares.contains(i);
        boolean dragged = dragging && selectedSquares.contains(i);
        float brighten = (selected ? SELECT_BRIGHTEN : 0f) + (dragged ? DRAG_BRIGHTEN : 0f);
        int base = i * FLOATS_PER_SQ;
        squareData[base + 2] = Math.min(1f, baseColors[i * 3] + brighten);
        squareData[base + 3] = Math.min(1f, baseColors[i * 3 + 1] + brighten);
        squareData[base + 4] = Math.min(1f, baseColors[i * 3 + 2] + brighten);
        dirty[i] = true;
    }

    private void uploadAllSquares() {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment mapped = squareBuf.map(tmp);
            for (int i = 0; i < squareData.length; i++)
                mapped.setAtIndex(ValueLayout.JAVA_FLOAT, i, squareData[i]);
            squareBuf.unmap();
        }
    }

    private void uploadDirtySquares() {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment mapped = squareBuf.map(tmp);
            for (int i = 0; i < NUM_SQUARES; i++) {
                if (!dirty[i]) continue;
                int base = i * FLOATS_PER_SQ;
                long byteOff = (long) base * Float.BYTES;
                for (int f = 0; f < FLOATS_PER_SQ; f++)
                    mapped.set(ValueLayout.JAVA_FLOAT, byteOff + (long) f * Float.BYTES, squareData[base + f]);
                dirty[i] = false;
            }
            squareBuf.unmap();
        }
    }

    @Override
    protected void onResize(int newWidth, int newHeight) {
    }

    @Override
    protected VkPipeline createPipeline() {
        VkPipeline.Builder builder = VkPipeline.builder()
                .device(device)
                .vertexShader(ShaderLoader.builder("/shaders/squares.vert").compile())
                .fragmentShader(ShaderLoader.builder("/shaders/squares.frag").compile())
                .triangleTopology()
                .dynamicViewport()
                .dynamicScissor()
                .alphaBlend()
                .descriptorSetLayouts(descLayout.handle())
                .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, PC_SIZE);

        if (useDynamicRendering) {
            builder.dynamicRendering(0, VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value());
        } else {
            builder.renderPass(renderPass.handle());
        }

        return builder.build(arena);
    }

    @Override
    protected void onDraw(VkCommandBuffer commandBuffer, Arena frameArena) {
        uploadDirtySquares();

        descSet.bind(commandBuffer, pipeline, 0, frameArena);

        MemorySegment pc = frameArena.allocate(PC_SIZE);
        pc.set(ValueLayout.JAVA_FLOAT, 0, (float) width);
        pc.set(ValueLayout.JAVA_FLOAT, 4, (float) height);
        VkPushConstantsCmd.pushConstants(commandBuffer, pipeline.layout(),
                VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, pc, PC_SIZE);
    }

    @Override
    protected int vertexCount() {
        return 6;
    }

    @Override
    protected int instanceCount() {
        return NUM_SQUARES * SLICES_PER_SQ;
    }

    // --- Input handling (called from GLFW callback thread) ---

    public int hitTest(double mx, double my) {
        for (int i = NUM_SQUARES - 1; i >= 0; i--) {
            int base = i * FLOATS_PER_SQ;
            if (hitsSquare(mx, my, squareData[base], squareData[base + 1], SQUARE_SIZE, CORNER_RADIUS))
                return i;
        }
        return -1;
    }

    private static boolean hitsSquare(double mx, double my, float sx, float sy, float size, float r) {
        double lx = mx - sx, ly = my - sy;
        if (lx < 0 || lx >= size || ly < 0 || ly >= size) return false;
        boolean inCornerCol = lx < r || lx >= size - r;
        boolean inCornerRow = ly < r || ly >= size - r;
        if (!inCornerCol || !inCornerRow) return true;
        double arcX = lx < r ? r : size - r;
        double arcY = ly < r ? r : size - r;
        double dx = lx - arcX, dy = ly - arcY;
        return dx * dx + dy * dy < (double) r * r;
    }

    public void onMouseMove(double mx, double my) {
        if (dragging) {
            double dx = mx - lastDragX;
            double dy = my - lastDragY;
            lastDragX = mx;
            lastDragY = my;
            for (int i : selectedSquares) {
                int base = i * FLOATS_PER_SQ;
                squareData[base] += (float) dx;
                squareData[base + 1] += (float) dy;
                dirty[i] = true;
            }
        } else {
            hoveredSquare.set(hitTest(mx, my));
        }
    }

    /**
     * @param additive true when Ctrl or Shift is held
     */
    public void onMousePress(double mx, double my, boolean additive) {
        int hit = hitTest(mx, my);

        if (hit < 0) {
            // Clicked empty space — clear selection unless additive
            if (!additive) {
                Set<Integer> prev = new HashSet<>(selectedSquares);
                selectedSquares.clear();
                for (int i : prev) refreshColor(i);
            }
            return;
        }

        if (!additive) {
            // Clear all previously selected squares that aren't the new hit
            Set<Integer> prev = new HashSet<>(selectedSquares);
            prev.remove(hit);
            selectedSquares.clear();
            for (int i : prev) refreshColor(i);
        }

        // Add hit to selection if not already there
        if (selectedSquares.add(hit)) {
            refreshColor(hit);
        }

        // Begin drag for all selected squares, anchored to the hit square
        int base = hit * FLOATS_PER_SQ;
        dragOffsetX = (float) (mx - squareData[base]);
        dragOffsetY = (float) (my - squareData[base + 1]);
        lastDragX = mx;
        lastDragY = my;
        primaryDrag.set(hit);
        dragging = true;
        hoveredSquare.set(-1);

        // Refresh colors to show drag brightness on all selected
        for (int i : selectedSquares) refreshColor(i);
    }

    /**
     * @param additive true when Ctrl or Shift is held
     */
    public void onMouseRelease(double mx, double my, boolean additive) {
        dragging = false;
        primaryDrag.set(-1);

        if (!additive) {
            // Deselect all
            Set<Integer> prev = new HashSet<>(selectedSquares);
            selectedSquares.clear();
            for (int i : prev) refreshColor(i);
        } else {
            // Keep selection, just remove drag brightness
            for (int i : selectedSquares) refreshColor(i);
        }

        hoveredSquare.set(hitTest(mx, my));
    }

    public boolean isInteracting() {
        return hoveredSquare.get() >= 0 || dragging;
    }

    @Override
    protected void cleanupResources() {
        super.cleanupResources();
        if (descPool != null) descPool.close();
        if (descLayout != null) descLayout.close();
        if (squareBuf != null) squareBuf.close();
    }
}
