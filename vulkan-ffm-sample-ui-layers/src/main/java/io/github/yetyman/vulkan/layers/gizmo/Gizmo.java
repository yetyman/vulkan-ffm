package io.github.yetyman.vulkan.layers.gizmo;

import io.github.yetyman.vulkan.layers.scene3d.OverlayDrawList;

/**
 * A drawable debug overlay element with a name and spatial bounds.
 *
 * Gizmos are rendered in immediate mode each frame via the {@link GizmoOverlayLayer}.
 * They are cleared and re-submitted every frame (retained-mode registration is owned
 * by the consumer, not the layer). Each gizmo provides:
 *   - A human-readable name (for labels and debug identification)
 *   - A 2D screen-space bounding box (for label placement and hit testing)
 *   - An immediate-mode render callback that draws into an OverlayDrawList
 *
 * Implementations should be lightweight - all heavy computation (buffer reads, transforms)
 * happens before render() is called, cached in the gizmo instance.
 */
public interface Gizmo {

    /** @return human-readable name for this gizmo (used for screen labels). */
    String name();

    /**
     * @return screen-space bounding rectangle [x, y, width, height] in pixels.
     * Used for label placement and potential hit testing.
     */
    float[] bounds();

    /**
     * Renders this gizmo's visual representation into the draw list.
     * Called once per frame by GizmoOverlayLayer during update().
     *
     * @param drawList the overlay draw list to append primitives to
     * @param frameNumber monotonically increasing frame counter for animation
     */
    void render(OverlayDrawList drawList, long frameNumber);
}
