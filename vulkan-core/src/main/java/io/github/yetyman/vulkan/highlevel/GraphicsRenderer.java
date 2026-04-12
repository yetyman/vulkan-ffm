package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import java.lang.foreign.*;

/**
 * Base renderer providing common Vulkan rendering pipeline functionality.
 * Handles swapchain, render pass, command buffers, and synchronization.
 * 
 * Thread Safety: This class is NOT thread-safe. All methods must be called from the same thread.
 * Subclasses should document their own thread safety guarantees.
 * 
 * Resource Lifecycle:
 * 1. Construction - stores references, no Vulkan resources allocated
 * 2. init() - creates all Vulkan resources (swapchain, render pass, sync objects)
 * 3. drawFrame() - renders frames, uses frame-local Arena for temporary allocations
 * 4. resize() - recreates swapchain and framebuffers
 * 5. close() - destroys all Vulkan resources in reverse order
 */
public abstract class GraphicsRenderer implements AutoCloseable {
    
    protected final Arena arena;
    protected final VkDevice device;
    protected final MemorySegment queue;
    protected final MemorySegment surface;
    protected int width, height;

    /** When true, skips VkRenderPass/VkFramebuffer creation and uses dynamic rendering instead. */
    protected final boolean useDynamicRendering;
    /** MSAA sample count. VK_SAMPLE_COUNT_1_BIT = no MSAA. */
    protected final int sampleCount;

    // Core Vulkan objects
    protected VkSwapchain swapchain;
    protected VkImageView[] swapchainImageViews;
    protected VkRenderPass renderPass;       // null when useDynamicRendering = true
    protected VkFramebuffer[] framebuffers;  // null when useDynamicRendering = true
    protected VkCommandPool commandPool;
    protected VkCommandBuffer[] commandBuffers;
    // Semaphores are private: both are keyed by swapchain image index (imgIdx), NOT frame-in-flight.
    // The present engine holds them until the image retires; subclasses must never index by currentFrame.
    private VkSemaphore[] imageAvailableSemaphores;
    private VkSemaphore[] renderFinishedSemaphores;
    protected VkFence[] inFlightFences;

    // imageAvailableSemaphores[i] is the semaphore last used to acquire swapchain image i.
    // acquireSemaphorePool holds one extra semaphore used as the "next" acquire semaphore
    // before we know which image index will be returned.
    private VkSemaphore acquireSemaphorePool;
    private int currentFrame = 0;
    private final int maxFramesInFlight;

    // Per-frame ring arenas: one per frame-in-flight, reset at the start of each frame.
    // Subclasses access the current frame's arena via frameArena().
    private Arena[] frameArenas;
    private Arena currentFrameArena;

    /**
     * Optional lock acquired around vkQueueSubmit and vkQueuePresentKHR.
     * Set this when the graphics queue handle is shared with another thread (e.g. a compute worker)
     * to prevent simultaneous queue access violations.
     */
    private java.util.concurrent.locks.Lock queueLock = null;

    /** Sets a lock to be held during queue submit and present. */
    public void setQueueLock(java.util.concurrent.locks.Lock lock) { this.queueLock = lock; }

    protected GraphicsRenderer(Arena arena, VkDevice device, MemorySegment queue,
                          MemorySegment surface, int width, int height, int maxFramesInFlight) {
        this(arena, device, queue, surface, width, height, maxFramesInFlight,
            VulkanCapabilities.dynamicRendering);
    }

    protected GraphicsRenderer(Arena arena, VkDevice device, MemorySegment queue,
                          MemorySegment surface, int width, int height, int maxFramesInFlight,
                          boolean useDynamicRendering) {
        this(arena, device, queue, surface, width, height, maxFramesInFlight, useDynamicRendering,
            VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value());
    }

    protected GraphicsRenderer(Arena arena, VkDevice device, MemorySegment queue,
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
        swapchain = VkSwapchain.create(arena, device, surface, width, height);
    }
    
    private void createImageViews() {
        MemorySegment[] images = swapchain.getImages();
        swapchainImageViews = new VkImageView[images.length];
        for (int i = 0; i < images.length; i++) {
            swapchainImageViews[i] = VkImageView.builder()
                .device(device)
                .image(images[i])
                .viewType(VkImageViewType.VK_IMAGE_VIEW_TYPE_2D.value())
                .format(VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value())
                .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value())
                .build(arena);
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
        // Both acquire and render-finished semaphores are per-swapchain-image:
        // the present engine holds them until the image is retired, so they must
        // not be reused until that image is re-acquired.
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
        frameArenas = new Arena[maxFramesInFlight];
        for (int i = 0; i < maxFramesInFlight; i++) {
            frameArenas[i] = Arena.ofConfined();
        }
    }

    /** @return the arena for the current frame-in-flight. Valid from fence-wait through present. */
    protected Arena frameArena() { return currentFrameArena; }

    /** @return the current frame-in-flight index (0..maxFramesInFlight-1). */
    protected int currentFrame() { return currentFrame; }

    /** @return the renderFinished semaphore handle for the given swapchain image index. */
    protected MemorySegment renderFinishedSemaphoreHandle(int imgIdx) {
        return renderFinishedSemaphores[imgIdx].handle();
    }

    public void drawFrame() {
        currentFrameArena = frameArenas[currentFrame];
        currentFrameArena.close();
        frameArenas[currentFrame] = Arena.ofConfined();
        currentFrameArena = frameArenas[currentFrame];

        VkFenceOps.waitFor(device)
            .fence(inFlightFences[currentFrame].handle())
            .execute(currentFrameArena).check();
        VkFenceOps.waitFor(device)
            .fence(inFlightFences[currentFrame].handle())
            .reset(currentFrameArena).check();

        // Acquire using the pool semaphore. After we learn imgIdx, swap it with
        // imageAvailableSemaphores[imgIdx]. The swapped-out semaphore is now safe
        // to reuse next frame because re-acquiring that image proves its prior
        // present has retired and the semaphore is no longer in use by the swapchain.
        int imgIdx = VkSwapchainOps.acquireNextImage(device, swapchain.handle())
            .semaphore(acquireSemaphorePool.handle())
            .execute(currentFrameArena);

        // Swap: acquireSemaphorePool ↔ imageAvailableSemaphores[imgIdx]
        VkSemaphore justSignaled = acquireSemaphorePool;
        acquireSemaphorePool = imageAvailableSemaphores[imgIdx];
        imageAvailableSemaphores[imgIdx] = justSignaled;

        recordCommandBuffer(commandBuffers[currentFrame], imgIdx, currentFrameArena);

        if (queueLock != null) queueLock.lock();
        try {
            VkSubmit.builder()
                .waitSemaphore(imageAvailableSemaphores[imgIdx].handle(),
                              VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value())
                .commandBuffer(commandBuffers[currentFrame])
                .signalSemaphore(renderFinishedSemaphores[imgIdx].handle())
                .submit(queue, inFlightFences[currentFrame].handle(), currentFrameArena).check();

            VkPresent.builder()
                .waitSemaphore(renderFinishedSemaphores[imgIdx].handle())
                .swapchain(swapchain.handle(), imgIdx)
                .present(queue, currentFrameArena);
        } finally {
            if (queueLock != null) queueLock.unlock();
        }

        currentFrame = (currentFrame + 1) % maxFramesInFlight;
    }
    
    public final void resize(int newWidth, int newHeight) {
        for (VkFramebuffer framebuffer : framebuffers != null ? framebuffers : new VkFramebuffer[0]) {
            framebuffer.close();
        }
        for (VkImageView imageView : swapchainImageViews) {
            imageView.close();
        }
        swapchain.close();

        width = newWidth;
        height = newHeight;

        createSwapchain();
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
        for (int i = 0; i < swapchainImageViews.length; i++) {
            imageAvailableSemaphores[i] = VkSemaphore.create(arena, device);
            renderFinishedSemaphores[i] = VkSemaphore.create(arena, device);
        }
        acquireSemaphorePool = VkSemaphore.create(arena, device);
    }
    
    @Override
    public final void close() {
        for (VkSemaphore sem : imageAvailableSemaphores) sem.close();
        for (VkSemaphore sem : renderFinishedSemaphores) sem.close();
        acquireSemaphorePool.close();
        for (int i = 0; i < maxFramesInFlight; i++) {
            inFlightFences[i].close();
        }
        if (frameArenas != null) {
            for (Arena a : frameArenas) a.close();
        }
        commandPool.close();
        if (framebuffers != null) {
            for (VkFramebuffer framebuffer : framebuffers) framebuffer.close();
        }
        if (renderPass != null) renderPass.close();
        for (VkImageView imageView : swapchainImageViews) imageView.close();
        swapchain.close();
        cleanupResources();
    }
    
    // Abstract methods for subclasses
    protected abstract void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex, Arena frameArena);

    // Optional hooks
    /**
     * Called to create the render pass. Only called when useDynamicRendering = false.
     * Override when using the render pass path.
     */
    protected VkRenderPass createRenderPassImpl() {
        throw new UnsupportedOperationException(
            getClass().getSimpleName() + " must override createRenderPassImpl() when not using dynamic rendering. Dynamic rendering may not be available on older devices");
    }
    /**
     * Called to create each framebuffer. Only called when useDynamicRendering = false.
     * Override when using the render pass path.
     */
    protected VkFramebuffer createFramebufferImpl(int imageIndex) {
        throw new UnsupportedOperationException(
            getClass().getSimpleName() + " must override createFramebufferImpl() when not using dynamic rendering. Dynamic rendering may not be available on older devices");
    }
    protected void initializeResources(int queueFamilyIndex) {}
    protected void postRenderPassInit() {}
    protected void onResize(int width, int height) {}
    protected void cleanupResources() {}
}