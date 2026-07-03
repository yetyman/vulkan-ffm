package io.github.yetyman.enumgen;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class Win32Deduplicator {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: Win32Deduplicator <main-generated-dir> <win32-generated-dir>");
            System.exit(1);
        }
        
        Path mainDir = Paths.get(args[0]);
        Path win32Dir = Paths.get(args[1]);
        
        int removed = 0;
        int imported = 0;
        for (Path win32File : Files.walk(win32Dir).filter(p -> p.toString().endsWith(".java")).toList()) {
            String className = win32File.getFileName().toString();
            Path mainFile = mainDir.resolve(className);
            
            if (Files.exists(mainFile) && filesIdentical(mainFile, win32File)) {
                Files.delete(win32File);
                removed++;
                System.out.println("Removed duplicate: " + className);
            } else {
                // Add import for parent package
                addParentImport(win32File);
                imported++;
            }
        }
        
        System.out.println("Removed " + removed + " duplicate classes, added imports to " + imported + " classes");
    }
    
    private static boolean filesIdentical(Path file1, Path file2) throws IOException {
        List<String> lines1 = Files.readAllLines(file1);
        List<String> lines2 = Files.readAllLines(file2);
        
        // Skip package declaration (first non-comment line)
        int start1 = findFirstNonPackageLine(lines1);
        int start2 = findFirstNonPackageLine(lines2);
        
        if (lines1.size() - start1 != lines2.size() - start2) return false;
        
        for (int i = 0; i < lines1.size() - start1; i++) {
            if (!lines1.get(start1 + i).equals(lines2.get(start2 + i))) {
                return false;
            }
        }
        return true;
    }
    
    private static int findFirstNonPackageLine(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty() && !line.startsWith("//") && !line.startsWith("package")) {
                return i;
            }
        }
        return 0;
    }
    
    private static void addParentImport(Path file) throws IOException {
        // Read as raw text and reinsert the import line using the file's own detected line
        // terminator, instead of Files.readAllLines()/Files.write(path, List<String>), which
        // discards original terminators and rejoins with System.lineSeparator(), silently
        // normalizing the whole file to whatever line ending the JVM reports.
        String content = Files.readString(file);
        String terminator = detectLineTerminator(content);
        List<String> lines = content.isEmpty() ? List.of() : List.of(content.split("\r\n|\r|\n", -1));

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("package ") && line.contains(".win32")) {
                String parentPackage = line.replace(".win32", "");
                String importLine = "import " + parentPackage.substring(8, parentPackage.length() - 1) + ".*;";

                List<String> updated = new java.util.ArrayList<>(lines);
                updated.add(i + 2, importLine);

                StringBuilder rebuilt = new StringBuilder(content.length() + importLine.length() + terminator.length());
                for (int j = 0; j < updated.size(); j++) {
                    rebuilt.append(updated.get(j));
                    if (j < updated.size() - 1) {
                        rebuilt.append(terminator);
                    }
                }
                Files.writeString(file, rebuilt.toString());
                break;
            }
        }
    }

    /** Detects the predominant line terminator used in the given text, defaulting to the platform separator if none is found. */
    private static String detectLineTerminator(String content) {
        int idx = content.indexOf('\n');
        if (idx < 0) {
            return System.lineSeparator();
        }
        if (idx > 0 && content.charAt(idx - 1) == '\r') {
            return "\r\n";
        }
        return "\n";
    }
}