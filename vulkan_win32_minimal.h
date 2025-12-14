#define VK_USE_PLATFORM_WIN32_KHR

// Minimal Windows types needed for Vulkan Win32
typedef void* HANDLE;
typedef HANDLE HINSTANCE;
typedef HANDLE HWND;

// Include Vulkan core first
#include <vulkan/vulkan_core.h>

// Win32 surface creation info
typedef struct VkWin32SurfaceCreateInfoKHR {
    VkStructureType    sType;
    const void*        pNext;
    VkFlags            flags;
    HINSTANCE          hinstance;
    HWND               hwnd;
} VkWin32SurfaceCreateInfoKHR;

// Win32 function declarations
typedef VkResult (VKAPI_PTR *PFN_vkCreateWin32SurfaceKHR)(VkInstance instance, const VkWin32SurfaceCreateInfoKHR* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkSurfaceKHR* pSurface);
typedef VkBool32 (VKAPI_PTR *PFN_vkGetPhysicalDeviceWin32PresentationSupportKHR)(VkPhysicalDevice physicalDevice, uint32_t queueFamilyIndex);

// Function declarations
VKAPI_ATTR VkResult VKAPI_CALL vkCreateWin32SurfaceKHR(
    VkInstance                        instance,
    const VkWin32SurfaceCreateInfoKHR* pCreateInfo,
    const VkAllocationCallbacks*      pAllocator,
    VkSurfaceKHR*                     pSurface);

VKAPI_ATTR VkBool32 VKAPI_CALL vkGetPhysicalDeviceWin32PresentationSupportKHR(
    VkPhysicalDevice                  physicalDevice,
    uint32_t                          queueFamilyIndex);

// Constants
#define VK_KHR_WIN32_SURFACE_SPEC_VERSION 6
#define VK_KHR_WIN32_SURFACE_EXTENSION_NAME "VK_KHR_win32_surface"