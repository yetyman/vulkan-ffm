package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.shaderc.generated.*;
import io.github.yetyman.shaderc.enums.*;
import io.github.yetyman.spirv.SpirvReflectLoader;
import io.github.yetyman.spirv.generated.SpvReflectBlockVariable;
import io.github.yetyman.spirv.generated.SpvReflectDescriptorBinding;
import io.github.yetyman.spirv.generated.SpvReflectDescriptorSet;
import io.github.yetyman.spirv.generated.SpvReflectShaderModule;
import io.github.yetyman.spirv.generated.SpvReflectSpecializationConstant;
import io.github.yetyman.spirv.generated.SpvReflectNumericTraits;
import io.github.yetyman.spirv.generated.SpvReflectTypeDescription;
import io.github.yetyman.spirv.generated.SpirvReflectFFM;
import io.github.yetyman.spirv.enums.*;
import io.github.yetyman.vulkan.util.Logger;
import java.io.*;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.lang.foreign.ValueLayout.*;

/**
 * Static facade for shader compilation and loading, with a Builder for multi-step configuration.
 */
public class ShaderLoader {
    static {
        SpirvReflectLoader.load();
    }

    private static Function<ShaderCompileRequest, byte[]> defaultCompiler = ShaderLoader::shadercCompile;
    private static final ConcurrentHashMap<String, CompiledShader> compiledCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompiledShader> spirvCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompiledShader> defineVariantCache = new ConcurrentHashMap<>();

    private ShaderLoader() {}

    public static void setDefaultCompiler(Function<ShaderCompileRequest, byte[]> compiler) {
        defaultCompiler = compiler;
    }

    /** @return a Builder for the given resource path, with shader kind inferred from extension. */
    public static Builder builder(String resourcePath) {
        return new Builder(resourcePath);
    }

    /** @return a Builder for an inline vertex shader. */
    public static Builder vertex() { return new Builder(ShadercShaderKind.shaderc_vertex_shader); }

    /** @return a Builder for an inline fragment shader. */
    public static Builder fragment() { return new Builder(ShadercShaderKind.shaderc_fragment_shader); }

    /** @return a Builder for an inline compute shader. */
    public static Builder compute() { return new Builder(ShadercShaderKind.shaderc_compute_shader); }

    /** @return a Builder for an inline geometry shader. */
    public static Builder geometry() { return new Builder(ShadercShaderKind.shaderc_geometry_shader); }

    /** @return a Builder for an inline tessellation control shader. */
    public static Builder tessControl() { return new Builder(ShadercShaderKind.shaderc_tess_control_shader); }

    /** @return a Builder for an inline tessellation evaluation shader. */
    public static Builder tessEval() { return new Builder(ShadercShaderKind.shaderc_tess_evaluation_shader); }

    /** Compiles and reflects a shader, caching by path. */
    public static CompiledShader compileShader(String resourcePath) {
        return compiledCache.computeIfAbsent(resourcePath, p -> builder(p).compileShader());
    }

    /**
     * Compiles a shader with preprocessor defines, caching by (path, defines).
     * Each unique define set produces a distinct CompiledShader.
     */
    public static CompiledShader compileShader(String resourcePath, Map<String, String> defines) {
        if (defines == null || defines.isEmpty()) return compileShader(resourcePath);
        String cacheKey = defineVariantCacheKey(resourcePath, defines);
        return defineVariantCache.computeIfAbsent(cacheKey, k -> {
            Builder b = builder(resourcePath);
            defines.forEach(b::define);
            return b.compileShader();
        });
    }

    /** Loads a pre-compiled SPIR-V file and reflects it, caching by path. */
    public static CompiledShader loadCompiledShader(String resourcePath) {
        return spirvCache.computeIfAbsent(resourcePath, p -> builder(p).loadCompiledShader());
    }

    /** Evicts a path from all caches, e.g. after hot-reload. Also removes all define variants. */
    public static void invalidate(String resourcePath) {
        compiledCache.remove(resourcePath);
        spirvCache.remove(resourcePath);
        defineVariantCache.keySet().removeIf(k -> k.startsWith(resourcePath + "|"));
    }

    /**
     * Compiles the shader at the given path and returns a ready-to-use ShaderInstance for the device.
     * The underlying CompiledShader is cached; the ShaderInstance is not.
     */
    public static io.github.yetyman.vulkan.shaders.ShaderInstance load(String resourcePath, io.github.yetyman.vulkan.VkDevice device) {
        return io.github.yetyman.vulkan.shaders.ShaderInstance.from(resourcePath, device);
    }

    private static String defineVariantCacheKey(String resourcePath, Map<String, String> defines) {
        StringBuilder sb = new StringBuilder(resourcePath).append('|');
        defines.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> sb.append(e.getKey()).append('=').append(e.getValue()).append(','));
        return sb.toString();
    }

    public static class Builder {
        private final String resourcePath;
        private final ShadercShaderKind shaderKind;
        private Function<ShaderCompileRequest, byte[]> compiler;
        private final Map<String, String> defines = new HashMap<>();
        private final List<String> includePaths = new ArrayList<>();
        private boolean optimize = false;
        private ShadercSourceLanguage sourceLanguage = ShadercSourceLanguage.shaderc_source_language_glsl;

        private String name;

        private Builder(String resourcePath) {
            this.resourcePath = resourcePath;
            this.shaderKind = inferShaderKind(resourcePath);
            this.compiler = defaultCompiler;
        }

        private Builder(ShadercShaderKind shaderKind) {
            this.resourcePath = "<inline>";
            this.shaderKind = shaderKind;
            this.compiler = defaultCompiler;
        }

        /**
         * Overrides the logical name used for caching and class generation.
         * Useful when resourcePath is a temp file or inline source rather than a stable classpath resource.
         */
        public Builder name(String name) { this.name = name; return this; }

        /** @return the logical name: the explicit override if set, the resource path if it's a real path, or null for inline shaders. */
        public String name() { return name != null ? name : (resourcePath.equals("<inline>") ? null : resourcePath); }

        /** Sets a custom compile pipeline, replacing the default shaderc compiler. */
        public Builder compiler(Function<ShaderCompileRequest, byte[]> compiler) {
            this.compiler = compiler;
            return this;
        }

        /** Sets the source language (default: GLSL). */
        public Builder sourceLanguage(ShadercSourceLanguage language) {
            this.sourceLanguage = language;
            return this;
        }

        /** Shorthand for {@code sourceLanguage(shaderc_source_language_hlsl)}. */
        public Builder hlsl() {
            this.sourceLanguage = ShadercSourceLanguage.shaderc_source_language_hlsl;
            return this;
        }

        /** Adds a preprocessor define. */
        public Builder define(String name, String value) {
            defines.put(name, value);
            return this;
        }

        /** Adds an include search path. */
        public Builder includePath(String path) {
            includePaths.add(path);
            return this;
        }

        /** Enables performance optimization during compilation. */
        public Builder optimize() {
            this.optimize = true;
            return this;
        }

        private String inlineSource;

        /** Sets inline GLSL/HLSL source, bypassing file/resource loading. */
        public Builder source(String source) {
            this.inlineSource = source;
            return this;
        }

        /** Compiles to raw SPIR-V bytes. */
        public byte[] compile() {
            return compiler.apply(new ShaderCompileRequest(
                resourcePath, shaderKind, sourceLanguage, defines, includePaths, optimize, inlineSource));
        }

        /** Loads a pre-compiled SPIR-V resource from the classpath or filesystem. */
        public byte[] loadSpirV() {
            try (InputStream is = ShaderLoader.class.getResourceAsStream(resourcePath)) {
                if (is != null) return is.readAllBytes();
                java.nio.file.Path fsPath = java.nio.file.Path.of(resourcePath);
                if (java.nio.file.Files.exists(fsPath))
                    return java.nio.file.Files.readAllBytes(fsPath);
                throw new FileNotFoundException(resourcePath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load SPIR-V: " + resourcePath, e);
            }
        }

        /** Compiles the shader and returns a reflected CompiledShader. */
        public CompiledShader compileShader() {
            byte[] spirv = compile();
            return new CompiledShader(spirv, new ShaderReflection(spirv), shaderKind, name(), defines);
        }

        /** Loads a pre-compiled SPIR-V and returns a reflected CompiledShader. */
        public CompiledShader loadCompiledShader() {
            byte[] spirv = loadSpirV();
            return new CompiledShader(spirv, new ShaderReflection(spirv), shaderKind, name(), defines);
        }
    }
    
    private static byte[] shadercCompile(ShaderCompileRequest request) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment compiler = ShadercFFM.shaderc_compiler_initialize();
            if (compiler.equals(MemorySegment.NULL)) {
                throw new RuntimeException("Failed to initialize shaderc compiler");
            }
            try {
                String source = request.inlineSource != null ? request.inlineSource : loadResource(request.resourcePath);
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
        InputStream is = ShaderLoader.class.getResourceAsStream(path);
        if (is == null) {
            java.nio.file.Path fsPath = java.nio.file.Path.of(path);
            if (java.nio.file.Files.exists(fsPath))
                return java.nio.file.Files.readString(fsPath, StandardCharsets.UTF_8);
            throw new FileNotFoundException(path);
        }
        try (is) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public record ShaderCompileRequest(String resourcePath, ShadercShaderKind shaderKind,
                                       ShadercSourceLanguage sourceLanguage, Map<String, String> defines,
                                       List<String> includePaths, boolean optimize, String inlineSource) {
        public ShaderCompileRequest(String resourcePath, ShadercShaderKind shaderKind,
                                    ShadercSourceLanguage sourceLanguage, Map<String, String> defines,
                                    List<String> includePaths, boolean optimize, String inlineSource) {
            this.resourcePath = resourcePath;
            this.shaderKind = shaderKind;
            this.sourceLanguage = sourceLanguage;
            this.defines = new HashMap<>(defines);
            this.includePaths = new ArrayList<>(includePaths);
            this.optimize = optimize;
            this.inlineSource = inlineSource;
        }

        /** Backwards-compatible constructor without inline source. */
        public ShaderCompileRequest(String resourcePath, ShadercShaderKind shaderKind,
                                    ShadercSourceLanguage sourceLanguage, Map<String, String> defines,
                                    List<String> includePaths, boolean optimize) {
            this(resourcePath, shaderKind, sourceLanguage, defines, includePaths, optimize, null);
        }
    }
    
    public static class ShaderReflection {
        // SpvReflectShaderModule is an opaque internal struct; the jextract binding only captures
        // the public header fields but the real C struct contains internal parsing state making it
        // several kilobytes. We use native calloc so the C runtime owns the memory.
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
            try { return (MemorySegment) CALLOC.invokeExact(count, size); }
            catch (Throwable t) { throw new RuntimeException("calloc failed", t); }
        }

        private static void nativeFree(MemorySegment ptr) {
            try { FREE.invokeExact(ptr); }
            catch (Throwable t) { throw new RuntimeException("free failed", t); }
        }

        private final Map<Integer, DescriptorSetInfo> descriptorSets;
        private final Map<String, DescriptorBindingInfo> bindingsByName;
        private final List<PushConstantBlockInfo> pushConstantBlocks;
        private final List<SpecializationConstantInfo> specializationConstants;
        private final Map<String, SpecializationConstantInfo> specializationConstantsByName;

        private ShaderReflection(byte[] spirv) {
            Map<Integer, DescriptorSetInfo> sets = new HashMap<>();
            Map<String, DescriptorBindingInfo> byName = new HashMap<>();
            List<PushConstantBlockInfo> pushConstants = new ArrayList<>();
            List<SpecializationConstantInfo> specConstants = new ArrayList<>();
            parseAll(spirv, sets, byName, pushConstants, specConstants);
            this.descriptorSets = sets;
            this.bindingsByName = byName;
            this.pushConstantBlocks = List.copyOf(pushConstants);
            this.specializationConstants = List.copyOf(specConstants);
            Map<String, SpecializationConstantInfo> byNameMap = new HashMap<>();
            for (SpecializationConstantInfo sc : specConstants) byNameMap.put(sc.name(), sc);
            this.specializationConstantsByName = Collections.unmodifiableMap(byNameMap);
        }
        
        public Map<Integer, DescriptorSetInfo> getDescriptorSets() { return Collections.unmodifiableMap(descriptorSets); }
        public DescriptorSetInfo getDescriptorSet(int set) { return descriptorSets.get(set); }
        public Set<Integer> getSetNumbers() { return descriptorSets.keySet(); }

        /** @return binding info by shader variable name, or null if not found */
        public DescriptorBindingInfo getBindingByName(String name) { return bindingsByName.get(name); }

        public List<PushConstantBlockInfo> getPushConstantBlocks() { return pushConstantBlocks; }

        public List<SpecializationConstantInfo> getSpecializationConstants() { return specializationConstants; }

        /** @return specialization constant info by shader variable name, or null if not found */
        public SpecializationConstantInfo getSpecializationConstant(String name) { return specializationConstantsByName.get(name); }

        /** @return the first push constant member with the given name across all blocks, or null */
        public StructMemberInfo getPushConstantMember(String name) {
            for (PushConstantBlockInfo block : pushConstantBlocks) {
                StructMemberInfo m = block.getMember(name);
                if (m != null) return m;
            }
            return null;
        }
        
        private void parseAll(byte[] spirv,
                              Map<Integer, DescriptorSetInfo> sets,
                              Map<String, DescriptorBindingInfo> byName,
                              List<PushConstantBlockInfo> pushConstants,
                              List<SpecializationConstantInfo> specializationConstants) {
            MemorySegment moduleRaw = nativeCalloc(1, SpvReflectShaderModule.sizeof());
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment module = moduleRaw.reinterpret(SpvReflectShaderModule.sizeof(), arena, null);
                MemorySegment codeSegment = arena.allocateFrom(JAVA_BYTE, spirv);
                SpirvReflectResult result = SpirvReflectResult.fromValue(
                    SpirvReflectFFM.spvReflectCreateShaderModule(spirv.length, codeSegment, module));
                if (result != SpirvReflectResult.SPV_REFLECT_RESULT_SUCCESS) {
                    Logger.warn("SPIRV-Reflect: spvReflectCreateShaderModule failed with " + result);
                    return;
                }
                try {
                    parseDescriptorSets(module, arena, sets, byName);
                    parsePushConstants(module, arena, pushConstants);
                    parseSpecializationConstants(module, arena, specializationConstants);
                } finally {
                    SpirvReflectFFM.spvReflectDestroyShaderModule(module);
                }
            } catch (Exception e) {
                Logger.warn("SPIRV-Reflect: failed to parse shader: " + e.getMessage());
            } finally {
                nativeFree(moduleRaw);
            }
        }

        private void parseDescriptorSets(MemorySegment module, Arena arena,
                                         Map<Integer, DescriptorSetInfo> sets,
                                         Map<String, DescriptorBindingInfo> byName) {
            MemorySegment countPtr = arena.allocate(JAVA_INT);
            SpirvReflectFFM.spvReflectEnumerateDescriptorSets(module, countPtr, MemorySegment.NULL);
            int setCount = countPtr.get(JAVA_INT, 0);
            if (setCount == 0) return;

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
                        MemorySegment namePtr = SpvReflectDescriptorBinding.name(bindingPtr);
                        String name = namePtr.equals(MemorySegment.NULL) ? null : namePtr.reinterpret(256, arena, null).getString(0);
                        // Parse block members for UBO/SSBO bindings
                        List<StructMemberInfo> blockMembers = new ArrayList<>();
                        SpirvReflectDescriptorType descType = SpirvReflectDescriptorType.fromValue(descriptorTypeVal);
                        if (descType == SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER
                                || descType == SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER) {
                            MemorySegment block = SpvReflectDescriptorBinding.block(bindingPtr);
                            int memberCount = SpvReflectBlockVariable.member_count(block);
                            if (memberCount > 0) {
                                MemorySegment membersPtr = SpvReflectBlockVariable.members(block)
                                    .reinterpret(SpvReflectBlockVariable.sizeof() * memberCount, arena, null);
                                for (int m = 0; m < memberCount; m++) {
                                    blockMembers.add(parseMember(SpvReflectBlockVariable.asSlice(membersPtr, m), arena));
                                }
                            }
                        }
                        DescriptorBindingInfo info = new DescriptorBindingInfo(
                            bindingIndex, descType, count, 0, name, blockMembers);
                        setInfo.addBinding(bindingIndex, info);
                        if (name != null && !name.isEmpty()) byName.put(name, info);
                    }
                }
                sets.put(setNumber, setInfo);
            }
        }

        private void parsePushConstants(MemorySegment module, Arena arena, List<PushConstantBlockInfo> out) {
            MemorySegment countPtr = arena.allocate(JAVA_INT);
            SpirvReflectFFM.spvReflectEnumeratePushConstantBlocks(module, countPtr, MemorySegment.NULL);
            int blockCount = countPtr.get(JAVA_INT, 0);
            if (blockCount == 0) return;

            MemorySegment blocksPtr = arena.allocate(ADDRESS, blockCount);
            SpirvReflectFFM.spvReflectEnumeratePushConstantBlocks(module, countPtr, blocksPtr);

            for (int i = 0; i < blockCount; i++) {
                MemorySegment blockPtr = blocksPtr.getAtIndex(ADDRESS, i)
                    .reinterpret(SpvReflectBlockVariable.sizeof(), arena, null);
                MemorySegment namePtr = SpvReflectBlockVariable.name(blockPtr);
                int offset = SpvReflectBlockVariable.offset(blockPtr);
                int size = SpvReflectBlockVariable.size(blockPtr);
                int memberCount = SpvReflectBlockVariable.member_count(blockPtr);
                String blockName = namePtr.equals(MemorySegment.NULL) ? "" : namePtr.reinterpret(256, arena, null).getString(0);
                List<StructMemberInfo> members = new ArrayList<>();
                if (memberCount > 0) {
                    MemorySegment membersPtr = SpvReflectBlockVariable.members(blockPtr)
                        .reinterpret(SpvReflectBlockVariable.sizeof() * memberCount, arena, null);
                    for (int m = 0; m < memberCount; m++) {
                        members.add(parseMember(SpvReflectBlockVariable.asSlice(membersPtr, m), arena));
                    }
                }
                // SPIRV-Reflect reports block variable members=0 for single-member push constant blocks.
                // The member name is in type_description->struct_member_name, and the block variable
                // itself is the leaf. Synthesize a single member from the block's own type_description.
                if (memberCount == 0) {
                    MemorySegment typeDescPtr = SpvReflectBlockVariable.type_description(blockPtr);
                    if (!typeDescPtr.equals(MemorySegment.NULL)) {
                        MemorySegment typeDesc = typeDescPtr.reinterpret(SpvReflectTypeDescription.sizeof(), arena, null);
                        int tdMemberCount = SpvReflectTypeDescription.member_count(typeDesc);
                        if (tdMemberCount > 0) {
                            MemorySegment tdMembersPtr = SpvReflectTypeDescription.members(typeDesc)
                                .reinterpret(SpvReflectTypeDescription.sizeof() * tdMemberCount, arena, null);
                            for (int m = 0; m < tdMemberCount; m++) {
                                MemorySegment tdMember = SpvReflectTypeDescription.asSlice(tdMembersPtr, m);
                                MemorySegment memberNamePtr = SpvReflectTypeDescription.struct_member_name(tdMember);
                                String memberName = memberNamePtr.equals(MemorySegment.NULL) ? "" :
                                    memberNamePtr.reinterpret(256, arena, null).getString(0);
                                int typeFlags = SpvReflectTypeDescription.type_flags(tdMember);
                                int memberSize = size / tdMemberCount;
                                int memberOffset = offset + m * memberSize;
                                members.add(new StructMemberInfo(memberName, memberOffset, memberSize, typeFlags, List.of()));
                            }
                        } else {
                            MemorySegment memberNamePtr = SpvReflectTypeDescription.struct_member_name(typeDesc);
                            String memberName = memberNamePtr.equals(MemorySegment.NULL) ? blockName :
                                memberNamePtr.reinterpret(256, arena, null).getString(0);
                            if (memberName.isEmpty()) memberName = blockName;
                            int typeFlags = SpvReflectTypeDescription.type_flags(typeDesc);
                            members.add(new StructMemberInfo(memberName, offset, size, typeFlags, List.of()));
                        }
                    }
                }
                out.add(new PushConstantBlockInfo(blockName, offset, size, List.copyOf(members)));
            }
        }

        private void parseSpecializationConstants(MemorySegment module, Arena arena, List<SpecializationConstantInfo> out) {
            MemorySegment countPtr = arena.allocate(JAVA_INT);
            SpirvReflectFFM.spvReflectEnumerateSpecializationConstants(module, countPtr, MemorySegment.NULL);
            int count = countPtr.get(JAVA_INT, 0);
            if (count == 0) return;

            MemorySegment constantsPtr = arena.allocate(ADDRESS, count);
            SpirvReflectFFM.spvReflectEnumerateSpecializationConstants(module, countPtr, constantsPtr);

            for (int i = 0; i < count; i++) {
                MemorySegment sc = constantsPtr.getAtIndex(ADDRESS, i)
                    .reinterpret(SpvReflectSpecializationConstant.sizeof(), arena, null);
                int constantId = SpvReflectSpecializationConstant.constant_id(sc);
                MemorySegment namePtr = SpvReflectSpecializationConstant.name(sc);
                String name = namePtr.equals(MemorySegment.NULL) ? "spec" + constantId
                    : namePtr.reinterpret(256, arena, null).getString(0);
                int typeFlags = 0;
                MemorySegment typeDescPtr = SpvReflectSpecializationConstant.type_description(sc);
                if (!typeDescPtr.equals(MemorySegment.NULL)) {
                    MemorySegment typeDesc = typeDescPtr.reinterpret(SpvReflectTypeDescription.sizeof(), arena, null);
                    // Use type_description->op to discriminate: 20=OpTypeBool, 21=OpTypeInt, 22=OpTypeFloat
                    int op = SpvReflectTypeDescription.op(typeDesc);
                    if      (op == 20) typeFlags = 0x00000002; // SPV_REFLECT_TYPE_FLAG_BOOL
                    else if (op == 21) typeFlags = 0x00000004; // SPV_REFLECT_TYPE_FLAG_INT
                    else if (op == 22) typeFlags = 0x00000008; // SPV_REFLECT_TYPE_FLAG_FLOAT
                    else               typeFlags = SpvReflectTypeDescription.type_flags(typeDesc);
                }
                int defaultValueSize = SpvReflectSpecializationConstant.default_value_size(sc);
                MemorySegment defaultValuePtr = SpvReflectSpecializationConstant.default_value(sc);
                int defaultValueBits = (!defaultValuePtr.equals(MemorySegment.NULL) && defaultValueSize >= 4)
                    ? defaultValuePtr.reinterpret(defaultValueSize, arena, null).get(JAVA_INT, 0)
                    : (int) defaultValuePtr.address();
                out.add(new SpecializationConstantInfo(name, constantId, typeFlags, defaultValueBits));
            }
        }

        private StructMemberInfo parseMember(MemorySegment memberPtr, Arena arena) {
            MemorySegment namePtr = SpvReflectBlockVariable.name(memberPtr);
            String name = namePtr.equals(MemorySegment.NULL) ? "" : namePtr.reinterpret(256, arena, null).getString(0);
            int offset = SpvReflectBlockVariable.offset(memberPtr);
            int size = SpvReflectBlockVariable.size(memberPtr);
            int memberCount = SpvReflectBlockVariable.member_count(memberPtr);
            int typeFlags = 0;
            String typeName = null;
            int scalarWidth = 0, scalarSignedness = 0, vectorComponents = 0, matrixColumns = 0, matrixRows = 0;
            MemorySegment typeDescPtr = SpvReflectBlockVariable.type_description(memberPtr);
            if (!typeDescPtr.equals(MemorySegment.NULL)) {
                MemorySegment typeDesc = typeDescPtr.reinterpret(SpvReflectTypeDescription.sizeof(), arena, null);
                typeFlags = SpvReflectTypeDescription.type_flags(typeDesc);
                MemorySegment typeNamePtr = SpvReflectTypeDescription.type_name(typeDesc);
                if (!typeNamePtr.equals(MemorySegment.NULL)) {
                    String raw = typeNamePtr.reinterpret(256, arena, null).getString(0);
                    if (!raw.isEmpty()) typeName = raw;
                }
                // For array-of-struct, type_name is null on the array; try struct_type_description
                // then type_description->members[0] (runtime array element type)
                if (typeName == null) {
                    MemorySegment structTypeDescPtr = SpvReflectTypeDescription.struct_type_description(typeDesc);
                    if (!structTypeDescPtr.equals(MemorySegment.NULL)) {
                        MemorySegment structTypeDesc = structTypeDescPtr.reinterpret(SpvReflectTypeDescription.sizeof(), arena, null);
                        MemorySegment elemTypeNamePtr = SpvReflectTypeDescription.type_name(structTypeDesc);
                        if (!elemTypeNamePtr.equals(MemorySegment.NULL)) {
                            String raw = elemTypeNamePtr.reinterpret(256, arena, null).getString(0);
                            if (!raw.isEmpty()) typeName = raw;
                        }
                    }
                }
                if (typeName == null) {
                    int tdMemberCount = SpvReflectTypeDescription.member_count(typeDesc);
                    MemorySegment tdMembersPtr = SpvReflectTypeDescription.members(typeDesc);
                    if (tdMemberCount > 0 && !tdMembersPtr.equals(MemorySegment.NULL)) {
                        MemorySegment firstTdMember = tdMembersPtr
                            .reinterpret(SpvReflectTypeDescription.sizeof(), arena, null);
                        MemorySegment elemTypeNamePtr = SpvReflectTypeDescription.type_name(firstTdMember);
                        if (!elemTypeNamePtr.equals(MemorySegment.NULL)) {
                            String raw = elemTypeNamePtr.reinterpret(256, arena, null).getString(0);
                            if (!raw.isEmpty()) typeName = raw;
                        }
                    }
                }
                // Numeric traits — scalar width/signedness, vector component count, matrix dimensions
                MemorySegment traits = SpvReflectTypeDescription.traits(typeDesc)
                    .reinterpret(SpvReflectNumericTraits.sizeof(), arena, null);
                MemorySegment scalar = SpvReflectNumericTraits.scalar(traits)
                    .reinterpret(SpvReflectNumericTraits.Scalar.sizeof(), arena, null);
                scalarWidth      = SpvReflectNumericTraits.Scalar.width(scalar);
                scalarSignedness = SpvReflectNumericTraits.Scalar.signedness(scalar);
                MemorySegment vector = SpvReflectNumericTraits.vector(traits)
                    .reinterpret(SpvReflectNumericTraits.Vector.sizeof(), arena, null);
                vectorComponents = SpvReflectNumericTraits.Vector.component_count(vector);
                MemorySegment matrix = SpvReflectNumericTraits.matrix(traits)
                    .reinterpret(SpvReflectNumericTraits.Matrix.sizeof(), arena, null);
                matrixColumns = SpvReflectNumericTraits.Matrix.column_count(matrix);
                matrixRows    = SpvReflectNumericTraits.Matrix.row_count(matrix);
            }
            List<StructMemberInfo> nested = new ArrayList<>();
            if (memberCount > 0) {
                MemorySegment nestedPtr = SpvReflectBlockVariable.members(memberPtr)
                    .reinterpret(SpvReflectBlockVariable.sizeof() * memberCount, arena, null);
                for (int m = 0; m < memberCount; m++) {
                    nested.add(parseMember(SpvReflectBlockVariable.asSlice(nestedPtr, m), arena));
                }
            }
            return new StructMemberInfo(name, offset, size, typeFlags, List.copyOf(nested), typeName,
                scalarWidth, scalarSignedness, vectorComponents, matrixColumns, matrixRows);
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
        
        public DescriptorBindingInfo getBinding(int binding) { return bindings.get(binding); }
        
        void addBinding(int binding, DescriptorBindingInfo info) { bindings.put(binding, info); }
    }
    
    public static class DescriptorBindingInfo {
        private final int binding;
        private final SpirvReflectDescriptorType descriptorType;
        private final int descriptorCount;
        private final int stageFlags;
        private final String name;
        private final List<StructMemberInfo> blockMembers;

        /** Constructor for reflection-parsed bindings (stage flags determined by shader stage). */
        public DescriptorBindingInfo(int binding, SpirvReflectDescriptorType descriptorType, int descriptorCount) {
            this(binding, descriptorType, descriptorCount, 0, null, List.of());
        }

        /** Constructor for reflection-parsed bindings with name. */
        public DescriptorBindingInfo(int binding, SpirvReflectDescriptorType descriptorType, int descriptorCount, String name) {
            this(binding, descriptorType, descriptorCount, 0, name, List.of());
        }

        /** Constructor for manually-added bindings with explicit stage flags. */
        public DescriptorBindingInfo(int binding, SpirvReflectDescriptorType descriptorType, int descriptorCount, int stageFlags) {
            this(binding, descriptorType, descriptorCount, stageFlags, null, List.of());
        }

        public DescriptorBindingInfo(int binding, SpirvReflectDescriptorType descriptorType, int descriptorCount, int stageFlags, String name) {
            this(binding, descriptorType, descriptorCount, stageFlags, name, List.of());
        }

        public DescriptorBindingInfo(int binding, SpirvReflectDescriptorType descriptorType, int descriptorCount, int stageFlags, String name, List<StructMemberInfo> blockMembers) {
            this.binding = binding;
            this.descriptorType = descriptorType;
            this.descriptorCount = descriptorCount;
            this.stageFlags = stageFlags;
            this.name = name;
            this.blockMembers = List.copyOf(blockMembers);
        }

        public int getBinding() { return binding; }
        public SpirvReflectDescriptorType getDescriptorType() { return descriptorType; }
        public int getDescriptorCount() { return descriptorCount; }
        /** @return explicit stage flags, or 0 if this binding uses the shader's default stage flags. */
        public int getStageFlags() { return stageFlags; }
        /** @return shader variable name from reflection, or null if not available */
        public String getName() { return name; }
        /** @return reflected struct members for UBO/SSBO block variables, empty for other types. */
        public List<StructMemberInfo> getBlockMembers() { return blockMembers; }
    }

    public static class PushConstantBlockInfo {
        private final String name;
        private final int offset;
        private final int size;
        private final List<StructMemberInfo> members;
        private final Map<String, StructMemberInfo> membersByName;

        public PushConstantBlockInfo(String name, int offset, int size, List<StructMemberInfo> members) {
            this.name = name;
            this.offset = offset;
            this.size = size;
            this.members = members;
            Map<String, StructMemberInfo> m = new HashMap<>();
            for (StructMemberInfo member : members) m.put(member.name(), member);
            this.membersByName = Collections.unmodifiableMap(m);
        }

        public String name() { return name; }
        public int offset() { return offset; }
        public int size() { return size; }
        public List<StructMemberInfo> members() { return members; }
        public StructMemberInfo getMember(String name) { return membersByName.get(name); }
    }

    public record StructMemberInfo(String name, int offset, int size, int typeFlags, List<StructMemberInfo> members, String typeName,
                                    int scalarWidth, int scalarSignedness, int vectorComponents, int matrixColumns, int matrixRows) {
        /** Convenience constructor without numeric traits. */
        public StructMemberInfo(String name, int offset, int size, int typeFlags, List<StructMemberInfo> members) {
            this(name, offset, size, typeFlags, members, null, 0, 0, 0, 0, 0);
        }
        /** Convenience constructor without numeric traits but with typeName. */
        public StructMemberInfo(String name, int offset, int size, int typeFlags, List<StructMemberInfo> members, String typeName) {
            this(name, offset, size, typeFlags, members, typeName, 0, 0, 0, 0, 0);
        }
        public boolean isFloat()  { return (typeFlags & 0x00000008) != 0; }
        public boolean isInt()    { return (typeFlags & 0x00000004) != 0; }
        public boolean isBool()   { return (typeFlags & 0x00000002) != 0; }
        public boolean isVector() { return (typeFlags & 0x00000100) != 0; }
        public boolean isMatrix() { return (typeFlags & 0x00000200) != 0; }
        public boolean isStruct() { return (typeFlags & 0x00000800) != 0; }
        public boolean isArray()  { return (typeFlags & 0x00010000) != 0; }
    }

    /**
     * Reflected specialization constant (layout(constant_id = N) const type name = default).
     * defaultValueBits is the raw 32-bit representation of the default value from SPIR-V.
     */
    public record SpecializationConstantInfo(String name, int constantId, int typeFlags, int defaultValueBits) {
        public boolean isFloat()  { return (typeFlags & 0x00000008) != 0; }
        public boolean isInt()    { return (typeFlags & 0x00000004) != 0; }
        public boolean isBool()   { return (typeFlags & 0x00000002) != 0; }

        /** @return the default value interpreted as float */
        public float defaultFloat()   { return Float.intBitsToFloat(defaultValueBits); }
        /** @return the default value interpreted as int */
        public int defaultInt()       { return defaultValueBits; }
        /** @return the default value interpreted as boolean (non-zero = true) */
        public boolean defaultBool()  { return defaultValueBits != 0; }
    }
}
