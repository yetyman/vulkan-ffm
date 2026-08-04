package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.scheduling.ExecutionBucket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prints an ASCII visualization of a compiled render graph DAG to the console.
 */
public class RenderGraphVisualizer {

    /**
     * Prints the compiled graph to stdout.
     */
    public static void print(CompiledGraph compiled) {
        System.out.println(visualize(compiled));
    }

    /**
     * Returns an ASCII string representation of the compiled graph DAG.
     */
    public static String visualize(CompiledGraph compiled) {
        List<RenderNode> nodes = compiled.activeNodes();
        List<ExecutionBucket> buckets = compiled.executionBuckets();

        // Build adjacency: writer -> readers (via shared resources)
        Map<RenderNode, Set<RenderNode>> successors = new LinkedHashMap<>();
        for (RenderNode node : nodes) {
            successors.put(node, new HashSet<>());
        }

        Map<GraphResource, List<RenderNode>> resourceWriters = new HashMap<>();
        for (RenderNode node : nodes) {
            for (ResourceEdge edge : node.writes()) {
                resourceWriters.computeIfAbsent(edge.resource(), k -> new ArrayList<>()).add(node);
            }
        }
        for (RenderNode reader : nodes) {
            for (ResourceEdge edge : reader.reads()) {
                List<RenderNode> writers = resourceWriters.get(edge.resource());
                if (writers != null) {
                    for (RenderNode writer : writers) {
                        if (writer != reader) {
                            successors.get(writer).add(reader);
                        }
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Render Graph ===\n\n");

        // Print buckets with nodes
        for (int b = 0; b < buckets.size(); b++) {
            ExecutionBucket bucket = buckets.get(b);
            sb.append(String.format("Bucket %d [%s, queue family %d]\n",
                b, bucket.queue().capability(), bucket.queue().queueFamilyIndex()));

            for (RenderNode node : bucket.nodes()) {
                sb.append(String.format("  [%s] %s\n", node.type(), node.name()));

                // Show reads
                for (ResourceEdge edge : node.reads()) {
                    sb.append(String.format("    <- %s (access=0x%X, stage=0x%X)\n",
                        edge.resource().name(), edge.accessMask(), edge.stageMask()));
                }
                // Show writes
                for (ResourceEdge edge : node.writes()) {
                    sb.append(String.format("    -> %s (access=0x%X, stage=0x%X)\n",
                        edge.resource().name(), edge.accessMask(), edge.stageMask()));
                }
            }

            // Draw edges to next bucket
            if (b < buckets.size() - 1) {
                Set<String> edgeLabels = new HashSet<>();
                for (RenderNode node : bucket.nodes()) {
                    for (RenderNode succ : successors.getOrDefault(node, Set.of())) {
                        // Check if successor is in a later bucket
                        if (!bucket.nodes().contains(succ)) {
                            edgeLabels.add(node.name() + " --> " + succ.name());
                        }
                    }
                }
                if (!edgeLabels.isEmpty()) {
                    sb.append("  |\n");
                    for (String label : edgeLabels) {
                        sb.append("  |  ").append(label).append("\n");
                    }
                    sb.append("  v\n");
                } else {
                    sb.append("  |\n  v\n");
                }
            }
        }

        // Summary
        sb.append("\n--- Summary ---\n");
        sb.append(String.format("Nodes: %d active, %d buckets\n", nodes.size(), buckets.size()));

        // Resource lifetimes
        sb.append("Resources:\n");
        Set<GraphResource> seen = new HashSet<>();
        for (RenderNode node : nodes) {
            for (ResourceEdge edge : node.writes()) {
                if (seen.add(edge.resource())) {
                    GraphResource r = edge.resource();
                    String kind = r.isTransient() ? "transient" : r.isImported() ? "imported" : "persistent";
                    sb.append(String.format("  %s [%s] lifetime=[%d..%d]\n",
                        r.name(), kind,
                        r.lifetime().firstWritePass(), r.lifetime().lastReadPass()));
                }
            }
            for (ResourceEdge edge : node.reads()) {
                if (seen.add(edge.resource())) {
                    GraphResource r = edge.resource();
                    String kind = r.isTransient() ? "transient" : r.isImported() ? "imported" : "persistent";
                    sb.append(String.format("  %s [%s] lifetime=[%d..%d]\n",
                        r.name(), kind,
                        r.lifetime().firstWritePass(), r.lifetime().lastReadPass()));
                }
            }
        }

        return sb.toString();
    }
}
