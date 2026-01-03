package io.github.yetyman.enumgen;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class EnumGenerator {
    
    private static final Pattern ENUM_COMMENT = Pattern.compile("\\* enum (\\w+)\\.(VK_\\w+) = (-?\\d+)");
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: EnumGenerator <input-dir> <output-dir> [package]");
            System.exit(1);
        }
        
        Path inputDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);
        String packageName = args.length > 2 ? args[2] : "io.github.yetyman.vulkan.enums";
        
        Map<String, Map<String, Integer>> enums = new HashMap<>();
        
        // Parse all generated files
        Files.walk(inputDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(file -> parseFile(file, enums));
        
        // Generate enum classes
        Files.createDirectories(outputDir);
        for (Map.Entry<String, Map<String, Integer>> entry : enums.entrySet()) {
            generateEnumClass(entry.getKey(), entry.getValue(), outputDir, packageName);
        }
        
        System.out.println("Generated " + enums.size() + " enum classes");
    }
    
    private static void parseFile(Path file, Map<String, Map<String, Integer>> enums) {
        try {
            String content = Files.readString(file);
            Matcher matcher = ENUM_COMMENT.matcher(content);
            
            while (matcher.find()) {
                String enumType = matcher.group(1);
                String constantName = matcher.group(2);
                int value = Integer.parseInt(matcher.group(3));
                
                enums.computeIfAbsent(enumType, k -> new TreeMap<>())
                     .put(constantName, value);
            }
        } catch (IOException e) {
            System.err.println("Error reading " + file + ": " + e.getMessage());
        }
    }
    
    private static void generateEnumClass(String enumType, Map<String, Integer> constants, Path outputDir, String packageName) {
        try {
            Path outputFile = outputDir.resolve(enumType + ".java");
            StringBuilder sb = new StringBuilder();
            
            sb.append("package ").append(packageName).append(";\n\n");
            sb.append("import java.util.*;\n\n");
            sb.append("/**\n");
            sb.append(" * Type-safe constants for ").append(enumType).append("\n");
            sb.append(" * Generated from jextract bindings\n");
            sb.append(" */\n");
            sb.append("public record ").append(enumType).append("(int value) {\n\n");
            
            for (Map.Entry<String, Integer> entry : constants.entrySet()) {
                String name = entry.getKey();
                int value = entry.getValue();
                
                // Check if we already have an instance for this value
                String existingName = null;
                for (Map.Entry<String, Integer> existing : constants.entrySet()) {
                    if (existing.getValue().equals(value) && existing.getKey().compareTo(name) < 0) {
                        existingName = existing.getKey();
                        break;
                    }
                }
                
                if (existingName != null) {
                    sb.append("    public static final ").append(enumType).append(" ").append(name).append(" = ").append(existingName).append(";\n");
                } else {
                    sb.append("    public static final ").append(enumType).append(" ").append(name).append(" = new ").append(enumType).append("(").append(value).append(");\n");
                }
            }
            
            sb.append("\n    public static ").append(enumType).append(" fromValue(int value) {\n");
            sb.append("        return switch (value) {\n");
            
            // Handle duplicate values - prefer generic names over vendor-specific
            Map<Integer, String> valueToName = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : constants.entrySet()) {
                int value = entry.getValue();
                String name = entry.getKey();
                
                if (!valueToName.containsKey(value)) {
                    valueToName.put(value, name);
                } else {
                    String existing = valueToName.get(value);
                    // Prefer names without vendor suffixes
                    if (hasVendorSuffix(existing) && !hasVendorSuffix(name)) {
                        valueToName.put(value, name);
                    } else if (!hasVendorSuffix(existing) && hasVendorSuffix(name)) {
                        // Keep existing
                    } else if (name.length() < existing.length()) {
                        // Prefer shorter names
                        valueToName.put(value, name);
                    }
                }
            }
            
            for (Map.Entry<Integer, String> entry : valueToName.entrySet()) {
                sb.append("            case ").append(entry.getKey()).append(" -> ").append(entry.getValue()).append(";\n");
            }
            sb.append("            default -> new ").append(enumType).append("(value);\n");
            sb.append("        };\n");
            sb.append("    }\n");
            
            sb.append("\n    private static boolean hasVendorSuffix(String name) {\n");
            sb.append("        return name.endsWith(\"_KHR\") || name.endsWith(\"_EXT\") || name.endsWith(\"_NV\") || \n");
            sb.append("               name.endsWith(\"_AMD\") || name.endsWith(\"_INTEL\") || name.endsWith(\"_ARM\");\n");
            sb.append("    }\n");
            
            sb.append("}\n");
            
            Files.writeString(outputFile, sb.toString());
            System.out.println("Generated " + enumType + " with " + constants.size() + " constants");
        } catch (IOException e) {
            System.err.println("Error writing " + enumType + ": " + e.getMessage());
        }
    }
    
    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
