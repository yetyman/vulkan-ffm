package io.github.yetyman.vulkan.sample.spatial;

import io.github.yetyman.vulkan.foundation.ecs.*;

/**
 * Demonstrates hierarchical transform propagation through the ECS node tree.
 *
 * This example shows:
 * - Parent/child transform composition (world = parent.world * local)
 * - Dirty flag optimization (only recomputes when needed)
 * - Reparenting automatically re-resolves parent transforms via NEAREST_ANCESTOR DI
 * - How TraversalView can be used to iterate all transforms for bulk GPU upload
 */
public class TransformTreeExample {

    public static void main(String[] args) {
        try (Tree tree = new Tree()) {
            // Build a simple scene hierarchy:
            // root
            //   └── armature (translated to 0, 5, 0)
            //        ├── shoulder (rotated)
            //        │    └── elbow (translated along arm)
            //        └── hip (translated down)

            Node root = tree.root();
            root.addComponent(new TransformComponent());

            Node armature = root.createChild();
            TransformComponent armatureTransform = armature.addComponent(new TransformComponent());
            armatureTransform.setPosition(0, 5, 0);

            Node shoulder = armature.createChild();
            TransformComponent shoulderTransform = shoulder.addComponent(new TransformComponent());
            shoulderTransform.setRotationAxisAngle(0, 0, 1, (float) (Math.PI / 4)); // 45 degrees

            Node elbow = shoulder.createChild();
            TransformComponent elbowTransform = elbow.addComponent(new TransformComponent());
            elbowTransform.setPosition(3, 0, 0); // 3 units along the arm

            Node hip = armature.createChild();
            TransformComponent hipTransform = hip.addComponent(new TransformComponent());
            hipTransform.setPosition(0, -2, 0);

            // Initialize the tree - runs DI resolution which connects parent transforms
            tree.initialize();

            // --- Demonstrate transform propagation ---

            System.out.println("=== Transform Propagation Demo ===");
            System.out.println();

            // Elbow's world position should be: armature(0,5,0) + shoulder(rotated) + elbow(3,0,0)
            float[] elbowWorld = elbowTransform.worldMatrix();
            System.out.printf("Elbow world position: (%.2f, %.2f, %.2f)%n",
                    elbowWorld[12], elbowWorld[13], elbowWorld[14]);

            // Hip's world position: armature(0,5,0) + hip(0,-2,0) = (0,3,0)
            float[] hipWorld = hipTransform.worldMatrix();
            System.out.printf("Hip world position: (%.2f, %.2f, %.2f)%n",
                    hipWorld[12], hipWorld[13], hipWorld[14]);

            // --- Demonstrate dirty propagation ---

            System.out.println();
            System.out.println("=== Moving armature up by 10 ===");
            armatureTransform.translate(0, 10, 0);

            // Elbow's world should automatically pick up the change (lazy recompute)
            elbowWorld = elbowTransform.worldMatrix();
            System.out.printf("Elbow world position after armature move: (%.2f, %.2f, %.2f)%n",
                    elbowWorld[12], elbowWorld[13], elbowWorld[14]);

            hipWorld = hipTransform.worldMatrix();
            System.out.printf("Hip world position after armature move: (%.2f, %.2f, %.2f)%n",
                    hipWorld[12], hipWorld[13], hipWorld[14]);

            // --- Demonstrate traversal view for bulk access ---

            System.out.println();
            System.out.println("=== All transforms via traversal view ===");

            TraversalView<TransformComponent> transformView =
                    tree.getOrCreateComponentTraversal(TransformComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER);

            System.out.println("Total transforms in tree: " + transformView.liveCount());
            transformView.forEach((node, transform) -> {
                float[] world = transform.worldMatrix();
                System.out.printf("  Node(depth=%d): world pos=(%.2f, %.2f, %.2f)%n",
                        node.depth(), world[12], world[13], world[14]);
            });

            // --- Demonstrate reparenting ---

            System.out.println();
            System.out.println("=== Reparenting elbow under hip ===");
            elbow.setParent(hip);

            // After reparent, elbow's parent transform is now hip's
            elbowWorld = elbowTransform.worldMatrix();
            System.out.printf("Elbow world position after reparent: (%.2f, %.2f, %.2f)%n",
                    elbowWorld[12], elbowWorld[13], elbowWorld[14]);
        }
    }
}
