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
public abstract class BaseRenderer implements AutoCloseable {
    
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
    protected VkSemaphore[] imageAvailableSemaphores;
    protected VkSemaphore[] renderFinishedSemaphores;
    protected VkFence[] inFlightFences;

    private int currentFrame = 0;
    private final int maxFramesInFlight;

    protected BaseRenderer(Arena arena, VkDevice device, MemorySegment queue,
                          MemorySegment surface, int width, int height, int maxFramesInFlight) {
        this(arena, device, queue, surface, width, height, maxFramesInFlight,
            VulkanCapabilities.dynamicRendering);
    }

    protected BaseRenderer(Arena arena, VkDevice device, MemorySegment queue,
                          MemorySegment surface, int width, int height, int maxFramesInFlight,
                          boolean useDynamicRendering) {
        this(arena, device, queue, surface, width, height, maxFramesInFlight, useDynamicRendering,
            VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value());
    }

    protected BaseRenderer(Arena arena, VkDevice device, MemorySegment queue,
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
        imageAvailableSemaphores = new VkSemaphore[maxFramesInFlight];
        renderFinishedSemaphores = new VkSemaphore[maxFramesInFlight];
        inFlightFences = new VkFence[maxFramesInFlight];
        
        for (int i = 0; i < maxFramesInFlight; i++) {
            imageAvailableSemaphores[i] = VkSemaphore.create(arena, device);
            renderFinishedSemaphores[i] = VkSemaphore.create(arena, device);
            inFlightFences[i] = VkFence.create(arena, device, true);
        }
    }
    
    public void drawFrame() {
        try (Arena frameArena = Arena.ofConfined()) {
            VkFenceOps.waitFor(device)
                .fence(inFlightFences[currentFrame].handle())
                .execute(frameArena).check();
            VkFenceOps.waitFor(device)
                .fence(inFlightFences[currentFrame].handle())
                .reset(frameArena).check();

            int imgIdx = VkSwapchainOps.acquireNextImage(device, swapchain.handle())
                .semaphore(imageAvailableSemaphores[currentFrame].handle())
                .execute(frameArena);

            recordCommandBuffer(commandBuffers[currentFrame], imgIdx, frameArena);

            VkSubmit.builder()
                .waitSemaphore(imageAvailableSemaphores[currentFrame].handle(),
                              VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value())
                .commandBuffer(commandBuffers[currentFrame])
                .signalSemaphore(renderFinishedSemaphores[currentFrame].handle())
                .submit(queue, inFlightFences[currentFrame].handle(), frameArena).check();

            VkPresent.builder()
                .waitSemaphore(renderFinishedSemaphores[currentFrame].handle())
                .swapchain(swapchain.handle(), imgIdx)
                .present(queue, frameArena);

            currentFrame = (currentFrame + 1) % maxFramesInFlight;
        }
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
        onResize(newWidth, newHeight);
        if (!useDynamicRendering) createFramebuffers();
    }
    
    @Override
    public final void close() {
        for (int i = 0; i < maxFramesInFlight; i++) {
            imageAvailableSemaphores[i].close();
            renderFinishedSemaphores[i].close();
            inFlightFences[i].close();
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