@echo off
REM Generate Vulkan FFM bindings using jextract

set VULKAN_SDK=C:\VulkanSDK\1.4.335.0
set VULKAN_INCLUDE=%VULKAN_SDK%\Include
set OUTPUT_DIR=vulkan-core\src\main\java

jextract ^
  --output %OUTPUT_DIR% ^
  --target-package io.github.yetyman.vulkan.generated ^
  --include-dir %VULKAN_INCLUDE% ^
  --header-class-name VulkanFFM ^
  %VULKAN_INCLUDE%\vulkan\vulkan.h

echo Vulkan bindings generated in %OUTPUT_DIR%\io\github\yetyman\vulkan\generated
