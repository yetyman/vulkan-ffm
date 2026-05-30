package io.github.yetyman.vulkan.loop;

/**
 * Allocation-free frame metrics with circular-buffer rolling statistics.
 *
 * <h2>Timing model</h2>
 * The graphics framework uses N frames in flight (N = swapchain depth, typically 2 or 3).
 * Each frame slot S has its own fence. When frame K (using slot S = K % N) begins, it waits
 * on inFlightFences[S]. That fence was last signaled by frame K-N's GPU completion — meaning
 * frame K-N is the one whose pixels just became visible.
 *
 * To measure REAL input-to-displayed-frame latency, we keep a per-slot input timestamp ring:
 * <ol>
 *   <li>{@link #stampInput()} is called before {@code glfwPollEvents} every loop iteration</li>
 *   <li>When fence[S] signals (at the start of frame K's drawFrame), we know frame K-N just completed.
 *       {@link #onSlotReady(int)} computes latency as {@code now - slotInputStamps[S]} —
 *       that's the latency of the input that drove frame K-N to display.</li>
 *   <li>{@link #onSlotReady(int)} then captures the current input stamp into slot[S] for
 *       use N frames later when frame K's own fence signals.</li>
 * </ol>
 *
 * <h2>Per-stage timing</h2>
 * Within drawFrame, the framework calls {@link #stamp(Stage)} at fixed points:
 * <ul>
 *   <li>{@link Stage#FENCE_WAIT_END} — CPU done waiting for GPU; everything before this is
 *       "free time" (CPU idle, the loop is GPU-bound)</li>
 *   <li>{@link Stage#RECORD_END} — command buffer recording done</li>
 *   <li>{@link Stage#SUBMIT_END} — vkQueueSubmit returned</li>
 *   <li>{@link Stage#PRESENT_END} — vkQueuePresentKHR returned</li>
 * </ul>
 * Each section duration is tracked in its own circular buffer with running statistics.
 *
 * <h2>Allocation-free guarantee</h2>
 * All state is preallocated. Frame-time recording, latency recording, and stamp methods do
 * zero allocations. Only {@link #summary()} allocates a String for output.
 */
public class FrameMetrics {

    /** Stage markers within a single frame */
    public enum Stage {
        FRAME_START,     // beginFrame() (also: just before drawFrame's fence wait)
        FENCE_WAIT_END,  // CPU done waiting on GPU fence
        RECORD_END,      // command buffer recording finished
        SUBMIT_END,      // vkQueueSubmit returned
        PRESENT_END;     // vkQueuePresentKHR returned (== drawFrame done)
        static final int COUNT = values().length;
    }

    private final int windowSize;

    // Per-frame stage timestamps (overwritten each frame)
    private final long[] stageTimes = new long[Stage.COUNT];

    // Per-slot input timestamps (sized to swapchain depth via configureSlots)
    private long[] slotInputStamps = new long[0];

    // Most recent input timestamp (set by stampInput, captured into slot at onSlotReady)
    private volatile long lastInputNanos;

    // Spike threshold (multiplier of average)
    private double spikeThreshold = 2.0;

    // Rolling statistics
    private final SeriesStats frameTime;
    private final SeriesStats latency;
    private final SeriesStats freeWait;   // FRAME_START -> FENCE_WAIT_END
    private final SeriesStats recordTime; // FENCE_WAIT_END -> RECORD_END
    private final SeriesStats submitTime; // RECORD_END -> SUBMIT_END
    private final SeriesStats presentTime;// SUBMIT_END -> PRESENT_END

    // FPS — counted over a 1-second wall window
    private long fpsWindowStart;
    private int fpsFrameCount;
    private int currentFps;

    private FrameMetrics(int windowSize) {
        this.windowSize = windowSize;
        this.frameTime = new SeriesStats(windowSize);
        this.latency = new SeriesStats(windowSize);
        this.freeWait = new SeriesStats(windowSize);
        this.recordTime = new SeriesStats(windowSize);
        this.submitTime = new SeriesStats(windowSize);
        this.presentTime = new SeriesStats(windowSize);
        this.fpsWindowStart = System.nanoTime();
    }

    /** Creates a metrics tracker with the given circular buffer window size */
    public static FrameMetrics create(int windowSize) {
        if (windowSize < 2) throw new IllegalArgumentException("windowSize must be >= 2");
        return new FrameMetrics(windowSize);
    }

    /** Creates a metrics tracker with a 300-frame window */
    public static FrameMetrics create() { return new FrameMetrics(300); }

    /**
     * Configures the per-slot input timestamp ring. Must be called before drawFrame is invoked.
     * @param slotCount number of frames in flight (swapchain depth)
     */
    public void configureSlots(int slotCount) {
        if (slotCount < 1) throw new IllegalArgumentException("slotCount must be >= 1");
        if (slotInputStamps.length != slotCount) {
            this.slotInputStamps = new long[slotCount];
        }
    }

    /** @return the configured slot count (frames in flight) */
    public int slotCount() { return slotInputStamps.length; }

    /** Sets the spike threshold multiplier (default: 2.0x average) */
    public void spikeThreshold(double multiplier) { this.spikeThreshold = multiplier; }

    // --- Frame lifecycle ---

    /**
     * Records the most recent input poll time. Call right before {@code glfwPollEvents()}.
     * Thread-safe (volatile write).
     */
    public void stampInput() {
        lastInputNanos = System.nanoTime();
    }

    /** Marks the start of frame work. Called before the GPU fence wait. */
    public void beginFrame() {
        stageTimes[Stage.FRAME_START.ordinal()] = System.nanoTime();
    }

    /**
     * Records a per-stage timestamp. Use within drawFrame at the framework's
     * well-known stage points.
     */
    public void stamp(Stage stage) {
        stageTimes[stage.ordinal()] = System.nanoTime();
    }

    /**
     * Called immediately after {@code inFlightFences[slot]} has been waited on.
     * The GPU work for the previous frame at this slot is now complete — that frame's
     * pixels are on (or about to be on) screen, so we can compute its true input-to-
     * present latency.
     *
     * After computing latency, captures the current input stamp into the slot for use
     * when this newly-submitted frame's fence signals (one swapchain-depth later).
     */
    public void onSlotReady(int slot) {
        long now = System.nanoTime();
        long oldStamp = slotInputStamps[slot];
        if (oldStamp > 0) {
            long lat = now - oldStamp;
            if (lat < 0) lat = 0;
            latency.record(lat);
        }
        // Capture the input timestamp for THIS frame; we'll see its latency N frames from now.
        long inputAtFrameStart = lastInputNanos;
        slotInputStamps[slot] = inputAtFrameStart > 0 ? inputAtFrameStart : now;
    }

    /**
     * Marks end of frame (after present). Records frame time and per-stage section durations.
     */
    public void endFrame() {
        long now = System.nanoTime();
        // Ensure PRESENT_END is captured even if caller didn't stamp it
        if (stageTimes[Stage.PRESENT_END.ordinal()] < stageTimes[Stage.FRAME_START.ordinal()]) {
            stageTimes[Stage.PRESENT_END.ordinal()] = now;
        }

        long frameStart = stageTimes[Stage.FRAME_START.ordinal()];
        long fenceEnd   = stageTimes[Stage.FENCE_WAIT_END.ordinal()];
        long recordEnd  = stageTimes[Stage.RECORD_END.ordinal()];
        long submitEnd  = stageTimes[Stage.SUBMIT_END.ordinal()];
        long presentEnd = stageTimes[Stage.PRESENT_END.ordinal()];

        frameTime.record(presentEnd - frameStart);
        // Sections only recorded if their stage was actually stamped (>= frame_start)
        if (fenceEnd >= frameStart)              freeWait.record(fenceEnd - frameStart);
        if (recordEnd >= fenceEnd && fenceEnd > 0)   recordTime.record(recordEnd - fenceEnd);
        if (submitEnd >= recordEnd && recordEnd > 0) submitTime.record(submitEnd - recordEnd);
        if (presentEnd >= submitEnd && submitEnd > 0) presentTime.record(presentEnd - submitEnd);

        // FPS counter
        fpsFrameCount++;
        if (now - fpsWindowStart >= 1_000_000_000L) {
            currentFps = fpsFrameCount;
            fpsFrameCount = 0;
            fpsWindowStart = now;
        }
    }

    // --- Queries ---

    public int fps() { return currentFps; }
    public int sampleCount() { return frameTime.count; }
    public int windowSize() { return windowSize; }

    public double frameTimeMs() { return frameTime.lastMs(); }
    public double avgFrameTimeMs() { return frameTime.avgMs(); }
    public double maxFrameTimeMs() { return frameTime.maxMs(); }
    public double minFrameTimeMs() { return frameTime.minMs(); }

    public double latencyMs() { return latency.lastMs(); }
    public double avgLatencyMs() { return latency.avgMs(); }
    public double maxLatencyMs() { return latency.maxMs(); }
    public double minLatencyMs() { return latency.minMs(); }
    public int latencySpikes() { return latency.spikes(spikeThreshold); }
    public int frameTimeSpikes() { return frameTime.spikes(spikeThreshold); }

    public double avgFreeWaitMs() { return freeWait.avgMs(); }
    public double avgRecordMs()   { return recordTime.avgMs(); }
    public double avgSubmitMs()   { return submitTime.avgMs(); }
    public double avgPresentMs()  { return presentTime.avgMs(); }

    /** @return derived FPS from worst frame time in the window */
    public int fpsMin() {
        long worstNs = frameTime.maxRaw();
        return worstNs > 0 ? (int)(1_000_000_000L / worstNs) : 0;
    }

    // --- Summary ---

    /**
     * @return constant-width formatted summary line:
     * {@code fps: #### | fps min: #### | latency: ##.#ms, ##.#ms max | spikes: ####/#### | wait/rec/sub/pres: #.#/#.#/#.#/#.# ms}
     */
    public String summary() {
        return String.format(
            "fps: %4d | fps min: %4d | latency: %5.1fms, %5.1fms max | spikes: %4d/%4d | wait/rec/sub/pres: %4.1f/%4.1f/%4.1f/%4.1f ms",
            fps(), fpsMin(),
            avgLatencyMs(), maxLatencyMs(),
            latencySpikes(), latency.count,
            avgFreeWaitMs(), avgRecordMs(), avgSubmitMs(), avgPresentMs());
    }

    // --- Internal: circular buffer with running stats ---

    private static final class SeriesStats {
        final long[] buffer;
        int head = 0;
        int count = 0;
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = 0;
        boolean minMaxDirty = false;
        long lastValue;

        SeriesStats(int size) { this.buffer = new long[size]; }

        void record(long value) {
            if (value < 0) value = 0;
            if (count == buffer.length) {
                long old = buffer[head];
                sum -= old;
                if (old == min || old == max) minMaxDirty = true;
            } else {
                count++;
            }
            buffer[head] = value;
            sum += value;
            head = (head + 1) % buffer.length;
            lastValue = value;
            if (!minMaxDirty) {
                if (value < min) min = value;
                if (value > max) max = value;
            }
        }

        void recomputeMinMaxIfDirty() {
            if (!minMaxDirty) return;
            long mn = Long.MAX_VALUE, mx = 0;
            for (int i = 0; i < count; i++) {
                long v = buffer[i];
                if (v < mn) mn = v;
                if (v > mx) mx = v;
            }
            min = mn;
            max = mx;
            minMaxDirty = false;
        }

        double avgMs() { return count == 0 ? 0 : (sum / (double) count) / 1_000_000.0; }
        double maxMs() { recomputeMinMaxIfDirty(); return max / 1_000_000.0; }
        double minMs() { recomputeMinMaxIfDirty(); return count == 0 ? 0 : min / 1_000_000.0; }
        double lastMs() { return lastValue / 1_000_000.0; }
        long maxRaw() { recomputeMinMaxIfDirty(); return max; }

        int spikes(double thresholdMultiplier) {
            if (count < 2) return 0;
            double avg = sum / (double) count;
            double thr = avg * thresholdMultiplier;
            int s = 0;
            for (int i = 0; i < count; i++) if (buffer[i] > thr) s++;
            return s;
        }
    }
}
