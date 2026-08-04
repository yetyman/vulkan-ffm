package io.github.yetyman.vulkan.assets;

import io.github.yetyman.vulkan.ui.assets.AssetRegistry;
import io.github.yetyman.vulkan.ui.assets.AssetType;

import java.util.HashMap;
import java.util.Map;

/**
 * Color tokens and style definitions shared across UILayers.
 *
 * A theme is a named set of RGBA color tokens (e.g. "background", "text", "accent")
 * plus arbitrary named style values (floats, e.g. "cornerRadius", "borderWidth").
 * Layers look up tokens by name so a single theme swap restyles every layer at once.
 *
 * Multiple named themes may be registered (e.g. "light", "dark"); one is active at a time.
 */
public class ThemeRegistry {
    public static final AssetType<ThemeRegistry> TYPE = AssetType.of(ThemeRegistry.class);

    /** Extracts from registry. Call once at layer startup and cache. */
    public static ThemeRegistry from(AssetRegistry registry) {
        return registry.get(TYPE);
    }

    /** @return a registry pre-populated with a single default dark theme, set active. */
    public static ThemeRegistry loadDefault() {
        ThemeRegistry registry = new ThemeRegistry();
        Theme dark = new Theme("dark");
        dark.setColor("background", 0.10f, 0.10f, 0.11f, 1.0f);
        dark.setColor("surface", 0.16f, 0.16f, 0.18f, 1.0f);
        dark.setColor("text", 0.92f, 0.92f, 0.92f, 1.0f);
        dark.setColor("textMuted", 0.60f, 0.60f, 0.62f, 1.0f);
        dark.setColor("accent", 0.20f, 0.55f, 0.95f, 1.0f);
        dark.setColor("border", 0.30f, 0.30f, 0.32f, 1.0f);
        dark.setStyle("cornerRadius", 4.0f);
        dark.setStyle("borderWidth", 1.0f);
        registry.register(dark);
        registry.setActive("dark");
        return registry;
    }

    private final Map<String, Theme> themes = new HashMap<>();
    private Theme active;

    public ThemeRegistry() {}

    /** Registers a theme under its name(). Overwrites any existing theme with the same name. */
    public void register(Theme theme) {
        themes.put(theme.name(), theme);
        if (active == null) active = theme;
    }

    /** Sets the active theme by name. */
    public void setActive(String name) {
        Theme theme = themes.get(name);
        if (theme == null) throw new IllegalArgumentException("No theme registered with name: " + name);
        active = theme;
    }

    /** @return the currently active theme. */
    public Theme active() { return active; }

    /** @return the theme registered under name, or null if absent. */
    public Theme get(String name) { return themes.get(name); }

    /** A named set of color tokens and style values. */
    public static class Theme {
        private final String name;
        private final Map<String, float[]> colors = new HashMap<>();
        private final Map<String, Float> styles = new HashMap<>();

        public Theme(String name) { this.name = name; }

        public String name() { return name; }

        /** Sets an RGBA color token, each component in [0, 1]. */
        public void setColor(String token, float r, float g, float b, float a) {
            colors.put(token, new float[]{r, g, b, a});
        }

        /** @return the RGBA color token, or null if not defined in this theme. */
        public float[] getColor(String token) { return colors.get(token); }

        /** Sets a named float style value (e.g. cornerRadius, borderWidth). */
        public void setStyle(String key, float value) { styles.put(key, value); }

        /** @return the named float style value, or defaultValue if not defined in this theme. */
        public float getStyle(String key, float defaultValue) {
            return styles.getOrDefault(key, defaultValue);
        }
    }
}
