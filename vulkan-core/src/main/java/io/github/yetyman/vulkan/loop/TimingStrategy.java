package io.github.yetyman.vulkan.loop;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures timing information around loop work and named sections within it.
 * Compose into a {@link LoopThread} to gain visibility into frame timing and budget adherence.
 *
 * <pre>{@code
 * loopThread.timing(TimingStrategy.profiled());
 * loopThread.timing(TimingStrategy.budgeted(Map.of("physics", 0.4, "render", 0.5)));
 * }</pre>
 */
public interface TimingStrategy {

    /**
     * Called before the work function runs.
     */
    void beforeWork();

    /**
     * Called after the work function completes.
     */
    void afterWork();

    /**
     * Called at the start of a named section within the work function.
     */
    void beginSection(String sectionName);

    /**
     * Called at the end of a named section within the work function.
     */
    void endSection(String sectionName);

    /**
     * Returns the allotted fraction of the frame budget for the named section (0.0–1.0).
     * Returns 1.0 if no allotment is configured for the section.
     */
    double sectionAllotment(String sectionName);

    /**
     * No-op timing — zero overhead, suitable for release builds.
     */
    static TimingStrategy none() {
        return new TimingStrategy() {
            public void beforeWork() {
            }

            public void afterWork() {
            }

            public void beginSection(String s) {
            }

            public void endSection(String s) {
            }

            public double sectionAllotment(String s) {
                return 1.0;
            }
        };
    }

    /**
     * Records frame and section durations, accessible via {@link TimingStrategy.Stats}.
     */
    static TimingStrategy profiled() {
        return new Profiled(Map.of());
    }

    /**
     * Records timing and warns when sections exceed their allotted budget fraction.
     *
     * @param allotments map of section name to fraction of frame budget (e.g. "physics" -> 0.4)
     */
    static TimingStrategy budgeted(Map<String, Double> allotments) {
        return new Profiled(allotments);
    }

    /**
     * @return the most recent frame duration in nanoseconds, or 0 if not available.
     */
    default long lastFrameNanos() {
        return 0;
    }

    /**
     * @return the most recent duration of the named section in nanoseconds, or 0 if not available.
     */
    default long lastSectionNanos(String sectionName) {
        return 0;
    }

    class Profiled implements TimingStrategy {
        private final Map<String, Double> allotments;
        private final ConcurrentHashMap<String, Long> sectionStart = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Long> sectionDurations = new ConcurrentHashMap<>();
        private volatile long frameStart;
        private volatile long lastFrameNanos;

        private Profiled(Map<String, Double> allotments) {
            this.allotments = allotments;
        }

        @Override
        public void beforeWork() {
            frameStart = System.nanoTime();
        }

        @Override
        public void afterWork() {
            lastFrameNanos = System.nanoTime() - frameStart;
        }

        @Override
        public void beginSection(String name) {
            sectionStart.put(name, System.nanoTime());
        }

        @Override
        public void endSection(String name) {
            Long start = sectionStart.remove(name);
            if (start == null) return;
            long duration = System.nanoTime() - start;
            sectionDurations.put(name, duration);
            double allotment = allotments.getOrDefault(name, 1.0);
            if (lastFrameNanos > 0 && duration > lastFrameNanos * allotment) {
                System.err.printf("[TimingStrategy] section '%s' exceeded budget: %.2fms / allotted %.0f%%%n",
                        name, duration / 1_000_000.0, allotment * 100);
            }
        }

        @Override
        public double sectionAllotment(String name) {
            return allotments.getOrDefault(name, 1.0);
        }

        @Override
        public long lastFrameNanos() {
            return lastFrameNanos;
        }

        @Override
        public long lastSectionNanos(String name) {
            return sectionDurations.getOrDefault(name, 0L);
        }
    }
}
