package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.shaderc.generated.*;
import io.github.yetyman.shaderc.enums.*;
import io.github.yetyman.spirv.SpirvReflectLoader;
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

        private ShaderReflection(byte[] spirv) {
            Map<Integer, DescriptorSetInfo> sets = new HashMap<>();
            Map<String, DescriptorBindingInfo> byName = new HashMap<>();
            List<PushConstantBlockInfo> pushConstants = new ArrayList<>();
            parseAll(spirv, sets, byName, pushConstants);
            this.descriptorSets = sets;
            this.bindingsByName = byName;
            this.pushConstantBlocks = List.copyOf(pushConstants);
        }
        
        public Map<Integer, DescriptorSetInfo> getDescriptorSets() { return Collections.unmodifiableMap(descriptorSets); }
        public DescriptorSetInfo getDescriptorSet(int set) { return descriptorSets.get(set); }
        public Set<Integer> getSetNumbers() { return descriptorSets.keySet(); }

        /** @return binding info by shader variable name, or null if not found */
        public DescriptorBindingInfo getBindingByName(String name) { return bindingsByName.get(name); }

        public List<PushConstantBlockInfo> getPushConstantBlocks() { return pushConstantBlocks; }

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
                              List<PushConstantBlockInfo> pushConstants) {
            MemorySegment module = nativeCalloc(1, SpvReflectShaderModule.sizeof());
            try (Arena arena = Arena.ofConfined()) {
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
                } finally {
                    SpirvReflectFFM.spvReflectDestroyShaderModule(module);
                }
            } catch (Exception e) {
                Logger.warn("SPIRV-Reflect: failed to parse shader: " + e.getMessage());
            } finally {
                nativeFree(module);
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
                        DescriptorBindingInfo info = new DescriptorBindingInfo(
                            bindingIndex, SpirvReflectDescriptorType.fromValue(descriptorTypeVal), count, name);
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
                String blockName = namePtr.equals(MemorySegment.NULL) ? "" : namePtr.reinterpret(256, arena, null).getString(0);
                int offset = SpvReflectBlockVariable.offset(blockPtr);
                int size = SpvReflectBlockVariable.size(blockPtr);
                int memberCount = SpvReflectBlockVariable.member_count(blockPtr);
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
                            // Multi-member block exposed only through type_description->members
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
                            // Single-member leaf: struct_member_name on the block's own type_description is the member name
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

        private StructMemberInfo parseMember(MemorySegment memberPtr, Arena arena) {
            MemorySegment namePtr = SpvReflectBlockVariable.name(memberPtr);
            String name = namePtr.equals(MemorySegment.NULL) ? "" : namePtr.reinterpret(256, arena, null).getString(0);
            int offset = SpvReflectBlockVariable.offset(memberPtr);
            int size = SpvReflectBlockVariable.size(memberPtr);
            int memberCount = SpvReflectBlockVariable.member_count(memberPtr);
            int typeFlags = 0;
            MemorySegment typeDescPtr = SpvReflectBlockVariable.type_description(memberPtr);
            if (!typeDescPtr.equals(MemorySegment.NULL)) {
                MemorySegment typeDesc = typeDescPtr.reinterpret(SpvReflectTypeDescription.sizeof(), arena, null);
                typeFlags = SpvReflectTypeDescription.type_flags(typeDesc);
            }
            List<StructMemberInfo> nested = new ArrayList<>();
            if (memberCount > 0) {
                MemorySegment nestedPtr = SpvReflectBlockVariable.members(memberPtr)
                    .reinterpret(SpvReflectBlockVariable.sizeof() * memberCount, arena, null);
                for (int m = 0; m < memberCount; m++) {
                    nested.add(parseMember(SpvReflectBlockVariable.asSlice(nestedPtr, m), arena));
                }
            }
            return new StructMemberInfo(name, offset, size, typeFlags, List.copyOf(nested));
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

        /** Constructor for reflection-parsed bindings (stage flags determined by shader stage). */
        public DescriptorBindingInfo(int binding, SpirvReflectDescriptorType descriptorType, int descriptorCount) {
            this(binding, descriptorType, descriptorCount, 0, null);
        }

        /** Constructor for reflection-parsed bindings with name. */
        public DescriptorBindingInfo(int binding, SpirvReflectDescriptorType descriptorType, int descriptorCount, String name) {
            this(binding, descriptorType, descriptorCount, 0, name);
        }

        /** Constructor for manually-added bindings with explicit stage flags. */
        public DescriptorBindingInfo(int binding, SpirvReflectDescriptorType descriptorType, int descriptorCount, int stageFlags) {
            this(binding, descriptorType, descriptorCount, stageFlags, null);
        }

        public DescriptorBindingInfo(int binding, SpirvReflectDescriptorType descriptorType, int descriptorCount, int stageFlags, String name) {
            this.binding = binding;
            this.descriptorType = descriptorType;
            this.descriptorCount = descriptorCount;
            this.stageFlags = stageFlags;
            this.name = name;
        }

        public int getBinding() { return binding; }
        public SpirvReflectDescriptorType getDescriptorType() { return descriptorType; }
        public int getDescriptorCount() { return descriptorCount; }
        /** @return explicit stage flags, or 0 if this binding uses the shader's default stage flags. */
        public int getStageFlags() { return stageFlags; }
        /** @return shader variable name from reflection, or null if not available */
        public String getName() { return name; }
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

    public record StructMemberInfo(String name, int offset, int size, int typeFlags, List<StructMemberInfo> members) {
        public boolean isFloat()  { return (typeFlags & 0x00000008) != 0; }
        public boolean isInt()    { return (typeFlags & 0x00000004) != 0; }
        public boolean isVector() { return (typeFlags & 0x00000100) != 0; }
        public boolean isMatrix() { return (typeFlags & 0x00000200) != 0; }
        public boolean isStruct() { return (typeFlags & 0x00000800) != 0; }
        public boolean isArray()  { return (typeFlags & 0x00010000) != 0; }
    }
}
