package io.github.yetyman.vulkan.graph.nodes;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.ScheduleHint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Synchronous CPU work item with a defined place in the DAG.
 * The graph schedules it between GPU submissions at the optimal point
 * and emits host barriers around it.
 */
public class CpuWorkNode implements RenderNode {

    private final String name;
    private final List<ResourceEdge> reads;
    private final List<ResourceEdge> writes;
    private final ScheduleHint scheduleHint;
    private final Consumer<ExecutionContext> workFunc;

    private CpuWorkNode(Builder b) {
        this.name = b.name;
        this.reads = Collections.unmodifiableList(b.reads);
        this.writes = Collections.unmodifiableList(b.writes);
        this.scheduleHint = b.scheduleHint;
        this.workFunc = b.workFunc;
    }

    public static Builder builder() { return new Builder(); }

    @Override public String name() { return name; }
    @Override public NodeType type() { return NodeType.CPU_WORK; }
    @Override public List<ResourceEdge> reads() { return reads; }
    @Override public List<ResourceEdge> writes() { return writes; }
    @Override public ScheduleHint scheduleHint() { return scheduleHint; }
    @Override public QueueCapability requiredQueue() { return QueueCapability.ANY; }

    @Override
    public void execute(ExecutionContext ctx) {
        workFunc.accept(ctx);
    }

    public static class Builder {
        private String name;
        private final List<ResourceEdge> reads = new ArrayList<>();
        private final List<ResourceEdge> writes = new ArrayList<>();
        private ScheduleHint scheduleHint = ScheduleHint.NONE;
        private Consumer<ExecutionContext> workFunc;

        private Builder() {}

        public Builder name(String name) { this.name = name; return this; }
        public Builder reads(ResourceEdge edge) { this.reads.add(edge); return this; }
        public Builder writes(ResourceEdge edge) { this.writes.add(edge); return this; }
        public Builder scheduleHint(ScheduleHint hint) { this.scheduleHint = hint; return this; }
        public Builder does(Consumer<ExecutionContext> func) { this.workFunc = func; return this; }

        public CpuWorkNode build() {
            if (name == null) throw new IllegalStateException("name not set");
            if (workFunc == null) throw new IllegalStateException("work function not set");
            return new CpuWorkNode(this);
        }
    }
}
