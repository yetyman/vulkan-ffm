package io.github.yetyman.vulkan.graph.resources;

import io.github.yetyman.vulkan.VkDescriptorSetLayout;

import java.lang.foreign.MemorySegment;
import java.util.List;

/**
 * A temporal resource that participates in cross-frame cycles (TAA history, simulation ping-pong).
 * Maintains multiple physical slots and flips between them each submission.
 *
 * The graph manages the ring automatically: readers of the "previous" slot get the most recently
 * written copy, writers of the "current" slot get the next slot in the ring.
 *
 * The flip counter advances only when the resource is actually written (respects pass activation).
 */
public class TemporalResource {

    private final String name;
    private final ResourceDescriptor descriptor;
    private final int bufferCount;
    private final InitialState initialState;
    private final int descriptorBinding; // -1 if not configured
    private final VkDescriptorSetLayout descriptorLayout; // null if not configured
    private final int pairedWriteBinding; // -1 if not paired
    private final VkDescriptorSetLayout pairedLayout; // null if not paired
    private final TemporalResizeStrategy resizeStrategy;

    // Physical slots allocated during graph compilation
    private GraphResource[] physicalSlots;

    // Optional graph-managed descriptor sets (null if user manages descriptors manually)
    private TemporalDescriptorBinding readDescriptorBinding;
    private TemporalDescriptorBinding writeDescriptorBinding;
    private TemporalDescriptorBinding pairedDescriptorBinding;

    // Flip counter - incremented only when actually written
    private int writeCount = 0;

    // Staleness: submissions since last write (for multi-rate temporal resources)
    private int submissionsSinceWrite = 0;

    // Submission-local lifetime tracking for cross-frame aliasing
    private int firstUseOrder = -1;
    private int lastUseOrder = -1;

    private TemporalResource(Builder b) {
        this.name = b.name;
        this.descriptor = b.descriptor;
        this.bufferCount = b.bufferCount;
        this.initialState = b.initialState;
        this.descriptorBinding = b.descriptorBinding;
        this.descriptorLayout = b.descriptorLayout;
        this.pairedWriteBinding = b.pairedWriteBinding;
        this.pairedLayout = b.pairedLayout;
        this.resizeStrategy = b.resizeStrategy != null ? b.resizeStrategy : TemporalResizeStrategy.clear();
    }

    public static Builder builder() { return new Builder(); }

    /** @return resource name */
    public String name() { return name; }

    /** @return resource descriptor (format, size, usage) */
    public ResourceDescriptor descriptor() { return descriptor; }

    /** @return number of physical buffer copies (2 = double, 3 = triple) */
    public int bufferCount() { return bufferCount; }

    /** @return initial state for first-frame reads, or null if not set */
    public InitialState initialState() { return initialState; }

    /** @return the descriptor binding index for graph-managed sets, or -1 if not configured */
    public int descriptorBinding() { return descriptorBinding; }

    /** @return the descriptor set layout for graph-managed sets, or null if not configured */
    public VkDescriptorSetLayout descriptorLayout() { return descriptorLayout; }

    /** @return true if graph-managed descriptor sets are configured (both binding and layout provided) */
    public boolean hasDescriptorBinding() { return descriptorBinding >= 0 && descriptorLayout != null; }

    /** @return true if paired (read+write) descriptor sets are configured */
    public boolean hasPairedDescriptorBinding() { return pairedWriteBinding >= 0 && pairedLayout != null; }

    /** @return the write binding index for paired descriptors, or -1 */
    public int pairedWriteBinding() { return pairedWriteBinding; }

    /** @return the layout for paired descriptors, or null */
    public VkDescriptorSetLayout pairedLayout() { return pairedLayout; }

    /** @return the resize strategy for this temporal resource */
    public TemporalResizeStrategy resizeStrategy() { return resizeStrategy; }

    /** @return true if initial state is defined */
    public boolean hasInitialState() { return initialState != null; }

    /** @return the current write count (number of times actually written) */
    public int writeCount() { return writeCount; }

    /**
     * Returns the physical slot to write to for the current submission.
     * This is the "current" slot that will become "previous" on the next submission.
     */
    public GraphResource currentWriteSlot() {
        if (physicalSlots == null) throw new IllegalStateException("Physical slots not allocated for '" + name + "'");
        return physicalSlots[writeCount % bufferCount];
    }

    /**
     * Returns the physical slot to read from (the most recently written slot).
     * On frame 0 before any write, this returns the slot that should hold initial state.
     */
    public GraphResource previousReadSlot() {
        if (physicalSlots == null) throw new IllegalStateException("Physical slots not allocated for '" + name + "'");
        return physicalSlots[(writeCount - 1 + bufferCount) % bufferCount];
    }

    /**
     * Returns the physical slot for a specific offset from the current write position.
     *
     * @param framesBack 1 = previous write, 2 = two writes ago, etc.
     */
    public GraphResource slotAtOffset(int framesBack) {
        if (framesBack < 1 || framesBack >= bufferCount) {
            throw new IllegalArgumentException("framesBack=" + framesBack + " out of range [1.." + (bufferCount - 1) + "]");
        }
        return physicalSlots[(writeCount - framesBack + bufferCount) % bufferCount];
    }

    /**
     * Called by the executor when the temporal resource is actually written this submission.
     * Advances the flip counter and resets staleness.
     */
    public void onWriteExecuted() {
        writeCount++;
        submissionsSinceWrite = 0;
    }

    /** Called each submission to track staleness (even if not written) */
    public void advanceSubmission() {
        submissionsSinceWrite++;
    }

    /** Resets the write count (used during resize to invalidate history) */
    public void resetWriteCount() {
        writeCount = 0;
        submissionsSinceWrite = 0;
    }

    /** @return submissions since last write (0 = written this frame) */
    public int staleness() { return submissionsSinceWrite; }

    /**
     * Sets the physical resource slots. Called during graph compilation/allocation.
     */
    public void setPhysicalSlots(GraphResource[] slots) {
        if (slots.length != bufferCount) {
            throw new IllegalArgumentException("Expected " + bufferCount + " slots, got " + slots.length);
        }
        this.physicalSlots = slots;
    }

    /** @return the physical slots, or null if not yet allocated */
    public GraphResource[] physicalSlots() { return physicalSlots; }

    /** Sets the graph-managed descriptor binding for read operations */
    public void setReadDescriptorBinding(TemporalDescriptorBinding binding) {
        this.readDescriptorBinding = binding;
    }

    /** Sets the graph-managed descriptor binding for write operations */
    public void setWriteDescriptorBinding(TemporalDescriptorBinding binding) {
        this.writeDescriptorBinding = binding;
    }

    /** @return the read descriptor binding, or null if not configured */
    public TemporalDescriptorBinding readDescriptorBinding() { return readDescriptorBinding; }

    /** @return the write descriptor binding, or null if not configured */
    public TemporalDescriptorBinding writeDescriptorBinding() { return writeDescriptorBinding; }

    /** Sets the graph-managed paired descriptor binding (read+write in same set) */
    public void setPairedDescriptorBinding(TemporalDescriptorBinding binding) {
        this.pairedDescriptorBinding = binding;
    }

    /** @return the paired descriptor binding, or null if not configured */
    public TemporalDescriptorBinding pairedDescriptorBinding() { return pairedDescriptorBinding; }

    /** Records a usage at the given pass order index (for aliasing lifetime tracking) */
    public void recordUse(int passOrder) {
        if (firstUseOrder < 0 || passOrder < firstUseOrder) firstUseOrder = passOrder;
        if (passOrder > lastUseOrder) lastUseOrder = passOrder;
    }

    /** Resets per-submission lifetime tracking */
    public void resetSubmissionLifetime() {
        firstUseOrder = -1;
        lastUseOrder = -1;
    }

    /** @return first pass order index that uses this resource this submission */
    public int firstUseOrder() { return firstUseOrder; }

    /** @return last pass order index that uses this resource this submission */
    public int lastUseOrder() { return lastUseOrder; }

    public static class Builder {
        private String name;
        private ResourceDescriptor descriptor;
        private int bufferCount = 2;
        private InitialState initialState;
        private int descriptorBinding = -1;
        private VkDescriptorSetLayout descriptorLayout;
        private int pairedWriteBinding = -1;
        private VkDescriptorSetLayout pairedLayout;
        private TemporalResizeStrategy resizeStrategy;

        private Builder() {}

        /** Sets the resource name */
        public Builder name(String name) { this.name = name; return this; }

        /** Sets the resource descriptor */
        public Builder descriptor(ResourceDescriptor descriptor) { this.descriptor = descriptor; return this; }

        /** Sets the number of physical buffer copies (default: 2 for double-buffering) */
        public Builder bufferCount(int count) {
            if (count < 2) throw new IllegalArgumentException("bufferCount must be >= 2");
            this.bufferCount = count;
            return this;
        }

        /** Sets the initial state for first-frame reads */
        public Builder initialState(InitialState state) { this.initialState = state; return this; }

        /**
         * Enables graph-managed descriptor sets for this temporal resource.
         * The graph will allocate descriptor sets bound to each physical slot and provide
         * them via ctx.temporalReadDescriptorSet() / ctx.temporalWriteDescriptorSet().
         *
         * @param binding the binding index within the descriptor set layout
         */
        public Builder descriptorBinding(int binding) {
            this.descriptorBinding = binding;
            return this;
        }

        /**
         * Sets the descriptor set layout for graph-managed descriptor sets.
         * Must be provided alongside {@link #descriptorBinding(int)} for auto-creation to work.
         * The layout must have a storage buffer binding at the index specified by descriptorBinding.
         *
         * @param layout the descriptor set layout (not owned by the temporal resource)
         */
        public Builder descriptorLayout(VkDescriptorSetLayout layout) {
            this.descriptorLayout = layout;
            return this;
        }

        /**
         * Configures paired (read+write) descriptor sets for compute shaders that need both
         * the read slot and write slot in the same descriptor set.
         *
         * The graph creates N sets (one per flip state), each with:
         * - readBinding bound to the previous frame's output
         * - writeBinding bound to the current frame's target
         *
         * Use ctx.temporalReadDescriptorSet() to get the correctly-paired set.
         *
         * @param readBinding binding index for the read buffer
         * @param writeBinding binding index for the write buffer
         * @param layout the descriptor set layout with both bindings
         */
        public Builder pairedDescriptor(int readBinding, int writeBinding, VkDescriptorSetLayout layout) {
            this.descriptorBinding = readBinding;
            this.pairedWriteBinding = writeBinding;
            this.pairedLayout = layout;
            return this;
        }

        /** @return the configured descriptor binding index, or -1 if not configured */
        public int getDescriptorBinding() { return descriptorBinding; }

        /** Sets the resize strategy for this temporal resource (default: clear) */
        public Builder resizeStrategy(TemporalResizeStrategy strategy) {
            this.resizeStrategy = strategy;
            return this;
        }

        public TemporalResource build() {
            if (name == null) throw new IllegalStateException("name not set");
            if (descriptor == null) throw new IllegalStateException("descriptor not set");
            return new TemporalResource(this);
        }
    }
}
