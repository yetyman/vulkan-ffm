package io.github.yetyman.sample;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.generated.VkApplicationInfo;
import io.github.yetyman.vulkan.generated.VkInstanceCreateInfo;
import io.github.yetyman.vulkan.generated.VkDeviceQueueCreateInfo;
import io.github.yetyman.vulkan.generated.VkDeviceCreateInfo;
import io.github.yetyman.vulkan.generated.win32.VkWin32SurfaceCreateInfoKHR;
import org.lwjgl.PointerBuffer;

import java.lang.foreign.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryUtil.*;

public class TriangleApp {
    static { VulkanLibrary.load(); }
    
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    
    private long window;
    private Arena arena;
    private MemorySegment instance;
    private MemorySegment physicalDevice;
    private MemorySegment device;
    private MemorySegment queue;
    private int queueFamilyIndex;
    
    public void run() {
        VulkanLibrary.load();
        initWindow();
        initVulkan();
        mainLoop();
        cleanup();
    }
    
    private void initWindow() {
        if (!glfwInit()) {
            throw new RuntimeException("Failed to initialize GLFW");
        }
        
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
        
        window = glfwCreateWindow(WIDTH, HEIGHT, "Vulkan Triangle", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create window");
        }
        
        System.out.println("[OK] Window created");
    }
    
    private MemorySegment surface;
    
    private void initVulkan() {
        arena = Arena.ofConfined();
        
        MemorySegment appInfo = VkApplicationInfo.allocate(arena);
        VkApplicationInfo.sType(appInfo, VkStructureType.VK_STRUCTURE_TYPE_APPLICATION_INFO);
        VkApplicationInfo.pNext(appInfo, MemorySegment.NULL);
        VkApplicationInfo.pApplicationName(appInfo, arena.allocateFrom("Triangle App"));
        VkApplicationInfo.applicationVersion(appInfo, 1);
        VkApplicationInfo.pEngineName(appInfo, arena.allocateFrom("NoEngine"));
        VkApplicationInfo.engineVersion(appInfo, 0);
        VkApplicationInfo.apiVersion(appInfo, Vulkan.VK_API_VERSION_1_0);
        
        PointerBuffer glfwExtensions = glfwGetRequiredInstanceExtensions();
        if (glfwExtensions == null) {
            throw new RuntimeException("Failed to get GLFW extensions");
        }
        
        String[] extensions = new String[glfwExtensions.remaining()];
        for (int i = 0; i < extensions.length; i++) {
            extensions[i] = glfwExtensions.getStringUTF8(i);
        }
        
        System.out.println("Extensions: " + String.join(", ", extensions));
        
        MemorySegment createInfo = VkInstanceCreateInfo.allocate(arena);
        VkInstanceCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
        VkInstanceCreateInfo.pNext(createInfo, MemorySegment.NULL);
        VkInstanceCreateInfo.flags(createInfo, 0);
        VkInstanceCreateInfo.pApplicationInfo(createInfo, appInfo);
        
        if (extensions != null && extensions.length > 0) {
            MemorySegment extArray = arena.allocate(ValueLayout.ADDRESS, extensions.length);
            for (int i = 0; i < extensions.length; i++) {
                extArray.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(extensions[i]));
            }
            VkInstanceCreateInfo.enabledExtensionCount(createInfo, extensions.length);
            VkInstanceCreateInfo.ppEnabledExtensionNames(createInfo, extArray);
        } else {
            VkInstanceCreateInfo.enabledExtensionCount(createInfo, 0);
            VkInstanceCreateInfo.ppEnabledExtensionNames(createInfo, MemorySegment.NULL);
        }
        
        VkInstanceCreateInfo.enabledLayerCount(createInfo, 0);
        VkInstanceCreateInfo.ppEnabledLayerNames(createInfo, MemorySegment.NULL);
        
        MemorySegment instancePtr = arena.allocate(ValueLayout.ADDRESS);
        VkResult result = Vulkan.createInstance(createInfo, instancePtr);
        System.out.println("Create instance result: " + result);
        result.check();
        instance = instancePtr.get(ValueLayout.ADDRESS, 0);
        System.out.println("[OK] Vulkan instance created");

        
        MemorySegment deviceCount = arena.allocate(ValueLayout.JAVA_INT);
        Vulkan.enumeratePhysicalDevices(instance, deviceCount, MemorySegment.NULL).check();
        int count = deviceCount.get(ValueLayout.JAVA_INT, 0);
        
        if (count == 0) {
            throw new RuntimeException("No Vulkan devices found");
        }
        
        MemorySegment devices = arena.allocate(ValueLayout.ADDRESS, count);
        Vulkan.enumeratePhysicalDevices(instance, deviceCount, devices).check();
        physicalDevice = devices.get(ValueLayout.ADDRESS, 0);
        System.out.println("[OK] Physical device selected");
        
        queueFamilyIndex = 0;
        
        MemorySegment queuePriorities = arena.allocate(ValueLayout.JAVA_FLOAT);
        queuePriorities.set(ValueLayout.JAVA_FLOAT, 0, 1.0f);
        
        MemorySegment queueCreateInfo = VkDeviceQueueCreateInfo.allocate(arena);
        VkDeviceQueueCreateInfo.sType(queueCreateInfo, VkStructureType.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
        VkDeviceQueueCreateInfo.pNext(queueCreateInfo, MemorySegment.NULL);
        VkDeviceQueueCreateInfo.flags(queueCreateInfo, 0);
        VkDeviceQueueCreateInfo.queueFamilyIndex(queueCreateInfo, queueFamilyIndex);
        VkDeviceQueueCreateInfo.queueCount(queueCreateInfo, 1);
        VkDeviceQueueCreateInfo.pQueuePriorities(queueCreateInfo, queuePriorities);
        
        String[] deviceExtensions = {"VK_KHR_swapchain"};
        
        MemorySegment queueCreateInfos = arena.allocate(VkDeviceQueueCreateInfo.layout());
        MemorySegment.copy(queueCreateInfo, 0, queueCreateInfos, 0, VkDeviceQueueCreateInfo.layout().byteSize());
        
        MemorySegment deviceCreateInfo = VkDeviceCreateInfo.allocate(arena);
        VkDeviceCreateInfo.sType(deviceCreateInfo, VkStructureType.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
        VkDeviceCreateInfo.pNext(deviceCreateInfo, MemorySegment.NULL);
        VkDeviceCreateInfo.flags(deviceCreateInfo, 0);
        VkDeviceCreateInfo.queueCreateInfoCount(deviceCreateInfo, 1);
        VkDeviceCreateInfo.pQueueCreateInfos(deviceCreateInfo, queueCreateInfos);
        VkDeviceCreateInfo.enabledLayerCount(deviceCreateInfo, 0);
        VkDeviceCreateInfo.ppEnabledLayerNames(deviceCreateInfo, MemorySegment.NULL);
        
        MemorySegment extArray = arena.allocate(ValueLayout.ADDRESS, deviceExtensions.length);
        for (int i = 0; i < deviceExtensions.length; i++) {
            extArray.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(deviceExtensions[i]));
        }
        VkDeviceCreateInfo.enabledExtensionCount(deviceCreateInfo, deviceExtensions.length);
        VkDeviceCreateInfo.ppEnabledExtensionNames(deviceCreateInfo, extArray);
        VkDeviceCreateInfo.pEnabledFeatures(deviceCreateInfo, MemorySegment.NULL);
        
        MemorySegment devicePtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.createDevice(physicalDevice, deviceCreateInfo, devicePtr).check();
        device = devicePtr.get(ValueLayout.ADDRESS, 0);
        System.out.println("[OK] Logical device created");
        
        MemorySegment queuePtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.getDeviceQueue(device, queueFamilyIndex, 0, queuePtr);
        queue = queuePtr.get(ValueLayout.ADDRESS, 0);
        System.out.println("[OK] Queue retrieved");
        
        long hwnd = org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(window);
        
        MemorySegment surfaceCreateInfo = VkWin32SurfaceCreateInfoKHR.allocate(arena);
        VkWin32SurfaceCreateInfoKHR.sType(surfaceCreateInfo, 1000009000);
        VkWin32SurfaceCreateInfoKHR.pNext(surfaceCreateInfo, MemorySegment.NULL);
        VkWin32SurfaceCreateInfoKHR.flags(surfaceCreateInfo, 0);
        VkWin32SurfaceCreateInfoKHR.hinstance(surfaceCreateInfo, MemorySegment.NULL);
        VkWin32SurfaceCreateInfoKHR.hwnd(surfaceCreateInfo, MemorySegment.ofAddress(hwnd));
        
        MemorySegment surfacePtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanWin32.createWin32Surface(instance, surfaceCreateInfo, surfacePtr).check();
        surface = surfacePtr.get(ValueLayout.ADDRESS, 0);
        System.out.println("[OK] Surface created");
    }
    
    private Renderer renderer;
    
    private void mainLoop() {
        renderer = new Renderer(arena, device, queue, surface, WIDTH, HEIGHT);
        renderer.init(physicalDevice, queueFamilyIndex);
        
        System.out.println("[OK] Rendering enabled with per-frame Arena");
        
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
            renderer.drawFrame();
        }
    }
    
    private void cleanup() {
        if (renderer != null) {
            renderer.cleanup();
        }
        
        if (device != null && !device.equals(MemorySegment.NULL)) {
            Vulkan.deviceWaitIdle(device).check();
            Vulkan.destroyDevice(device);
            System.out.println("[OK] Device destroyed");
        }
        
        if (surface != null && !surface.equals(MemorySegment.NULL)) {
            VulkanSurface.destroySurface(instance, surface);
            System.out.println("[OK] Surface destroyed");
        }
        
        if (instance != null && !instance.equals(MemorySegment.NULL)) {
            Vulkan.destroyInstance(instance);
            System.out.println("[OK] Instance destroyed");
        }
        
        if (arena != null) {
            arena.close();
        }
        
        if (window != NULL) {
            glfwDestroyWindow(window);
            System.out.println("[OK] Window destroyed");
        }
        
        glfwTerminate();
    }
    
    public static void main(String[] args) {
        try {
            new TriangleApp().run();
        } catch (Exception e) {
            System.err.println("Application error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
