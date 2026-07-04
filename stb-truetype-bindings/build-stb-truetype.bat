@echo off
setlocal

echo Building stb_truetype library...

REM Check if cmake is available
where cmake >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: cmake not found in PATH
    echo Please install CMake and add to PATH
    exit /b 1
)

REM Check if curl is available (used to fetch the single header from GitHub)
where curl >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: curl not found in PATH
    echo Please install curl and add to PATH, or manually place stb_truetype.h in build\stb\
    exit /b 1
)

REM Create build directory
if not exist "build" mkdir build
cd build

REM Fetch stb_truetype.h if not present
if not exist "stb" mkdir stb
if not exist "stb\stb_truetype.h" (
    echo Downloading stb_truetype.h from nothings/stb...
    curl -L -o "stb\stb_truetype.h" "https://raw.githubusercontent.com/nothings/stb/master/stb_truetype.h"
    if %ERRORLEVEL% neq 0 (
        echo Error: Failed to download stb_truetype.h
        exit /b 1
    )
)

cd stb

REM Generate the implementation shim - stb_truetype.h is header-only and
REM requires exactly one translation unit that defines STB_TRUETYPE_IMPLEMENTATION.
echo Creating implementation shim...
(
echo #define STB_TRUETYPE_IMPLEMENTATION
echo #include "stb_truetype.h"
) > stb_truetype_impl.c

REM Generate CMakeLists.txt for a shared library
echo Creating CMakeLists.txt for shared library...
(
echo cmake_minimum_required(VERSION 3.10^)
echo project(stb-truetype^)
echo.
echo set(CMAKE_C_STANDARD 99^)
echo.
echo add_library(stb-truetype SHARED stb_truetype_impl.c^)
echo target_include_directories(stb-truetype PUBLIC .^)
echo.
echo # Export all symbols on Windows
echo if(WIN32^)
echo     set_target_properties(stb-truetype PROPERTIES WINDOWS_EXPORT_ALL_SYMBOLS TRUE^)
echo endif(^)
echo.
echo install(TARGETS stb-truetype DESTINATION lib^)
echo install(FILES stb_truetype.h DESTINATION include^)
) > CMakeLists.txt

REM Configure and build
echo Configuring with CMake...
cmake -B build -S . -DCMAKE_BUILD_TYPE=Release
if %ERRORLEVEL% neq 0 (
    echo Error: CMake configuration failed
    exit /b 1
)

echo Building...
cmake --build build --config Release
if %ERRORLEVEL% neq 0 (
    echo Error: Build failed
    exit /b 1
)

REM Copy built library to resources
echo Copying library to resources...
cd ..\..
if not exist "src\main\resources\natives" mkdir "src\main\resources\natives"

if exist "build\stb\build\Release\stb-truetype.dll" (
    copy "build\stb\build\Release\stb-truetype.dll" "src\main\resources\natives\"
) else if exist "build\stb\build\libstb-truetype.dll" (
    copy "build\stb\build\libstb-truetype.dll" "src\main\resources\natives\stb-truetype.dll"
) else if exist "build\stb\build\libstb-truetype.so" (
    copy "build\stb\build\libstb-truetype.so" "src\main\resources\natives\"
) else (
    echo Error: Could not find built library
    exit /b 1
)

echo.
echo stb_truetype library built and copied successfully!
echo You can now run generate-stb-truetype-bindings.bat
echo.

pause
