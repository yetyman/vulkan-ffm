package io.github.yetyman.vulkan.foundation.ecs;

/**
 * Declares a dependency on another component, specifying how to find it and how to share it.
 *
 * @param type     the component type required
 * @param claim    how this dependency instance is shared (PERMISSIVE, SELF_EXCLUSIVE, EXCLUSIVE)
 * @param scope    where to look (SELF = same node, NEAREST_ANCESTOR = walk up parent chain)
 * @param fallback what to do if the dependency is not found
 * @param <T>      the component type
 */
public record Dependency<T extends Component>(
        Class<T> type,
        ClaimStyle claim,
        LookupScope scope,
        FallbackPolicy<T> fallback
) {

    /**
     * Convenience: a required dependency on the same node with permissive sharing.
     */
    public static <T extends Component> Dependency<T> selfRequired(Class<T> type) {
        return new Dependency<>(type, ClaimStyle.PERMISSIVE, LookupScope.SELF, FallbackPolicy.required());
    }

    /**
     * Convenience: a required dependency looked up via nearest ancestor with permissive sharing.
     */
    public static <T extends Component> Dependency<T> ancestorRequired(Class<T> type) {
        return new Dependency<>(type, ClaimStyle.PERMISSIVE, LookupScope.NEAREST_ANCESTOR, FallbackPolicy.required());
    }

    /**
     * Convenience: an optional dependency on the same node with permissive sharing.
     */
    public static <T extends Component> Dependency<T> selfOptional(Class<T> type) {
        return new Dependency<>(type, ClaimStyle.PERMISSIVE, LookupScope.SELF, FallbackPolicy.optional());
    }

    /**
     * Convenience: an optional dependency looked up via nearest ancestor with permissive sharing.
     */
    public static <T extends Component> Dependency<T> ancestorOptional(Class<T> type) {
        return new Dependency<>(type, ClaimStyle.PERMISSIVE, LookupScope.NEAREST_ANCESTOR, FallbackPolicy.optional());
    }
}
