package io.github.yetyman.vulkan.mesh.lod;

/**
 * A typed key for the {@link LodContext} side channel. Follows the identity-token pattern
 * used by {@code AssetType} in the node-tree module and {@code AttributeSemantic} in the
 * mesh vocabulary: instances are compared by identity, not by name.
 *
 * <p>Research LOD schemes and app-specific selectors define their own keys to supply custom
 * inputs without modifying {@link LodContext}:
 *
 * <pre>{@code
 * // In a research module:
 * public static final ContextKey<OcclusionFeedback> OCCLUSION =
 *         ContextKey.of("occlusionFeedback", OcclusionFeedback.class);
 *
 * // Supplying:
 * context.builder().put(OCCLUSION, myFeedback).build();
 *
 * // Consuming in a selector:
 * context.get(OCCLUSION).ifPresent(fb -> ...);
 * }</pre>
 *
 * @param <T> the type of value associated with this key
 */
public final class ContextKey<T> {

    private final String name;
    private final Class<T> type;

    private ContextKey(String name, Class<T> type) {
        this.name = name;
        this.type = type;
    }

    /**
     * Creates a new context key. Each call returns a distinct identity even for the same
     * name and type, so keys should be held as static finals.
     */
    public static <T> ContextKey<T> of(String name, Class<T> type) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (type == null) throw new IllegalArgumentException("type required");
        return new ContextKey<>(name, type);
    }

    /** Diagnostic name. */
    public String name() { return name; }

    /** The value type. */
    public Class<T> type() { return type; }

    @Override
    public String toString() {
        return "ContextKey[" + name + " : " + type.getSimpleName() + "]";
    }

    // Identity semantics: equals/hashCode are Object defaults (reference equality).
}
