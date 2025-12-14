@echo off
REM Generate Vulkan FFM bindings using jextract

set VULKAN_SDK=C:\VulkanSDK\1.4.335.0
set VULKAN_INCLUDE=%VULKAN_SDK%\Include
set OUTPUT_DIR=src\main\java

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

echo Core Vulkan bindings generated in %OUTPUT_DIR%\io\github\yetyman\vulkan\generated

REM Generate enum classes
echo Generating enum classes...
call mvn compile -q
call mvn exec:java -q "-Dexec.mainClass=io.github.yetyman.enumgen.EnumGenerator" "-Dexec.args=src\main\java\io\github\yetyman\vulkan\generated src\main\java\io\github\yetyman\vulkan\enums"
echo Enum classes generated
