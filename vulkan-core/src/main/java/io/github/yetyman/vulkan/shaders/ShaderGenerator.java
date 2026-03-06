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
            // Walk the directory on the filesystem (not classpath) for --dir mode
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
        String source = emit(resourcePath, javaPackage, compiled);
        String className = className(resourcePath);
        Path outFile = outputDir.resolve(className + ".java");
        Files.writeString(outFile, source);
        System.out.println("Generated " + outFile);
    }

    private static String emit(String resourcePath, String pkg, CompiledShader compiled) {
        ShaderLoader.ShaderReflection reflection = compiled.getReflection();
        String className = className(resourcePath);
        StringBuilder sb = new StringBuilder();

        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import io.github.yetyman.vulkan.shaders.ShaderInstance;\n");
        sb.append("import io.github.yetyman.vulkan.shaders.PushConstant;\n");
        sb.append("import io.github.yetyman.vulkan.shaders.StorageBufferSlot;\n");
        sb.append("import io.github.yetyman.vulkan.shaders.TextureSlot;\n");
        sb.append("import io.github.yetyman.vulkan.shaders.UniformBufferSlot;\n");
        sb.append("import io.github.yetyman.vulkan.VkDevice;\n");
        sb.append("import io.github.yetyman.vulkan.VkCommandBuffer;\n");
        sb.append("import io.github.yetyman.vulkan.buffers.BufferWritable;\n\n");

        sb.append("/** Generated shader wrapper for ").append(resourcePath).append(" */\n");
        sb.append("public class ").append(className).append(" {\n\n");
        sb.append("    public final ShaderInstance shader;\n\n");

        // Push constants
        List<ShaderLoader.PushConstantBlockInfo> blocks = reflection.getPushConstantBlocks();
        if (!blocks.isEmpty()) {
            sb.append("    // Push Constants\n");
            for (ShaderLoader.PushConstantBlockInfo block : blocks) {
                for (ShaderLoader.StructMemberInfo member : block.members()) {
                    String javaType = inferJavaType(member);
                    sb.append("    public final PushConstant<").append(javaType).append("> ")
                      .append(member.name()).append(";\n");
                }
            }
            sb.append("\n");
        }

        // Descriptor sets — group by set number
        Map<Integer, List<ShaderLoader.DescriptorBindingInfo>> bySet = new TreeMap<>();
        for (Map.Entry<Integer, ShaderLoader.DescriptorSetInfo> entry : reflection.getDescriptorSets().entrySet()) {
            bySet.put(entry.getKey(), new ArrayList<>(entry.getValue().getBindings().values()));
        }

        for (Map.Entry<Integer, List<ShaderLoader.DescriptorBindingInfo>> entry : bySet.entrySet()) {
            sb.append("    // Set ").append(entry.getKey()).append("\n");
            for (ShaderLoader.DescriptorBindingInfo binding : entry.getValue()) {
                String name = binding.getName() != null ? binding.getName() : "binding" + binding.getBinding();
                String slotType = slotType(binding.getDescriptorType());
                sb.append("    public final ").append(slotType).append(" ").append(name).append(";\n");
            }
            sb.append("\n");
        }

        // Constructor
        sb.append("    public ").append(className).append("(VkDevice device) {\n");
        sb.append("        this.shader = ShaderInstance.from(\"").append(resourcePath).append("\", device);\n");

        for (ShaderLoader.PushConstantBlockInfo block : blocks) {
            for (ShaderLoader.StructMemberInfo member : block.members()) {
                String javaType = inferJavaType(member);
                sb.append("        this.").append(member.name())
                  .append(" = shader.getPushConstant(\"").append(member.name())
                  .append("\", ").append(javaType).append(".class);\n");
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

        // cmdDraw stub
        sb.append("    /** TODO: requires draw command builder */\n");
        sb.append("    public void cmdDraw(VkCommandBuffer commandBuffer) {\n");
        sb.append("        shader.flush(commandBuffer);\n");
        sb.append("        // TODO: vkCmdDraw / vkCmdDrawIndexed\n");
        sb.append("    }\n\n");

        // Mirrored struct records
        emitStructRecords(sb, blocks, pkg);

        sb.append("}\n");
        return sb.toString();
    }

    private static void emitStructRecords(StringBuilder sb, List<ShaderLoader.PushConstantBlockInfo> blocks, String pkg) {
        for (ShaderLoader.PushConstantBlockInfo block : blocks) {
            if (block.members().isEmpty()) continue;
            String recordName = toClassName(block.name().isEmpty() ? "PushConstants" : block.name());
            sb.append("    /** Mirrors push constant block '").append(block.name()).append("' (").append(block.size()).append(" bytes) */\n");
            sb.append("    public record ").append(recordName).append("(\n");
            List<ShaderLoader.StructMemberInfo> members = block.members();
            for (int i = 0; i < members.size(); i++) {
                ShaderLoader.StructMemberInfo m = members.get(i);
                sb.append("        ").append(inferJavaType(m)).append(" ").append(m.name());
                if (i < members.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("    ) implements BufferWritable {}\n\n");
        }
    }

    private static String slotType(SpirvReflectDescriptorType type) {
        if (type.equals(SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER))
            return "StorageBufferSlot<Object>";
        if (type.equals(SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER))
            return "UniformBufferSlot<Object>";
        if (type.equals(SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER))
            return "TextureSlot";
        return "DescriptorSlot";
    }

    private static String slotFactoryCall(ShaderLoader.DescriptorBindingInfo binding) {
        String name = binding.getName() != null ? binding.getName() : "binding" + binding.getBinding();
        SpirvReflectDescriptorType type = binding.getDescriptorType();
        if (type.equals(SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER))
            return "shader.getStorageBufferSlot(\"" + name + "\", Object.class)";
        if (type.equals(SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER))
            return "shader.getUniformBufferSlot(\"" + name + "\", Object.class)";
        if (type.equals(SpirvReflectDescriptorType.SPV_REFLECT_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER))
            return "shader.getTextureSlot(\"" + name + "\")";
        return "shader.getTextureSlot(\"" + name + "\")";
    }

    private static String inferJavaType(ShaderLoader.StructMemberInfo member) {
        if (member.isMatrix()) return "float[]";
        if (member.isVector()) return "float[]";
        if (member.isFloat()) return "Float";
        if (member.isInt()) return "Integer";
        if (member.isStruct()) return toClassName(member.name());
        return "Object";
    }

    private static String className(String resourcePath) {
        String filename = Paths.get(resourcePath).getFileName().toString();
        // e.g. model.vert -> ModelVertShader, triangle.frag -> TriangleFragShader
        String[] parts = filename.split("\\.");
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

    private static boolean isShaderFile(String path) {
        return path.endsWith(".vert") || path.endsWith(".frag") || path.endsWith(".comp")
            || path.endsWith(".geom") || path.endsWith(".tesc") || path.endsWith(".tese");
    }
}
