package io.github.yetyman.vulkan.graph.edges;

import io.github.yetyman.vulkan.graph.resources.TemporalResource;

/**
 * A temporal dependency edge representing a cross-frame read or write on a TemporalResource.
 * Temporal reads create back-edges in the logical graph (reading from a previous submission's write).
 * Temporal writes close the cycle (writing for the next submission to read).
 */
public class TemporalEdge {

    private final TemporalResource temporalResource;
    private final TemporalAccessType accessType;
    private final int accessMask;
    private final int stageMask;
    private final int imageLayout; // -1 for buffers

    private TemporalEdge(TemporalResource temporalResource, TemporalAccessType accessType,
                         int accessMask, int stageMask, int imageLayout) {
        this.temporalResource = temporalResource;
        this.accessType = accessType;
        this.accessMask = accessMask;
        this.stageMask = stageMask;
        this.imageLayout = imageLayout;
    }

    /** Creates a temporal read edge (reads from previous submission's write) */
    public static TemporalEdge readPrevious(TemporalResource resource, int accessMask, int stageMask) {
        return new TemporalEdge(resource, TemporalAccessType.READ_PREVIOUS, accessMask, stageMask, -1);
    }

    /** Creates a temporal read edge for an image (reads from previous submission's write) */
    public static TemporalEdge readPreviousImage(TemporalResource resource, int accessMask, int stageMask, int layout) {
        return new TemporalEdge(resource, TemporalAccessType.READ_PREVIOUS, accessMask, stageMask, layout);
    }

    /** Creates a temporal write edge (writes for next submission to read) */
    public static TemporalEdge writeCurrent(TemporalResource resource, int accessMask, int stageMask) {
        return new TemporalEdge(resource, TemporalAccessType.WRITE_CURRENT, accessMask, stageMask, -1);
    }

    /** Creates a temporal write edge for an image */
    public static TemporalEdge writeCurrentImage(TemporalResource resource, int accessMask, int stageMask, int layout) {
        return new TemporalEdge(resource, TemporalAccessType.WRITE_CURRENT, accessMask, stageMask, layout);
    }

    /** @return the temporal resource this edge references */
    public TemporalResource temporalResource() { return temporalResource; }

    /** @return whether this is a read-previous or write-current */
    public TemporalAccessType accessType() { return accessType; }

    /** @return the VkAccessFlagBits mask */
    public int accessMask() { return accessMask; }

    /** @return the VkPipelineStageFlagBits mask */
    public int stageMask() { return stageMask; }

    /** @return required/target image layout, or -1 for buffers */
    public int imageLayout() { return imageLayout; }

    /** @return true if this is a temporal read (back-edge) */
    public boolean isReadPrevious() { return accessType == TemporalAccessType.READ_PREVIOUS; }

    /** @return true if this is a temporal write (closes the cycle) */
    public boolean isWriteCurrent() { return accessType == TemporalAccessType.WRITE_CURRENT; }

    public enum TemporalAccessType {
        /** Reads from the previous submission's write slot */
        READ_PREVIOUS,
        /** Writes to the current slot (for next submission to read) */
        WRITE_CURRENT
    }
}
