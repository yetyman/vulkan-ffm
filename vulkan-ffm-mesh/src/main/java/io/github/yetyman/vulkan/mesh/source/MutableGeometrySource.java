package io.github.yetyman.vulkan.mesh.source;

import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.ElementWindow;

/**
 * A {@link GeometrySource} that changes after upload and can report what changed.
 *
 * <p>A capability interface rather than an addition to {@link GeometrySource}, because most sources
 * are immutable once produced and should not be forced to carry dirty-tracking state. Callers that
 * track their own dirtiness pass an explicit window to the update path instead; neither approach is
 * privileged.
 *
 * <p>Implementations are expected to coalesce edits into a window rather than tracking individual
 * elements: one slightly-larger upload is almost always cheaper than many small ones, since cost is
 * dominated by per-submission overhead rather than bytes. {@link ElementWindow#union} exists for
 * exactly this.
 */
public interface MutableGeometrySource extends GeometrySource {

    /**
     * @return true if {@code semantic} has changed since the last {@link #clearDirty}
     */
    boolean isDirty(AttributeSemantic semantic);

    /**
     * @return the element range of {@code semantic} that changed, or an empty window when clean.
     * May be wider than the true edit if the implementation coalesces.
     */
    ElementWindow dirtyWindow(AttributeSemantic semantic);

    /**
     * Marks {@code semantic} clean. Called by the update path after a successful upload, so the
     * next update does not re-send unchanged data.
     */
    void clearDirty(AttributeSemantic semantic);

    /**
     * @return true if the index stream has changed since {@link #clearIndicesDirty}
     */
    default boolean areIndicesDirty() {
        return false;
    }

    /**
     * Marks the index stream clean.
     */
    default void clearIndicesDirty() {
    }
}
