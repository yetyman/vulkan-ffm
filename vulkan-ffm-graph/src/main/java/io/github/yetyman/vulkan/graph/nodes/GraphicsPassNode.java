package io.github.yetyman.vulkan.graph.nodes;

import io.github.yetyman.vulkan.VkRendering;
import io.github.yetyman.vulkan.graph.edges.FeedbackEdge;
import io.github.yetyman.vulkan.graph.edges.OptionalEdge;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.edges.SemaphoreEdge;
import io.github.yetyman.vulkan.graph.edges.TemporalEdge;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.ImportedResource;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.ScheduleHint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A rasterization pass node in the render graph.
 *
 * When a {@code VkRendering.Builder} is provided via {@link Builder#autoRendering(VkRendering.Builder)},
 * the executor automatically begins dynamic rendering before calling execute() and ends it after,
 * using the builder's cached zero-allocation path. The node's execute lambda only needs to record
 * draw calls.
 */
public class GraphicsPassNode implements RenderNode {

    private final String name;
    private final List<ResourceEdge> reads;
    private final List<ResourceEdge> writes;
    private final List<SemaphoreEdge> externalWaits;
    private final List<SemaphoreEdge> externalSignals;
    private final List<FeedbackEdge> feedbackReads;
    private final List<TemporalEdge> temporalEdges;
    private final List<GraphResource> bindlessReads;
    private final List<OptionalEdge> optionalReads;
    private final ScheduleHint scheduleHint;
    private final Consumer<ExecutionContext> executeFunc;
    private final Consumer<NodeStats> statsFunc;
    private volatile boolean active = true;

    // Auto-rendering: a pre-configured VkRendering.Builder that the executor caches and reuses
    private final VkRendering.Builder renderingBuilder;

    // Optional imported resource whose image view is patched into the auto-rendering color attachment each frame
    private final ImportedResource colorAttachmentImport;
    private final int colorAttachmentIndex;

    private GraphicsPassNode(Builder b) {
        this.name = b.name;
        this.reads = Collections.unmodifiableList(b.reads);
        this.writes = Collections.unmodifiableList(b.writes);
        this.externalWaits = Collections.unmodifiableList(b.externalWaits);
        this.externalSignals = Collections.unmodifiableList(b.externalSignals);
        this.feedbackReads = Collections.unmodifiableList(b.feedbackReads);
        this.temporalEdges = Collections.unmodifiableList(b.temporalEdges);
        this.bindlessReads = Collections.unmodifiableList(b.bindlessReads);
        this.optionalReads = Collections.unmodifiableList(b.optionalReads);
        this.scheduleHint = b.scheduleHint;
        this.executeFunc = b.executeFunc;
        this.statsFunc = b.statsFunc;
        this.renderingBuilder = b.renderingBuilder;
        this.colorAttachmentImport = b.colorAttachmentImport;
        this.colorAttachmentIndex = b.colorAttachmentIndex;
    }

    public static Builder builder() { return new Builder(); }

    @Override public String name() { return name; }
    @Override public NodeType type() { return NodeType.GRAPHICS; }
    @Override public List<ResourceEdge> reads() { return reads; }
    @Override public List<ResourceEdge> writes() { return writes; }
    @Override public List<SemaphoreEdge> externalWaits() { return externalWaits; }
    @Override public List<SemaphoreEdge> externalSignals() { return externalSignals; }
    @Override public List<FeedbackEdge> feedbackReads() { return feedbackReads; }
    @Override public List<TemporalEdge> temporalEdges() { return temporalEdges; }
    @Override public List<GraphResource> bindlessReads() { return bindlessReads; }
    @Override public List<OptionalEdge> optionalReads() { return optionalReads; }
    @Override public ScheduleHint scheduleHint() { return scheduleHint; }
    @Override public QueueCapability requiredQueue() { return QueueCapability.GRAPHICS; }
    @Override public boolean isActive() { return active; }

    /** Allows toggling this node on/off (e.g. from onStats feedback) */
    public void setActive(boolean active) { this.active = active; }

    /** @return true if this node uses auto-managed dynamic rendering */
    public boolean autoRendering() { return renderingBuilder != null; }

    /** @return the VkRendering.Builder for auto-rendering, or null if not enabled */
    public VkRendering.Builder renderingBuilder() { return renderingBuilder; }

    /** @return the imported resource used as color attachment, or null if not configured */
    public ImportedResource colorAttachmentImport() { return colorAttachmentImport; }

    /** @return the color attachment index to patch with the imported resource's image view */
    public int colorAttachmentIndex() { return colorAttachmentIndex; }

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
        private final List<TemporalEdge> temporalEdges = new ArrayList<>();
        private final List<GraphResource> bindlessReads = new ArrayList<>();
        private final List<OptionalEdge> optionalReads = new ArrayList<>();
        private ScheduleHint scheduleHint = ScheduleHint.NONE;
        private Consumer<ExecutionContext> executeFunc;
        private Consumer<NodeStats> statsFunc;
        private VkRendering.Builder renderingBuilder;
        private ImportedResource colorAttachmentImport;
        private int colorAttachmentIndex = 0;

        private Builder() {}

        public Builder name(String name) { this.name = name; return this; }
        public Builder reads(ResourceEdge edge) { this.reads.add(edge); return this; }
        public Builder writes(ResourceEdge edge) { this.writes.add(edge); return this; }
        public Builder externalWait(SemaphoreEdge edge) { this.externalWaits.add(edge); return this; }
        public Builder externalSignal(SemaphoreEdge edge) { this.externalSignals.add(edge); return this; }
        public Builder feedbackRead(FeedbackEdge edge) { this.feedbackReads.add(edge); return this; }
        public Builder temporalEdge(TemporalEdge edge) { this.temporalEdges.add(edge); return this; }
        public Builder bindlessReads(GraphResource resource) { this.bindlessReads.add(resource); return this; }
        public Builder optionalRead(OptionalEdge edge) { this.optionalReads.add(edge); return this; }
        public Builder scheduleHint(ScheduleHint hint) { this.scheduleHint = hint; return this; }
        public Builder execute(Consumer<ExecutionContext> func) { this.executeFunc = func; return this; }
        public Builder onStats(Consumer<NodeStats> func) { this.statsFunc = func; return this; }

        /**
         * Enables auto-managed dynamic rendering using the provided VkRendering.Builder.
         * The graph will call buildAndCache() on this builder at compile time, then
         * beginCached()/end() around each execute() call with zero per-frame allocation.
         *
         * The caller configures the VkRendering.Builder with attachments, load/store ops,
         * clear values, and render area as usual. The graph handles caching and patching.
         *
         * @param rendering a fully-configured VkRendering.Builder (device, attachments, area set)
         */
        public Builder autoRendering(VkRendering.Builder rendering) {
            this.renderingBuilder = rendering;
            return this;
        }

        /**
         * Configures the auto-rendering to patch its color attachment image view from an
         * ImportedResource each frame. The executor calls patchColorView(index, importedResource.handle())
         * before beginCached(), so the correct swapchain image is always used.
         *
         * Requires {@link #autoRendering(VkRendering.Builder)} to be set.
         * Also automatically adds a write edge for the imported resource with
         * COLOR_ATTACHMENT_WRITE access and COLOR_ATTACHMENT_OUTPUT stage.
         *
         * @param imported the imported resource (e.g. swapchain image)
         * @param attachmentIndex which color attachment index to patch (usually 0)
         */
        public Builder colorAttachment(ImportedResource imported, int attachmentIndex) {
            this.colorAttachmentImport = imported;
            this.colorAttachmentIndex = attachmentIndex;
            // Auto-add write edge for the imported resource with correct layout
            // VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT = 0x100
            // VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT = 0x400
            // VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL = 2
            this.writes.add(ResourceEdge.writeImage(imported, 0x100, 0x400, 2));
            return this;
        }

        /**
         * Configures the auto-rendering to patch color attachment 0 from an ImportedResource.
         * Shorthand for {@link #colorAttachment(ImportedResource, int)} with index 0.
         */
        public Builder colorAttachment(ImportedResource imported) {
            return colorAttachment(imported, 0);
        }

        public GraphicsPassNode build() {
            if (name == null) throw new IllegalStateException("name not set");
            if (executeFunc == null) throw new IllegalStateException("execute function not set");
            return new GraphicsPassNode(this);
        }
    }
}
