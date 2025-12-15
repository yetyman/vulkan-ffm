package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;

import java.lang.foreign.*;

public class Renderer {
    private static final int MAX_FRAMES_IN_FLIGHT = 3;
    
    private final Arena arena;
    private final MemorySegment device;
    private final MemorySegment queue;
    private int width;
    private int height;
    
    private MemorySegment surface;
    private VkSwapchain swapchain;
    private VkImageView[] swapchainImageViews;
    private VkRenderPass renderPass;
    private VkFramebuffer[] framebuffers;
    private VkPipeline pipeline;
    private VkCommandPool commandPool;
    private MemorySegment[] commandBuffers;
    private VkSemaphore[] imageAvailableSemaphores;
    private VkSemaphore[] renderFinishedSemaphores;
    private VkFence[] inFlightFences;
    
    public Renderer(Arena arena, MemorySegment device, MemorySegment queue, 
                    MemorySegment surface, int width, int height) {
        this.arena = arena;
        this.device = device;
        this.queue = queue;
        this.surface = surface;
        this.width = width;
        this.height = height;
    }
    
    public void init(MemorySegment physicalDevice, int queueFamilyIndex) {
        createSwapchain(physicalDevice);
        createImageViews();
        createRenderPass();
        createGraphicsPipeline();
        createFramebuffers();
        createCommandPool(queueFamilyIndex);
        createCommandBuffers();
        createSyncObjects();
        System.out.println("[OK] Renderer initialized with " + MAX_FRAMES_IN_FLIGHT + " frames in flight");
    }
    
    private void createSwapchain(MemorySegment physicalDevice) {
        swapchain = VkSwapchain.create(arena, device, surface, width, height); // VSync enabled
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
    
    private void createRenderPass() {
        renderPass = VkRenderPass.builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_B8G8R8A8_SRGB, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
            .subpassDependency(~0, 0, 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                0, VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
            .build(arena);
        System.out.println("[OK] Render pass created");
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
                .dimensions(width, height)
                .build(arena);
        }
        System.out.println("[OK] Framebuffers created");
    }
    
    private void createCommandPool(int queueFamilyIndex) {
        commandPool = VkCommandPool.create(arena, device, queueFamilyIndex);
        System.out.println("[OK] Command pool created");
    }
    
    private void createCommandBuffers() {
        commandBuffers = VkCommandBufferAlloc.builder()
            .device(device)
            .commandPool(commandPool.handle())
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
    
    private int currentFrame = 0;
    
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
                .waitSemaphore(imageAvailableSemaphores[currentFrame].handle(), VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .commandBuffer(commandBuffers[currentFrame])
                .signalSemaphore(renderFinishedSemaphores[currentFrame].handle())
                .submit(queue, inFlightFences[currentFrame].handle(), frameArena).check();
            
            VkPresent.builder()
                .waitSemaphore(renderFinishedSemaphores[currentFrame].handle())
                .swapchain(swapchain.handle(), imgIdx)
                .present(queue, frameArena);
            
            currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
        }
    }
    
    private void recordCommandBuffer(MemorySegment commandBuffer, int imageIndex, Arena frameArena) {
        VkCommandBuffer.begin(commandBuffer).execute(frameArena);
        
        VkCommandBuffer.beginRenderPass(commandBuffer, renderPass.handle(), framebuffers[imageIndex].handle())
            .renderArea(0, 0, width, height)
            .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
            .execute(frameArena);
        
        VulkanExtensions.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle());
        
        // Set dynamic viewport and scissor
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
        VulkanExtensions.cmdDraw(commandBuffer, 3, 1, 0, 0);
        VulkanExtensions.cmdEndRenderPass(commandBuffer);
        VulkanExtensions.endCommandBuffer(commandBuffer).check();
    }
    
    public void cleanup() {
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            imageAvailableSemaphores[i].close();
            renderFinishedSemaphores[i].close();
            inFlightFences[i].close();
        }
        commandPool.close();
        for (VkFramebuffer framebuffer : framebuffers) {
            framebuffer.close();
        }
        pipeline.close();
        renderPass.close();
        for (VkImageView imageView : swapchainImageViews) {
            imageView.close();
        }
        swapchain.close();
    }
    
    public void resize(int newWidth, int newHeight) {
        // Clean up old resources
        for (VkFramebuffer framebuffer : framebuffers) {
            framebuffer.close();
        }
        for (VkImageView imageView : swapchainImageViews) {
            imageView.close();
        }
        swapchain.close();
        
        // Update dimensions
        width = newWidth;
        height = newHeight;
        
        // Recreate resources with new size (pipeline stays the same!)
        createSwapchain(null);
        createImageViews();
        createFramebuffers();
        
        System.out.println("[OK] Swapchain recreated for resize");
    }
}
