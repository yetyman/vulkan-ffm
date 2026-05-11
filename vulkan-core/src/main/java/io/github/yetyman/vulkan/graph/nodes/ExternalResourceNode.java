package io.github.yetyman.vulkan.graph.nodes;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.edges.SemaphoreEdge;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.ScheduleHint;

import java.lang.foreign.MemorySegment;
import java.util.Collections;
import java.util.List;

/**
 * Root node representing an externally-produced resource (async CPU data, streaming upload).
 * The graph treats it as a producer with an external semaphore. The readyWhen semaphore
 * becomes a queue wait on whichever pass first consumes the resource.
 */
public class ExternalResourceNode implements RenderNode {

    private final String name;
    private final GraphResource resource;
    private final ResourceEdge writeEdge;
    private final SemaphoreEdge readySemaphore;

    private ExternalResourceNode(Builder b) {
        this.name = b.name;
        this.resource = b.resource;
        this.writeEdge = ResourceEdge.write(b.resource, b.initialAccessMask, b.initialStageMask);
        this.readySemaphore = b.readySemaphore;
    }

    public static Builder builder() { return new Builder(); }

    @Override public String name() { return name; }
    @Override public NodeType type() { return NodeType.TRANSFER; }
    @Override public List<ResourceEdge> reads() { return Collections.emptyList(); }
    @Override public List<ResourceEdge> writes() { return List.of(writeEdge); }
    @Override public List<SemaphoreEdge> externalWaits() { return List.of(readySemaphore); }
    @Override public ScheduleHint scheduleHint() { return ScheduleHint.EARLY; }
    @Override public QueueCapability requiredQueue() { return QueueCapability.ANY; }

    /** @return the resource this node produces */
    public GraphResource resource() { return resource; }

    @Override
    public void execute(ExecutionContext ctx) {
        // No-op: the resource was produced externally. This node exists only for
        // dependency tracking and semaphore wait injection.
    }

    public static class Builder {
        private String name;
        private GraphResource resource;
        private SemaphoreEdge readySemaphore;
        private int initialAccessMask;
        private int initialStageMask;

        private Builder() {}

        /** Sets the node name */
        public Builder name(String name) { this.name = name; return this; }

        /** Sets the resource this external source produces */
        public Builder produces(GraphResource resource) { this.resource = resource; return this; }

        /** Sets the semaphore that signals when the resource is ready */
        public Builder readyWhen(MemorySegment semaphore, int stageMask) {
            this.readySemaphore = SemaphoreEdge.binary(semaphore, stageMask);
            return this;
        }

        /** Sets the semaphore (timeline) that signals when the resource is ready */
        public Builder readyWhen(MemorySegment semaphore, long value, int stageMask) {
            this.readySemaphore = SemaphoreEdge.timeline(semaphore, value, stageMask);
            return this;
        }

        /** Sets the initial access mask the resource will be in after the external work */
        public Builder initialAccessMask(int mask) { this.initialAccessMask = mask; return this; }

        /** Sets the initial stage mask */
        public Builder initialStageMask(int mask) { this.initialStageMask = mask; return this; }

        public ExternalResourceNode build() {
            if (name == null) throw new IllegalStateException("name not set");
            if (resource == null) throw new IllegalStateException("resource not set");
            if (readySemaphore == null) throw new IllegalStateException("readyWhen semaphore not set");
            return new ExternalResourceNode(this);
        }
    }
}
