package io.github.yetyman.vulkan.nodetree;

/**
 * Claim styles controlling how dependency instances are shared between components.
 *
 * Claims are tracked on the claimed component instance itself (not on Node or Tree),
 * keeping the check local to whichever component is actually being depended upon.
 */
public enum ClaimStyle {
    /** Any number of components may bind to the same dependency instance. Default/common case. */
    PERMISSIVE,

    /**
     * Only one instance of the REQUESTING component's own concrete type may claim a given
     * dependency instance. Two FocusHighlightComponents cannot both bind to the same
     * ColorComponent; a FocusHighlightComponent and an unrelated AnimationComponent can.
     */
    SELF_EXCLUSIVE,

    /**
     * Once claimed by ANY component, no other component of any type may also bind to that
     * same dependency instance - they must resolve/create their own separate instance.
     */
    EXCLUSIVE
}
