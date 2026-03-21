package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.spirv.enums.SpirvReflectDescriptorType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates a typed Java shader wrapper class from a compiled GLSL shader file.
 *
 * Usage:
 *   ShaderGenerator <shaderResourcePath> <outputDir> <javaPackage>
 *   ShaderGenerator --dir <shaderResourceDir> <outputDir> <javaPackage>
 *
 * The shader resource path must be accessible on the classpath (e.g. /shaders/model.vert).
 * Output file is named <ShaderFileName>Shader.java.
 */
public class ShaderGenerator {

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: ShaderGenerator <shaderResourcePath> <outputDir> <javaPackage>");
            System.err.println("       ShaderGenerator --dir <shaderResourceDir> <outputDir> <javaPackage>");
            System.exit(1);
        }

        if (args[0].equals("--dir")) {
            if (args.length < 4) {
                System.err.println("Usage: ShaderGenerator --dir <shaderResourceDir> <outputDir> <javaPackage>");
                System.exit(1);
            }
            String dir = args[1];
            Path outputDir = Paths.get(args[2]);
            String pkg = args[3];
            Files.createDirectories(outputDir);
            Path dirPath = Paths.get(dir);
            Files.walk(dirPath)
                .filter(p -> isShaderFile(p.toString()))
                .forEach(p -> {
                    try {
                        String resourcePath = "/" + dirPath.relativize(p).toString().replace('\\', '/');
                        generate(resourcePath, outputDir, pkg);
                    } catch (Exception e) {
                        System.err.println("Failed to generate for " + p + ": " + e.getMessage());
                    }
                });
        } else {
            String resourcePath = args[0];
            Path outputDir = Paths.get(args[1]);
            String pkg = args[2];
            Files.createDirectories(outputDir);
            generate(resourcePath, outputDir, pkg);
        }
    }

    public static void generate(String resourcePath, Path outputDir, String javaPackage) throws IOException {
        CompiledShader compiled = ShaderLoader.compileShader(resourcePath);
        generate(compiled, resourcePath, outputDir, javaPackage);
    }

    /**
     * Generates a shader wrapper class from an already-compiled shader.
     * Uses {@code compiled.getName()} as the logical name if set, otherwise falls back to {@code resourcePath}.
     */
    public static void generate(CompiledShader compiled, String resourcePath, Path outputDir, String javaPackage) throws IOException {
        String logicalName = compiled.getName() != null ? compiled.getName() : resourcePath;
        String source = emit(logicalName, javaPackage, compiled);
        String className = className(logicalName);
        Path outFile = outputDir.resolve(className + ".java");
        Files.writeString(outFile, source);
        System.out.println("Generated " + outFile);
    }

    private static String emit(String resourcePath, String pkg, CompiledShader compiled) {
        ShaderLoader.ShaderReflection reflection = compiled.getReflection();
        String className = className(resourcePath);
        List<ShaderLoader.SpecializationConstantInfo> specConstants = reflection.getSpecializationConstants();
        boolean hasSpecConstants = !specConstants.isEmpty();
        StringBuilder sb = new StringBuilder();

        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import io.github.yetyman.vulkan.shaders.CompiledShader;\n");
        sb.append("import io.github.yetyman.vulkan.shaders.ShaderInstance;\n");
        sb.append("import io.github.yetyman.vulkan.shaders.ShaderLoader;\n");
        sb.append("import io.github.yetyman.vulkan.shaders.PushConstant;\n");
        sb.append("import io.github.yetyman.vulkan.shaders.StorageBufferSlot;\n");
        sb.append("import io.github.yetyman.vulkan.shaders.TextureSlot;\n");
        sb.append("import io.github.yetyman.vulkan.shaders.UniformBufferSlot;\n");
        sb.append("import io.github.yetyman.vulkan.VkDevice;\n");
        sb.append("import io.github.yetyman.vulkan.VkCommandBuffer;\n");
        sb.append("import io.github.yetyman.vulkan.buffers.BufferWritable;\n");
        sb.append("import java.nio.ByteBuffer;\n");
        sb.append("import java.util.Map;\n");
        sb.append("\n");

        sb.append("/** Generated shader wrapper for ").append(resourcePath).append(" */\n");
        sb.append("public class ").append(className).append(" implements AutoCloseable {\n\n");
        sb.append("    public final ShaderInstance shader;\n\n");

        // Specialization constant defaults as public static finals
        if (!specConstants.isEmpty()) {
            sb.append("    // Specialization constant defaults\n");
            for (ShaderLoader.SpecializationConstantInfo sc : specConstants) {
                String constName = "DEFAULT_" + toConstantName(sc.name());
                sb.append("    public static final ").append(specJavaType(sc)).append(" ").append(constName)
                  .append(" = ").append(specDefaultLiteral(sc)).append(";\n");
            }
            sb.append("\n");
        }

        // Preprocessor defines as public static final String constants
        Map<String, String> defines = compiled.getDefines();
        if (!defines.isEmpty()) {
            sb.append("    // Preprocessor defines\n");
            new TreeMap<>(defines).forEach((k, v) -> {
                sb.append("    public static final String ").append(toConstantName(k))
                  .append(" = \"").append(v).append("\";\n");
            });
            sb.append("\n");
        }
        // Push constants
        List<ShaderLoader.PushConstantBlockInfo> blocks = reflection.getPushConstantBlocks();
        if (!blocks.isEmpty()) {
            sb.append("    // Push Constants\n");
            for (ShaderLoader.PushConstantBlockInfo block : blocks) {
                for (ShaderLoader.StructMemberInfo member : block.members()) {
                    String javaType = inferBoxedType(member);
                    sb.append("    public final PushConstant<").append(javaType).append("> ")
                      .append(member.name()).append(";\n");
                }
            }
            sb.append("\n");
        }

        // Descriptor sets
        Map<Integer, List<ShaderLoader.DescriptorBindingInfo>> bySet = new TreeMap<>();
        for (Map.Entry<Integer, ShaderLoader.DescriptorSetInfo> entry : reflection.getDescriptorSets().entrySet()) {
            bySet.put(entry.getKey(), new ArrayList<>(entry.getValue().getBindings().values()));
        }
        for (Map.Entry<Integer, List<ShaderLoader.DescriptorBindingInfo>> entry : bySet.entrySet()) {
            sb.append("    // Set ").append(entry.getKey()).append("\n");
            for (ShaderLoader.DescriptorBindingInfo binding : entry.getValue()) {
                String name = binding.getName() != null ? binding.getName() : "binding" + binding.getBinding();
                sb.append("    public final ").append(slotType(binding.getDescriptorType())).append(" ").append(name).append(";\n");
            }
            sb.append("\n");
        }

        // Specialization constant instance fields (set at build time)
        if (!specConstants.isEmpty()) {
            sb.append("    // Specialization constant values\n");
            for (ShaderLoader.SpecializationConstantInfo sc : specConstants) {
                sb.append("    public final ").append(specJavaType(sc)).append(" ").append(sc.name()).append(";\n");
            }
            sb.append("\n");
        }

        // Private constructor — called only from Builder.build()
        sb.append("    private ").append(className).append("(ShaderInstance shader");
        if (!specConstants.isEmpty()) {
            for (ShaderLoader.SpecializationConstantInfo sc : specConstants) {
                sb.append(", ").append(specJavaType(sc)).append(" ").append(sc.name());
            }
        }
        sb.append(") {\n");
        sb.append("        this.shader = shader;\n");
        for (ShaderLoader.SpecializationConstantInfo sc : specConstants) {
            sb.append("        this.").append(sc.name()).append(" = ").append(sc.name()).append(";\n");
        }
        for (ShaderLoader.PushConstantBlockInfo block : blocks) {
            for (ShaderLoader.StructMemberInfo member : block.members()) {
                String javaType = inferBoxedType(member);
                String classLiteral = javaType.endsWith("[]") ? "Object.class" : javaType + ".class";
                sb.append("        this.").append(member.name())
                  .append(" = shader.getPushConstant(\"").append(member.name())
                  .append("\", ").append(classLiteral).append(");\n");
            }
        }
        for (Map.Entry<Integer, List<ShaderLoader.DescriptorBindingInfo>> entry : bySet.entrySet()) {
            for (ShaderLoader.DescriptorBindingInfo binding : entry.getValue()) {
                String name = binding.getName() != null ? binding.getName() : "binding" + binding.getBinding();
                sb.append("        this.").append(name).append(" = ")
                  .append(slotFactoryCall(binding)).append(";\n");
            }
        }
        sb.append("    }\n\n");

        // flush + close
        sb.append("    public void flush(VkCommandBuffer commandBuffer) { shader.flush(commandBuffer); }\n\n");
        sb.append("    @Override\n");
        sb.append("    public void close() { shader.close(); }\n\n");

        // Specialization constant getters — return instance fields directly
        if (!specConstants.isEmpty()) {
            for (ShaderLoader.SpecializationConstantInfo sc : specConstants) {
                String type = specJavaType(sc);
                String methodName = sc.isBool()
                    ? "is" + Character.toUpperCase(sc.name().charAt(0)) + sc.name().substring(1)
                    : "get" + Character.toUpperCase(sc.name().charAt(0)) + sc.name().substring(1);
                sb.append("    public ").append(type).append(" ").append(methodName)
                  .append("() { return ").append(sc.name()).append("; }\n");
            }
            sb.append("\n");
        }

        // Struct records for push constant blocks
        emitStructRecords(sb, blocks);

        // Companion records for UBO/SSBO bindings
        emitBufferRecords(sb, bySet);

        // Builder
        emitBuilder(sb, className, resourcePath, specConstants);

        sb.append("}\n");
        return sb.toString();
    }

    private static void emitBuilder(StringBuilder sb, String className, String resourcePath,
                                    List<ShaderLoader.SpecializationConstantInfo> specConstants) {
        sb.append("    /** @return a new Builder for configuring and creating a ").append(className).append(" instance. */\n");
        sb.append("    public static Builder builder(VkDevice device) { return new Builder(device); }\n\n");

        sb.append("    public static class Builder {\n");
        sb.append("        private final VkDevice device;\n");
        sb.append("        private Map<String, String> defines = Map.of();\n");
        // spec constant fields with defaults referencing the static finals
        for (ShaderLoader.SpecializationConstantInfo sc : specConstants) {
            sb.append("        private ").append(specJavaType(sc)).append(" ").append(sc.name())
              .append(" = ").append("DEFAULT_" + toConstantName(sc.name())).append(";\n");
        }
        sb.append("\n");
        sb.append("        private Builder(VkDevice device) { this.device = device; }\n\n");

        sb.append("        /** Sets preprocessor defines for this shader variant. */\n");
        sb.append("        public Builder defines(Map<String, String> defines) { this.defines = defines; return this; }\n");

        // fluent setter per spec constant
        for (ShaderLoader.SpecializationConstantInfo sc : specConstants) {
            sb.append("\n        /** Sets specialization constant '").append(sc.name()).append("' (default: ").append(specDefaultLiteral(sc)).append("). */\n");
            sb.append("        public Builder ").append(sc.name()).append("(").append(specJavaType(sc)).append(" value) ")
              .append("{ this.").append(sc.name()).append(" = value; return this; }\n");
        }

        sb.append("\n        public ").append(className).append(" build() {\n");
        if (specConstants.isEmpty()) {
            sb.append("            CompiledShader compiled = ShaderLoader.compileShader(\"").append(resourcePath).append("\", defines);\n");
            sb.append("            return new ").append(className).append("(compiled.createInstance(device));\n");
        } else {
            sb.append("            CompiledShader compiled = ShaderLoader.compileShader(\"").append(resourcePath).append("\", defines);\n");
            sb.append("            ShaderInstance instance = compiled.instanceBuilder(device)\n");
            for (ShaderLoader.SpecializationConstantInfo sc : specConstants) {
                sb.append("                .specialize(\"").append(sc.name()).append("\", ").append(sc.name()).append(")\n");
            }
            sb.append("                .build();\n");
            sb.append("            return new ").append(className).append("(instance");
            for (ShaderLoader.SpecializationConstantInfo sc : specConstants) {
                sb.append(", ").append(sc.name());
            }
            sb.append(");\n");
        }
        sb.append("        }\n");
        sb.append("    }\n\n");
    }

    private static void emitBufferRecords(StringBuilder sb,
                                           Map<Integer, List<ShaderLoader.DescriptorBindingInfo>> bySet) {
        // Collect all nested struct types that need their own record, keyed by type name.
        // Use LinkedHashMap to preserve encounter order (nested records before their parents).
        java.util.LinkedHashMap<String, List<ShaderLoader.StructMemberInfo>> nestedStructs = new java.util.LinkedHashMap<>();
        for (List<ShaderLoader.DescriptorBindingInfo> bindings : bySet.values()) {
            for (ShaderLoader.DescriptorBindingInfo binding : bindings) {
                if (binding.getBlockMembers().isEmpty()) continue;
                collectNestedStructs(binding.getBlockMembers(), nestedStructs);
            }
        }

        // Emit nested struct records first
        for (Map.Entry<String, List<ShaderLoader.StructMemberInfo>> entry : nestedStructs.entrySet()) {
            emitRecord(sb, entry.getKey(), entry.getValue(), "struct");
        }

        // Emit top-level block records
        for (List<ShaderLoader.DescriptorBindingInfo> bindings : bySet.values()) {
            for (ShaderLoader.DescriptorBindingInfo binding : bindings) {
                List<ShaderLoader.StructMemberInfo> members = binding.getBlockMembers();
                if (members.isEmpty()) continue;
                boolean isStorage = binding.getDescriptorType()
                    .equals(SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER);
                String bindingName = binding.getName() != null ? binding.getName() : "binding" + binding.getBinding();
                // For SSBOs, SPIRV-Reflect may flatten a runtime array: the single block member
                // represents the element type (e.g. Light), not a wrapper. Treat it as Light[].
                List<ShaderLoader.StructMemberInfo> effectiveMembers = members;
                if (isStorage && members.size() == 1) {
                    ShaderLoader.StructMemberInfo sole = members.get(0);
                    if (sole.typeName() != null && !sole.members().isEmpty()) {
                        // Synthesize a single-field member list with the array type
                        effectiveMembers = List.of(new ShaderLoader.StructMemberInfo(
                            sole.name(), sole.offset(), sole.size(),
                            sole.typeFlags() | 0x00010000, // force isArray
                            sole.members(), sole.typeName(),
                            sole.scalarWidth(), sole.scalarSignedness(),
                            sole.vectorComponents(), sole.matrixColumns(), sole.matrixRows()));
                    }
                }
                String recordName = toClassName(bindingName);
                emitRecord(sb, recordName, effectiveMembers, isStorage ? "SSBO" : "UBO",
                    isStorage && members.size() == 1 && effectiveMembers != members);
            }
        }
    }

    private static void collectNestedStructs(List<ShaderLoader.StructMemberInfo> members,
                                              java.util.LinkedHashMap<String, List<ShaderLoader.StructMemberInfo>> out) {
        for (ShaderLoader.StructMemberInfo m : members) {
            if (!m.members().isEmpty() && m.typeName() != null && !m.typeName().isEmpty()) {
                collectNestedStructs(m.members(), out);
                out.putIfAbsent(m.typeName(), m.members());
            }
        }
    }

    private static void emitRecord(StringBuilder sb, String recordName, List<ShaderLoader.StructMemberInfo> members,
                                   String kind) {
        emitRecord(sb, recordName, members, kind, false);
    }

    private static void emitRecord(StringBuilder sb, String recordName, List<ShaderLoader.StructMemberInfo> members,
                                   String kind, boolean runtimeArray) {
        sb.append("    /** Mirrors ").append(kind).append(" '").append(recordName).append("'. */\n");
        sb.append("    public static class ").append(recordName).append(" implements BufferWritable {\n");
        for (ShaderLoader.StructMemberInfo m : members) {
            String glslComment = glslTypeName(m);
            sb.append("        public ").append(inferJavaType(m)).append(" ").append(m.name()).append(";");
            if (glslComment != null) sb.append(" // ").append(glslComment);
            sb.append("\n");
        }
        if (runtimeArray) {
            ShaderLoader.StructMemberInfo arrayMember = members.get(0);
            int elemSize = arrayMember.size();
            sb.append("        @Override public int byteSize() { return ").append(arrayMember.name())
              .append(".length * ").append(elemSize).append("; }\n");
        } else {
            sb.append("        @Override public int byteSize() { return ").append(totalSize(members)).append("; }\n");
        }
        sb.append("        @Override public void writeTo(ByteBuffer buf) {\n");
        for (ShaderLoader.StructMemberInfo m : members) {
            emitWriteField(sb, m, "            ");
        }
        sb.append("        }\n");
        sb.append("        @Override public void readFrom(ByteBuffer buf) {\n");
        for (ShaderLoader.StructMemberInfo m : members) {
            emitReadField(sb, m, "            ");
        }
        sb.append("        }\n");
        sb.append("    }\n\n");
    }

    private static void emitWriteField(StringBuilder sb, ShaderLoader.StructMemberInfo m, String indent) {
        String field = m.name();
        String type = inferJavaType(m);
        if (type.equals("float[]") || type.equals("int[]") || type.equals("double[]")) {
            String putMethod = type.equals("int[]") ? "putInt" : type.equals("double[]") ? "putDouble" : "putFloat";
            sb.append(indent).append("for (var v : ").append(field).append(") buf.").append(putMethod).append("(v);\n");
        } else if (type.equals("float"))   { sb.append(indent).append("buf.putFloat(").append(field).append(");\n");
        } else if (type.equals("int"))     { sb.append(indent).append("buf.putInt(").append(field).append(");\n");
        } else if (type.equals("double"))  { sb.append(indent).append("buf.putDouble(").append(field).append(");\n");
        } else if (type.equals("boolean")) { sb.append(indent).append("buf.putInt(").append(field).append(" ? 1 : 0);\n");
        } else if (type.endsWith("[]"))    { sb.append(indent).append("for (var e : ").append(field).append(") e.writeTo(buf);\n");
        } else                             { sb.append(indent).append(field).append(".writeTo(buf);\n");
        }
    }

    private static void emitReadField(StringBuilder sb, ShaderLoader.StructMemberInfo m, String indent) {
        String field = m.name();
        String type = inferJavaType(m);
        if (type.equals("float[]") || type.equals("int[]") || type.equals("double[]")) {
            int count = m.size() / (type.equals("double[]") ? 8 : 4);
            String getMethod = type.equals("int[]") ? "getInt" : type.equals("double[]") ? "getDouble" : "getFloat";
            sb.append(indent).append(field).append(" = new ").append(type, 0, type.length() - 2).append("[").append(count).append("];\n");
            sb.append(indent).append("for (int i = 0; i < ").append(count).append("; i++) ").append(field).append("[i] = buf.").append(getMethod).append("();\n");
        } else if (type.equals("float"))   { sb.append(indent).append(field).append(" = buf.getFloat();\n");
        } else if (type.equals("int"))     { sb.append(indent).append(field).append(" = buf.getInt();\n");
        } else if (type.equals("double"))  { sb.append(indent).append(field).append(" = buf.getDouble();\n");
        } else if (type.equals("boolean")) { sb.append(indent).append(field).append(" = buf.getInt() != 0;\n");
        } else if (type.endsWith("[]")) {
            String elemType = type.substring(0, type.length() - 2);
            sb.append(indent).append("for (int i = 0; i < ").append(field).append(".length; i++) { ").append(field).append("[i] = new ").append(elemType).append("(); ").append(field).append("[i].readFrom(buf); }\n");
        } else {
            sb.append(indent).append("if (").append(field).append(" == null) ").append(field).append(" = new ").append(type).append("();\n");
            sb.append(indent).append(field).append(".readFrom(buf);\n");
        }
    }

    private static int totalSize(List<ShaderLoader.StructMemberInfo> members) {
        return members.stream().mapToInt(ShaderLoader.StructMemberInfo::size).sum();
    }

    private static void emitStructRecords(StringBuilder sb, List<ShaderLoader.PushConstantBlockInfo> blocks) {
        for (ShaderLoader.PushConstantBlockInfo block : blocks) {
            if (block.members().isEmpty()) continue;
            String recordName = toClassName(block.name().isEmpty() ? "PushConstants" : block.name());
            emitRecord(sb, recordName, block.members(), "push constant block");
        }
    }

    // ---- Type helpers ----

    private static String specJavaType(ShaderLoader.SpecializationConstantInfo sc) {
        if (sc.isBool())  return "boolean";
        if (sc.isFloat()) return "float";
        return "int";
    }

    private static String specDefaultLiteral(ShaderLoader.SpecializationConstantInfo sc) {
        if (sc.isBool())  return sc.defaultBool() ? "true" : "false";
        if (sc.isFloat()) return sc.defaultFloat() + "f";
        return String.valueOf(sc.defaultInt());
    }

    private static String slotType(SpirvReflectDescriptorType type) {
        if (type.equals(SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER))
            return "StorageBufferSlot";
        if (type.equals(SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER))
            return "UniformBufferSlot";
        if (type.equals(SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER))
            return "TextureSlot";
        return "DescriptorSlot";
    }

    private static String slotFactoryCall(ShaderLoader.DescriptorBindingInfo binding) {
        String name = binding.getName() != null ? binding.getName() : "binding" + binding.getBinding();
        SpirvReflectDescriptorType type = binding.getDescriptorType();
        if (type.equals(SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER))
            return "shader.getStorageBufferSlot(\"" + name + "\")";
        if (type.equals(SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER))
            return "shader.getUniformBufferSlot(\"" + name + "\")";
        if (type.equals(SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER))
            return "shader.getTextureSlot(\"" + name + "\")";
        return "shader.getTextureSlot(\"" + name + "\")";
    }

    private static String inferJavaType(ShaderLoader.StructMemberInfo member) {
        if (member.isArray()) {
            if (member.typeName() != null) return member.typeName() + "[]";
            if (!member.members().isEmpty()) return toClassName(member.name()) + "[]";
            return scalarArrayType(member) + "[]";
        }
        if (member.isMatrix()) return matrixJavaType(member);
        if (member.isVector()) return vectorJavaType(member);
        // typeName + nested members = struct (flags may be 0 for SSBO block vars)
        if (member.typeName() != null && !member.members().isEmpty()) return member.typeName();
        if (member.isStruct()) return member.typeName() != null ? member.typeName() : toClassName(member.name());
        if (member.isBool())   return "boolean";
        if (member.isInt())    return "int";
        if (member.isFloat())  return member.scalarWidth() == 64 ? "double" : "float";
        int size = member.size();
        if (size == 4)  return "float";
        if (size == 8)  return "float[]";
        if (size == 12) return "float[]";
        if (size == 16) return "float[]";
        if (size == 64) return "float[]";
        return "float[]";
    }

    private static String vectorJavaType(ShaderLoader.StructMemberInfo m) {
        int components = m.vectorComponents() > 0 ? m.vectorComponents() : m.size() / 4;
        if (m.isBool())  return "boolean[]";
        if (m.isInt())   return "int[]";
        return "float[]";
    }

    private static String matrixJavaType(ShaderLoader.StructMemberInfo m) {
        return m.scalarWidth() == 64 ? "double[]" : "float[]";
    }

    private static String scalarArrayType(ShaderLoader.StructMemberInfo m) {
        if (m.isBool())  return "boolean";
        if (m.isInt())   return "int";
        if (m.isFloat()) return m.scalarWidth() == 64 ? "double" : "float";
        return "float";
    }

    /**
     * Returns a GLSL type name comment for array[] fields, or null if the field needs no comment
     * (i.e. it's a plain scalar or named struct type that is already self-documenting).
     */
    private static String glslTypeName(ShaderLoader.StructMemberInfo m) {
        if (m.isMatrix()) {
            int cols = m.matrixColumns() > 0 ? m.matrixColumns() : (int) Math.sqrt(m.size() / (m.scalarWidth() == 64 ? 8 : 4));
            int rows = m.matrixRows() > 0 ? m.matrixRows() : cols;
            String prefix = m.scalarWidth() == 64 ? "dmat" : "mat";
            return cols == rows ? prefix + cols + " column-major" : prefix + cols + "x" + rows + " column-major";
        }
        if (m.isVector()) {
            int components = m.vectorComponents() > 0 ? m.vectorComponents() : m.size() / (m.scalarWidth() == 64 ? 8 : 4);
            if (m.isBool())  return "bvec" + components;
            if (m.isInt())   return (m.scalarSignedness() == 0 ? "u" : "") + "ivec" + components;
            if (m.scalarWidth() == 64) return "dvec" + components;
            return "vec" + components;
        }
        if (m.isArray() && m.typeName() == null) {
            int elemBytes = m.scalarWidth() == 64 ? 8 : 4;
            int count = elemBytes > 0 ? m.size() / elemBytes : 0;
            String elem = m.isBool() ? "bool" : m.isInt() ? "int" : m.scalarWidth() == 64 ? "double" : "float";
            return elem + "[" + count + "]";
        }
        // Flags may be zero for SSBO/UBO block members — fall back to size heuristics
        if (m.typeName() == null && m.members().isEmpty()) {
            return switch (m.size()) {
                case 8  -> "vec2";
                case 12 -> "vec3";
                case 16 -> "vec4";
                case 32 -> "mat2 / vec4[2]";
                case 36 -> "mat3 (padded)";
                case 48 -> "mat4x3 / vec4[3]";
                case 64 -> "mat4 column-major";
                default -> null;
            };
        }
        return null;
    }

    /** Boxed version of inferJavaType for use as generic type parameters (e.g. PushConstant<T>). */
    private static String inferBoxedType(ShaderLoader.StructMemberInfo member) {
        String t = inferJavaType(member);
        if (t.equals("float"))   return "Float";
        if (t.equals("int"))     return "Integer";
        if (t.equals("double"))  return "Double";
        if (t.equals("boolean")) return "Boolean";
        return t;
    }

    private static String className(String resourcePath) {
        String filename = Paths.get(resourcePath).getFileName().toString();
        String[] parts = filename.split("[.\\-_]");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        name.append("Shader");
        return name.toString();
    }

    private static String toClassName(String name) {
        if (name == null || name.isEmpty()) return "Unknown";
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Converts a camelCase name to SCREAMING_SNAKE_CASE for static final fields. */
    private static String toConstantName(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && Character.isLowerCase(name.charAt(i - 1))) sb.append('_');
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    private static boolean isShaderFile(String path) {
        return path.endsWith(".vert") || path.endsWith(".frag") || path.endsWith(".comp")
            || path.endsWith(".geom") || path.endsWith(".tesc") || path.endsWith(".tese");
    }
}
