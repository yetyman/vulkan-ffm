@echo off
REM Generate Vulkan FFM bindings using jextract

set VULKAN_SDK=C:\VulkanSDK\1.4.335.0
set VULKAN_INCLUDE=%VULKAN_SDK%\Include
set OUTPUT_DIR=vulkan-bindings\src\main\java

REM Generate core Vulkan bindings
echo Generating core Vulkan bindings...
jextract ^
  --output %OUTPUT_DIR% ^
  --target-package io.github.yetyman.vulkan.generated ^
  --include-dir %VULKAN_INCLUDE% ^
  --header-class-name VulkanFFM ^
  %VULKAN_INCLUDE%\vulkan\vulkan.h
if %ERRORLEVEL% GEQ 5 (
    echo Core bindings generation failed with error %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)

REM Generate Win32-specific bindings (includes core + Win32)
echo Generating Win32 Vulkan bindings...
jextract ^
  --output %OUTPUT_DIR% ^
  --target-package io.github.yetyman.vulkan.generated.win32 ^
  --include-dir %VULKAN_INCLUDE% ^
  --include-dir "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Tools\MSVC\14.37.32822\include" ^
  --include-dir "C:\Program Files (x86)\Windows Kits\10\Include\10.0.22621.0\um" ^
  --include-dir "C:\Program Files (x86)\Windows Kits\10\Include\10.0.22621.0\shared" ^
  --include-dir "C:\Program Files (x86)\Windows Kits\10\Include\10.0.22621.0\ucrt" ^
  --header-class-name VulkanWin32FFM ^
  vulkan_win32_wrapper.h
if %ERRORLEVEL% GEQ 5 (
    echo Win32 bindings generation failed with error %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)

echo Core Vulkan bindings generated in %OUTPUT_DIR%\io\github\yetyman\vulkan\generated
echo Win32 Vulkan bindings generated in %OUTPUT_DIR%\io\github\yetyman\vulkan\generated\win32
