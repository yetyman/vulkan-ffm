# Shaderc FFM Bindings

Auto-generated FFM bindings for Google Shaderc shader compiler.

## Setup

1. **Generate bindings**:
   ```bash
   generate-shaderc-bindings.bat
   ```

2. **Download shaderc library**:
   - Get `shaderc_shared.dll` from [shaderc releases](https://github.com/google/shaderc/releases)
   - Place in `src/main/resources/natives/`

3. **Build**:
   ```bash
   mvn clean compile
   ```

## Usage

```java
import io.github.yetyman.shaderc.generated.*;

// Initialize compiler
MemorySegment compiler = ShadercFFM.shaderc_compiler_initialize();

// Compile GLSL to SPIR-V
String glslSource = "...";
MemorySegment result = ShadercFFM.shaderc_compile_into_spv(
    compiler,
    arena.allocateFrom(glslSource),
    glslSource.length(),
    ShadercFFM.shaderc_vertex_shader(),
    arena.allocateFrom("shader.vert"),
    arena.allocateFrom("main"),
    MemorySegment.NULL
);

// Get compiled SPIR-V bytes
MemorySegment spirvBytes = ShadercFFM.shaderc_result_get_bytes(result);
long spirvLength = ShadercFFM.shaderc_result_get_length(result);

// Cleanup
ShadercFFM.shaderc_result_release(result);
ShadercFFM.shaderc_compiler_release(compiler);
```

## Supported Languages

- **GLSL** (default)
- **HLSL** (set source language to `shaderc_source_language_hlsl`)