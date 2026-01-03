package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.util.function.Consumer;

/**
 * Debug messenger for handling Vulkan validation layer messages.
 * Provides filtering and custom message handling capabilities.
 * 
 * Example usage:
 * <pre>{@code
 * // Basic debug messenger for development
 * VkDebugMessenger debugMessenger = VkDebugMessenger.builder()
 *     .instance(vulkanInstance)
 *     .errorsAndWarnings()
 *     .build(arena);
 * 
 * // Custom message handler with filtering
 * VkDebugMessenger debugMessenger = VkDebugMessenger.builder()
 *     .instance(vulkanInstance)
 *     .allMessages()
 *     .messageHandler(msg -> {
 *         if (msg.isError()) {
 *             logger.error("Vulkan Error: {}", msg.message());
 *         } else if (msg.isWarning()) {
 *             logger.warn("Vulkan Warning: {}", msg.message());
 *         }
 *     })
 *     .build(arena);
 * }</pre>
 */
public class VkDebugMessenger implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment instance;
    private final Consumer<DebugMessage> messageHandler;
    
    private VkDebugMessenger(MemorySegment handle, MemorySegment instance, Consumer<DebugMessage> messageHandler) {
        this.handle = handle;
        this.instance = instance;
        this.messageHandler = messageHandler;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the debug messenger handle */
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        if (handle != null && !handle.equals(MemorySegment.NULL)) {
            Vulkan.destroyDebugUtilsMessengerEXT(instance, handle);
        }
    }
    
    /**
     * Debug message information.
     */
    public record DebugMessage(
        int messageSeverity,
        int messageTypes,
        String messageIdName,
        int messageIdNumber,
        String message,
        String[] objects
    ) {
        public boolean isError() {
            return (messageSeverity & VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0;
        }
        
        public boolean isWarning() {
            return (messageSeverity & VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0;
        }
        
        public boolean isInfo() {
            return (messageSeverity & VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0;
        }
        
        public boolean isVerbose() {
            return (messageSeverity & VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT) != 0;
        }
        
        public String getSeverityString() {
            if (isError()) return "ERROR";
            if (isWarning()) return "WARNING";
            if (isInfo()) return "INFO";
            if (isVerbose()) return "VERBOSE";
            return "UNKNOWN";
        }
    }
    
    public static class Builder {
        private MemorySegment instance;
        private int messageSeverity = VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
                                     VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
        private int messageType = VkDebugUtilsMessageTypeFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
                                 VkDebugUtilsMessageTypeFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
                                 VkDebugUtilsMessageTypeFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
        private Consumer<DebugMessage> messageHandler = VkDebugMessenger::defaultMessageHandler;
        
        public Builder instance(MemorySegment instance) {
            this.instance = instance;
            return this;
        }
        
        public Builder messageSeverity(int severity) {
            this.messageSeverity = severity;
            return this;
        }
        
        public Builder messageType(int type) {
            this.messageType = type;
            return this;
        }
        
        public Builder messageHandler(Consumer<DebugMessage> handler) {
            this.messageHandler = handler;
            return this;
        }
        
        public Builder errorsOnly() {
            this.messageSeverity = VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
            return this;
        }
        
        public Builder errorsAndWarnings() {
            this.messageSeverity = VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT |
                                  VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
            return this;
        }
        
        public Builder allMessages() {
            this.messageSeverity = VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT |
                                  VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT |
                                  VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
                                  VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
            return this;
        }
        
        public Builder validationOnly() {
            this.messageType = VkDebugUtilsMessageTypeFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT;
            return this;
        }
        
        public Builder performanceOnly() {
            this.messageType = VkDebugUtilsMessageTypeFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
            return this;
        }
        
        public VkDebugMessenger build(Arena arena) {
            if (instance == null) throw new IllegalStateException("instance not set");
            
            // Create debug callback function
            MemorySegment callback = createDebugCallback(arena, messageHandler);
            
            MemorySegment createInfo = VkDebugUtilsMessengerCreateInfoEXT.allocate(arena);
            VkDebugUtilsMessengerCreateInfoEXT.sType(createInfo, 
                VkStructureType.VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT);
            VkDebugUtilsMessengerCreateInfoEXT.messageSeverity(createInfo, messageSeverity);
            VkDebugUtilsMessengerCreateInfoEXT.messageType(createInfo, messageType);
            VkDebugUtilsMessengerCreateInfoEXT.pfnUserCallback(createInfo, callback);
            
            MemorySegment messengerPtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.createDebugUtilsMessengerEXT(instance, createInfo, messengerPtr).check();
            
            return new VkDebugMessenger(messengerPtr.get(ValueLayout.ADDRESS, 0), instance, messageHandler);
        }
        
        private MemorySegment createDebugCallback(Arena arena, Consumer<DebugMessage> handler) {
            // Create a function descriptor for the debug callback
            FunctionDescriptor descriptor = FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,           // messageSeverity
                ValueLayout.JAVA_INT,           // messageTypes
                ValueLayout.ADDRESS,            // pCallbackData
                ValueLayout.ADDRESS             // pUserData
            );
            
            // Create method handle for static callback method
            try {
                MethodHandle callbackHandle = MethodHandles.lookup().findStatic(
                    VkDebugMessenger.class, "debugCallbackImpl",
                    MethodType.methodType(int.class, int.class, int.class, MemorySegment.class, MemorySegment.class)
                );
                
                return Linker.nativeLinker().upcallStub(callbackHandle, descriptor, arena);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create debug callback", e);
            }
        }
    }
    
    // Static callback implementation
    public static int debugCallbackImpl(int messageSeverity, int messageTypes, 
                                       MemorySegment pCallbackData, MemorySegment pUserData) {
        try {
            // Extract message ID name
            MemorySegment pMessageIdName = VkDebugUtilsMessengerCallbackDataEXT.pMessageIdName(pCallbackData);
            String messageIdName = pMessageIdName.equals(MemorySegment.NULL) ? "" : pMessageIdName.getString(0);
            
            // Extract message ID number
            int messageIdNumber = VkDebugUtilsMessengerCallbackDataEXT.messageIdNumber(pCallbackData);
            
            // Extract message
            MemorySegment pMessage = VkDebugUtilsMessengerCallbackDataEXT.pMessage(pCallbackData);
            String message = pMessage.equals(MemorySegment.NULL) ? "No message" : pMessage.getString(0);
            
            // Extract object information
            int objectCount = VkDebugUtilsMessengerCallbackDataEXT.objectCount(pCallbackData);
            String[] objects = new String[objectCount];
            
            if (objectCount > 0) {
                MemorySegment pObjects = VkDebugUtilsMessengerCallbackDataEXT.pObjects(pCallbackData);
                for (int i = 0; i < objectCount; i++) {
                    MemorySegment objectInfo = pObjects.asSlice(i * VkDebugUtilsObjectNameInfoEXT.layout().byteSize(), 
                        VkDebugUtilsObjectNameInfoEXT.layout());
                    
                    int objectType = VkDebugUtilsObjectNameInfoEXT.objectType(objectInfo);
                    long objectHandle = VkDebugUtilsObjectNameInfoEXT.objectHandle(objectInfo);
                    MemorySegment pObjectName = VkDebugUtilsObjectNameInfoEXT.pObjectName(objectInfo);
                    
                    String objectName = pObjectName.equals(MemorySegment.NULL) ? 
                        String.format("Object(type=%d, handle=0x%x)", objectType, objectHandle) :
                        pObjectName.getString(0);
                    
                    objects[i] = objectName;
                }
            }
            
            DebugMessage debugMessage = new DebugMessage(messageSeverity, messageTypes, 
                messageIdName, messageIdNumber, message, objects);
            
            defaultMessageHandler(debugMessage);
        } catch (Exception e) {
            System.err.println("Error in debug callback: " + e.getMessage());
            e.printStackTrace();
        }
        
        return 0; // VK_FALSE - don't abort
    }

    
    private static void defaultMessageHandler(DebugMessage message) {
        String prefix = "[VULKAN " + message.getSeverityString() + "]";
        StringBuilder output = new StringBuilder();
        output.append(String.format("%s %s (%d): %s", 
            prefix, message.messageIdName(), message.messageIdNumber(), message.message()));
        
        // Add object information if available
        if (message.objects().length > 0) {
            output.append("\n  Objects involved:");
            for (String obj : message.objects()) {
                output.append("\n    - ").append(obj);
            }
        }
        
        String finalOutput = output.toString();
        if (message.isError()) {
            System.err.println(finalOutput);
        } else {
            System.out.println(finalOutput);
        }
    }
}