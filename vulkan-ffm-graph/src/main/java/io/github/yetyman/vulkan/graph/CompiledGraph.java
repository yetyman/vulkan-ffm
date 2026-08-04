package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.memory.ResourceAlias;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.ResourceLifetime;
import io.github.yetyman.vulkan.graph.scheduling.ExecutionBucket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The output of render graph compilation. Contains the ordered execution plan,
 * active nodes, computed resource lifetimes, and aliasing groups.
 */
public class CompiledGraph {

    private final List<RenderNode> activeNodes;
    private final List<ExecutionBucket> executionBuckets;
    private final Map<GraphResource, ResourceLifetime> lifetimes;
    private final List<ResourceAlias> aliasingGroups;

    CompiledGraph(List<RenderNode> activeNodes,
                  List<ExecutionBucket> executionBuckets,
                  Map<GraphResource, ResourceLifetime> lifetimes,
                  List<ResourceAlias> aliasingGroups) {
        this.activeNodes = Collections.unmodifiableList(activeNodes);
        this.executionBuckets = Collections.unmodifiableList(executionBuckets);
        this.lifetimes = Collections.unmodifiableMap(lifetimes);
        this.aliasingGroups = aliasingGroups != null ? Collections.unmodifiableList(aliasingGroups) : Collections.emptyList();
    }

    /** @return nodes that survived culling, in execution order */
    public List<RenderNode> activeNodes() { return activeNodes; }

    /** @return ordered execution buckets (groups of parallel nodes) */
    public List<ExecutionBucket> executionBuckets() { return executionBuckets; }

    /** @return computed resource lifetimes */
    public Map<GraphResource, ResourceLifetime> lifetimes() { return lifetimes; }

    /** @return aliasing groups for transient resources with non-overlapping lifetimes */
    public List<ResourceAlias> aliasingGroups() { return aliasingGroups; }

    // --- Introspection API ---

    /** Pass info for introspection */
    public record PassInfo(String name, String type, int bucketIndex, String queue, List<String> reads, List<String> writes) {}

    /** Resource info for introspection */
    public record ResourceInfo(String name, boolean isTransient, boolean isImported, int firstUse, int lastUse) {}

    /** Edge info for introspection */
    public record EdgeInfo(String from, String to, String resource, boolean crossQueue) {}

    /** @return ordered pass information for visualization/debugging */
    public List<PassInfo> passes() {
        List<PassInfo> result = new ArrayList<>();
        for (int b = 0; b < executionBuckets.size(); b++) {
            ExecutionBucket bucket = executionBuckets.get(b);
            String queueName = bucket.queue().capability().name();
            for (RenderNode node : bucket.nodes()) {
                List<String> reads = node.reads().stream().map(e -> e.resource().name()).toList();
                List<String> writes = node.writes().stream().map(e -> e.resource().name()).toList();
                result.add(new PassInfo(node.name(), node.type().name(), b, queueName, reads, writes));
            }
        }
        return result;
    }

    /** @return resource information with lifetime data */
    public List<ResourceInfo> resources() {
        List<ResourceInfo> result = new ArrayList<>();
        for (var entry : lifetimes.entrySet()) {
            GraphResource res = entry.getKey();
            ResourceLifetime lt = entry.getValue();
            result.add(new ResourceInfo(res.name(), res.isTransient(), res.isImported(),
                lt.firstWritePass(), lt.lastReadPass()));
        }
        return result;
    }

    /** @return edge information derived from resource dependencies */
    public List<EdgeInfo> edges() {
        List<EdgeInfo> result = new ArrayList<>();
        // Build writer map
        java.util.Map<GraphResource, String> writerMap = new java.util.HashMap<>();
        for (RenderNode node : activeNodes) {
            for (ResourceEdge edge : node.writes()) {
                writerMap.put(edge.resource(), node.name());
            }
        }
        // Find reader->writer edges
        for (RenderNode node : activeNodes) {
            for (ResourceEdge edge : node.reads()) {
                String writer = writerMap.get(edge.resource());
                if (writer != null) {
                    result.add(new EdgeInfo(writer, node.name(), edge.resource().name(), false));
                }
            }
        }
        return result;
    }

    /** @return DOT format graph for visualization tools (Graphviz) */
    public String exportDot() {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph RenderGraph {\n");
        sb.append("  rankdir=LR;\n");
        for (RenderNode node : activeNodes) {
            sb.append("  \"").append(node.name()).append("\" [shape=box];\n");
        }
        for (EdgeInfo edge : edges()) {
            sb.append("  \"").append(edge.from()).append("\" -> \"").append(edge.to())
              .append("\" [label=\"").append(edge.resource()).append("\"];\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /** @return JSON format graph for programmatic consumption */
    public String exportJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"passes\":[");
        List<PassInfo> pi = passes();
        for (int i = 0; i < pi.size(); i++) {
            if (i > 0) sb.append(",");
            PassInfo p = pi.get(i);
            sb.append("{\"name\":\"").append(p.name()).append("\",\"type\":\"").append(p.type())
              .append("\",\"bucket\":").append(p.bucketIndex())
              .append(",\"queue\":\"").append(p.queue()).append("\"}");
        }
        sb.append("],\"edges\":[");
        List<EdgeInfo> ei = edges();
        for (int i = 0; i < ei.size(); i++) {
            if (i > 0) sb.append(",");
            EdgeInfo e = ei.get(i);
            sb.append("{\"from\":\"").append(e.from()).append("\",\"to\":\"").append(e.to())
              .append("\",\"resource\":\"").append(e.resource()).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }
}
