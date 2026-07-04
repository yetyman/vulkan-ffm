package io.github.yetyman.vulkan.foundation.ui.assets;

import java.util.Objects;

/**
 * Type-safe key for AssetRegistry lookups.
 * Supports both class-only keys and class+name disambiguation for multiple
 * instances of the same type (e.g. two FontRegistry instances for different devices).
 */
public final class AssetType<T> {
    private final Class<T> type;
    private final String name; // null for unnamed (class-only lookup)

    private AssetType(Class<T> type, String name) {
        this.type = type;
        this.name = name;
    }

    /** @return an unnamed type token for the given class. */
    public static <T> AssetType<T> of(Class<T> type) {
        return new AssetType<>(type, null);
    }

    /** @return a named type token for the given class, disambiguating multiple instances of the same type. */
    public static <T> AssetType<T> of(Class<T> type, String name) {
        return new AssetType<>(type, name);
    }

    public Class<T> type() { return type; }
    public String name() { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssetType<?> other)) return false;
        return type.equals(other.type) && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }

    @Override
    public String toString() {
        return name != null ? type.getSimpleName() + "[" + name + "]" : type.getSimpleName();
    }
}
