package io.github.yetyman.vulkan;

public class VulkanException extends RuntimeException {
    public VulkanException(String message) {
        super(message);
    }
    
    public VulkanException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static class DeviceException extends VulkanException {
        public DeviceException(String message) { super(message); }
    }
    
    public static class SurfaceException extends VulkanException {
        public SurfaceException(String message) { super(message); }
    }
    
    public static class SwapchainException extends VulkanException {
        public SwapchainException(String message) { super(message); }
    }
}