package io.github.yetyman.vulkan.graph.nodes;

import io.github.yetyman.vulkan.graph.edges.FeedbackEdge;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.edges.SemaphoreEdge;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.ScheduleHint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A compute dispatch node in the render graph.
 */
public class ComputePassNode implements RenderNode {

    private final String name;
    private final List<ResourceEdge> reads;
    private final List<ResourceEdge> writes;
    private final List<SemaphoreEdge> externalWaits;
    private final List<SemaphoreEdge> externalSignals;
    private final List<FeedbackEdge> feedbackReads;
    private final List<GraphResource> bindlessReads;
    private final ScheduleHint scheduleHint;
    private final QueueCapability requiredQueue;
    private final Consumer<ExecutionContext> executeFunc;
    private final Consumer<NodeStats> statsFunc;
    private volatile boolean active = true;

    private ComputePassNode(Builder b) {
        this.name = b.name;
        this.reads = Collections.unmodifiableList(b.reads);
        this.writes = Collections.unmodifiableList(b.writes);
        this.externalWaits = Collections.unmodifiableList(b.externalWaits);
        this.externalSignals = Collections.unmodifiableList(b.externalSignals);
        this.feedbackReads = Collections.unmodifiableList(b.feedbackReads);
        this.bindlessReads = Collections.unmodifiableList(b.bindlessReads);
        this.scheduleHint = b.scheduleHint;
        this.requiredQueue = b.requiredQueue;
        this.executeFunc = b.executeFunc;
        this.statsFunc = b.statsFunc;
    }

    public static Builder builder() { return new Builder(); }

    @Override public String name() { return name; }
    @Override public NodeType type() { return NodeType.COMPUTE; }
    @Override public List<ResourceEdge> reads() { return reads; }
    @Override public List<ResourceEdge> writes() { return writes; }
    @Override public List<SemaphoreEdge> externalWaits() { return externalWaits; }
    @Override public List<SemaphoreEdge> externalSignals() { return externalSignals; }
    @Override public List<FeedbackEdge> feedbackReads() { return feedbackReads; }
    @Override public List<GraphResource> bindlessReads() { return bindlessReads; }
    @Override public ScheduleHint scheduleHint() { return scheduleHint; }
    @Override public QueueCapability requiredQueue() { return requiredQueue; }
    @Override public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }

    @Override
    public void execute(ExecutionContext ctx) {
        executeFunc.accept(ctx);
    }

    @Override
    public void onStats(NodeStats stats) {
        if (statsFunc != null) statsFunc.accept(stats);
    }

    public static class Builder {
        private String name;
        private final List<ResourceEdge> reads = new ArrayList<>();
        private final List<ResourceEdge> writes = new ArrayList<>();
        private final List<SemaphoreEdge> externalWaits = new ArrayList<>();
        private final List<SemaphoreEdge> externalSignals = new ArrayList<>();
        private final List<FeedbackEdge> feedbackReads = new ArrayList<>();
        private final List<GraphResource> bindlessReads = new ArrayList<>();
        private ScheduleHint scheduleHint = ScheduleHint.NONE;
        private QueueCapability requiredQueue = QueueCapability.COMPUTE;
        private Consumer<ExecutionContext> executeFunc;
        private Consumer<NodeStats> statsFunc;

        private Builder() {}

        public Builder name(String name) { this.name = name; return this; }
        public Builder reads(ResourceEdge edge) { this.reads.add(edge); return this; }
        public Builder writes(ResourceEdge edge) { this.writes.add(edge); return this; }
        public Builder externalWait(SemaphoreEdge edge) { this.externalWaits.add(edge); return this; }
        public Builder externalSignal(SemaphoreEdge edge) { this.externalSignals.add(edge); return this; }
        public Builder feedbackRead(FeedbackEdge edge) { this.feedbackReads.add(edge); return this; }
        public Builder bindlessReads(GraphResource resource) { this.bindlessReads.add(resource); return this; }
        public Builder scheduleHint(ScheduleHint hint) { this.scheduleHint = hint; return this; }
        public Builder requiredQueue(QueueCapability cap) { this.requiredQueue = cap; return this; }
        public Builder execute(Consumer<ExecutionContext> func) { this.executeFunc = func; return this; }
        public Builder onStats(Consumer<NodeStats> func) { this.statsFunc = func; return this; }

        public ComputePassNode build() {
            if (name == null) throw new IllegalStateException("name not set");
            if (executeFunc == null) throw new IllegalStateException("execute function not set");
            return new ComputePassNode(this);
        }
    }
}
