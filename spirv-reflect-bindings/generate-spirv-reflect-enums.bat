@echo off
setlocal

echo Generating SPIRV-Reflect enums...

REM Compile EnumGenerator
javac -cp "src\main\java" src\main\java\io\github\yetyman\enumgen\EnumGenerator.java

if %ERRORLEVEL% neq 0 (
    echo Error: Failed to compile EnumGenerator
    exit /b 1
)

REM Run EnumGenerator
java -cp "src\main\java" io.github.yetyman.enumgen.EnumGenerator "src\main\java\io\github\yetyman\spirv\generated" "src\main\java\io\github\yetyman\spirv\enums"

if %ERRORLEVEL% neq 0 (
    echo Error: Failed to generate enums
    exit /b 1
)

echo SPIRV-Reflect enums generated successfully!
pause