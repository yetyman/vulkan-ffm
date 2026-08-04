package io.github.yetyman.vulkan.ui;

import io.github.yetyman.vulkan.ui.assets.AssetRegistry;
import io.github.yetyman.vulkan.highlevel.VulkanContext;

import java.lang.foreign.Arena;

/**
 * Platform context shared by all UILayers.
 * Created once at startup. Remains valid for the UI system's lifetime.
 * Layers receive this in initialize() and hold a reference.
 */
public class UIContext {
    private final VulkanContext vulkan;
    private final AssetRegistry assets;
    private int width;
    private int height;
    private float dpiScale;
    private final Arena applicationArena; // lives for duration of the UI system

    private UIContext(Builder b) {
        this.vulkan = b.vulkan;
        this.assets = b.assets;
        this.width = b.width;
        this.height = b.height;
        this.dpiScale = b.dpiScale;
        this.applicationArena = b.applicationArena;
    }

    public VulkanContext vulkan() { return vulkan; }
    public AssetRegistry assets() { return assets; }
    public int width() { return width; }
    public int height() { return height; }
    public float dpiScale() { return dpiScale; }
    public Arena applicationArena() { return applicationArena; }

    /** Called by UIComposite on resize. */
    void updateDimensions(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Called by the application when the windowing system reports a DPI scale change. */
    public void updateDpiScale(float dpiScale) {
        this.dpiScale = dpiScale;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private VulkanContext vulkan;
        private AssetRegistry assets;
        private int width;
        private int height;
        private float dpiScale = 1.0f;
        private Arena applicationArena;

        private Builder() {}

        /** Sets the underlying Vulkan instance/device/queue context. */
        public Builder vulkan(VulkanContext vulkan) { this.vulkan = vulkan; return this; }

        /** Sets the shared asset registry (fonts, themes, clipboard, cursors, custom services). */
        public Builder assets(AssetRegistry assets) { this.assets = assets; return this; }

        /** Sets the initial surface dimensions in pixels. */
        public Builder dimensions(int width, int height) { this.width = width; this.height = height; return this; }

        /** Sets the initial DPI scale factor (1.0 = 96 DPI baseline). */
        public Builder dpiScale(float dpiScale) { this.dpiScale = dpiScale; return this; }

        /** Sets the arena that lives for the duration of the UI system. */
        public Builder applicationArena(Arena arena) { this.applicationArena = arena; return this; }

        public UIContext build() {
            if (vulkan == null) throw new IllegalStateException("vulkan not set");
            if (assets == null) throw new IllegalStateException("assets not set");
            if (applicationArena == null) throw new IllegalStateException("applicationArena not set");
            return new UIContext(this);
        }
    }
}
