@echo off
setlocal

echo Building SPIRV-Reflect library...

REM Check if cmake is available
where cmake >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: cmake not found in PATH
    echo Please install CMake and add to PATH
    exit /b 1
)

REM Check if git is available
where git >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: git not found in PATH
    echo Please install Git and add to PATH
    exit /b 1
)

REM Create build directory
if not exist "build" mkdir build
cd build

REM Clone spirv-reflect if not exists
if not exist "SPIRV-Reflect" (
    echo Cloning SPIRV-Reflect...
    git clone https://github.com/KhronosGroup/SPIRV-Reflect.git
    if %ERRORLEVEL% neq 0 (
        echo Error: Failed to clone SPIRV-Reflect
        exit /b 1
    )
)

cd SPIRV-Reflect

REM Create CMakeLists.txt for shared library
echo Creating CMakeLists.txt for shared library...
(
echo cmake_minimum_required(VERSION 3.10^)
echo project(spirv-reflect^)
echo.
echo set(CMAKE_C_STANDARD 99^)
echo set(CMAKE_CXX_STANDARD 11^)
echo.
echo add_library(spirv-reflect SHARED spirv_reflect.c^)
echo target_include_directories(spirv-reflect PUBLIC .^)
echo.
echo # Export all symbols on Windows
echo if(WIN32^)
echo     set_target_properties(spirv-reflect PROPERTIES WINDOWS_EXPORT_ALL_SYMBOLS TRUE^)
echo endif(^)
echo.
echo # Install library
echo install(TARGETS spirv-reflect DESTINATION lib^)
echo install(FILES spirv_reflect.h DESTINATION include^)
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

if exist "build\SPIRV-Reflect\build\Release\spirv-reflect.dll" (
    copy "build\SPIRV-Reflect\build\Release\spirv-reflect.dll" "src\main\resources\natives\"
) else if exist "build\SPIRV-Reflect\build\libspirv-reflect.dll" (
    copy "build\SPIRV-Reflect\build\libspirv-reflect.dll" "src\main\resources\natives\spirv-reflect.dll"
) else if exist "build\SPIRV-Reflect\build\libspirv-reflect.so" (
    copy "build\SPIRV-Reflect\build\libspirv-reflect.so" "src\main\resources\natives\"
) else (
    echo Error: Could not find built library
    exit /b 1
)

echo.
echo SPIRV-Reflect library built and copied successfully!
echo You can now run generate-spirv-reflect-bindings.bat
echo.

pause