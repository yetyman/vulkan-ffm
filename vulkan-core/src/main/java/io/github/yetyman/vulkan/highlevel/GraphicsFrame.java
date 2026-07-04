package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.util.BumpAllocator;

import java.lang.foreign.*;

/**
 * Manages the Vulkan frame execution engine: swapchain, render pass, command buffers,
 * synchronization, and per-frame acquire/record/submit/present cycle.
 * <p>
 * Subclasses implement {@link #recordCommandBuffer} to provide per-frame draw logic.
 */
public abstract class GraphicsFrame implements AutoCloseable {

    protected final Arena arena;
    protected final VkDevice device;
    protected final VkQueue queue;
    protected final MemorySegment surface;
    protected int width, height;

    /**
     * When true, skips VkRenderPass/VkFramebuffer creation and uses dynamic rendering instead.
     */
    protected final boolean useDynamicRendering;
    /**
     * MSAA sample count. VK_SAMPLE_COUNT_1_BIT = no MSAA.
     */
    protected final int sampleCount;

    protected VkSwapchain swapchain;
    protected VkImageView[] swapchainImageViews;
    protected VkRenderPass renderPass;
    protected VkFramebuffer[] framebuffers;
    protected VkCommandPool commandPool;
    protected VkCommandBuffer[] commandBuffers;

    private VkSemaphore[] imageAvailableSemaphores;
    private VkSemaphore[] renderFinishedSemaphores;
    private VkSemaphore acquireSemaphorePool;
    private int currentFrame = 0;
    private final int maxFramesInFlight;
    // tracks which frame-slot last submitted work for each swapchain image index; -1 = never used
    private int[] imageLastFrame;

    private final java.util.List<TimelineWait> timelineWaits = new java.util.ArrayList<>();

    private record TimelineWait(VkTimelineSemaphore semaphore, int stageMask) {}

    private Arena[] frameArenas;
    private Arena currentFrameArena;

    protected VkFence[] inFlightFences;

    private io.github.yetyman.vulkan.loop.FrameMetrics metrics;

    /** When true, frame arenas are Arena.ofShared() — required for parallel command recording.
     *  When false (default), Arena.ofConfined() — much cheaper FFM session validation. */
    private boolean sharedFrameArenas = false;

    // Cached submit/present structs — built lazily on first drawFrame() call (after all
    // addTimelineWait() calls have completed) and patched per frame instead of rebuilt.
    // Allocated from a dedicated confined arena created on the render thread (not the
    // constructor's arena field, which is owned by whichever thread constructed this
    // GraphicsFrame and is typically NOT the render thread that drives drawFrame()).
    private Arena syncStructArena;
    private VkSubmit.Builder.Cached[] cachedSubmits;
    private VkPresent.Builder.Cached cachedPresent;

    protected GraphicsFrame(Arena arena, VkDevice device, VkQueue queue,
                            MemorySegment surface, int width, int height, int maxFramesInFlight) {
        this(arena, device, queue, surface, width, height, maxFramesInFlight,
                VulkanCapabilities.dynamicRendering);
    }

    protected GraphicsFrame(Arena arena, VkDevice device, VkQueue queue,
                            MemorySegment surface, int width, int height, int maxFramesInFlight,
                            boolean useDynamicRendering) {
        this(arena, device, queue, surface, width, height, maxFramesInFlight, useDynamicRendering,
                VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value());
    }

    protected GraphicsFrame(Arena arena, VkDevice device, VkQueue queue,
                            MemorySegment surface, int width, int height, int maxFramesInFlight,
                            boolean useDynamicRendering, int sampleCount) {
        this.arena = arena;
        this.device = device;
        this.queue = queue;
        this.surface = surface;
        this.width = width;
        this.height = height;
        this.maxFramesInFlight = maxFramesInFlight;
        this.useDynamicRendering = useDynamicRendering;
        this.sampleCount = sampleCount;
    }

    public final void init(int queueFamilyIndex) {
        createSwapchain();
        createImageViews();
        if (!useDynamicRendering) createRenderPass();
        initializeResources(queueFamilyIndex);
        if (!useDynamicRendering) createFramebuffers();
        createCommandPool(queueFamilyIndex);
        createCommandBuffers();
        createSyncObjects();
        postRenderPassInit();
    }

    private void createSwapchain() {
        try (Arena tmp = Arena.ofConfined()) {
            swapchain = VkSwapchain.create(tmp, device, surface, width, height);
        }
    }

    private void createImageViews() {
        MemorySegment[] images = swapchain.getImages();
        swapchainImageViews = new VkImageView[images.length];
        try (Arena tmp = Arena.ofConfined()) {
            for (int i = 0; i < images.length; i++) {
                swapchainImageViews[i] = VkImageView.builder()
                        .device(device)
                        .image(images[i])
                        .viewType(VkImageViewType.VK_IMAGE_VIEW_TYPE_2D.value())
                        .format(VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value())
                        .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value())
                        .build(tmp);
            }
        }
    }

    private void createRenderPass() {
        renderPass = createRenderPassImpl();
    }

    private void createFramebuffers() {
        framebuffers = new VkFramebuffer[swapchainImageViews.length];
        for (int i = 0; i < swapchainImageViews.length; i++) {
            framebuffers[i] = createFramebufferImpl(i);
        }
    }

    private void createCommandPool(int queueFamilyIndex) {
        commandPool = VkCommandPool.builder()
                .device(device)
                .queueFamilyIndex(queueFamilyIndex)
                .resetCommandBufferBit()
                .build(arena);
    }

    private void createCommandBuffers() {
        commandBuffers = VkCommandBufferAlloc.builder()
                .device(device)
                .commandPool(commandPool.handle())
                .primary()
                .count(maxFramesInFlight)
                .allocate(arena);
    }

    private void createSyncObjects() {
        imageAvailableSemaphores = new VkSemaphore[swapchainImageViews.length];
        renderFinishedSemaphores = new VkSemaphore[swapchainImageViews.length];
        inFlightFences = new VkFence[maxFramesInFlight];

        for (int i = 0; i < swapchainImageViews.length; i++) {
            imageAvailableSemaphores[i] = VkSemaphore.create(arena, device);
            renderFinishedSemaphores[i] = VkSemaphore.create(arena, device);
        }
        acquireSemaphorePool = VkSemaphore.create(arena, device);
        for (int i = 0; i < maxFramesInFlight; i++) {
            inFlightFences[i] = VkFence.create(arena, device, true);
        }
        imageLastFrame = new int[swapchainImageViews.length];
        java.util.Arrays.fill(imageLastFrame, -1);
        frameArenas = new Arena[maxFramesInFlight];
        // Note: arena allocation deferred to first drawFrame call so confined arenas
        // are created on the rendering thread, not the construction thread.
    }

    /**
     * @return the arena for the current frame-in-flight, creating it lazily on first access.
     * Valid from fence-wait through present, then closed at the start of the next frame in
     * this slot. Most command recording should prefer {@link #frameAllocator()} instead —
     * this is only needed when something must outlive a single Vulkan call within the frame
     * (e.g. cross-thread parallel command recording via RenderGraphExecutor).
     */
    protected Arena frameArena() {
        if (currentFrameArena == null) {
            currentFrameArena = sharedFrameArenas ? Arena.ofShared() : Arena.ofConfined();
            frameArenas[currentFrame] = currentFrameArena;
        }
        return currentFrameArena;
    }

    /**
     * @return a thread-local scratch allocator for the current frame's command recording.
     * Backed by {@link BumpAllocator} — allocations made through this allocator during
     * {@link #recordCommandBuffer} (and everything it calls, including {@code beforeRenderPass}
     * and {@code onDraw} on subclasses) are freed in bulk when recording completes, with no
     * native allocation on the hot path. Do not retain the returned allocator or any segment
     * obtained from it beyond the current {@link #recordCommandBuffer} call.
     */
    protected SegmentAllocator frameAllocator() {
        return BumpAllocator.get();
    }

    /**
     * @return the current frame-in-flight index (0..maxFramesInFlight-1).
     */
    protected int currentFrame() {
        return currentFrame;
    }

    /** @return the maximum number of frames-in-flight (swapchain depth). */
    public int maxFramesInFlight() {
        return maxFramesInFlight;
    }

    /**
     * Attach a {@link io.github.yetyman.vulkan.loop.FrameMetrics} for per-stage stamps and
     * slot-based input-to-display latency tracking. The metrics object's slot ring is
     * configured to {@code maxFramesInFlight} automatically.
     */
    public void metrics(io.github.yetyman.vulkan.loop.FrameMetrics metrics) {
        this.metrics = metrics;
        if (metrics != null) {
            metrics.configureSlots(maxFramesInFlight);
        }
    }

    /** @return the attached frame metrics, or null if none */
    public io.github.yetyman.vulkan.loop.FrameMetrics metrics() {
        return metrics;
    }

    /**
     * Enables shared (cross-thread) per-frame arenas. Required if any node records commands
     * from worker threads (e.g. {@link io.github.yetyman.vulkan.graph.RenderGraphExecutor}
     * with parallel recording enabled). Default is confined (single-thread, much faster
     * FFM session validation). Must be set before the first {@link #drawFrame()} call.
     */
    public void sharedFrameArenas(boolean shared) {
        this.sharedFrameArenas = shared;
    }

    /**
     * @return the renderFinished semaphore handle for the given swapchain image index.
     */
    protected MemorySegment renderFinishedSemaphoreHandle(int imgIdx) {
        return renderFinishedSemaphores[imgIdx].handle();
    }

    /**
     * @return the device this frame executes on.
     */
    public VkDevice device() {
        return device;
    }

    /**
     * @return the graphics queue this frame submits to.
     */
    public VkQueue graphicsQueue() {
        return queue;
    }

    /**
     * Adds a GPU-side wait on a timeline semaphore to every graphics submit.
     * The current counter value is sampled at submit time each frame.
     * Call before the render loop starts.
     *
     * @param semaphore the timeline semaphore to wait on
     * @param stageMask the pipeline stage at which to wait
     */
    public void addTimelineWait(VkTimelineSemaphore semaphore, int stageMask) {
        timelineWaits.add(new TimelineWait(semaphore, stageMask));
    }

    public void drawFrame() {
        if (swapchain == null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        if (cachedSubmits == null) {
            buildCachedSubmitAndPresent();
        }
        BumpAllocator ba = BumpAllocator.get();
        ba.push();
        try {
            currentFrameArena = frameArenas[currentFrame];
            if (currentFrameArena != null) {
                currentFrameArena.close();
                frameArenas[currentFrame] = null;
                currentFrameArena = null;
            }

            if (metrics != null) metrics.beginFrame();

            VkFenceOps.waitSingleCritical(device, inFlightFences[currentFrame].handle()).check();
            VkFenceOps.resetSingle(device, inFlightFences[currentFrame].handle()).check();

            if (metrics != null) {
                metrics.stamp(io.github.yetyman.vulkan.loop.FrameMetrics.Stage.FENCE_WAIT_END);
                // Frame at this slot just completed -> compute its true latency and stash this frame's input
                metrics.onSlotReady(currentFrame);
            }

            int imgIdx = VkSwapchainOps.acquireNextImage(device, swapchain.handle())
                    .semaphore(acquireSemaphorePool.handle())
                    .executeCritical(ba);

            VkSemaphore justSignaled = acquireSemaphorePool;
            // imageAvailableSemaphores[imgIdx] was signaled by the previous acquire of this image.
            // It is only safe to reuse once the frame that waited on it has completed.
            int lastFrame = imageLastFrame[imgIdx];
            if (lastFrame >= 0 && lastFrame != currentFrame) {
                VkFenceOps.waitSingle(device, inFlightFences[lastFrame].handle()).check();
            }
            acquireSemaphorePool = imageAvailableSemaphores[imgIdx];
            imageAvailableSemaphores[imgIdx] = justSignaled;

            recordCommandBuffer(commandBuffers[currentFrame], imgIdx, ba);
            if (metrics != null) metrics.stamp(io.github.yetyman.vulkan.loop.FrameMetrics.Stage.RECORD_END);

            VkSubmit.Builder.Cached submitCached = cachedSubmits[currentFrame];
            submitCached.patchWaitSemaphore(0, imageAvailableSemaphores[imgIdx].handle());
            submitCached.patchSignalSemaphore(0, renderFinishedSemaphores[imgIdx].handle());
            for (int i = 0; i < timelineWaits.size(); i++) {
                TimelineWait w = timelineWaits.get(i);
                long val = w.semaphore().completedGeneration();
                // wait index 0 is the image-available binary semaphore; timeline waits follow
                submitCached.patchWaitTimelineValue(i + 1, Math.max(val, 0));
            }
            submitCached.submit(queue.handle(), inFlightFences[currentFrame].handle());
            if (metrics != null) metrics.stamp(io.github.yetyman.vulkan.loop.FrameMetrics.Stage.SUBMIT_END);

            imageLastFrame[imgIdx] = currentFrame;

            cachedPresent.patchWaitSemaphore(0, renderFinishedSemaphores[imgIdx].handle());
            cachedPresent.patchSwapchain(0, swapchain.handle());
            cachedPresent.patchImageIndex(0, imgIdx);
            cachedPresent.present(queue.handle());
            if (metrics != null) {
                metrics.stamp(io.github.yetyman.vulkan.loop.FrameMetrics.Stage.PRESENT_END);
                metrics.endFrame();
            }
        } finally {
            ba.pop();
        }

        currentFrame = (currentFrame + 1) % maxFramesInFlight;
    }

    /**
     * Builds the per-frame-slot cached VkSubmitInfo structs and the shared cached
     * VkPresentInfoKHR struct. Called once, lazily, on the first {@link #drawFrame()} call —
     * by this point all {@link #addTimelineWait} calls have completed (they must be made
     * before the render loop starts), so the submit struct's shape (wait/signal/timeline
     * slot counts) is final.
     *
     * Timeline wait slots are always present in the cached struct even if a timeline
     * semaphore's counter is still 0 -- waiting for counter >= 0 is a no-op wait, identical
     * in behavior to omitting the wait, so no conditional slot count is needed.
     */
    private void buildCachedSubmitAndPresent() {
        syncStructArena = Arena.ofConfined();
        cachedSubmits = new VkSubmit.Builder.Cached[maxFramesInFlight];
        int colorAttachmentOutput = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value();
        for (int i = 0; i < maxFramesInFlight; i++) {
            VkSubmit.Builder b = VkSubmit.builder()
                    .waitSemaphore(MemorySegment.NULL, colorAttachmentOutput)
                    .commandBuffer(commandBuffers[i])
                    .signalSemaphore(MemorySegment.NULL);
            for (TimelineWait w : timelineWaits) {
                b.waitTimelineSemaphore(w.semaphore(), 0, w.stageMask());
            }
            cachedSubmits[i] = b.buildAndCache(syncStructArena);
        }
        cachedPresent = VkPresent.builder()
                .waitSemaphore(MemorySegment.NULL)
                .swapchain(MemorySegment.NULL, 0)
                .buildAndCache(syncStructArena);
    }

    public final void resize(int newWidth, int newHeight) {
        Vulkan.queueWaitIdle(queue.handle()).check();
        doResize(newWidth, newHeight);
    }

    private void doResize(int newWidth, int newHeight) {
        for (VkFramebuffer framebuffer : framebuffers != null ? framebuffers : new VkFramebuffer[0])
            framebuffer.close();
        if (swapchainImageViews != null) for (VkImageView imageView : swapchainImageViews) imageView.close();
        if (swapchain != null) swapchain.close();

        width = newWidth;
        height = newHeight;

        try {
            createSwapchain();
        } catch (IllegalStateException e) {
            swapchain = null;
            swapchainImageViews = null;
            return;
        }
        width = swapchain.width();
        height = swapchain.height();
        createImageViews();
        recreateImageAvailableSemaphores();
        onResize(newWidth, newHeight);
        if (!useDynamicRendering) createFramebuffers();
    }

    private void recreateImageAvailableSemaphores() {
        for (VkSemaphore sem : imageAvailableSemaphores) sem.close();
        for (VkSemaphore sem : renderFinishedSemaphores) sem.close();
        acquireSemaphorePool.close();
        imageAvailableSemaphores = new VkSemaphore[swapchainImageViews.length];
        renderFinishedSemaphores = new VkSemaphore[swapchainImageViews.length];
        imageLastFrame = new int[swapchainImageViews.length];
        java.util.Arrays.fill(imageLastFrame, -1);
        try (Arena tmp = Arena.ofConfined()) {
            for (int i = 0; i < swapchainImageViews.length; i++) {
                imageAvailableSemaphores[i] = VkSemaphore.create(tmp, device);
                renderFinishedSemaphores[i] = VkSemaphore.create(tmp, device);
            }
            acquireSemaphorePool = VkSemaphore.create(tmp, device);
        }
    }

    @Override
    public final void close() {
        Vulkan.queueWaitIdle(queue.handle()).check();
        for (VkSemaphore sem : imageAvailableSemaphores) sem.close();
        for (VkSemaphore sem : renderFinishedSemaphores) sem.close();
        acquireSemaphorePool.close();
        for (int i = 0; i < maxFramesInFlight; i++) {
            inFlightFences[i].close();
        }
        if (frameArenas != null) {
            for (Arena a : frameArenas) {
                if (a != null) {
                    try { a.close(); } catch (Throwable ignored) { /* may be confined to another thread */ }
                }
            }
        }
        if (syncStructArena != null) {
            try { syncStructArena.close(); } catch (Throwable ignored) { /* may be confined to another thread */ }
        }
        commandPool.close();
        if (framebuffers != null) {
            for (VkFramebuffer framebuffer : framebuffers) framebuffer.close();
        }
        if (renderPass != null) renderPass.close();
        if (swapchainImageViews != null) for (VkImageView imageView : swapchainImageViews) imageView.close();
        if (swapchain != null) swapchain.close();
        cleanupResources();
    }

    protected abstract void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex, SegmentAllocator frameAllocator);

    protected VkRenderPass createRenderPassImpl() {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " must override createRenderPassImpl() when not using dynamic rendering.");
    }

    protected VkFramebuffer createFramebufferImpl(int imageIndex) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " must override createFramebufferImpl() when not using dynamic rendering.");
    }

    protected void initializeResources(int queueFamilyIndex) {
    }

    protected void postRenderPassInit() {
    }

    protected void onResize(int width, int height) {
    }

    protected void cleanupResources() {
    }
}
