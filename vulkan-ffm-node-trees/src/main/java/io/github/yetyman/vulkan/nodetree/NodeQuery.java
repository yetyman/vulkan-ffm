package io.github.yetyman.vulkan.nodetree;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Utility for querying nodes in the tree by component composition.
 *
 * Processing systems often need to find nodes matching specific component combinations.
 * This utility provides a fluent API for building and executing such queries.
 *
 * Example:
 * <pre>
 *   List&lt;Node&gt; renderables = NodeQuery.from(tree)
 *       .withComponent(MeshComponent.class)
 *       .withComponent(MaterialComponent.class)
 *       .execute();
 * </pre>
 */
public class NodeQuery {

    private final Node root;
    private final List<Class<? extends Component>> requiredComponents = new ArrayList<>();
    private final List<Class<? extends Component>> excludedComponents = new ArrayList<>();
    private Predicate<Node> filter;

    private NodeQuery(Node root) {
        this.root = root;
    }

    /** Creates a query starting from the tree's root. */
    public static NodeQuery from(Tree tree) {
        return new NodeQuery(tree.root());
    }

    /** Creates a query starting from a specific subtree root. */
    public static NodeQuery from(Node subtreeRoot) {
        return new NodeQuery(subtreeRoot);
    }

    /** Requires matching nodes to have a component of this type. */
    public NodeQuery withComponent(Class<? extends Component> type) {
        requiredComponents.add(type);
        return this;
    }

    /** Excludes nodes that have a component of this type. */
    public NodeQuery withoutComponent(Class<? extends Component> type) {
        excludedComponents.add(type);
        return this;
    }

    /** Adds a custom predicate filter. */
    public NodeQuery where(Predicate<Node> predicate) {
        this.filter = (this.filter == null) ? predicate : this.filter.and(predicate);
        return this;
    }

    /** Executes the query, returning all matching nodes. */
    public List<Node> execute() {
        List<Node> results = new ArrayList<>();
        root.traverseDepthFirst(node -> {
            if (matches(node)) {
                results.add(node);
            }
        });
        return results;
    }

    /** Executes the query, returning the first matching node or null. */
    public Node first() {
        // Can't short-circuit traverseDepthFirst, so use manual traversal
        return findFirst(root);
    }

    /** @return the count of matching nodes. */
    public int count() {
        int[] count = {0};
        root.traverseDepthFirst(node -> {
            if (matches(node)) count[0]++;
        });
        return count[0];
    }

    /** @return true if any node matches. */
    public boolean exists() {
        return first() != null;
    }

    private boolean matches(Node node) {
        for (Class<? extends Component> required : requiredComponents) {
            if (node.findComponent(required) == null) return false;
        }
        for (Class<? extends Component> excluded : excludedComponents) {
            if (node.findComponent(excluded) != null) return false;
        }
        if (filter != null && !filter.test(node)) return false;
        return true;
    }

    private Node findFirst(Node node) {
        if (matches(node)) return node;
        for (Node child : node.children()) {
            Node found = findFirst(child);
            if (found != null) return found;
        }
        return null;
    }
}
