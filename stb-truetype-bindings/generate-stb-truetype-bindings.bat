@echo off
setlocal

echo Generating stb_truetype FFM bindings...

REM Check if jextract is available
where jextract >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: jextract not found in PATH
    echo Please ensure JDK with jextract is installed and in PATH
    exit /b 1
)

REM Ensure stb_truetype.h is present
if not exist "build\stb\stb_truetype.h" (
    echo stb_truetype.h not found. Run build-stb-truetype.bat first.
    exit /b 1
)

REM Clean previous generated files
if exist "src\main\java\io\github\yetyman\stbtruetype\generated" (
    echo Cleaning previous generated files...
    rmdir /s /q "src\main\java\io\github\yetyman\stbtruetype\generated"
)

REM Create output directory
mkdir "src\main\java\io\github\yetyman\stbtruetype\generated" 2>nul

REM Generate bindings from the wrapper header (mirrors the upstream ABI but
REM excludes preprocessor-only content jextract cannot use directly).
echo Running jextract against stb_truetype_wrapper.h...
jextract ^
    --output "src\main\java" ^
    --target-package "io.github.yetyman.stbtruetype.generated" ^
    --library "stb-truetype" ^
    --header-class-name "StbTrueTypeFFM" ^
    "stb_truetype_wrapper.h"

if %ERRORLEVEL% neq 0 (
    echo Error: jextract failed
    exit /b 1
)

echo.
echo stb_truetype bindings generated successfully!
echo.

pause
