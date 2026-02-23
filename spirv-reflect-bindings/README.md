# SPIRV-Reflect FFM Bindings

Auto-generated FFM bindings for SPIRV-Reflect shader reflection library.

## Setup

1. **Build spirv-reflect library**:
   ```bash
   build-spirv-reflect.bat
   ```
   (Requires CMake and Git)

2. **Generate bindings**:
   ```bash
   generate-spirv-reflect-bindings.bat
   ```

3. **Build**:
   ```bash
   mvn clean compile
   ```

## Usage

```java
import io.github.yetyman.spirv.generated.*;

try (Arena arena = Arena.ofConfined()) {
    // Create shader module from SPIR-V bytecode
    MemorySegment module = SpvReflectShaderModule.allocate(arena);
    byte[] spirvCode = loadSpirVFromFile("shader.spv");
    
    MemorySegment codeSegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, spirvCode);
    SpvReflectResult result = SpirvReflectFFM.spvReflectCreateShaderModule(
        spirvCode.length, codeSegment, module);
    
    if (result != SPV_REFLECT_RESULT_SUCCESS) {
        throw new RuntimeException("Failed to create shader module");
    }
    
    // Enumerate descriptor sets
    MemorySegment countPtr = arena.allocate(ValueLayout.JAVA_INT);
    SpirvReflectFFM.spvReflectEnumerateDescriptorSets(module, countPtr, MemorySegment.NULL);
    int setCount = countPtr.get(ValueLayout.JAVA_INT, 0);
    
    MemorySegment setsPtr = arena.allocate(ValueLayout.ADDRESS, setCount);
    SpirvReflectFFM.spvReflectEnumerateDescriptorSets(module, countPtr, setsPtr);
    
    // Process descriptor sets...
    for (int i = 0; i < setCount; i++) {
        MemorySegment setPtr = setsPtr.getAtIndex(ValueLayout.ADDRESS, i);
        // Extract set information...
    }
    
    // Cleanup
    SpirvReflectFFM.spvReflectDestroyShaderModule(module);
}
```

## Features

- Extract descriptor set layouts from SPIR-V
- Get input/output variable information  
- Analyze push constant blocks
- Query shader stage and entry points