package io.github.yetyman.criticalgen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Post-processes jextract-generated Java sources to inject Linker.Option.critical(false)
 * on all downcall handles except those that block or fire synchronous upcalls.
 *
 * Usage: CriticalNativeInjector <sourceDir> [sourceDir2 ...]
 */
public class CriticalNativeInjector {

    private static final String DOWNCALL = "Linker.nativeLinker().downcallHandle(ADDR, DESC);";
    private static final String DOWNCALL_CRITICAL = "Linker.nativeLinker().downcallHandle(ADDR, DESC, Linker.Option.critical(false));";
    private static final String FIND_OR_THROW = "SYMBOL_LOOKUP.findOrThrow(\"";

    /**
     * Functions excluded from critical(false):
     * - CPU-blocking wait functions
     * - Functions that may block internally (queue submit, present, alloc, pipeline create)
     * - Debug/validation callback registration (fires upcalls synchronously during any Vulkan call)
     * - Deferred operation join (explicitly blocks calling thread)
     * - Query pool results with wait flag (can block; conservative exclusion)
     * - vkLatencySleepNV (explicitly sleeps)
     */
    private static final Set<String> EXCLUDED = Set.of(
        // CPU-blocking waits
        "vkWaitForFences",
        "vkWaitSemaphores",
        "vkWaitSemaphoresKHR",
        "vkDeviceWaitIdle",
        "vkQueueWaitIdle",
        // Swapchain acquire — can block waiting for image
        "vkAcquireNextImageKHR",
        "vkAcquireNextImage2KHR",
        // Queue submit — can block if driver internal queue is full
        "vkQueueSubmit",
        "vkQueueSubmit2",
        "vkQueueSubmit2KHR",
        // Present — can block on vsync. Comment out to enable critical natives if using
        // mailbox/immediate present modes (non-blocking).
        "vkQueuePresentKHR",
        // Sparse binding submit
        "vkQueueBindSparse",
        // Memory allocation — can block on OS
        "vkAllocateMemory",
        // Pipeline creation — driver may compile shaders; can block for seconds
        "vkCreateGraphicsPipelines",
        "vkCreateComputePipelines",
        "vkCreateRayTracingPipelinesKHR",
        "vkCreateRayTracingPipelinesNV",
        "vkCreateExecutionGraphPipelinesAMDX",
        // Shader module creation — driver may compile
        "vkCreateShaderModule",
        // Debug messenger / report callback registration — fires upcalls synchronously
        "vkCreateDebugUtilsMessengerEXT",
        "vkDestroyDebugUtilsMessengerEXT",
        "vkCreateDebugReportCallbackEXT",
        "vkDestroyDebugReportCallbackEXT",
        // Deferred operation — explicitly blocks calling thread doing deferred work
        "vkDeferredOperationJoinKHR",
        // Query pool results — conservative: can block with VK_QUERY_RESULT_WAIT_BIT
        "vkGetQueryPoolResults",
        // Latency sleep — explicitly sleeps
        "vkLatencySleepNV",
        // Memory mapping — may involve OS virtual memory operations
        "vkMapMemory",
        "vkMapMemory2",
        "vkMapMemory2KHR"
    );

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: CriticalNativeInjector <sourceDir> [sourceDir2 ...]");
            System.exit(1);
        }
        int modified = 0;
        for (String dir : args) {
            modified += processDirectory(Path.of(dir));
        }
        System.out.println("CriticalNativeInjector: modified " + modified + " file(s).");
    }

    private static int processDirectory(Path dir) throws IOException {
        int count = 0;
        try (var stream = Files.walk(dir)) {
            for (Path path : (Iterable<Path>) stream.filter(p -> p.toString().endsWith(".java"))::iterator) {
                if (processFile(path)) count++;
            }
        }
        return count;
    }

    private static boolean processFile(Path path) throws IOException {
        // Read as raw text and split preserving terminators so the file's original line-ending
        // style (LF or CRLF, possibly mixed) is not collapsed/rewritten by this pass.
        // Files.readAllLines()/Files.write(path, List<String>) always discard terminators and
        // rejoin with System.lineSeparator(), which silently normalizes every touched file to
        // whatever line ending the JVM reports - do not reintroduce that pattern here.
        String content = Files.readString(path);
        List<String> lines = splitPreservingTerminators(content);
        boolean changed = false;
        String currentFunction = null;

        for (int i = 0; i < lines.size(); i++) {
            String lineWithTerminator = lines.get(i);
            String line = stripTerminator(lineWithTerminator);

            int findIdx = line.indexOf(FIND_OR_THROW);
            if (findIdx >= 0) {
                int nameStart = findIdx + FIND_OR_THROW.length();
                int nameEnd = line.indexOf('"', nameStart);
                if (nameEnd > nameStart) {
                    currentFunction = line.substring(nameStart, nameEnd);
                }
                continue;
            }

            if (line.contains(DOWNCALL) && currentFunction != null) {
                if (!EXCLUDED.contains(currentFunction)) {
                    String terminator = lineWithTerminator.substring(line.length());
                    lines.set(i, line.replace(DOWNCALL, DOWNCALL_CRITICAL) + terminator);
                    changed = true;
                }
                currentFunction = null;
            }
        }

        if (changed) {
            StringBuilder rebuilt = new StringBuilder(content.length() + 64);
            for (String line : lines) {
                rebuilt.append(line);
            }
            Files.writeString(path, rebuilt.toString());
        }
        return changed;
    }

    /**
     * Splits text into lines, keeping each line's original terminator (\r\n, \n, or none for a
     * trailing partial line) attached so the file can be reassembled byte-for-byte identical
     * except for the targeted replacement.
     */
    private static List<String> splitPreservingTerminators(String content) {
        List<String> result = new java.util.ArrayList<>();
        int start = 0;
        int length = content.length();
        for (int i = 0; i < length; i++) {
            char c = content.charAt(i);
            if (c == '\n') {
                result.add(content.substring(start, i + 1));
                start = i + 1;
            } else if (c == '\r') {
                if (i + 1 < length && content.charAt(i + 1) == '\n') {
                    result.add(content.substring(start, i + 2));
                    start = i + 2;
                    i++;
                } else {
                    result.add(content.substring(start, i + 1));
                    start = i + 1;
                }
            }
        }
        if (start < length) {
            result.add(content.substring(start));
        }
        return result;
    }

    /** Returns the line content without its trailing \r\n, \n, or \r terminator, if any. */
    private static String stripTerminator(String lineWithTerminator) {
        int end = lineWithTerminator.length();
        if (end > 0 && lineWithTerminator.charAt(end - 1) == '\n') {
            end--;
            if (end > 0 && lineWithTerminator.charAt(end - 1) == '\r') {
                end--;
            }
        } else if (end > 0 && lineWithTerminator.charAt(end - 1) == '\r') {
            end--;
        }
        return lineWithTerminator.substring(0, end);
    }
}
