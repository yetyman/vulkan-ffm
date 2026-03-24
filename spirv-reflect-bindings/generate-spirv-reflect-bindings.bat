@echo off
setlocal

echo Generating SPIRV-Reflect FFM bindings...

REM Check if jextract is available
where jextract >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: jextract not found in PATH
    echo Please ensure JDK with jextract is installed and in PATH
    exit /b 1
)

REM Ensure SPIRV-Reflect is cloned
if not exist "build\SPIRV-Reflect\spirv_reflect.h" (
    echo SPIRV-Reflect source not found. Run build-spirv-reflect.bat first.
    exit /b 1
)

REM Clean previous generated files
if exist "src\main\java\io\github\yetyman\spirv\generated" (
    echo Cleaning previous generated files...
    rmdir /s /q "src\main\java\io\github\yetyman\spirv\generated"
)

REM Create output directory
mkdir "src\main\java\io\github\yetyman\spirv\generated" 2>nul

REM Generate bindings from the real upstream header.
REM jextract-shims/ provides a stub string.h (spirv_reflect.h includes it for
REM memcpy, which jextract doesn't need). The shim path comes first so it
REM shadows the system header.
echo Running jextract against upstream spirv_reflect.h...
jextract ^
    --output "src\main\java" ^
    --target-package "io.github.yetyman.spirv.generated" ^
    --library "spirv-reflect" ^
    --header-class-name "SpirvReflectFFM" ^
    -I "jextract-shims" ^
    -I "build\SPIRV-Reflect\include" ^
    "build\SPIRV-Reflect\spirv_reflect.h"

if %ERRORLEVEL% neq 0 (
    echo Error: jextract failed
    exit /b 1
)

echo.
echo SPIRV-Reflect bindings generated successfully from upstream header!
echo.

pause
