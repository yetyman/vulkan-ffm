package io.github.yetyman.vulkan.graph.nodes;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.edges.TemporalEdge;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.ScheduleHint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A pass that runs multiple iterations until convergence or a max iteration cap.
 * Used for iterative light bounces, fluid simulation steps, etc.
 *
 * The graph compiler detects self-dependency (readsAndWrites on the same resource)
 * and automatically allocates ping-pong buffers. The iteration count can be:
 * - Fixed (maxIterations only, no predicate)
 * - Pre-determined (application sets count before submission)
 * - Predicate-driven (checked after each iteration)
 *
 * Usage:
 * <pre>
 *   IterativePassNode.builder()
 *       .name("light_bounce")
 *       .reads(sceneData)
 *       .readsAndWrites(bounceAccumulator)
 *       .maxIterations(16)
 *       .continueWhen(() -> !converged())
 *       .execute((ctx, iteration) -> { ... })
 *       .build();
 * </pre>
 */
public class IterativePassNode implements RenderNode {

    private final String name;
    private final List<ResourceEdge> reads;
    private final List<ResourceEdge> writes;
    private final List<GraphResource> readsAndWrites;
    private final ScheduleHint scheduleHint;
    private final QueueCapability requiredQueue;
    private final int maxIterations;
    private final BooleanSupplier continueWhen;
    private final IterativeExecuteFunc executeFunc;
    private volatile boolean active = true;
    private int iterationCount = -1; // pre-determined count, or -1 for predicate

    private IterativePassNode(Builder b) {
        this.name = b.name;
        this.reads = Collections.unmodifiableList(b.reads);
        this.writes = Collections.unmodifiableList(b.writes);
        this.readsAndWrites = Collections.unmodifiableList(b.readsAndWrites);
        this.scheduleHint = b.scheduleHint;
        this.requiredQueue = b.requiredQueue;
        this.maxIterations = b.maxIterations;
        this.continueWhen = b.continueWhen;
        this.executeFunc = b.executeFunc;
    }

    public static Builder builder() { return new Builder(); }

    @Override public String name() { return name; }
    @Override public NodeType type() { return NodeType.COMPUTE; }
    @Override public List<ResourceEdge> reads() { return reads; }
    @Override public List<ResourceEdge> writes() { return writes; }
    @Override public ScheduleHint scheduleHint() { return scheduleHint; }
    @Override public QueueCapability requiredQueue() { return requiredQueue; }
    @Override public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }

    /** @return resources that are both read and written each iteration (ping-pong candidates) */
    public List<GraphResource> readsAndWrites() { return readsAndWrites; }

    /** @return maximum iteration count (safety cap) */
    public int maxIterations() { return maxIterations; }

    /** Sets a pre-determined iteration count (overrides predicate for this submission) */
    public void setIterationCount(int count) { this.iterationCount = count; }

    /** Clears the pre-determined count (reverts to predicate) */
    public void clearIterationCount() { this.iterationCount = -1; }

    @Override
    public void execute(ExecutionContext ctx) {
        int count = iterationCount >= 0 ? iterationCount : maxIterations;
        for (int i = 0; i < count; i++) {
            executeFunc.execute(ctx, i);
            if (continueWhen != null && iterationCount < 0 && !continueWhen.getAsBoolean()) break;
        }
    }

    @FunctionalInterface
    public interface IterativeExecuteFunc {
        void execute(ExecutionContext ctx, int iteration);
    }

    public static class Builder {
        private String name;
        private final List<ResourceEdge> reads = new ArrayList<>();
        private final List<ResourceEdge> writes = new ArrayList<>();
        private final List<GraphResource> readsAndWrites = new ArrayList<>();
        private ScheduleHint scheduleHint = ScheduleHint.NONE;
        private QueueCapability requiredQueue = QueueCapability.COMPUTE;
        private int maxIterations = 1;
        private BooleanSupplier continueWhen;
        private IterativeExecuteFunc executeFunc;

        private Builder() {}

        public Builder name(String name) { this.name = name; return this; }
        public Builder reads(ResourceEdge edge) { this.reads.add(edge); return this; }
        public Builder writes(ResourceEdge edge) { this.writes.add(edge); return this; }
        /** Declares a resource that is both read and written each iteration (auto ping-pong) */
        public Builder readsAndWrites(GraphResource resource) { this.readsAndWrites.add(resource); return this; }
        public Builder scheduleHint(ScheduleHint hint) { this.scheduleHint = hint; return this; }
        public Builder requiredQueue(QueueCapability cap) { this.requiredQueue = cap; return this; }
        public Builder maxIterations(int max) { this.maxIterations = max; return this; }
        public Builder continueWhen(BooleanSupplier predicate) { this.continueWhen = predicate; return this; }
        public Builder execute(IterativeExecuteFunc func) { this.executeFunc = func; return this; }

        public IterativePassNode build() {
            if (name == null) throw new IllegalStateException("name not set");
            if (executeFunc == null) throw new IllegalStateException("execute function not set");
            if (maxIterations < 1) throw new IllegalStateException("maxIterations must be >= 1");
            return new IterativePassNode(this);
        }
    }
}
