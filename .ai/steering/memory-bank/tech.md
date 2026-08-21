# Technology Stack

## Language & Runtime
- **Java 25+** (compiler source/target: 25)
- **Foreign Function & Memory (FFM) API** — core mechanism for all native calls
- No JNI; all native interop via `java.lang.foreign.*`

## Build System
- **Maven 3.6+**, multi-module POM
- Root artifact: `io.github.yetyman:VulkanFFM:1.0-SNAPSHOT`
- Default modules: `helpers-core`, `vulkan-core`, `vulkan-ffm-graph`, `vulkan-ffm-node-trees`, `vulkan-ffm-mesh`, `vulkan-ffm-mesh-processing`, `vulkan-ffm-sample-ui-layers`, `sample-app`
- Profile `with-bindings`: adds `vulkan-bindings`, `glfw-bindings`, `shaderc-bindings`, `spirv-reflect-bindings`, `stb-truetype-bindings`
- Surefire plugin 3.5.4 for tests

## Key Dependencies
- `vulkan-bindings` — auto-generated Vulkan FFM bindings (local module)
- `glfw-bindings` — auto-generated GLFW FFM bindings with bundled natives (local module)
- `shaderc-bindings` — auto-generated shaderc FFM bindings (local module, runtime GLSL→SPIR-V compilation)
- `spirv-reflect-bindings` — auto-generated SPIRV-Reflect FFM bindings (local module, shader reflection)
- `stb-truetype-bindings` — auto-generated stb_truetype FFM bindings (local module, font rasterization)
- `jgltf-model` — GLTF model loading (sample-app only)
- No external Vulkan SDK required at runtime (uses system Vulkan driver)

## Native Libraries
- **Vulkan**: loaded from system (`vulkan-1.dll` on Windows, `libvulkan.so` on Linux)
- **GLFW**: bundled as native DLL inside `glfw-bindings` JAR resources (`/natives/`)
- **shaderc**: bundled native library for runtime GLSL/HLSL→SPIR-V compilation
- **SPIRV-Reflect**: bundled native library for SPIR-V introspection (descriptors, push constants, spec constants)
- **stb_truetype**: bundled native library for font rasterization and glyph metrics
- Loading handled by `VulkanLibrary.java`, `NativeLibraryLoader.java`, `SpirvReflectLoader.java`

## Binding Generation
- Tool: **jextract** (JDK tool for generating FFM bindings from C headers)
- Vulkan: `generate-vulkan-bindings.bat`, `generate-vulkan-win32-bindings.bat`
- GLFW: `generate-glfw-bindings.bat`
- shaderc: generates `ShadercFFM.java` + enum wrappers
- SPIRV-Reflect: generates `SpirvReflectFFM.java` + enum wrappers
- Wrapper headers: `vulkan_win32_wrapper.h`, `glfw3_wrapper.h`

## Memory Model
- All native allocations use `java.lang.foreign.Arena`
- `Arena.ofConfined()` for scoped lifetime (most common)
- `Arena.ofShared()` for cross-thread lifetime (TransferBatch, SparsePageAllocator)
- `Arena.global()` for long-lived handles (queues, command pool registry)
- `MemorySegment.NULL` used as the null pointer / no-allocator sentinel
- Native `calloc`/`free` via Linker for oversized opaque structs (SpvReflectShaderModule)

## Shader Pipeline
- Shaders written in GLSL (or HLSL via `ShaderLoader.Builder.hlsl()`)
- Compiled to SPIR-V at runtime via shaderc (no offline compilation step required)
- Reflected at runtime via SPIRV-Reflect for descriptor sets, push constants, specialization constants
- `ShaderLoader` caches compiled shaders by path and define-variant key
- `ShaderGenerator` generates typed Java wrapper classes from reflected shaders (CLI or API)
- Specialization constants supported at both ShaderInstance level and VkComputePipeline builder level
- Pre-compiled `.spv` files can still be loaded via `ShaderLoader.builder(path).loadCompiledShader()`

## Platform Support
- **Windows**: primary target, Win32 surface support, bundled GLFW/shaderc/SPIRV-Reflect DLLs
- **Linux**: supported via `libvulkan-dev` + system GLFW
- **macOS**: supported via MoltenVK

## Development Commands
```bash
# Build core + sample-app
mvn clean install

# Build including binding regeneration
mvn clean install -Pwith-bindings

# Run simple triangle app
mvn exec:java -pl sample-app -Dexec.mainClass="io.github.yetyman.vulkan.sample.simple.SimpleTriangleApp"

# Run complex triangle app
mvn exec:java -pl sample-app -Dexec.mainClass="io.github.yetyman.vulkan.sample.complex.ComplexTriangleApp"

# Run shader example (integration test)
mvn exec:java -pl vulkan-core -Dexec.mainClass="io.github.yetyman.vulkan.shaders.ShaderExample"

# Run buffer example (integration test)
mvn exec:java -pl vulkan-core -Dexec.mainClass="io.github.yetyman.vulkan.buffers.BufferExample"

# Generate shader wrapper classes from GLSL
java -cp vulkan-core.jar io.github.yetyman.vulkan.shaders.ShaderGenerator /shaders/model.vert outputDir io.example.shaders
java -cp vulkan-core.jar io.github.yetyman.vulkan.shaders.ShaderGenerator --dir shaders/ outputDir io.example.shaders

# Regenerate Vulkan bindings (Windows)
cd vulkan-bindings && generate-vulkan-bindings.bat

# Regenerate GLFW bindings (Windows)
cd glfw-bindings && generate-glfw-bindings.bat

# Regenerate shaderc bindings (Windows)
cd shaderc-bindings && generate-shaderc-bindings.bat

# Regenerate SPIRV-Reflect bindings (Windows)
cd spirv-reflect-bindings && generate-spirv-reflect-bindings.bat

# Regenerate stb_truetype bindings (Windows)
cd stb-truetype-bindings && generate-stb-truetype-bindings.bat

# Generate shader .bat helper
cd vulkan-core && generate-shader.bat
```
