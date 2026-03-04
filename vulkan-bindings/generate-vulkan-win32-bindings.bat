@echo off
set VULKAN_SDK=C:\VulkanSDK\1.4.335.0
set VULKAN_INCLUDE=%VULKAN_SDK%\Include
set OUTPUT_DIR=src\main\java

REM Generate core Vulkan bindings
echo Generating core Vulkan bindings...
cmd /c jextract ^
  --output %OUTPUT_DIR% ^
  --target-package io.github.yetyman.vulkan.generated ^
  --include-dir %VULKAN_INCLUDE% ^
  --header-class-name VulkanFFM ^
  %VULKAN_INCLUDE%\vulkan\vulkan.h
echo Core Vulkan bindings generated

REM Generate dynamic function loaders
echo Generating dynamic function loaders...
call mvn compile -q
call mvn exec:java -q "-Dexec.mainClass=io.github.yetyman.dyngen.DynFunctionGenerator" "-Dexec.args=%VULKAN_SDK%\share\vulkan\registry\vk.xml src\main\java\io\github\yetyman\vulkan\generated src\main\java\io\github\yetyman\vulkan\generated"
echo Dynamic function loaders generated

REM Generate core enum classes
echo Generating enum classes...
call mvn compile -q
call mvn exec:java -q "-Dexec.mainClass=io.github.yetyman.enumgen.EnumGenerator" "-Dexec.args=src\main\java\io\github\yetyman\vulkan\generated src\main\java\io\github\yetyman\vulkan\enums"
echo Enum classes generated

REM Windows-only: generate Win32 bindings
if not "%OS%"=="Windows_NT" goto end

echo Generating Win32 Vulkan bindings...
cmd /c jextract ^
  --output %OUTPUT_DIR% ^
  --target-package io.github.yetyman.vulkan.generated.win32 ^
  --include-dir %VULKAN_INCLUDE% ^
  --include-dir "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Tools\MSVC\14.37.32822\include" ^
  --include-dir "C:\Program Files (x86)\Windows Kits\10\Include\10.0.22621.0\um" ^
  --include-dir "C:\Program Files (x86)\Windows Kits\10\Include\10.0.22621.0\shared" ^
  --include-dir "C:\Program Files (x86)\Windows Kits\10\Include\10.0.22621.0\ucrt" ^
  --header-class-name VulkanWin32FFM ^
  vulkan_win32_wrapper.h
echo Win32 Vulkan bindings generated

echo Removing Win32 duplicate classes...
call mvn compile -q
call mvn exec:java -q "-Dexec.mainClass=io.github.yetyman.enumgen.Win32Deduplicator" "-Dexec.args=src\main\java\io\github\yetyman\vulkan\generated src\main\java\io\github\yetyman\vulkan\generated\win32"

echo Generating Win32 enum classes...
call mvn compile -q
call mvn exec:java -q "-Dexec.mainClass=io.github.yetyman.enumgen.EnumGenerator" "-Dexec.args=src\main\java\io\github\yetyman\vulkan\generated\win32 src\main\java\io\github\yetyman\vulkan\enums\win32 io.github.yetyman.vulkan.enums.win32"

echo Removing Win32 enum duplicates...
call mvn compile -q
call mvn exec:java -q "-Dexec.mainClass=io.github.yetyman.enumgen.Win32Deduplicator" "-Dexec.args=src\main\java\io\github\yetyman\vulkan\enums src\main\java\io\github\yetyman\vulkan\enums\win32"
echo Win32 bindings complete

:end
echo Done.
