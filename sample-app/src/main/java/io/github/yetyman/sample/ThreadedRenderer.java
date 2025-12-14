package io.github.yetyman.sample;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

public class ThreadedRenderer {
    public enum Mode { BEST_EFFICIENCY, BEST_PERFORMANCE, ADAPTIVE }
    
    private static final int MAX_FRAMES_IN_FLIGHT = 2;
    private static final int TRIANGLES_COUNT = 1000; // Back to 1000 triangles with depth testing
    
    private final Arena arena;
    private final MemorySegment device;
    private final MemorySegment queue;
    private final MemorySegment surface;
    private int width, height;
    
    private VkSwapchain swapchain;
    private VkImageView[] swapchainImageViews;
    private MemorySegment depthImage;
    private VkImageView depthImageView;
    private VkRenderPass renderPass;
    private VkFramebuffer[] framebuffers;
    private VkPipeline pipeline;
    private VkCommandPool mainCommandPool;
    private MemorySegment[] commandBuffers;
    private VkSemaphore[] imageAvailableSemaphores;
    private VkSemaphore[] renderFinishedSemaphores;
    private VkFence[] inFlightFences;
    
    // Threading
    private ThreadPoolExecutor executor;
    private final ConcurrentHashMap<Thread, VkCommandPool> threadPools = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Thread, Arena> threadArenas = new ConcurrentHashMap<>();
    private Mode mode = Mode.ADAPTIVE;
    private final AtomicInteger currentThreadCount = new AtomicInteger(1);
    
    // Performance tracking
    private long lastFrameTime = System.nanoTime();
    private final ArrayDeque<Long> frameTimes = new ArrayDeque<>();
    private final int FRAME_HISTORY = 60;
    
    public ThreadedRenderer(Arena arena, MemorySegment device, MemorySegment queue, 
                           MemorySegment surface, int width, int height) {
        this.arena = arena;
        this.device = device;
        this.queue = queue;
        this.surface = surface;
        this.width = width;
        this.height = height;
        
        // Initialize thread pool
        int coreCount = Runtime.getRuntime().availableProcessors();
        executor = new ThreadPoolExecutor(1, coreCount, 60L, TimeUnit.SECONDS, 
            new LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, "VulkanWorker");
                t.setDaemon(true);
                return t;
            });
    }
    
    public void init(MemorySegment physicalDevice, int queueFamilyIndex) {
        this.physicalDevice = physicalDevice;
        createSwapchain(physicalDevice);
        createImageViews();
        createDepthResources(); // Create but don't use in render pass
        createRenderPass();
        createGraphicsPipeline();
        createFramebuffers();
        createMainCommandPool(queueFamilyIndex);
        createCommandBuffers();
        createSyncObjects();
        System.out.println("[OK] Threaded renderer initialized with " + TRIANGLES_COUNT + " triangles (depth buffer created but not used)");
    }
    
    private void createSwapchain(MemorySegment physicalDevice) {
        swapchain = VkSwapchain.create(arena, device, surface, width, height);
        System.out.println("[OK] Swapchain created with " + swapchain.getImages().length + " images");
    }
    
    private void createImageViews() {
        MemorySegment[] images = swapchain.getImages();
        swapchainImageViews = new VkImageView[images.length];
        for (int i = 0; i < images.length; i++) {
            swapchainImageViews[i] = VkImageView.builder()
                .device(device)
                .image(images[i])
                .viewType(VkImageViewType.VK_IMAGE_VIEW_TYPE_2D)
                .format(VkFormat.VK_FORMAT_B8G8R8A8_SRGB)
                .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT)
                .build(arena);
        }
        System.out.println("[OK] Image views created");
    }
    
    private MemorySegment physicalDevice; // Need this for memory type queries
    
    private void createDepthResources() {
        System.out.println("[DEBUG] Creating depth image...");
        // Create depth image
        MemorySegment imageInfo = VkImageCreateInfo.allocate(arena);
        VkImageCreateInfo.sType(imageInfo, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
        VkImageCreateInfo.imageType(imageInfo, VkImageType.VK_IMAGE_TYPE_2D);
        VkImageCreateInfo.format(imageInfo, VkFormat.VK_FORMAT_D24_UNORM_S8_UINT);
        MemorySegment extent = VkImageCreateInfo.extent(imageInfo);
        VkExtent3D.width(extent, width);
        VkExtent3D.height(extent, height);
        VkExtent3D.depth(extent, 1);
        VkImageCreateInfo.mipLevels(imageInfo, 1);
        VkImageCreateInfo.arrayLayers(imageInfo, 1);
        VkImageCreateInfo.samples(imageInfo, VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT);
        VkImageCreateInfo.tiling(imageInfo, VkImageTiling.VK_IMAGE_TILING_OPTIMAL);
        VkImageCreateInfo.usage(imageInfo, VkImageUsageFlagBits.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
        VkImageCreateInfo.sharingMode(imageInfo, VkSharingMode.VK_SHARING_MODE_EXCLUSIVE);
        VkImageCreateInfo.initialLayout(imageInfo, VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED);
        
        MemorySegment imagePtr = arena.allocate(ValueLayout.ADDRESS);
        System.out.println("[DEBUG] Calling vkCreateImage...");
        VulkanExtensions.createImage(device, imageInfo, imagePtr).check();
        depthImage = imagePtr.get(ValueLayout.ADDRESS, 0);
        System.out.println("[DEBUG] Image created, getting memory requirements...");
        
        // Allocate and bind memory
        MemorySegment memReqs = VkMemoryRequirements.allocate(arena);
        VulkanExtensions.getImageMemoryRequirements(device, depthImage, memReqs);
        System.out.println("[DEBUG] Memory requirements obtained, allocating memory...");
        
        MemorySegment allocInfo = VkMemoryAllocateInfo.allocate(arena);
        VkMemoryAllocateInfo.sType(allocInfo, VkStructureType.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
        VkMemoryAllocateInfo.allocationSize(allocInfo, VkMemoryRequirements.size(memReqs));
        // Use first available memory type
        int typeFilter = VkMemoryRequirements.memoryTypeBits(memReqs);
        int memoryTypeIndex = Integer.numberOfTrailingZeros(typeFilter);
        VkMemoryAllocateInfo.memoryTypeIndex(allocInfo, memoryTypeIndex);
        
        MemorySegment memoryPtr = arena.allocate(ValueLayout.ADDRESS);
        System.out.println("[DEBUG] Calling vkAllocateMemory...");
        VulkanExtensions.allocateMemory(device, allocInfo, memoryPtr).check();
        MemorySegment depthMemory = memoryPtr.get(ValueLayout.ADDRESS, 0);
        System.out.println("[DEBUG] Memory allocated, binding to image...");
        
        VulkanExtensions.bindImageMemory(device, depthImage, depthMemory, 0).check();
        System.out.println("[DEBUG] Memory bound, creating image view...");
        
        // Create depth image view
        depthImageView = VkImageView.builder()
            .device(device)
            .image(depthImage)
            .viewType(VkImageViewType.VK_IMAGE_VIEW_TYPE_2D)
            .format(VkFormat.VK_FORMAT_D24_UNORM_S8_UINT)
            .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT)
            .build(arena);
        System.out.println("[OK] Depth resources created");
    }
    
    private void createRenderPass() {
        renderPass = VkRenderPass.builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_B8G8R8A8_SRGB, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
            .depthAttachment(VkFormat.VK_FORMAT_D24_UNORM_S8_UINT, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .subpassDependency(~0, 0, 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT, 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT,
                0, VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
            .build(arena);
        System.out.println("[OK] Render pass created with depth buffer");
    }
    
    private void createGraphicsPipeline() {
        byte[] vertShaderCode = ShaderLoader.compileShader("/shaders/triangle.vert");
        byte[] fragShaderCode = ShaderLoader.compileShader("/shaders/triangle.frag");
        
        pipeline = VkPipeline.builder()
            .device(device)
            .renderPass(renderPass.handle())
            .viewport(0, 0, width, height)
            .vertexShader(vertShaderCode)
            .fragmentShader(fragShaderCode)
            .triangleTopology()
            .dynamicViewport()
            .dynamicScissor()
            .build(arena);
        System.out.println("[OK] Graphics pipeline created");
    }
    
    private void createFramebuffers() {
        framebuffers = new VkFramebuffer[swapchainImageViews.length];
        for (int i = 0; i < swapchainImageViews.length; i++) {
            framebuffers[i] = VkFramebuffer.builder()
                .device(device)
                .renderPass(renderPass.handle())
                .attachment(swapchainImageViews[i].handle())
                .attachment(depthImageView.handle())
                .dimensions(width, height)
                .build(arena);
        }
        System.out.println("[OK] Framebuffers created with depth buffer");
    }
    
    private void createMainCommandPool(int queueFamilyIndex) {
        mainCommandPool = VkCommandPool.create(arena, device, queueFamilyIndex);
        System.out.println("[OK] Main command pool created");
    }
    
    private void createCommandBuffers() {
        commandBuffers = VkCommandBufferAlloc.builder()
            .device(device)
            .commandPool(mainCommandPool.handle())
            .primary()
            .count(MAX_FRAMES_IN_FLIGHT)
            .allocate(arena);
        System.out.println("[OK] Command buffers allocated");
    }
    
    private void createSyncObjects() {
        imageAvailableSemaphores = new VkSemaphore[MAX_FRAMES_IN_FLIGHT];
        renderFinishedSemaphores = new VkSemaphore[MAX_FRAMES_IN_FLIGHT];
        inFlightFences = new VkFence[MAX_FRAMES_IN_FLIGHT];
        
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            imageAvailableSemaphores[i] = VkSemaphore.create(arena, device);
            renderFinishedSemaphores[i] = VkSemaphore.create(arena, device);
            inFlightFences[i] = VkFence.create(arena, device, true);
        }
        System.out.println("[OK] Sync objects created");
    }
    
    private int findMemoryType(int typeFilter, int properties, MemorySegment memProps) {
        int memoryTypeCount = VkPhysicalDeviceMemoryProperties.memoryTypeCount(memProps);
        
        for (int i = 0; i < memoryTypeCount; i++) {
            if ((typeFilter & (1 << i)) != 0) {
                try {
                    MemorySegment memType = VkPhysicalDeviceMemoryProperties.memoryTypes(memProps, i);
                    int flags = VkMemoryType.propertyFlags(memType);
                    if ((flags & properties) == properties) {
                        return i;
                    }
                } catch (Exception e) {
                    // Skip this memory type if we can't read it
                    continue;
                }
            }
        }
        throw new RuntimeException("Failed to find suitable memory type");
    }
    
    private int currentFrame = 0;
    
    public void drawFrame() {
        long frameStart = System.nanoTime();
        
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
            
            if (currentThreadCount.get() == 1) {
                // Single-threaded: use normal submission
                recordCommandBufferThreaded(commandBuffers[currentFrame], imgIdx, frameArena);
                
                VkSubmit.builder()
                    .waitSemaphore(imageAvailableSemaphores[currentFrame].handle(), VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .commandBuffer(commandBuffers[currentFrame])
                    .signalSemaphore(renderFinishedSemaphores[currentFrame].handle())
                    .submit(queue, inFlightFences[currentFrame].handle(), frameArena).check();
            } else {
                // Multi-threaded: command buffers are submitted inside recordCommandBufferThreaded
                recordCommandBufferThreaded(commandBuffers[currentFrame], imgIdx, frameArena);
            }
            
            VkPresent.builder()
                .waitSemaphore(renderFinishedSemaphores[currentFrame].handle())
                .swapchain(swapchain.handle(), imgIdx)
                .present(queue, frameArena);
            
            currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
        }
        
        // Track performance and adjust threads
        trackPerformance(frameStart);
        adjustThreadCount();
    }
    
    private void recordCommandBufferThreaded(MemorySegment primaryCommandBuffer, int imageIndex, Arena frameArena) {
        int threadsToUse = currentThreadCount.get();
        
        if (threadsToUse == 1) {
            // Single-threaded path for efficiency
            recordSingleThreaded(primaryCommandBuffer, imageIndex, frameArena);
            return;
        }
        
        // Multi-threaded path: each thread records separate primary command buffers
        int trianglesPerThread = TRIANGLES_COUNT / threadsToUse;
        MemorySegment[] threadCommandBuffers = new MemorySegment[threadsToUse];
        CountDownLatch latch = new CountDownLatch(threadsToUse);
        
        // Allocate command buffers for each thread from their own pools
        for (int t = 0; t < threadsToUse; t++) {
            final int threadIndex = t;
            final int startTriangle = t * trianglesPerThread;
            final int endTriangle = (t == threadsToUse - 1) ? TRIANGLES_COUNT : (t + 1) * trianglesPerThread;
            
            executor.submit(() -> {
                try {
                    Thread currentThread = Thread.currentThread();
                    Arena threadArena = getThreadArena(currentThread);
                    VkCommandPool threadPool = getThreadCommandPool(currentThread);
                    
                    // Allocate command buffer from thread's pool
                    MemorySegment[] cmdBuffers = VkCommandBufferAlloc.builder()
                        .device(device)
                        .commandPool(threadPool.handle())
                        .primary()
                        .count(1)
                        .allocate(threadArena);
                    
                    MemorySegment threadCmd = cmdBuffers[0];
                    threadCommandBuffers[threadIndex] = threadCmd;
                    
                    // Record commands for this thread's triangles
                    VkCommandBuffer.begin(threadCmd).execute(threadArena);
                    
                    VkCommandBuffer.beginRenderPass(threadCmd, renderPass.handle(), framebuffers[imageIndex].handle())
                        .renderArea(0, 0, width, height)
                        .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
                        .execute(threadArena);
                    
                    VulkanExtensions.cmdBindPipeline(threadCmd, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle());
                    
                    MemorySegment viewport = io.github.yetyman.vulkan.VkViewport.builder()
                        .position(0, 0)
                        .size(width, height)
                        .depthRange(0.0f, 1.0f)
                        .build(threadArena);
                    VulkanExtensions.cmdSetViewport(threadCmd, 0, 1, viewport);
                    
                    MemorySegment scissor = io.github.yetyman.vulkan.VkRect2D.builder()
                        .offset(0, 0)
                        .extent(width, height)
                        .build(threadArena);
                    VulkanExtensions.cmdSetScissor(threadCmd, 0, 1, scissor);
                    
                    // Use instanced rendering instead of individual draws
                    int triangleCount = endTriangle - startTriangle;
                    VulkanExtensions.cmdDraw(threadCmd, 3, triangleCount, 0, startTriangle);
                    
                    VulkanExtensions.cmdEndRenderPass(threadCmd);
                    VulkanExtensions.endCommandBuffer(threadCmd).check();
                    
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all threads to finish
        try {
            latch.await(50, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Submit all command buffers to queue (this must be single-threaded)
        for (int i = 0; i < threadsToUse; i++) {
            if (threadCommandBuffers[i] != null) {
                VkSubmit.builder()
                    .commandBuffer(threadCommandBuffers[i])
                    .submit(queue, MemorySegment.NULL, frameArena).check();
            }
        }
    }
    
    private void recordSingleThreaded(MemorySegment commandBuffer, int imageIndex, Arena frameArena) {
        VkCommandBuffer.begin(commandBuffer).execute(frameArena);
        
        VkCommandBuffer.beginRenderPass(commandBuffer, renderPass.handle(), framebuffers[imageIndex].handle())
            .renderArea(0, 0, width, height)
            .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
            .execute(frameArena);
        
        VulkanExtensions.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle());
        
        MemorySegment viewport = io.github.yetyman.vulkan.VkViewport.builder()
            .position(0, 0)
            .size(width, height)
            .depthRange(0.0f, 1.0f)
            .build(frameArena);
        VulkanExtensions.cmdSetViewport(commandBuffer, 0, 1, viewport);
        
        MemorySegment scissor = io.github.yetyman.vulkan.VkRect2D.builder()
            .offset(0, 0)
            .extent(width, height)
            .build(frameArena);
        VulkanExtensions.cmdSetScissor(commandBuffer, 0, 1, scissor);
        
        // Use instanced rendering - 3 vertices per triangle, TRIANGLES_COUNT instances
        VulkanExtensions.cmdDraw(commandBuffer, 3, TRIANGLES_COUNT, 0, 0);
        
        VulkanExtensions.cmdEndRenderPass(commandBuffer);
        VulkanExtensions.endCommandBuffer(commandBuffer).check();
    }
    
    private VkCommandPool getThreadCommandPool(Thread thread) {
        return threadPools.computeIfAbsent(thread, k -> {
            // Each thread gets its own command pool for thread safety
            return VkCommandPool.create(getThreadArena(k), device, 0); // Assuming graphics queue family 0
        });
    }
    
    private Arena getThreadArena(Thread thread) {
        return threadArenas.computeIfAbsent(thread, k -> Arena.ofShared());
    }
    
    private void trackPerformance(long frameStart) {
        long frameTime = System.nanoTime() - frameStart;
        frameTimes.addLast(frameTime);
        if (frameTimes.size() > FRAME_HISTORY) {
            frameTimes.removeFirst();
        }
        lastFrameTime = frameTime;
    }
    
    private void adjustThreadCount() {
        if (frameTimes.size() < 10) return; // Need some history
        
        double avgFrameTime = frameTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double targetFrameTime = 16_666_667.0; // 60 FPS in nanoseconds
        
        int currentThreads = currentThreadCount.get();
        int maxThreads = Runtime.getRuntime().availableProcessors();
        
        switch (mode) {
            case BEST_EFFICIENCY:
                // Minimize threads while maintaining 60fps
                if (avgFrameTime < targetFrameTime * 0.8 && currentThreads > 1) {
                    setThreadCount(currentThreads - 1);
                } else if (avgFrameTime > targetFrameTime && currentThreads < maxThreads) {
                    setThreadCount(currentThreads + 1);
                }
                break;
                
            case BEST_PERFORMANCE:
                // Maximize threads for best performance
                if (currentThreads < maxThreads && avgFrameTime > targetFrameTime * 0.5) {
                    setThreadCount(currentThreads + 1);
                }
                break;
                
            case ADAPTIVE:
                // Balance between efficiency and performance
                if (avgFrameTime > targetFrameTime && currentThreads < maxThreads) {
                    setThreadCount(currentThreads + 1);
                } else if (avgFrameTime < targetFrameTime * 0.7 && currentThreads > 1) {
                    setThreadCount(Math.max(1, currentThreads - 1));
                }
                break;
        }
    }
    
    public void setThreadCount(int count) {
        int maxThreads = Runtime.getRuntime().availableProcessors();
        count = Math.max(1, Math.min(count, maxThreads));
        
        executor.setCorePoolSize(count);
        executor.setMaximumPoolSize(count);
        currentThreadCount.set(count);
        
        System.out.println("[THREADS] Adjusted to " + count + " threads");
    }
    
    public void setMode(Mode mode) {
        this.mode = mode;
        System.out.println("[MODE] Switched to " + mode);
    }
    
    public int getActiveThreads() {
        return currentThreadCount.get();
    }
    
    public double getAverageFrameTime() {
        return frameTimes.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1_000_000.0; // ms
    }
    
    public void resize(int newWidth, int newHeight) {
        // Clean up old resources
        for (VkFramebuffer framebuffer : framebuffers) {
            framebuffer.close();
        }
        for (VkImageView imageView : swapchainImageViews) {
            imageView.close();
        }
        if (depthImageView != null) {
            depthImageView.close();
        }
        swapchain.close();
        
        // Update dimensions
        width = newWidth;
        height = newHeight;
        
        // Recreate resources with new size
        createSwapchain(physicalDevice);
        createImageViews();
        createDepthResources();
        createFramebuffers();
        
        System.out.println("[OK] Swapchain and depth buffer recreated for resize");
    }
    
    public void cleanup() {
        // Shutdown thread pool
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        // Clean up thread-local resources
        threadPools.values().forEach(VkCommandPool::close);
        threadArenas.values().forEach(Arena::close);
        
        // Clean up main resources
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            imageAvailableSemaphores[i].close();
            renderFinishedSemaphores[i].close();
            inFlightFences[i].close();
        }
        mainCommandPool.close();
        for (VkFramebuffer framebuffer : framebuffers) {
            framebuffer.close();
        }
        pipeline.close();
        renderPass.close();
        for (VkImageView imageView : swapchainImageViews) {
            imageView.close();
        }
        if (depthImageView != null) {
            depthImageView.close();
        }
        swapchain.close();
    }
}