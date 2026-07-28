package io.github.yetyman.vulkan.foundation.ecs;

/**
 * Policy for handling missing dependencies during resolution.
 * Called when resolution finds no existing instance matching a declared dependency.
 *
 * @param <T> the component type this policy can create defaults for
 */
@FunctionalInterface
public interface FallbackPolicy<T extends Component> {

    /**
     * Called when resolution finds no existing instance.
     *
     * @param requestingNode the node whose component declared this dependency
     * @return a newly-constructed default, or throw if this dependency has no sensible default
     * @throws IllegalStateException if this is a required dependency with no default
     */
    T createDefault(Node requestingNode);

    /**
     * Creates a policy that always throws - the dependency is required and has no default.
     */
    static <T extends Component> FallbackPolicy<T> required() {
        return node -> {
            throw new IllegalStateException("Required dependency not found and no default available");
        };
    }

    /**
     * Creates a policy that returns null - the dependency is optional.
     */
    static <T extends Component> FallbackPolicy<T> optional() {
        return node -> null;
    }
}
