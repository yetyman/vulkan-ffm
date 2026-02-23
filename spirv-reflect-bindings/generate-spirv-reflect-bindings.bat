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

REM Clean previous generated files
if exist "src\main\java\io\github\yetyman\spirv\generated" (
    echo Cleaning previous generated files...
    rmdir /s /q "src\main\java\io\github\yetyman\spirv\generated"
)

REM Create output directory
mkdir "src\main\java\io\github\yetyman\spirv\generated" 2>nul

REM Generate bindings
echo Running jextract...
jextract ^
    --output "src\main\java" ^
    --target-package "io.github.yetyman.spirv.generated" ^
    --library "spirv-reflect" ^
    --header-class-name "SpirvReflectFFM" ^
    "spirv_reflect_wrapper.h"

if %ERRORLEVEL% neq 0 (
    echo Error: jextract failed
    exit /b 1
)

echo.
echo SPIRV-Reflect bindings generated successfully!
echo.
echo Next steps:
echo 1. Download spirv-reflect library from:
echo    https://github.com/KhronosGroup/SPIRV-Reflect/releases
echo 2. Place spirv-reflect.dll in src\main\resources\natives\
echo 3. Build with: mvn clean compile
echo.

pause