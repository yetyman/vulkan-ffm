package io.github.yetyman.vulkan.assets;

import io.github.yetyman.vulkan.ui.assets.AssetRegistry;
import io.github.yetyman.vulkan.ui.assets.AssetType;

/**
 * Platform clipboard access, registered in AssetRegistry by the application.
 *
 * vulkan-ffm-node-trees defines only the contract. Concrete implementations depend on
 * a specific windowing backend (e.g. GLFW) and therefore live outside this module
 * (e.g. in sample-app or another windowing-integration module).
 */
public interface ClipboardAccess {
    AssetType<ClipboardAccess> TYPE = AssetType.of(ClipboardAccess.class);

    /** Extracts from registry. Call once at layer startup and cache. */
    static ClipboardAccess from(AssetRegistry registry) {
        return registry.get(TYPE);
    }

    /** @return the current clipboard text contents, or empty string if the clipboard is empty or unsupported. */
    String getText();

    /** Sets the clipboard text contents. */
    void setText(String text);
}
