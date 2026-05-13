package io.github.yetyman.vulkan.graph.scheduling;

import io.github.yetyman.vulkan.graph.RenderGraphException;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Shared topological sort utility using Kahn's algorithm.
 * Ordering is derived from resource edges: if node A writes resource R and node B reads
 * resource R, then A must come before B. Tie-breaking is controlled by a priority function.
 */
public final class TopologicalSort {

    private TopologicalSort() {}

    /**
     * Performs topological sort with the given priority function for tie-breaking.
     *
     * @param nodes the nodes to sort
     * @param priorityFunc assigns a priority to each node; higher priority is scheduled first
     * @return nodes in topological order
     * @throws RenderGraphException if a cycle is detected
     */
    public static List<RenderNode> sort(List<RenderNode> nodes, ToIntFunction<RenderNode> priorityFunc) {
        Map<RenderNode, Set<RenderNode>> successors = new HashMap<>(nodes.size());
        Map<RenderNode, Integer> inDegree = new HashMap<>(nodes.size());

        for (RenderNode node : nodes) {
            successors.put(node, new HashSet<>());
            inDegree.put(node, 0);
        }

        Map<GraphResource, List<RenderNode>> resourceWriters = new HashMap<>();
        for (RenderNode node : nodes) {
            for (ResourceEdge edge : node.writes()) {
                resourceWriters.computeIfAbsent(edge.resource(), k -> new ArrayList<>()).add(node);
            }
        }

        for (RenderNode reader : nodes) {
            for (ResourceEdge readEdge : reader.reads()) {
                List<RenderNode> writers = resourceWriters.get(readEdge.resource());
                if (writers != null) {
                    for (RenderNode writer : writers) {
                        if (writer != reader && successors.get(writer).add(reader)) {
                            inDegree.merge(reader, 1, Integer::sum);
                        }
                    }
                }
            }
        }

        List<RenderNode> ready = new ArrayList<>();
        for (RenderNode node : nodes) {
            if (inDegree.get(node) == 0) {
                ready.add(node);
            }
        }

        List<RenderNode> sorted = new ArrayList<>(nodes.size());
        while (!ready.isEmpty()) {
            int bestIdx = selectBest(ready, priorityFunc);
            RenderNode best = ready.remove(bestIdx);
            sorted.add(best);

            for (RenderNode succ : successors.get(best)) {
                int newDegree = inDegree.get(succ) - 1;
                inDegree.put(succ, newDegree);
                if (newDegree == 0) {
                    ready.add(succ);
                }
            }
        }

        if (sorted.size() != nodes.size()) {
            throw new RenderGraphException(
                "Cycle detected in render graph: " + sorted.size() + " of " + nodes.size() + " nodes sorted");
        }

        return sorted;
    }

    /**
     * Builds a successor map (writer -> readers) from resource edges.
     * Useful for critical path computation and other graph analysis.
     */
    public static Map<RenderNode, Set<RenderNode>> buildSuccessorMap(List<RenderNode> nodes) {
        Map<RenderNode, Set<RenderNode>> successors = new HashMap<>(nodes.size());
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
            for (ResourceEdge readEdge : reader.reads()) {
                List<RenderNode> writers = resourceWriters.get(readEdge.resource());
                if (writers != null) {
                    for (RenderNode writer : writers) {
                        if (writer != reader) {
                            successors.get(writer).add(reader);
                        }
                    }
                }
            }
        }

        return successors;
    }

    private static int selectBest(List<RenderNode> ready, ToIntFunction<RenderNode> priorityFunc) {
        int bestIdx = 0;
        int bestPriority = priorityFunc.applyAsInt(ready.get(0));
        for (int i = 1; i < ready.size(); i++) {
            int p = priorityFunc.applyAsInt(ready.get(i));
            if (p > bestPriority) {
                bestPriority = p;
                bestIdx = i;
            }
        }
        return bestIdx;
    }
}
