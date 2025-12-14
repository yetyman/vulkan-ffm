@echo off
REM Generate GLFW FFM bindings using jextract

set GLFW_INCLUDE=C:\GLFW\glfw-3.4.bin.WIN64\include
set OUTPUT_DIR=src\main\java

echo Generating GLFW bindings...
jextract ^
  --output %OUTPUT_DIR% ^
  --target-package io.github.yetyman.glfw.generated ^
  --include-dir %GLFW_INCLUDE% ^
  --library glfw3 ^
  --header-class-name GLFWFFM ^
  %~dp0glfw3_wrapper.h

if %ERRORLEVEL% GEQ 5 (
    echo GLFW bindings generation failed with error %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)

echo GLFW bindings generated in %OUTPUT_DIR%\io\github\yetyman\glfw\generated

REM Generate enum classes
echo Generating enum classes...
call mvn compile -q
call mvn exec:java -q "-Dexec.mainClass=io.github.yetyman.enumgen.EnumGenerator" "-Dexec.args=src\main\java\io\github\yetyman\glfw\generated src\main\java\io\github\yetyman\glfw\enums"
echo Enum classes generated
