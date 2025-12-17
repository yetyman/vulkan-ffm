package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.*;

/**
 * Encapsulates a swapchain image with its view and associated semaphores.
 */
public class SwapchainImage implements AutoCloseable {
    private final MemorySegment image;
    private final VkImageView imageView;
    private final VkSemaphore imageAvailableSemaphore;
    private final VkSemaphore renderFinishedSemaphore;
    
    public SwapchainImage(MemorySegment image, VkImageView imageView, 
                         VkSemaphore imageAvailableSemaphore, VkSemaphore renderFinishedSemaphore) {
        this.image = image;
        this.imageView = imageView;
        this.imageAvailableSemaphore = imageAvailableSemaphore;
        this.renderFinishedSemaphore = renderFinishedSemaphore;
    }
    
    public MemorySegment image() { return image; }
    public VkImageView imageView() { return imageView; }
    public VkSemaphore imageAvailableSemaphore() { return imageAvailableSemaphore; }
    public VkSemaphore renderFinishedSemaphore() { return renderFinishedSemaphore; }
    
    @Override
    public void close() {
        imageView.close();
        imageAvailableSemaphore.close();
        renderFinishedSemaphore.close();
    }
}