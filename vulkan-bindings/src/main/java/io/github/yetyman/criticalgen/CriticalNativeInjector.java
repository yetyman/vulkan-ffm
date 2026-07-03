package io.github.yetyman.criticalgen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Post-processes jextract-generated Java sources to inject Linker.Option.critical(false)
 * on all downcall handles except those in EXCLUDED, which are left as plain (non-critical)
 * downcalls in the bulk generated files.
 *
 * EXCLUDED functions fall into two distinct groups, both left untouched here:
 *
 * - Hard-excluded (never safe to be critical, no opt-in exists anywhere): functions that
 *   register debug/validation callbacks. Critical natives cannot invoke Java upcalls under
 *   any circumstances, so these must never be made critical, full stop.
 *
 * - Conditionally-excluded (may block for a data/context-dependent duration; safe to be
 *   critical from a correctness standpoint, but risk delaying a thread's cooperation with a
 *   GC safepoint for that blocking duration): CPU-blocking waits, queue submit/present/bind,
 *   allocation, pipeline/shader-module creation, deferred operation join, query pool results,
 *   latency sleep, memory mapping. These get an opt-in critical variant exposed separately in
 *   io.github.yetyman.vulkan.generated.critical.VulkanFFMCritical, decided per call site by the
 *   caller (who knows the actual arguments/context and therefore the realistic blocking risk),
 *   rather than forced globally by this generator.
 *
 * Usage: CriticalNativeInjector <sourceDir> [sourceDir2 ...]
 */
public class CriticalNativeInjector {

    private static final String DOWNCALL = "Linker.nativeLinker().downcallHandle(ADDR, DESC);";
    private static final String DOWNCALL_CRITICAL = "Linker.nativeLinker().downcallHandle(ADDR, DESC, Linker.Option.critical(false));";
    private static final String FIND_OR_THROW = "SYMBOL_LOOKUP.findOrThrow(\"";

    // Hard-excluded: fires upcalls synchronously during any Vulkan call. Critical natives
    // cannot invoke Java callbacks under any circumstance - no opt-in critical variant exists
    // or should ever be added for these.
    private static final Set<String> HARD_EXCLUDED = Set.of(
        "vkCreateDebugUtilsMessengerEXT",
        "vkDestroyDebugUtilsMessengerEXT",
        "vkCreateDebugReportCallbackEXT",
        "vkDestroyDebugReportCallbackEXT"
    );

    // Conditionally-excluded: may block for a duration that depends on arguments/driver/OS
    // state. Left as plain downcalls here; an opt-in critical variant for each of these is
    // hand-maintained in io.github.yetyman.vulkan.generated.critical.VulkanFFMCritical, keep
    // that list in sync if functions are added/removed here.
    private static final Set<String> CONDITIONALLY_EXCLUDED = Set.of(
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
        // Present — can block on vsync (timing depends on present mode)
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

    private static final Set<String> EXCLUDED = java.util.stream.Stream.concat(
        HARD_EXCLUDED.stream(), CONDITIONALLY_EXCLUDED.stream()
    ).collect(java.util.stream.Collectors.toUnmodifiableSet());

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
