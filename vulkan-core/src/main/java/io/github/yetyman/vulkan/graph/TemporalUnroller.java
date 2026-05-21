package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.edges.TemporalEdge;
import io.github.yetyman.vulkan.graph.nodes.NodeType;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.TemporalResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles temporal cycle detection, starting point validation, and per-submission
 * physical slot binding for TemporalResources.
 *
 * This is a compilation stage that runs after basic validation but before scheduling.
 * It validates that:
 * - Every temporal resource that is read is also written somewhere in the graph
 * - No non-temporal cycles exist (single-frame circular dependency = error)
 * - All temporal reads reachable from terminal outputs on frame 0 have initial state defined
 *
 * It also provides the unrolling logic that binds temporal reads/writes to physical slots
 * based on the current submission counter.
 */
public class TemporalUnroller {

    /**
     * Validates temporal edges in the graph. Called during compilation.
     *
     * @param nodes all nodes in the graph
     * @param temporalResources all declared temporal resources
     * @throws RenderGraphException if validation fails
     */
    public void validate(List<RenderNode> nodes, List<TemporalResource> temporalResources) {
        validateTemporalCompleteness(nodes, temporalResources);
        validateNoNonTemporalCycles(nodes);
    }

    /**
     * Validates starting point soundness: every temporal read reachable from a terminal output
     * on the first submission(s) must have an initial state defined.
     *
     * @param nodes all nodes in the graph
     * @param temporalResources all declared temporal resources
     * @throws RenderGraphException with comprehensive diagnostic if initial states are missing
     */
    public void validateStartingPoints(List<RenderNode> nodes, List<TemporalResource> temporalResources) {
        // Determine how many frames need validation based on max buffer count
        int maxBufferCount = temporalResources.stream()
            .mapToInt(TemporalResource::bufferCount)
            .max().orElse(2);
        int framesToValidate = maxBufferCount - 1;

        List<MissingInitialState> allMissing = new ArrayList<>();

        for (int frame = 0; frame < framesToValidate; frame++) {
            Set<TemporalResource> required = resolveRequiredInitials(nodes, frame);
            for (TemporalResource tr : required) {
                if (!tr.hasInitialState()) {
                    List<String> chain = findDependencyChain(nodes, tr);
                    allMissing.add(new MissingInitialState(tr, frame, chain));
                }
            }
        }

        if (!allMissing.isEmpty()) {
            throw new RenderGraphException(formatMissingInitialStateError(allMissing));
        }
    }

    /**
     * Resolves which temporal resources require initial state for a given submission index.
     * Uses backward reachability from terminal outputs through active passes.
     */
    public Set<TemporalResource> resolveRequiredInitials(List<RenderNode> nodes, int submissionIndex) {
        // Find terminal outputs (sinks)
        Set<RenderNode> sinks = new LinkedHashSet<>();
        Map<GraphResource, List<RenderNode>> producers = new HashMap<>();
        for (RenderNode node : nodes) {
            if (!node.isActive()) continue;
            for (ResourceEdge edge : node.writes()) {
                producers.computeIfAbsent(edge.resource(), k -> new ArrayList<>()).add(node);
            }
        }
        for (RenderNode node : nodes) {
            if (!node.isActive()) continue;
            if (isSink(node)) {
                sinks.add(node);
            }
        }

        // Backward reachability from sinks
        Set<RenderNode> reachable = new LinkedHashSet<>();
        List<RenderNode> worklist = new ArrayList<>(sinks);
        reachable.addAll(sinks);

        Set<TemporalResource> requiredTemporals = new LinkedHashSet<>();

        while (!worklist.isEmpty()) {
            RenderNode current = worklist.remove(worklist.size() - 1);

            // Check temporal reads on this node - these are back-edges
            for (TemporalEdge te : current.temporalEdges()) {
                if (te.isReadPrevious()) {
                    // This temporal resource is needed on this submission
                    // For submission 0, there's no previous write at all
                    // For submission N < bufferCount-1, some slots may not have been written yet
                    if (submissionIndex < te.temporalResource().bufferCount() - 1) {
                        requiredTemporals.add(te.temporalResource());
                    }
                }
            }

            // Traverse normal (non-temporal) read edges backwards
            for (ResourceEdge readEdge : current.reads()) {
                List<RenderNode> writers = producers.get(readEdge.resource());
                if (writers != null) {
                    for (RenderNode writer : writers) {
                        if (writer.isActive() && reachable.add(writer)) {
                            worklist.add(writer);
                        }
                    }
                }
            }
        }

        return requiredTemporals;
    }

    /**
     * Validates that every temporal resource that is read is also written somewhere in the graph.
     */
    private void validateTemporalCompleteness(List<RenderNode> nodes, List<TemporalResource> temporalResources) {
        Set<TemporalResource> written = new HashSet<>();
        Set<TemporalResource> read = new HashSet<>();

        for (RenderNode node : nodes) {
            for (TemporalEdge te : node.temporalEdges()) {
                if (te.isWriteCurrent()) {
                    written.add(te.temporalResource());
                } else if (te.isReadPrevious()) {
                    read.add(te.temporalResource());
                }
            }
        }

        for (TemporalResource tr : read) {
            if (!written.contains(tr)) {
                throw new RenderGraphException(
                    "Temporal resource '" + tr.name() + "' is read (readsTemporalPrevious) " +
                    "but never written (writesTemporalCurrent) in the graph. " +
                    "Every temporal resource that is read must also be written somewhere.");
            }
        }
    }

    /**
     * Validates that no non-temporal cycles exist in the graph.
     * Temporal edges (back-edges) are excluded from cycle detection since they represent
     * cross-frame dependencies, not single-frame circular dependencies.
     */
    private void validateNoNonTemporalCycles(List<RenderNode> nodes) {
        // Build adjacency from resource edges only (not temporal)
        Map<RenderNode, Set<RenderNode>> adj = new HashMap<>();
        Map<GraphResource, List<RenderNode>> producers = new HashMap<>();

        for (RenderNode node : nodes) {
            adj.put(node, new HashSet<>());
            for (ResourceEdge edge : node.writes()) {
                producers.computeIfAbsent(edge.resource(), k -> new ArrayList<>()).add(node);
            }
        }

        for (RenderNode node : nodes) {
            for (ResourceEdge edge : node.reads()) {
                List<RenderNode> writers = producers.get(edge.resource());
                if (writers != null) {
                    for (RenderNode writer : writers) {
                        if (writer != node) {
                            adj.get(writer).add(node); // writer -> reader edge
                        }
                    }
                }
            }
        }

        // DFS cycle detection
        Set<RenderNode> visited = new HashSet<>();
        Set<RenderNode> inStack = new HashSet<>();

        for (RenderNode node : nodes) {
            if (!visited.contains(node)) {
                if (hasCycleDFS(node, adj, visited, inStack)) {
                    throw new RenderGraphException(
                        "Non-temporal cycle detected in the graph. " +
                        "Circular dependencies within a single submission are not allowed. " +
                        "Use temporal edges (readsTemporalPrevious/writesTemporalCurrent) for cross-frame cycles.");
                }
            }
        }
    }

    private boolean hasCycleDFS(RenderNode node, Map<RenderNode, Set<RenderNode>> adj,
                                Set<RenderNode> visited, Set<RenderNode> inStack) {
        visited.add(node);
        inStack.add(node);

        Set<RenderNode> neighbors = adj.get(node);
        if (neighbors != null) {
            for (RenderNode neighbor : neighbors) {
                if (inStack.contains(neighbor)) return true;
                if (!visited.contains(neighbor) && hasCycleDFS(neighbor, adj, visited, inStack)) return true;
            }
        }

        inStack.remove(node);
        return false;
    }

    private boolean isSink(RenderNode node) {
        if (node.type() == NodeType.PRESENT) return true;
        for (ResourceEdge edge : node.writes()) {
            if (!edge.resource().isTransient()) return true;
        }
        // Also consider temporal writes as sinks (they persist across frames)
        for (TemporalEdge te : node.temporalEdges()) {
            if (te.isWriteCurrent()) return true;
        }
        return false;
    }

    /**
     * Finds the dependency chain from a temporal resource back to a terminal output.
     */
    private List<String> findDependencyChain(List<RenderNode> nodes, TemporalResource target) {
        // Find the node that reads this temporal resource
        for (RenderNode node : nodes) {
            for (TemporalEdge te : node.temporalEdges()) {
                if (te.isReadPrevious() && te.temporalResource() == target) {
                    List<String> chain = new ArrayList<>();
                    chain.add("[" + target.name() + " MISSING]");
                    chain.add(node.name());
                    return chain;
                }
            }
        }
        return List.of("[" + target.name() + " MISSING]");
    }

    private String formatMissingInitialStateError(List<MissingInitialState> missing) {
        StringBuilder sb = new StringBuilder();
        sb.append("FrameGraph compilation failed: missing initial state for temporal resources.\n\n");
        sb.append("The following temporal resources are read on early submissions but have no initialState defined:\n\n");

        for (int i = 0; i < missing.size(); i++) {
            MissingInitialState m = missing.get(i);
            sb.append("  ").append(i + 1).append(". \"").append(m.resource.name()).append("\"");
            sb.append(" (bufferCount=").append(m.resource.bufferCount()).append(")\n");
            sb.append("     - Required on frame ").append(m.frame).append("\n");
            if (!m.dependencyChain.isEmpty()) {
                sb.append("     - Dependency chain: ").append(String.join(" <- ", m.dependencyChain)).append("\n");
            }
            sb.append("     - Suggested fix: .initialState(InitialState.Clear.BLACK)\n\n");
        }

        return sb.toString();
    }

    private record MissingInitialState(TemporalResource resource, int frame, List<String> dependencyChain) {}
}
