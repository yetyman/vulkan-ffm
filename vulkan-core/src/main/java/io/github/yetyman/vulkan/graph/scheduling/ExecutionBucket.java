package io.github.yetyman.vulkan.graph.scheduling;

import io.github.yetyman.vulkan.graph.nodes.RenderNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A group of nodes that can execute in parallel on the same queue.
 * Nodes within a bucket have no data dependencies between them.
 */
public class ExecutionBucket {

    private final QueueAssignment queue;
    private final List<RenderNode> nodes;

    public ExecutionBucket(QueueAssignment queue, List<RenderNode> nodes) {
        this.queue = queue;
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
    }

    /** @return the queue this bucket executes on */
    public QueueAssignment queue() { return queue; }

    /** @return the nodes in this bucket */
    public List<RenderNode> nodes() { return nodes; }
}
