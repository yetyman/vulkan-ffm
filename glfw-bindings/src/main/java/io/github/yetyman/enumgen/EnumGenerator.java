package io.github.yetyman.enumgen;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class EnumGenerator {
    
    private static final Pattern ENUM_COMMENT = Pattern.compile("\\* enum (\\w+)\\.(GLFW_\\w+) = (-?\\d+)");
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: EnumGenerator <input-dir> <output-dir>");
            System.exit(1);
        }
        
        Path inputDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);
        
        Map<String, Map<String, Integer>> enums = new HashMap<>();
        
        // Parse all generated files
        Files.walk(inputDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(file -> parseFile(file, enums));
        
        // Generate enum classes
        Files.createDirectories(outputDir);
        for (Map.Entry<String, Map<String, Integer>> entry : enums.entrySet()) {
            generateEnumClass(entry.getKey(), entry.getValue(), outputDir);
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
    
    private static void generateEnumClass(String enumType, Map<String, Integer> constants, Path outputDir) {
        try {
            Path outputFile = outputDir.resolve(enumType + ".java");
            StringBuilder sb = new StringBuilder();
            
            sb.append("package io.github.yetyman.glfw.enums;\n\n");
            sb.append("/**\n");
            sb.append(" * Constants for ").append(enumType).append("\n");
            sb.append(" * Generated from jextract bindings\n");
            sb.append(" */\n");
            sb.append("public final class ").append(enumType).append(" {\n");
            sb.append("    private ").append(enumType).append("() {}\n\n");
            
            for (Map.Entry<String, Integer> entry : constants.entrySet()) {
                String name = entry.getKey();
                int value = entry.getValue();
                sb.append("    public static final int ").append(name).append(" = ").append(value).append(";\n");
            }
            
            sb.append("}\n");
            
            Files.writeString(outputFile, sb.toString());
            System.out.println("Generated " + enumType + " with " + constants.size() + " constants");
        } catch (IOException e) {
            System.err.println("Error writing " + enumType + ": " + e.getMessage());
        }
    }
}
