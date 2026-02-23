package io.github.yetyman.enumgen;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class EnumGenerator {
    
    private static final Pattern CONSTANT_PATTERN = Pattern.compile("private static final int (SPV_REFLECT_\\w+) = \\(int\\)(-?\\d+)L;");
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: EnumGenerator <input-dir> <output-dir>");
            System.exit(1);
        }
        
        Path inputDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);
        
        Map<String, Map<String, Long>> enums = new HashMap<>();
        
        // Parse all generated files
        Files.walk(inputDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(file -> parseFile(file, enums));
        
        // Generate enum classes
        Files.createDirectories(outputDir);
        for (Map.Entry<String, Map<String, Long>> entry : enums.entrySet()) {
            generateEnumClass(entry.getKey(), entry.getValue(), outputDir);
        }
        
        System.out.println("Generated " + enums.size() + " enum classes");
    }
    
    private static void parseFile(Path file, Map<String, Map<String, Long>> enums) {
        try {
            String content = Files.readString(file);
            Matcher matcher = CONSTANT_PATTERN.matcher(content);
            
            while (matcher.find()) {
                String constantName = matcher.group(1);
                long value = Long.parseLong(matcher.group(2));
                
                String enumType = determineEnumType(constantName);
                if (enumType != null) {
                    enums.computeIfAbsent(enumType, k -> new TreeMap<>())
                         .put(constantName, value);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading " + file + ": " + e.getMessage());
        }
    }
    
    private static String determineEnumType(String constantName) {
        if (constantName.startsWith("SPV_REFLECT_RESULT_")) return "SpirvReflectResult";
        if (constantName.startsWith("SPV_REFLECT_SHADER_STAGE_")) return "SpirvReflectShaderStage";
        if (constantName.startsWith("SPV_REFLECT_DESCRIPTOR_TYPE_")) return "SpirvReflectDescriptorType";
        
        return null;
    }
    
    private static void generateEnumClass(String enumType, Map<String, Long> constants, Path outputDir) {
        try {
            Path outputFile = outputDir.resolve(enumType + ".java");
            StringBuilder sb = new StringBuilder();
            
            sb.append("package io.github.yetyman.spirv.enums;\n\n");
            sb.append("import java.util.*;\n\n");
            sb.append("/**\n");
            sb.append(" * Type-safe constants for ").append(enumType).append("\n");
            sb.append(" * Generated from jextract bindings\n");
            sb.append(" */\n");
            sb.append("public record ").append(enumType).append("(int value) {\n\n");
            
            for (Map.Entry<String, Long> entry : constants.entrySet()) {
                String name = entry.getKey();
                long value = entry.getValue();
                int intValue = (int) value;
                
                // Check if we already have an instance for this value
                String existingName = null;
                for (Map.Entry<String, Long> existing : constants.entrySet()) {
                    if (existing.getValue().equals(value) && existing.getKey().compareTo(name) < 0) {
                        existingName = existing.getKey();
                        break;
                    }
                }
                
                if (existingName != null) {
                    sb.append("    public static final ").append(enumType).append(" ").append(name).append(" = ").append(existingName).append(";\n");
                } else {
                    sb.append("    public static final ").append(enumType).append(" ").append(name).append(" = new ").append(enumType).append("(").append(intValue).append(");\n");
                }
            }
            
            sb.append("\n    public static ").append(enumType).append(" fromValue(int value) {\n");
            sb.append("        return switch (value) {\n");
            
            // Handle duplicate values - prefer shorter names
            Map<Integer, String> valueToName = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : constants.entrySet()) {
                int intValue = (int) entry.getValue().longValue();
                String name = entry.getKey();
                
                if (!valueToName.containsKey(intValue)) {
                    valueToName.put(intValue, name);
                } else {
                    String existing = valueToName.get(intValue);
                    if (name.length() < existing.length()) {
                        valueToName.put(intValue, name);
                    }
                }
            }
            
            for (Map.Entry<Integer, String> entry : valueToName.entrySet()) {
                sb.append("            case ").append(entry.getKey()).append(" -> ").append(entry.getValue()).append(";\n");
            }
            sb.append("            default -> new ").append(enumType).append("(value);\n");
            sb.append("        };\n");
            sb.append("    }\n");
            
            sb.append("}\n");
            
            Files.writeString(outputFile, sb.toString());
            System.out.println("Generated " + enumType + " with " + constants.size() + " constants");
        } catch (IOException e) {
            System.err.println("Error writing " + enumType + ": " + e.getMessage());
        }
    }
}