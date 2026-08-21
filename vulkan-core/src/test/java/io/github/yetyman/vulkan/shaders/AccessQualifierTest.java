package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.vulkan.VulkanLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that SPIR-V access qualifiers (readonly, writeonly, readwrite) are correctly
 * extracted from shader reflection and exposed via {@link AccessQualifier}.
 *
 * These tests only require shaderc + SPIRV-Reflect (no Vulkan device needed).
 */
class AccessQualifierTest {

    @BeforeAll
    static void loadLibraries() {
        VulkanLibrary.load();
    }

    static final String COMPUTE_READONLY_WRITEONLY = String.join("\n",
            "#version 450",
            "layout(local_size_x = 64) in;",
            "layout(set = 0, binding = 0) readonly  buffer InputBuf  { uint data[]; } inputBuf;",
            "layout(set = 0, binding = 1) writeonly buffer OutputBuf { uint data[]; } outputBuf;",
            "layout(push_constant) uniform PC { uint multiplier; } pc;",
            "void main() {",
            "    uint idx = gl_GlobalInvocationID.x;",
            "    outputBuf.data[idx] = inputBuf.data[idx] * pc.multiplier;",
            "}"
    );

    static final String COMPUTE_READWRITE = String.join("\n",
            "#version 450",
            "layout(local_size_x = 64) in;",
            "layout(set = 0, binding = 0) buffer DataBuf { uint data[]; } dataBuf;",
            "void main() {",
            "    uint idx = gl_GlobalInvocationID.x;",
            "    dataBuf.data[idx] = dataBuf.data[idx] * 2;",
            "}"
    );

    @Test
    void readonlyBufferReflectsAsReadOnly() {
        CompiledShader compiled = ShaderLoader.compute().source(COMPUTE_READONLY_WRITEONLY).compileShader();
        assertNotNull(compiled);

        ShaderLoader.DescriptorBindingInfo inputBinding = compiled.getReflection().getBindingByName("inputBuf");
        assertNotNull(inputBinding, "inputBuf binding not found in reflection");
        assertEquals(AccessQualifier.READ_ONLY, inputBinding.getAccessQualifier(),
                "readonly buffer should reflect as READ_ONLY");
    }

    @Test
    void writeonlyBufferReflectsAsWriteOnly() {
        CompiledShader compiled = ShaderLoader.compute().source(COMPUTE_READONLY_WRITEONLY).compileShader();
        assertNotNull(compiled);

        ShaderLoader.DescriptorBindingInfo outputBinding = compiled.getReflection().getBindingByName("outputBuf");
        assertNotNull(outputBinding, "outputBuf binding not found in reflection");
        assertEquals(AccessQualifier.WRITE_ONLY, outputBinding.getAccessQualifier(),
                "writeonly buffer should reflect as WRITE_ONLY");
    }

    @Test
    void unqualifiedBufferReflectsAsReadWrite() {
        CompiledShader compiled = ShaderLoader.compute().source(COMPUTE_READWRITE).compileShader();
        assertNotNull(compiled);

        ShaderLoader.DescriptorBindingInfo dataBinding = compiled.getReflection().getBindingByName("dataBuf");
        assertNotNull(dataBinding, "dataBuf binding not found in reflection");
        assertEquals(AccessQualifier.READ_WRITE, dataBinding.getAccessQualifier(),
                "unqualified buffer should reflect as READ_WRITE");
    }
}
