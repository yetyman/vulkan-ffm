package io.github.yetyman.sample;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.VkCommandBufferAllocateInfo;
import io.github.yetyman.vulkan.generated.VkPresentInfoKHR;
import io.github.yetyman.vulkan.generated.VkSubmitInfo;
import io.github.yetyman.vulkan.generated.VkCommandBufferBeginInfo;
import io.github.yetyman.vulkan.generated.VkRenderPassBeginInfo;
import io.github.yetyman.vulkan.generated.VkRect2D;
import io.github.yetyman.vulkan.generated.VkOffset2D;
import io.github.yetyman.vulkan.generated.VkExtent2D;

import java.lang.foreign.*;

public class Renderer {
    private static final int MAX_FRAMES_IN_FLIGHT = 3;
    
    private final Arena arena;
    private final MemorySegment device;
    private final MemorySegment queue;
    private final int width;
    private final int height;
    
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
        MemorySegment allocInfo = VkCommandBufferAllocateInfo.allocate(arena);
        VkCommandBufferAllocateInfo.sType(allocInfo, VkStructureType.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
        VkCommandBufferAllocateInfo.pNext(allocInfo, MemorySegment.NULL);
        VkCommandBufferAllocateInfo.commandPool(allocInfo, commandPool.handle());
        VkCommandBufferAllocateInfo.level(allocInfo, VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
        VkCommandBufferAllocateInfo.commandBufferCount(allocInfo, MAX_FRAMES_IN_FLIGHT);
        
        commandBuffers = new MemorySegment[MAX_FRAMES_IN_FLIGHT];
        MemorySegment commandBuffersArray = arena.allocate(ValueLayout.ADDRESS, MAX_FRAMES_IN_FLIGHT);
        VulkanExtensions.allocateCommandBuffers(device, allocInfo, commandBuffersArray).check();
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            commandBuffers[i] = commandBuffersArray.getAtIndex(ValueLayout.ADDRESS, i);
        }
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
            MemorySegment fenceArray = frameArena.allocate(ValueLayout.ADDRESS);
            fenceArray.set(ValueLayout.ADDRESS, 0, inFlightFences[currentFrame].handle());
            VulkanExtensions.waitForFences(device, 1, fenceArray, 1, 0xFFFFFFFFFFFFFFFFL).check();
            VulkanExtensions.resetFences(device, 1, fenceArray).check();
            
            MemorySegment imageIndex = frameArena.allocate(ValueLayout.JAVA_INT);
            VulkanExtensions.acquireNextImageKHR(device, swapchain.handle(), 0xFFFFFFFFFFFFFFFFL,
                imageAvailableSemaphores[currentFrame].handle(), MemorySegment.NULL, imageIndex).check();
            int imgIdx = imageIndex.get(ValueLayout.JAVA_INT, 0);
            
            recordCommandBuffer(commandBuffers[currentFrame], imgIdx, frameArena);
            
            MemorySegment waitSemaphores = frameArena.allocate(ValueLayout.ADDRESS);
            waitSemaphores.set(ValueLayout.ADDRESS, 0, imageAvailableSemaphores[currentFrame].handle());
            MemorySegment waitStages = frameArena.allocate(ValueLayout.JAVA_INT);
            waitStages.set(ValueLayout.JAVA_INT, 0, VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            MemorySegment cmdBufArray = frameArena.allocate(ValueLayout.ADDRESS);
            cmdBufArray.set(ValueLayout.ADDRESS, 0, commandBuffers[currentFrame]);
            MemorySegment signalSemaphores = frameArena.allocate(ValueLayout.ADDRESS);
            signalSemaphores.set(ValueLayout.ADDRESS, 0, renderFinishedSemaphores[currentFrame].handle());
            
            MemorySegment submitInfo = VkSubmitInfo.allocate(frameArena);
            VkSubmitInfo.sType(submitInfo, VkStructureType.VK_STRUCTURE_TYPE_SUBMIT_INFO);
            VkSubmitInfo.pNext(submitInfo, MemorySegment.NULL);
            VkSubmitInfo.waitSemaphoreCount(submitInfo, 1);
            VkSubmitInfo.pWaitSemaphores(submitInfo, waitSemaphores);
            VkSubmitInfo.pWaitDstStageMask(submitInfo, waitStages);
            VkSubmitInfo.commandBufferCount(submitInfo, 1);
            VkSubmitInfo.pCommandBuffers(submitInfo, cmdBufArray);
            VkSubmitInfo.signalSemaphoreCount(submitInfo, 1);
            VkSubmitInfo.pSignalSemaphores(submitInfo, signalSemaphores);
            
            VulkanExtensions.queueSubmit(queue, 1, submitInfo, inFlightFences[currentFrame].handle()).check();
            
            MemorySegment swapchains = frameArena.allocate(ValueLayout.ADDRESS);
            swapchains.set(ValueLayout.ADDRESS, 0, swapchain.handle());
            MemorySegment imageIndices = frameArena.allocate(ValueLayout.JAVA_INT);
            imageIndices.set(ValueLayout.JAVA_INT, 0, imgIdx);
            
            MemorySegment presentInfo = VkPresentInfoKHR.allocate(frameArena);
            VkPresentInfoKHR.sType(presentInfo, 1000001001); // VK_STRUCTURE_TYPE_PRESENT_INFO_KHR
            VkPresentInfoKHR.pNext(presentInfo, MemorySegment.NULL);
            VkPresentInfoKHR.waitSemaphoreCount(presentInfo, 1);
            VkPresentInfoKHR.pWaitSemaphores(presentInfo, signalSemaphores);
            VkPresentInfoKHR.swapchainCount(presentInfo, 1);
            VkPresentInfoKHR.pSwapchains(presentInfo, swapchains);
            VkPresentInfoKHR.pImageIndices(presentInfo, imageIndices);
            VkPresentInfoKHR.pResults(presentInfo, MemorySegment.NULL);
            
            VulkanExtensions.queuePresentKHR(queue, presentInfo);
            
            currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
        }
    }
    
    private void recordCommandBuffer(MemorySegment commandBuffer, int imageIndex, Arena frameArena) {
        MemorySegment beginInfo = VkCommandBufferBeginInfo.allocate(frameArena);
        VkCommandBufferBeginInfo.sType(beginInfo, 42); // VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO
        VkCommandBufferBeginInfo.pNext(beginInfo, MemorySegment.NULL);
        VkCommandBufferBeginInfo.flags(beginInfo, 0);
        VkCommandBufferBeginInfo.pInheritanceInfo(beginInfo, MemorySegment.NULL);
        
        VulkanExtensions.beginCommandBuffer(commandBuffer, beginInfo).check();
        
        MemorySegment clearValue = frameArena.allocate(16);
        clearValue.set(ValueLayout.JAVA_FLOAT, 0, 0.0f);
        clearValue.set(ValueLayout.JAVA_FLOAT, 4, 0.0f);
        clearValue.set(ValueLayout.JAVA_FLOAT, 8, 0.0f);
        clearValue.set(ValueLayout.JAVA_FLOAT, 12, 1.0f);
        
        MemorySegment renderPassInfo = VkRenderPassBeginInfo.allocate(frameArena);
        VkRenderPassBeginInfo.sType(renderPassInfo, 43); // VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO
        VkRenderPassBeginInfo.pNext(renderPassInfo, MemorySegment.NULL);
        VkRenderPassBeginInfo.renderPass(renderPassInfo, renderPass.handle());
        VkRenderPassBeginInfo.framebuffer(renderPassInfo, framebuffers[imageIndex].handle());
        MemorySegment renderArea = VkRenderPassBeginInfo.renderArea(renderPassInfo);
        MemorySegment offset = VkRect2D.offset(renderArea);
        VkOffset2D.x(offset, 0);
        VkOffset2D.y(offset, 0);
        MemorySegment extent = VkRect2D.extent(renderArea);
        VkExtent2D.width(extent, width);
        VkExtent2D.height(extent, height);
        VkRenderPassBeginInfo.clearValueCount(renderPassInfo, 1);
        VkRenderPassBeginInfo.pClearValues(renderPassInfo, clearValue);
        
        VulkanExtensions.cmdBeginRenderPass(commandBuffer, renderPassInfo, VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE);
        VulkanExtensions.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle());
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
}
