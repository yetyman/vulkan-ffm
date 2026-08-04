package io.github.yetyman.vulkan.graph.nodes;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.edges.SemaphoreEdge;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.ScheduleHint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * An explicit buffer/image copy node in the render graph.
 */
public class TransferNode implements RenderNode {

    private final String name;
    private final List<ResourceEdge> reads;
    private final List<ResourceEdge> writes;
    private final List<SemaphoreEdge> externalWaits;
    private final List<SemaphoreEdge> externalSignals;
    private final ScheduleHint scheduleHint;
    private final QueueCapability requiredQueue;
    private final Consumer<ExecutionContext> executeFunc;

    private TransferNode(Builder b) {
        this.name = b.name;
        this.reads = Collections.unmodifiableList(b.reads);
        this.writes = Collections.unmodifiableList(b.writes);
        this.externalWaits = Collections.unmodifiableList(b.externalWaits);
        this.externalSignals = Collections.unmodifiableList(b.externalSignals);
        this.scheduleHint = b.scheduleHint;
        this.requiredQueue = b.requiredQueue;
        this.executeFunc = b.executeFunc;
    }

    public static Builder builder() { return new Builder(); }

    @Override public String name() { return name; }
    @Override public NodeType type() { return NodeType.TRANSFER; }
    @Override public List<ResourceEdge> reads() { return reads; }
    @Override public List<ResourceEdge> writes() { return writes; }
    @Override public List<SemaphoreEdge> externalWaits() { return externalWaits; }
    @Override public List<SemaphoreEdge> externalSignals() { return externalSignals; }
    @Override public ScheduleHint scheduleHint() { return scheduleHint; }
    @Override public QueueCapability requiredQueue() { return requiredQueue; }

    @Override
    public void execute(ExecutionContext ctx) {
        executeFunc.accept(ctx);
    }

    public static class Builder {
        private String name;
        private final List<ResourceEdge> reads = new ArrayList<>();
        private final List<ResourceEdge> writes = new ArrayList<>();
        private final List<SemaphoreEdge> externalWaits = new ArrayList<>();
        private final List<SemaphoreEdge> externalSignals = new ArrayList<>();
        private ScheduleHint scheduleHint = ScheduleHint.NONE;
        private QueueCapability requiredQueue = QueueCapability.TRANSFER;
        private Consumer<ExecutionContext> executeFunc;

        private Builder() {}

        public Builder name(String name) { this.name = name; return this; }
        public Builder reads(ResourceEdge edge) { this.reads.add(edge); return this; }
        public Builder writes(ResourceEdge edge) { this.writes.add(edge); return this; }
        public Builder externalWait(SemaphoreEdge edge) { this.externalWaits.add(edge); return this; }
        public Builder externalSignal(SemaphoreEdge edge) { this.externalSignals.add(edge); return this; }
        public Builder scheduleHint(ScheduleHint hint) { this.scheduleHint = hint; return this; }
        public Builder requiredQueue(QueueCapability cap) { this.requiredQueue = cap; return this; }
        public Builder execute(Consumer<ExecutionContext> func) { this.executeFunc = func; return this; }

        public TransferNode build() {
            if (name == null) throw new IllegalStateException("name not set");
            if (executeFunc == null) throw new IllegalStateException("execute function not set");
            return new TransferNode(this);
        }
    }
}
