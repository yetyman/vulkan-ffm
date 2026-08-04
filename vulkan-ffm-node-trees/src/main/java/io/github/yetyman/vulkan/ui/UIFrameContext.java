package io.github.yetyman.vulkan.ui;

import java.lang.foreign.Arena;

/**
 * Per-frame context provided to layers during update().
 * The frameArena is freed at the end of each frame - layers must not hold references
 * to memory allocated from it across frames.
 */
public class UIFrameContext {
    private final Arena frameArena;
    private final double deltaTime;   // seconds since last frame
    private final long frameNumber;
    private final UIContext ctx;

    public UIFrameContext(Arena frameArena, double deltaTime, long frameNumber, UIContext ctx) {
        this.frameArena = frameArena;
        this.deltaTime = deltaTime;
        this.frameNumber = frameNumber;
        this.ctx = ctx;
    }

    /** @return the per-frame arena. Not valid after this frame ends. */
    public Arena frameArena() { return frameArena; }

    /** @return seconds elapsed since the previous frame. */
    public double deltaTime() { return deltaTime; }

    /** @return monotonically increasing frame counter. */
    public long frameNumber() { return frameNumber; }

    /** @return the shared platform context. */
    public UIContext ctx() { return ctx; }
}
