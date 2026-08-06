package io.github.yetyman.vulkan.mesh;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Identifies what an attribute stream means: position, normal, texture coordinate, joint index,
 * or anything at all a caller invents.
 *
 * <p>This is deliberately not an enum. An enum of attribute semantics is the most common source of
 * lock-in in mesh libraries: the moment a caller needs {@code curvature}, {@code sdfGradient},
 * {@code simulationVelocity}, or whatever a paper published last month, they are forced into
 * {@code CUSTOM_0..7} and lose all naming and self-documentation, while every downstream switch
 * acquires a {@code default} case meaning "I do not know what this is". Instances are interned, so
 * identity comparison is the contract and costs the same as an enum at runtime.
 *
 * <p>This follows the same identity-token pattern as {@code EventType} in the node tree module.
 *
 * <p>Well-known semantics are provided as constants and indexed factories. Anything else comes from
 * {@link #of(String)}. Interning is case-insensitive, so {@code of("Position") == POSITION}.
 */
public final class AttributeSemantic {

    private static final Map<String, AttributeSemantic> INTERNED = new ConcurrentHashMap<>();

    /** Vertex position. */
    public static final AttributeSemantic POSITION = of("position", 3);
    /** Surface normal. */
    public static final AttributeSemantic NORMAL = of("normal", 3);
    /** Surface tangent. Often 4 components, with handedness in w. */
    public static final AttributeSemantic TANGENT = of("tangent", 4);
    /** Surface bitangent, when not derived from normal and tangent. */
    public static final AttributeSemantic BITANGENT = of("bitangent", 3);
    /** Per-vertex point size, for point rendering. */
    public static final AttributeSemantic POINT_SIZE = of("pointSize", 1);

    private final String name;
    private final int componentCountHint;

    private AttributeSemantic(String name, int componentCountHint) {
        this.name = name;
        this.componentCountHint = componentCountHint;
    }

    /**
     * Interns and returns the semantic with the given name.
     * Names are matched case-insensitively; the first registration decides the display spelling.
     */
    public static AttributeSemantic of(String name) {
        return of(name, 0);
    }

    /**
     * Interns and returns the semantic with the given name and component-count hint.
     * If the name is already interned, the existing instance is returned unchanged and the hint is
     * ignored.
     */
    public static AttributeSemantic of(String name, int componentCountHint) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("semantic name required");
        String trimmed = name.trim();
        return INTERNED.computeIfAbsent(trimmed.toLowerCase(java.util.Locale.ROOT),
                key -> new AttributeSemantic(trimmed, componentCountHint));
    }

    /** Texture coordinate set {@code set}, zero-based. */
    public static AttributeSemantic TEXCOORD(int set) {
        return of("texcoord" + requireNonNegative(set), 2);
    }

    /** Vertex color set {@code set}, zero-based. */
    public static AttributeSemantic COLOR(int set) {
        return of("color" + requireNonNegative(set), 4);
    }

    /** Skinning joint index set {@code set}, zero-based. */
    public static AttributeSemantic JOINTS(int set) {
        return of("joints" + requireNonNegative(set), 4);
    }

    /** Skinning joint weight set {@code set}, zero-based. */
    public static AttributeSemantic WEIGHTS(int set) {
        return of("weights" + requireNonNegative(set), 4);
    }

    /**
     * @return every semantic interned so far. Useful for diagnostics and tooling; the set grows as
     * new names are used, so do not treat it as a fixed vocabulary.
     */
    public static Collection<AttributeSemantic> interned() {
        return Collections.unmodifiableCollection(INTERNED.values());
    }

    /**
     * @return the display name of this semantic
     */
    public String name() {
        return name;
    }

    /**
     * @return a hint at the usual component count, or 0 when unknown.
     *
     * <p>This is a hint only, never authoritative. The {@link AttributeFormat} bound to the
     * attribute in a {@link MeshLayout} decides the real component count; a position may be 3
     * floats, 4 quantized shorts with padding, or a single packed integer.
     */
    public int componentCountHint() {
        return componentCountHint;
    }

    @Override
    public String toString() {
        return name;
    }

    private static int requireNonNegative(int set) {
        if (set < 0) throw new IllegalArgumentException("set index must be >= 0, was " + set);
        return set;
    }
}
