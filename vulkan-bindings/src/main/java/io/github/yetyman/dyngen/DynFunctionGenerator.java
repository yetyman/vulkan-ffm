package io.github.yetyman.dyngen;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Generates VkInstanceFunctions.java and VkDeviceFunctions.java from vk.xml.
 * These contain MethodHandles for all instance/device-level Vulkan commands
 * not already present as static methods in the jextract-generated VulkanFFM*.java files.
 *
 * Usage: DynFunctionGenerator <vk.xml> <generated-src-dir> <output-dir>
 *   generated-src-dir: directory containing VulkanFFM*.java (to detect already-bound functions)
 *   output-dir:        where to write VkInstanceFunctions.java and VkDeviceFunctions.java
 */
public class DynFunctionGenerator {

    private static final Set<String> INSTANCE_HANDLE_TYPES = Set.of("VkInstance", "VkPhysicalDevice");
    private static final Set<String> DEVICE_HANDLE_TYPES   = Set.of("VkDevice", "VkQueue", "VkCommandBuffer");

    // Pattern matches jextract-generated static method declarations for vk* functions:
    // e.g. "public static int vkCreateFoo(" or "public static void vkDestroyFoo("
    private static final Pattern JEXTRACT_FN = Pattern.compile(
        "public static (?:int|void|long|float) (vk[A-Za-z0-9_]+)\\(");

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: DynFunctionGenerator <vk.xml> <generated-src-dir> <output-dir>");
            System.exit(1);
        }
        Path xmlPath      = Path.of(args[0]);
        Path generatedDir = Path.of(args[1]);
        Path outDir       = Path.of(args[2]);

        // Collect all vk* functions already bound by jextract
        System.out.println("Scanning jextract output for already-bound functions...");
        Set<String> alreadyBound = scanJextractFunctions(generatedDir);
        System.out.println("Found " + alreadyBound.size() + " functions already bound by jextract");

        System.out.println("Parsing vk.xml...");
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlPath.toFile());
        doc.getDocumentElement().normalize();

        Map<String, Command> commands = new LinkedHashMap<>();
        Map<String, String>  aliases  = new LinkedHashMap<>();

        NodeList cmdNodes = doc.getElementsByTagName("command");
        for (int i = 0; i < cmdNodes.getLength(); i++) {
            Node n = cmdNodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element cmd = (Element) n;
            String alias = cmd.getAttribute("alias");
            if (!alias.isEmpty()) {
                aliases.put(cmd.getAttribute("name"), alias);
                continue;
            }
            Command c = parseCommand(cmd);
            if (c != null) commands.put(c.name, c);
        }

        // Aliases share the same signature as their target
        for (Map.Entry<String, String> e : aliases.entrySet()) {
            Command target = commands.get(e.getValue());
            if (target != null) commands.put(e.getKey(), new Command(e.getKey(), target.returnType, target.params));
        }

        List<Command> instanceCmds = new ArrayList<>();
        List<Command> deviceCmds   = new ArrayList<>();

        for (Command c : commands.values()) {
            if (alreadyBound.contains(c.name) || c.params.isEmpty()) continue;
            String firstType = c.params.get(0).type;
            if (DEVICE_HANDLE_TYPES.contains(firstType))        deviceCmds.add(c);
            else if (INSTANCE_HANDLE_TYPES.contains(firstType)) instanceCmds.add(c);
        }

        System.out.println("Writing " + instanceCmds.size() + " instance commands and " + deviceCmds.size() + " device commands...");
        Files.createDirectories(outDir);
        String pkg = "io.github.yetyman.vulkan.generated";
        write(outDir, pkg, "VkInstanceFunctions", instanceCmds, "instance", "vkGetInstanceProcAddr");
        write(outDir, pkg, "VkDeviceFunctions",   deviceCmds,   "device",   "vkGetDeviceProcAddr");

        System.out.println("Generated VkInstanceFunctions (" + instanceCmds.size() + " commands)");
        System.out.println("Generated VkDeviceFunctions ("   + deviceCmds.size()   + " commands)");
    }

    /** Scans all VulkanFFM*.java files in generatedDir and returns the set of vk* function names. */
    private static Set<String> scanJextractFunctions(Path generatedDir) throws IOException {
        Set<String> found = new HashSet<>();
        try (var stream = Files.list(generatedDir)) {
            stream.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("VulkanFFM") && name.endsWith(".java");
            }).forEach(p -> {
                try {
                    String content = Files.readString(p);
                    Matcher m = JEXTRACT_FN.matcher(content);
                    while (m.find()) found.add(m.group(1));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return found;
    }

    private static void write(Path outDir, String pkg, String className,
                               List<Command> cmds, String handleParam, String procAddrFn) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import java.lang.foreign.*;\n");
        sb.append("import java.lang.invoke.*;\n\n");
        sb.append("public class ").append(className).append(" {\n\n");

        for (Command c : cmds)
            sb.append("    public final MethodHandle ").append(c.name).append(";\n");

        sb.append("\n    public ").append(className).append("(MemorySegment ").append(handleParam).append(") {\n");
        sb.append("        Linker linker = Linker.nativeLinker();\n");
        for (Command c : cmds) {
            sb.append("        this.").append(c.name).append(" = load(").append(handleParam)
              .append(", linker, \"").append(c.name).append("\", ").append(functionDescriptor(c)).append(");\n");
        }
        sb.append("    }\n\n");

        sb.append("    private static MethodHandle load(MemorySegment handle, Linker linker, String name, FunctionDescriptor desc) {\n");
        sb.append("        try (Arena tmp = Arena.ofConfined()) {\n");
        sb.append("            MemorySegment fnPtr = VulkanFFM.").append(procAddrFn).append("(handle, tmp.allocateFrom(name));\n");
        sb.append("            if (fnPtr.equals(MemorySegment.NULL)) return null;\n");
        sb.append("            return linker.downcallHandle(fnPtr, desc);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");

        Files.writeString(outDir.resolve(className + ".java"), sb.toString());
    }

    private static String functionDescriptor(Command c) {
        boolean isVoid = c.returnType.equals("void");
        StringJoiner params = new StringJoiner(", ");
        for (Param p : c.params) params.add(toLayout(p.type, p.isPointer));

        if (isVoid) return "FunctionDescriptor.ofVoid(" + params + ")";
        String ret = toLayout(c.returnType, false);
        return "FunctionDescriptor.of(" + ret + (c.params.isEmpty() ? "" : ", " + params) + ")";
    }

    // Uses VulkanFFM.C_* constants to match jextract's layout naming convention.
    // jextract uses C_INT, C_POINTER, C_LONG_LONG — not ValueLayout.JAVA_*.
    private static String toLayout(String type, boolean isPointer) {
        if (isPointer) return "VulkanFFM.C_POINTER";
        return switch (type) {
            case "VkResult", "VkBool32", "VkFlags", "VkSampleMask",
                 "uint32_t", "int32_t", "int"                    -> "VulkanFFM.C_INT";
            case "uint64_t", "VkFlags64", "VkDeviceSize",
                 "VkDeviceAddress", "size_t"                     -> "VulkanFFM.C_LONG_LONG";
            case "float"                                         -> "VulkanFFM.C_FLOAT";
            default                                              -> "VulkanFFM.C_POINTER"; // opaque handles, structs by ptr
        };
    }

    private static Command parseCommand(Element cmd) {
        NodeList protos = cmd.getElementsByTagName("proto");
        if (protos.getLength() == 0) return null;
        Element proto = (Element) protos.item(0);
        String returnType = textOf(proto, "type");
        String name       = textOf(proto, "name");
        if (name.isEmpty()) return null;

        List<Param> params = new ArrayList<>();
        NodeList paramNodes = cmd.getElementsByTagName("param");
        for (int i = 0; i < paramNodes.getLength(); i++) {
            Element p    = (Element) paramNodes.item(i);
            String type  = textOf(p, "type");
            String pname = textOf(p, "name");
            boolean isPtr = p.getTextContent().contains("*") || p.getTextContent().contains("[");
            params.add(new Param(pname, type, isPtr));
        }
        return new Command(name, returnType, params);
    }

    private static String textOf(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() > 0 ? nl.item(0).getTextContent().trim() : "";
    }

    record Param(String name, String type, boolean isPointer) {}
    record Command(String name, String returnType, List<Param> params) {}
}
