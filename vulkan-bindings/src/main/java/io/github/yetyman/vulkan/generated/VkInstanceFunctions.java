package io.github.yetyman.vulkan.generated;

import java.lang.foreign.*;
import java.lang.invoke.*;

public class VkInstanceFunctions {

    public final MethodHandle vkGetInstanceProcAddr;
    public final MethodHandle vkCreateAndroidSurfaceKHR;
    public final MethodHandle vkCreateSurfaceOHOS;
    public final MethodHandle vkCreateViSurfaceNN;
    public final MethodHandle vkCreateWaylandSurfaceKHR;
    public final MethodHandle vkGetPhysicalDeviceWaylandPresentationSupportKHR;
    public final MethodHandle vkCreateWin32SurfaceKHR;
    public final MethodHandle vkGetPhysicalDeviceWin32PresentationSupportKHR;
    public final MethodHandle vkCreateXlibSurfaceKHR;
    public final MethodHandle vkGetPhysicalDeviceXlibPresentationSupportKHR;
    public final MethodHandle vkCreateXcbSurfaceKHR;
    public final MethodHandle vkGetPhysicalDeviceXcbPresentationSupportKHR;
    public final MethodHandle vkCreateDirectFBSurfaceEXT;
    public final MethodHandle vkGetPhysicalDeviceDirectFBPresentationSupportEXT;
    public final MethodHandle vkCreateImagePipeSurfaceFUCHSIA;
    public final MethodHandle vkCreateStreamDescriptorSurfaceGGP;
    public final MethodHandle vkCreateScreenSurfaceQNX;
    public final MethodHandle vkGetPhysicalDeviceScreenPresentationSupportQNX;
    public final MethodHandle vkGetPhysicalDeviceExternalMemorySciBufPropertiesNV;
    public final MethodHandle vkGetPhysicalDeviceSciBufAttributesNV;
    public final MethodHandle vkGetPhysicalDeviceSciSyncAttributesNV;
    public final MethodHandle vkAcquireXlibDisplayEXT;
    public final MethodHandle vkGetRandROutputDisplayEXT;
    public final MethodHandle vkAcquireWinrtDisplayNV;
    public final MethodHandle vkGetWinrtDisplayNV;
    public final MethodHandle vkCreateIOSSurfaceMVK;
    public final MethodHandle vkCreateMacOSSurfaceMVK;
    public final MethodHandle vkCreateMetalSurfaceEXT;
    public final MethodHandle vkGetPhysicalDeviceSurfacePresentModes2EXT;
    public final MethodHandle vkGetPhysicalDeviceRefreshableObjectTypesKHR;

    public VkInstanceFunctions(MemorySegment instance) {
        Linker linker = Linker.nativeLinker();
        this.vkGetInstanceProcAddr = load(instance, linker, "vkGetInstanceProcAddr", FunctionDescriptor.of(VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkCreateAndroidSurfaceKHR = load(instance, linker, "vkCreateAndroidSurfaceKHR", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkCreateSurfaceOHOS = load(instance, linker, "vkCreateSurfaceOHOS", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkCreateViSurfaceNN = load(instance, linker, "vkCreateViSurfaceNN", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkCreateWaylandSurfaceKHR = load(instance, linker, "vkCreateWaylandSurfaceKHR", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkGetPhysicalDeviceWaylandPresentationSupportKHR = load(instance, linker, "vkGetPhysicalDeviceWaylandPresentationSupportKHR", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_INT, VulkanFFM.C_POINTER));
        this.vkCreateWin32SurfaceKHR = load(instance, linker, "vkCreateWin32SurfaceKHR", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkGetPhysicalDeviceWin32PresentationSupportKHR = load(instance, linker, "vkGetPhysicalDeviceWin32PresentationSupportKHR", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_INT));
        this.vkCreateXlibSurfaceKHR = load(instance, linker, "vkCreateXlibSurfaceKHR", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkGetPhysicalDeviceXlibPresentationSupportKHR = load(instance, linker, "vkGetPhysicalDeviceXlibPresentationSupportKHR", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkCreateXcbSurfaceKHR = load(instance, linker, "vkCreateXcbSurfaceKHR", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkGetPhysicalDeviceXcbPresentationSupportKHR = load(instance, linker, "vkGetPhysicalDeviceXcbPresentationSupportKHR", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkCreateDirectFBSurfaceEXT = load(instance, linker, "vkCreateDirectFBSurfaceEXT", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkGetPhysicalDeviceDirectFBPresentationSupportEXT = load(instance, linker, "vkGetPhysicalDeviceDirectFBPresentationSupportEXT", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_INT, VulkanFFM.C_POINTER));
        this.vkCreateImagePipeSurfaceFUCHSIA = load(instance, linker, "vkCreateImagePipeSurfaceFUCHSIA", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkCreateStreamDescriptorSurfaceGGP = load(instance, linker, "vkCreateStreamDescriptorSurfaceGGP", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkCreateScreenSurfaceQNX = load(instance, linker, "vkCreateScreenSurfaceQNX", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkGetPhysicalDeviceScreenPresentationSupportQNX = load(instance, linker, "vkGetPhysicalDeviceScreenPresentationSupportQNX", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_INT, VulkanFFM.C_POINTER));
        this.vkGetPhysicalDeviceExternalMemorySciBufPropertiesNV = load(instance, linker, "vkGetPhysicalDeviceExternalMemorySciBufPropertiesNV", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkGetPhysicalDeviceSciBufAttributesNV = load(instance, linker, "vkGetPhysicalDeviceSciBufAttributesNV", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkGetPhysicalDeviceSciSyncAttributesNV = load(instance, linker, "vkGetPhysicalDeviceSciSyncAttributesNV", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkAcquireXlibDisplayEXT = load(instance, linker, "vkAcquireXlibDisplayEXT", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkGetRandROutputDisplayEXT = load(instance, linker, "vkGetRandROutputDisplayEXT", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkAcquireWinrtDisplayNV = load(instance, linker, "vkAcquireWinrtDisplayNV", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkGetWinrtDisplayNV = load(instance, linker, "vkGetWinrtDisplayNV", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_INT, VulkanFFM.C_POINTER));
        this.vkCreateIOSSurfaceMVK = load(instance, linker, "vkCreateIOSSurfaceMVK", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkCreateMacOSSurfaceMVK = load(instance, linker, "vkCreateMacOSSurfaceMVK", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkCreateMetalSurfaceEXT = load(instance, linker, "vkCreateMetalSurfaceEXT", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkGetPhysicalDeviceSurfacePresentModes2EXT = load(instance, linker, "vkGetPhysicalDeviceSurfacePresentModes2EXT", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkGetPhysicalDeviceRefreshableObjectTypesKHR = load(instance, linker, "vkGetPhysicalDeviceRefreshableObjectTypesKHR", FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
    }

    private static MethodHandle load(MemorySegment handle, Linker linker, String name, FunctionDescriptor desc) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment fnPtr = VulkanFFM.vkGetInstanceProcAddr(handle, tmp.allocateFrom(name));
            if (fnPtr.equals(MemorySegment.NULL)) return null;
            return linker.downcallHandle(fnPtr, desc);
        }
    }
}
