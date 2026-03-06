@echo off
setlocal

REM generate-shader.bat
REM Usage:
REM   generate-shader.bat <shaderResourcePath> <outputDir> <javaPackage>
REM   generate-shader.bat --dir <shaderResourceDir> <outputDir> <javaPackage>
REM
REM Example (single file):
REM   generate-shader.bat /shaders/model.vert src\main\java io.github.yetyman.vulkan.sample.shaders
REM
REM Example (directory):
REM   generate-shader.bat --dir src\main\resources\shaders src\main\java io.github.yetyman.vulkan.sample.shaders

if "%~1"=="" (
    echo Usage: generate-shader.bat ^<shaderResourcePath^> ^<outputDir^> ^<javaPackage^>
    echo        generate-shader.bat --dir ^<shaderResourceDir^> ^<outputDir^> ^<javaPackage^>
    exit /b 1
)

REM Build the project first to ensure ShaderGenerator is compiled
call mvn compile -pl vulkan-core -q
if %ERRORLEVEL% neq 0 (
    echo Build failed
    exit /b 1
)

mvn exec:java ^
    -pl vulkan-core ^
    -Dexec.mainClass="io.github.yetyman.vulkan.shaders.ShaderGenerator" ^
    -Dexec.args="%*"

if %ERRORLEVEL% neq 0 (
    echo ShaderGenerator failed
    exit /b 1
)

echo Done.
