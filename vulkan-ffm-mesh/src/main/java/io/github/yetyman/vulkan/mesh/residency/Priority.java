package io.github.yetyman.vulkan.mesh.residency;

/**
 * Priority hint for an upload plan. A scheduler may use this to order work when budget is
 * constrained, or to defer low-priority uploads across frames.
 */
public enum Priority {
    /** Must complete this frame. */
    IMMEDIATE,
    /** Should complete soon; may be deferred one frame under pressure. */
    HIGH,
    /** Normal streaming priority. */
    NORMAL,
    /** Background prefetch; defer freely. */
    LOW
}
