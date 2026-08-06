package io.github.yetyman.vulkan.mesh;

import io.github.yetyman.vulkan.enums.VkPrimitiveTopology;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How the elements of a partition form primitives.
 *
 * <p>Open to extension, for the same reason as {@link AttributeSemantic}: Vulkan's topology list is
 * closed, but the set of things a mesh module must describe is not. Meshlet clusters have no
 * {@code VkPrimitiveTopology} value. Gaussian and point splats are drawn as instanced geometry or
 * via compute. Tetrahedral and hexahedral volume meshes for FEM and physics have no rendering
 * topology at all yet are legitimately meshes. Half-edge structures for editing are topologies with
 * no draw path whatsoever.
 *
 * <p>{@link #vkTopology()} being empty means "cannot be handed to a graphics pipeline directly",
 * exactly parallel to {@link AttributeFormat#vertexInputFormat()}.
 */
public final class PrimitiveTopology {

    private static final Map<String, PrimitiveTopology> INTERNED = new ConcurrentHashMap<>();

    public static final PrimitiveTopology POINT_LIST =
            of("pointList", 1, VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_POINT_LIST.value());
    public static final PrimitiveTopology LINE_LIST =
            of("lineList", 2, VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_LINE_LIST.value());
    public static final PrimitiveTopology LINE_STRIP =
            of("lineStrip", 1, VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_LINE_STRIP.value());
    public static final PrimitiveTopology TRIANGLE_LIST =
            of("triangleList", 3, VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST.value());
    public static final PrimitiveTopology TRIANGLE_STRIP =
            of("triangleStrip", 1, VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP.value());
    public static final PrimitiveTopology TRIANGLE_FAN =
            of("triangleFan", 1, VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN.value());
    public static final PrimitiveTopology PATCH_LIST =
            of("patchList", 0, VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_PATCH_LIST.value());

    /** Meshlet clusters. A partitioning of triangles with no direct Vulkan topology. */
    public static final PrimitiveTopology MESHLET = of("meshlet", 0);
    /** Tetrahedral volume elements, for FEM and physics. No rendering topology. */
    public static final PrimitiveTopology TETRAHEDRA = of("tetrahedra", 4);
    /** Hexahedral volume elements. No rendering topology. */
    public static final PrimitiveTopology HEXAHEDRA = of("hexahedra", 8);
    /** Half-edge connectivity, for editing. No draw path. */
    public static final PrimitiveTopology HALF_EDGE = of("halfEdge", 0);
    /** Point or Gaussian splats, drawn as expanded geometry or via compute. */
    public static final PrimitiveTopology SPLAT = of("splat", 1);

    private final String name;
    private final int indicesPerPrimitive;
    private final int vkTopology; // -1 when there is none

    private PrimitiveTopology(String name, int indicesPerPrimitive, int vkTopology) {
        this.name = name;
        this.indicesPerPrimitive = indicesPerPrimitive;
        this.vkTopology = vkTopology;
    }

    /**
     * Interns and returns a topology with no Vulkan mapping.
     *
     * @param indicesPerPrimitive indices consumed per primitive, or 0 when not fixed
     */
    public static PrimitiveTopology of(String name, int indicesPerPrimitive) {
        return of(name, indicesPerPrimitive, -1);
    }

    /**
     * Interns and returns a topology with an explicit {@code VkPrimitiveTopology} mapping.
     * Names are matched case-insensitively; the first registration decides the mapping.
     */
    public static PrimitiveTopology of(String name, int indicesPerPrimitive, int vkTopology) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("topology name required");
        String trimmed = name.trim();
        return INTERNED.computeIfAbsent(trimmed.toLowerCase(Locale.ROOT),
                key -> new PrimitiveTopology(trimmed, indicesPerPrimitive, vkTopology));
    }

    /**
     * Interns and returns the topology with the given name, which must already exist.
     */
    public static PrimitiveTopology of(String name) {
        PrimitiveTopology existing = INTERNED.get(name.trim().toLowerCase(Locale.ROOT));
        if (existing == null)
            throw new IllegalArgumentException("unknown topology '" + name + "'; register it with of(name, indicesPerPrimitive)");
        return existing;
    }

    /**
     * @return every topology interned so far. Grows as new names are registered.
     */
    public static Collection<PrimitiveTopology> interned() {
        return Collections.unmodifiableCollection(INTERNED.values());
    }

    public String name() {
        return name;
    }

    /**
     * @return indices consumed per primitive, or 0 when not fixed (strips, fans, patches, clusters)
     */
    public int indicesPerPrimitive() {
        return indicesPerPrimitive;
    }

    /**
     * @return the {@code VkPrimitiveTopology} value, or empty when this topology cannot be handed
     * to a graphics pipeline directly
     */
    public OptionalInt vkTopology() {
        return vkTopology < 0 ? OptionalInt.empty() : OptionalInt.of(vkTopology);
    }

    /**
     * @return true if this topology can drive a graphics pipeline directly
     */
    public boolean isRenderable() {
        return vkTopology >= 0;
    }

    /**
     * @return the number of primitives {@code indexCount} indices form, or -1 when this topology
     * has no fixed indices-per-primitive ratio
     */
    public long primitiveCount(long indexCount) {
        if (indicesPerPrimitive <= 0) return -1;
        return indexCount / indicesPerPrimitive;
    }

    @Override
    public String toString() {
        return name;
    }
}
