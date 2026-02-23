package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.shaderc.generated.*;
import io.github.yetyman.shaderc.enums.*;
import io.github.yetyman.spirv.generated.*;
import io.github.yetyman.spirv.enums.*;
import io.github.yetyman.vulkan.util.Logger;
import java.io.*;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import static java.lang.foreign.ValueLayout.*;

/**
 * Fluent builder for shader loading with direct FFM compilation.
 */
public class ShaderLoader {
    static {
        SpirvReflectLoader.load();
    }

    private static Function<ShaderCompileRequest, byte[]> defaultCompiler = ShaderLoader::shadercCompile;
    
    private String resourcePath;
    private Function<ShaderCompileRequest, byte[]> compiler;
    private final Map<String, String> defines = new HashMap<>();
    private final List<String> includePaths = new ArrayList<>();
    private boolean optimize = false;
    private ShadercSourceLanguage sourceLanguage = ShadercSourceLanguage.shaderc_source_language_glsl;
    private ShadercShaderKind shaderKind;
    
    private ShaderLoader(String resourcePath) {
        this.resourcePath = resourcePath;
        this.compiler = defaultCompiler;
        this.shaderKind = inferShaderKind(resourcePath);
    }
    
    public static ShaderLoader load(String resourcePath) {
        return new ShaderLoader(resourcePath);
    }
    
    public static void setDefaultCompiler(Function<ShaderCompileRequest, byte[]> compiler) {
        defaultCompiler = compiler;
    }
    
    public ShaderLoader compiler(Function<ShaderCompileRequest, byte[]> compiler) {
        this.compiler = compiler;
        return this;
    }
    
    public ShaderLoader sourceLanguage(ShadercSourceLanguage language) {
        this.sourceLanguage = language;
        return this;
    }
    
    public ShaderLoader shaderKind(ShadercShaderKind kind) {
        this.shaderKind = kind;
        return this;
    }
    
    public ShaderLoader hlsl() {
        this.sourceLanguage = ShadercSourceLanguage.shaderc_source_language_hlsl;
        return this;
    }
    
    public ShaderLoader define(String name, String value) {
        defines.put(name, value);
        return this;
    }
    
    public ShaderLoader includePath(String path) {
        includePaths.add(path);
        return this;
    }
    
    public ShaderLoader optimize() {
        this.optimize = true;
        return this;
    }
    
    public byte[] compile() {
        ShaderCompileRequest request = new ShaderCompileRequest(
            resourcePath, shaderKind, sourceLanguage, defines, includePaths, optimize
        );
        return compiler.apply(request);
    }
    
    public byte[] loadSpirV() {
        try (InputStream is = ShaderLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) throw new FileNotFoundException(resourcePath);
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load SPIR-V: " + resourcePath, e);
        }
    }
    
    public CompiledShader compileShader() {
        byte[] spirv = compile();
        ShaderReflection reflection = new ShaderReflection(spirv);
        return new CompiledShader(spirv, reflection, shaderKind);
    }
    
    public CompiledShader loadCompiledShader() {
        byte[] spirv = loadSpirV();
        ShaderReflection reflection = new ShaderReflection(spirv);
        return new CompiledShader(spirv, reflection, shaderKind);
    }
    
    // Static convenience methods
    public static CompiledShader compileShader(String resourcePath) {
        return load(resourcePath).compileShader();
    }
    
    public static CompiledShader loadCompiledShader(String resourcePath) {
        return load(resourcePath).loadCompiledShader();
    }
    
    private static byte[] shadercCompile(ShaderCompileRequest request) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment compiler = ShadercFFM.shaderc_compiler_initialize();
            if (compiler.equals(MemorySegment.NULL)) {
                throw new RuntimeException("Failed to initialize shaderc compiler");
            }
            try {
                String source = loadResource(request.resourcePath);
                MemorySegment sourceSegment = arena.allocateFrom(source);

                MemorySegment options = ShadercFFM.shaderc_compile_options_initialize();
                if (options.equals(MemorySegment.NULL)) {
                    throw new RuntimeException("Failed to initialize shaderc compile options");
                }
                try {
                    ShadercFFM.shaderc_compile_options_set_source_language(options, request.sourceLanguage.value());
                    if (request.optimize) {
                        ShadercFFM.shaderc_compile_options_set_optimization_level(options,
                            ShadercOptimizationLevel.shaderc_optimization_level_performance.value());
                    }
                    for (Map.Entry<String, String> define : request.defines.entrySet()) {
                        MemorySegment nameSegment = arena.allocateFrom(define.getKey());
                        MemorySegment valueSegment = arena.allocateFrom(define.getValue());
                        ShadercFFM.shaderc_compile_options_add_macro_definition(
                            options, nameSegment, define.getKey().length(),
                            valueSegment, define.getValue().length());
                    }

                    MemorySegment filenameSegment = arena.allocateFrom(request.resourcePath);
                    MemorySegment entryPointSegment = arena.allocateFrom("main");
                    MemorySegment result = ShadercFFM.shaderc_compile_into_spv(
                        compiler, sourceSegment, source.length(), request.shaderKind.value(),
                        filenameSegment, entryPointSegment, options);
                    try {
                        int status = ShadercFFM.shaderc_result_get_compilation_status(result);
                        if (status != ShadercCompilationStatus.shaderc_compilation_status_success.value()) {
                            String error = ShadercFFM.shaderc_result_get_error_message(result).getString(0);
                            throw new RuntimeException("Shader compilation failed: " + error);
                        }
                        long length = ShadercFFM.shaderc_result_get_length(result);
                        MemorySegment bytesPtr = ShadercFFM.shaderc_result_get_bytes(result);
                        return bytesPtr.reinterpret(length, arena, null).toArray(JAVA_BYTE);
                    } finally {
                        ShadercFFM.shaderc_result_release(result);
                    }
                } finally {
                    ShadercFFM.shaderc_compile_options_release(options);
                }
            } finally {
                ShadercFFM.shaderc_compiler_release(compiler);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile shader: " + request.resourcePath, e);
        }
    }
    
    private static ShadercShaderKind inferShaderKind(String path) {
        if (path.endsWith(".vert")) return ShadercShaderKind.shaderc_vertex_shader;
        if (path.endsWith(".frag")) return ShadercShaderKind.shaderc_fragment_shader;
        if (path.endsWith(".comp")) return ShadercShaderKind.shaderc_compute_shader;
        if (path.endsWith(".geom")) return ShadercShaderKind.shaderc_geometry_shader;
        if (path.endsWith(".tesc")) return ShadercShaderKind.shaderc_tess_control_shader;
        if (path.endsWith(".tese")) return ShadercShaderKind.shaderc_tess_evaluation_shader;
        return ShadercShaderKind.shaderc_glsl_infer_from_source;
    }
    
    private static String loadResource(String path) throws IOException {
        try (InputStream is = ShaderLoader.class.getResourceAsStream(path)) {
            if (is == null) throw new FileNotFoundException(path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public record ShaderCompileRequest(String resourcePath, ShadercShaderKind shaderKind,
                                       ShadercSourceLanguage sourceLanguage, Map<String, String> defines,
                                       List<String> includePaths, boolean optimize) {
            public ShaderCompileRequest(String resourcePath, ShadercShaderKind shaderKind,
                                        ShadercSourceLanguage sourceLanguage, Map<String, String> defines,
                                        List<String> includePaths, boolean optimize) {
                this.resourcePath = resourcePath;
                this.shaderKind = shaderKind;
                this.sourceLanguage = sourceLanguage;
                this.defines = new HashMap<>(defines);
                this.includePaths = new ArrayList<>(includePaths);
                this.optimize = optimize;
            }
        }
    
    public static class ShaderReflection {
        private final byte[] spirv;
        private final Map<Integer, DescriptorSetInfo> descriptorSets;

        // SpvReflectShaderModule is an opaque internal struct; the jextract binding only captures
        // the public header fields (~80 bytes) but the real C struct contains internal parsing state
        // (SPIR-V word array, node arrays, etc.) making it several kilobytes. Allocating via the
        // Java arena would corrupt the heap. We use native calloc so the C runtime owns the memory.
        private static final MethodHandle CALLOC;
        private static final MethodHandle FREE;
        static {
            Linker linker = Linker.nativeLinker();
            SymbolLookup lookup = linker.defaultLookup();
            CALLOC = linker.downcallHandle(
                lookup.findOrThrow("calloc"),
                FunctionDescriptor.of(ADDRESS, JAVA_LONG, JAVA_LONG));
            FREE = linker.downcallHandle(
                lookup.findOrThrow("free"),
                FunctionDescriptor.ofVoid(ADDRESS));
        }

        private static MemorySegment nativeCalloc(long count, long size) {
            try {
                return (MemorySegment) CALLOC.invokeExact(count, size);
            } catch (Throwable t) {
                throw new RuntimeException("calloc failed", t);
            }
        }

        private static void nativeFree(MemorySegment ptr) {
            try {
                FREE.invokeExact(ptr);
            } catch (Throwable t) {
                throw new RuntimeException("free failed", t);
            }
        }

        private ShaderReflection(byte[] spirv) {
            this.spirv = spirv;
            this.descriptorSets = parseDescriptorSets(spirv);
        }
        
        public Map<Integer, DescriptorSetInfo> getDescriptorSets() {
            return Collections.unmodifiableMap(descriptorSets);
        }
        
        public DescriptorSetInfo getDescriptorSet(int set) {
            return descriptorSets.get(set);
        }
        
        public Set<Integer> getSetNumbers() {
            return descriptorSets.keySet();
        }
        
        private Map<Integer, DescriptorSetInfo> parseDescriptorSets(byte[] spirv) {
            Map<Integer, DescriptorSetInfo> sets = new HashMap<>();
            // 4096 bytes is a safe upper bound for SpvReflectShaderModule across all versions
            MemorySegment module = nativeCalloc(1, 4096);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment codeSegment = arena.allocateFrom(JAVA_BYTE, spirv);

                SpirvReflectResult result = SpirvReflectResult.fromValue(
                    SpirvReflectFFM.spvReflectCreateShaderModule(spirv.length, codeSegment, module));
                if (result != SpirvReflectResult.SPV_REFLECT_RESULT_SUCCESS) {
                    Logger.warn("SPIRV-Reflect: spvReflectCreateShaderModule failed with " + result);
                    return sets;
                }
                try {
                    MemorySegment countPtr = arena.allocate(JAVA_INT);
                    SpirvReflectFFM.spvReflectEnumerateDescriptorSets(module, countPtr, MemorySegment.NULL);
                    int setCount = countPtr.get(JAVA_INT, 0);
                    if (setCount == 0) return sets;

                    MemorySegment setsPtr = arena.allocate(ADDRESS, setCount);
                    SpirvReflectFFM.spvReflectEnumerateDescriptorSets(module, countPtr, setsPtr);

                    for (int i = 0; i < setCount; i++) {
                        MemorySegment setPtr = setsPtr.getAtIndex(ADDRESS, i)
                            .reinterpret(SpvReflectDescriptorSet.sizeof(), arena, null);
                        int setNumber = SpvReflectDescriptorSet.set(setPtr);
                        int bindingCount = SpvReflectDescriptorSet.binding_count(setPtr);
                        DescriptorSetInfo setInfo = new DescriptorSetInfo(setNumber);

                        if (bindingCount > 0) {
                            MemorySegment bindingsPtrPtr = SpvReflectDescriptorSet.bindings(setPtr)
                                .reinterpret(ADDRESS.byteSize() * bindingCount, arena, null);
                            for (int b = 0; b < bindingCount; b++) {
                                MemorySegment bindingPtr = bindingsPtrPtr.getAtIndex(ADDRESS, b)
                                    .reinterpret(SpvReflectDescriptorBinding.sizeof(), arena, null);
                                int bindingIndex = SpvReflectDescriptorBinding.binding(bindingPtr);
                                int descriptorTypeVal = SpvReflectDescriptorBinding.descriptor_type(bindingPtr);
                                int count = SpvReflectDescriptorBinding.count(bindingPtr);
                                setInfo.addBinding(bindingIndex, new DescriptorBindingInfo(
                                    bindingIndex,
                                    SpirvReflectDescriptorType.fromValue(descriptorTypeVal),
                                    count));
                            }
                        }
                        sets.put(setNumber, setInfo);
                    }
                } finally {
                    SpirvReflectFFM.spvReflectDestroyShaderModule(module);
                }
            } catch (Exception e) {
                Logger.warn("SPIRV-Reflect: failed to parse descriptor sets: " + e.getMessage());
            } finally {
                nativeFree(module);
            }
            return sets;
        }
    }
    
    public static class DescriptorSetInfo {
        private final int setNumber;
        private final Map<Integer, DescriptorBindingInfo> bindings;
        
        public DescriptorSetInfo(int setNumber) {
            this.setNumber = setNumber;
            this.bindings = new HashMap<>();
        }
        
        public int getSetNumber() { return setNumber; }
        
        public Map<Integer, DescriptorBindingInfo> getBindings() {
            return Collections.unmodifiableMap(bindings);
        }
        
        public DescriptorBindingInfo getBinding(int binding) {
            return bindings.get(binding);
        }
        
        void addBinding(int binding, DescriptorBindingInfo info) {
            bindings.put(binding, info);
        }
    }
    
    public static class DescriptorBindingInfo {
        private final int binding;
        private final SpirvReflectDescriptorType descriptorType;
        private final int descriptorCount;
        private final int stageFlags;

        /** Constructor for reflection-parsed bindings (stage flags determined by shader stage). */
        public DescriptorBindingInfo(int binding, SpirvReflectDescriptorType descriptorType, int descriptorCount) {
            this(binding, descriptorType, descriptorCount, 0);
        }

        /** Constructor for manually-added bindings with explicit stage flags. */
        public DescriptorBindingInfo(int binding, SpirvReflectDescriptorType descriptorType, int descriptorCount, int stageFlags) {
            this.binding = binding;
            this.descriptorType = descriptorType;
            this.descriptorCount = descriptorCount;
            this.stageFlags = stageFlags;
        }

        public int getBinding() { return binding; }
        public SpirvReflectDescriptorType getDescriptorType() { return descriptorType; }
        public int getDescriptorCount() { return descriptorCount; }
        /** @return explicit stage flags, or 0 if this binding uses the shader's default stage flags. */
        public int getStageFlags() { return stageFlags; }
    }
}
