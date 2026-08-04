package io.github.yetyman.vulkan.graph.scheduling;

/**
 * Hints to the scheduler about preferred placement of a node in the execution timeline.
 */
public enum ScheduleHint {
    /** No preference -- scheduler decides freely */
    NONE,
    /** Prefer early execution (e.g. async compute that feeds later passes) */
    EARLY,
    /** Prefer late execution (e.g. UI overlay that reads final color) */
    LATE,
    /** This node is on the critical path -- minimize latency to it */
    CRITICAL_PATH
}
