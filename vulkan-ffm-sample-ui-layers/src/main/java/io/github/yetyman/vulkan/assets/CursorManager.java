package io.github.yetyman.vulkan.assets;

import io.github.yetyman.vulkan.ui.assets.AssetRegistry;
import io.github.yetyman.vulkan.ui.assets.AssetType;

/**
 * Platform cursor shape management, registered in AssetRegistry by the application.
 *
 * vulkan-ffm-node-trees defines only the contract. Concrete implementations depend on
 * a specific windowing backend (e.g. GLFW) and therefore live outside this module
 * (e.g. in sample-app or another windowing-integration module).
 */
public interface CursorManager {
    AssetType<CursorManager> TYPE = AssetType.of(CursorManager.class);

    /** Extracts from registry. Call once at layer startup and cache. */
    static CursorManager from(AssetRegistry registry) {
        return registry.get(TYPE);
    }

    /** Sets the currently displayed cursor shape. */
    void setShape(CursorShape shape);

    /** @return the currently displayed cursor shape. */
    CursorShape getShape();

    /** Sets whether the cursor is visible. */
    void setVisible(boolean visible);

    /** @return true if the cursor is currently visible. */
    boolean isVisible();

    /** Standard cursor shapes supported across windowing backends. */
    enum CursorShape {
        ARROW,
        TEXT_INPUT,
        CROSSHAIR,
        HAND,
        RESIZE_EW,
        RESIZE_NS,
        RESIZE_NESW,
        RESIZE_NWSE,
        RESIZE_ALL,
        NOT_ALLOWED
    }
}
