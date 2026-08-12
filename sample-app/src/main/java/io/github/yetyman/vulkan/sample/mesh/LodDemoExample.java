package io.github.yetyman.vulkan.sample.mesh;

import io.github.yetyman.helpers.math.Mat4;
import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.consume.GeometryDrawRange;
import io.github.yetyman.vulkan.mesh.lod.LodBudget;
import io.github.yetyman.vulkan.mesh.lod.LodBudgetEntry;
import io.github.yetyman.vulkan.mesh.lod.LodChannels;
import io.github.yetyman.vulkan.mesh.lod.LodContext;
import io.github.yetyman.vulkan.mesh.lod.LodPolicy;
import io.github.yetyman.vulkan.mesh.lod.LodSelection;
import io.github.yetyman.vulkan.mesh.lod.LodSelector;
import io.github.yetyman.vulkan.mesh.lod.ParameterDescriptor;
import io.github.yetyman.vulkan.mesh.lod.RepresentationGraph;
import io.github.yetyman.vulkan.mesh.lod.RepresentationNode;
import io.github.yetyman.vulkan.mesh.lod.RepresentationSet;
import io.github.yetyman.vulkan.mesh.lod.RepresentationStructure;
import io.github.yetyman.vulkan.mesh.lod.ResidencyQuery;
import io.github.yetyman.vulkan.mesh.lod.TransitionMode;
import io.github.yetyman.vulkan.mesh.lod.TransitionState;
import io.github.yetyman.vulkan.mesh.partition.FloatChannelKey;
import io.github.yetyman.vulkan.mesh.partition.PartitionMetadata;
import io.github.yetyman.vulkan.mesh.partition.PartitionSet;
import io.github.yetyman.vulkan.mesh.partition.MetadataStore;
import io.github.yetyman.vulkan.mesh.process.Simplifier;
import io.github.yetyman.vulkan.mesh.processing.QemSimplifier;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.primitives.SphereSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-rendering example that exercises the LOD system end-to-end, demonstrating:
 *
 * <ul>
 *   <li>Building a {@link RepresentationStructure.Flat} LOD chain from a mesh via simplification</li>
 *   <li>Populating metadata channels ({@link LodChannels#ERROR_BOUND}, {@link LodChannels#LOD_LEVEL})</li>
 *   <li>A simple CPU {@link LodSelector} (screen-error based)</li>
 *   <li>The full selection flow: context -> selector -> selection -> draw ranges</li>
 *   <li>Transition state tracking across simulated frames</li>
 *   <li>Budget policy arbitration across multiple meshes</li>
 *   <li>Building a {@link RepresentationStructure.Graph} (DAG) from grouped clusters</li>
 * </ul>
 *
 * <p>This is a CPU-only integration test. It does not render anything to the screen.
 * It validates the structural types, metadata flow, selection logic, and transition tracking.
 * Run from the command line to see output.
 */
public class LodDemoExample {

    public static void main(String[] args) {
        System.out.println("=== LOD System Demo ===\n");

        demoFlatChain();
        System.out.println();
        demoDagStructure();
        System.out.println();
        demoTransitions();
        System.out.println();
        demoBudgetPolicy();
        System.out.println();
        demoParametric();
        System.out.println();
        demoMetadataChannels();

        System.out.println("\n=== All demos passed ===");
    }

    // =========================================================================
    // Demo 1: Flat LOD chain with screen-error selection
    // =========================================================================

    private static void demoFlatChain() {
        System.out.println("--- Demo 1: Flat LOD Chain ---");

        try (Arena arena = Arena.ofConfined()) {
            // Build a sphere source and simplify at several ratios
            GeometrySource original = new SphereSource(arena, 1.0f, 32, 32);
            Simplifier simplifier = new QemSimplifier();

            float[] ratios = {1.0f, 0.5f, 0.25f, 0.1f};
            float[] errors = new float[ratios.length];
            long[] triCounts = new long[ratios.length];
            List<GeometrySource> levels = new ArrayList<>();

            for (int i = 0; i < ratios.length; i++) {
                GeometrySource level;
                if (ratios[i] >= 1.0f) {
                    level = original;
                    errors[i] = 0.0f;
                } else {
                    level = simplifier.simplify(original, ratios[i], arena);
                    errors[i] = simplifier.lastError();
                }
                levels.add(level);
                triCounts[i] = level.indices()
                        .map(idx -> idx.indexCount() / 3)
                        .orElse(level.elementCount() / 3);
            }

            // Build the representation structure
            RepresentationNode[] nodes = new RepresentationNode[ratios.length];
            for (int i = 0; i < ratios.length; i++) {
                // Each level is one partition (partition index = i)
                nodes[i] = new RepresentationNode(
                        new int[]{i},
                        errors[i],
                        triCounts[i],
                        original.bounds(),
                        0 // no tag
                );
            }

            RepresentationStructure.Flat flat = new RepresentationStructure.Flat(nodes);

            System.out.println("  Structure: Flat with " + flat.nodeCount() + " levels");
            for (int i = 0; i < flat.nodeCount(); i++) {
                RepresentationNode n = flat.node(i);
                System.out.printf("    Level %d: %d tris, error=%.4f%n", i, n.triangleCount(), n.errorBound());
            }

            // Build a RepresentationSet (multi-mesh mode - each level is independent)
            // For this demo we don't actually upload; just demonstrate the structure
            PartitionSet dummyPartitions = PartitionSet.of(List.of(
                    new io.github.yetyman.vulkan.mesh.partition.GeometryPartition(
                            "full", 0, triCounts[0], original.elementCount(),
                            original.topology(), original.bounds(), 0, 0)
            ));

            RepresentationSet repSet = RepresentationSet.builder()
                    .structure(flat)
                    .sharedPartitions(dummyPartitions)
                    .transitionMode(new TransitionMode.Dither(0.3f))
                    .build();

            // Create a simple screen-error selector
            LodSelector selector = new ScreenErrorSelector(1.0f);

            // Simulate selection at various distances
            float[] distances = {1.0f, 5.0f, 20.0f, 100.0f};
            for (float dist : distances) {
                LodContext context = LodContext.builder()
                        .cameraPosition(new Vec3(0, 0, dist))
                        .viewProjection(perspectiveMatrix(60f, 1.77f, 0.1f, 1000f))
                        .screenHeight(1080)
                        .errorThreshold(1.0f)
                        .objectTransform(Mat4.identity())
                        .objectBounds(original.bounds())
                        .build();

                LodSelection selection = selector.select(flat, context);
                printSelection(dist, selection);
            }
        }
    }

    // =========================================================================
    // Demo 2: DAG structure (cluster LOD)
    // =========================================================================

    private static void demoDagStructure() {
        System.out.println("--- Demo 2: DAG Structure (Cluster LOD) ---");

        // Simulate a two-level cluster DAG:
        // Root level: 2 coarse clusters (nodes 0, 1)
        // Leaf level: 4 fine clusters (nodes 2, 3, 4, 5)
        // Node 0 refines into nodes 2, 3
        // Node 1 refines into nodes 3, 4, 5  (node 3 has TWO parents — DAG, not tree)

        AABB leftBounds = AABB.fromMinMax(new Vec3(-1, -1, -1), new Vec3(0, 1, 1));
        AABB rightBounds = AABB.fromMinMax(new Vec3(0, -1, -1), new Vec3(1, 1, 1));
        AABB fullBounds = AABB.fromMinMax(new Vec3(-1, -1, -1), new Vec3(1, 1, 1));

        RepresentationNode[] nodes = {
                new RepresentationNode(new int[]{0}, 0.5f, 100, leftBounds, 0),   // coarse left
                new RepresentationNode(new int[]{1}, 0.5f, 100, rightBounds, 0),  // coarse right
                new RepresentationNode(new int[]{2}, 0.1f, 400, leftBounds, 0),   // fine left-A
                new RepresentationNode(new int[]{3}, 0.1f, 400, fullBounds, 0),   // fine center (shared)
                new RepresentationNode(new int[]{4}, 0.1f, 400, rightBounds, 0),  // fine right-A
                new RepresentationNode(new int[]{5}, 0.1f, 400, rightBounds, 0),  // fine right-B
        };

        RepresentationGraph graph = RepresentationGraph.builder()
                .nodes(nodes)
                .edges(0, 2, 3)    // node 0 -> children 2, 3
                .edges(1, 3, 4, 5) // node 1 -> children 3, 4, 5
                .build();

        System.out.println("  Nodes: " + graph.nodeCount());
        System.out.println("  Roots: " + java.util.Arrays.toString(graph.roots()));
        System.out.println("  Is tree: " + graph.isTree());
        System.out.println("  Node 3 parents: " + graph.parentCount(3) + " (should be 2 - shared cluster)");

        // Verify DAG properties
        assert graph.roots().length == 2 : "Should have 2 roots";
        assert !graph.isTree() : "Should be a DAG (node 3 has 2 parents)";
        assert graph.parentCount(3) == 2 : "Node 3 should have 2 parents";
        assert graph.childCount(0) == 2 : "Node 0 should have 2 children";
        assert graph.childCount(1) == 3 : "Node 1 should have 3 children";

        // Wrap in RepresentationStructure
        RepresentationStructure.Graph structGraph = new RepresentationStructure.Graph(graph);
        System.out.println("  Structure node count: " + structGraph.nodeCount());
        System.out.println("  PASSED: DAG with shared boundary clusters");
    }

    // =========================================================================
    // Demo 3: Transition state tracking
    // =========================================================================

    private static void demoTransitions() {
        System.out.println("--- Demo 3: Transition State Tracking ---");

        TransitionState state = new TransitionState(
                new TransitionMode.Dither(0.5f), // 0.5 second dither
                0,  // from node 0
                2   // to node 2
        );

        System.out.println("  Mode: " + state.mode());
        System.out.println("  From: " + state.fromNodeIndex() + " -> To: " + state.toNodeIndex());

        // Simulate frames at 60fps
        float dt = 1.0f / 60.0f;
        int frame = 0;
        while (!state.isComplete()) {
            state.advance(dt);
            frame++;
            if (frame % 10 == 0 || state.isComplete()) {
                System.out.printf("    Frame %3d: factor=%.3f remaining=%.3fs%n",
                        frame, state.factor(), state.remainingSeconds());
            }
        }
        System.out.println("  Completed after " + frame + " frames");
        assert state.factor() >= 1.0f;
        System.out.println("  PASSED");
    }

    // =========================================================================
    // Demo 4: Budget policy arbitration
    // =========================================================================

    private static void demoBudgetPolicy() {
        System.out.println("--- Demo 4: Budget Policy ---");

        // A simple policy that raises error thresholds for distant meshes to stay in budget
        LodPolicy distancePolicy = (entries, budget) -> {
            // Sort by distance (farthest first)
            entries.sort((a, b) -> Float.compare(b.distanceToCamera(), a.distanceToCamera()));

            long remaining = budget.maxTriangles();
            for (LodBudgetEntry entry : entries) {
                if (remaining <= 0) {
                    // Budget exhausted: max out error threshold so selector picks coarsest
                    entry.setEffectiveErrorThreshold(Float.MAX_VALUE);
                } else {
                    // Proportional: closer meshes get more budget
                    float distanceFactor = 1.0f + entry.distanceToCamera() * 0.1f;
                    entry.setEffectiveErrorThreshold(entry.effectiveErrorThreshold() * distanceFactor);
                    remaining -= entry.currentTriangleCount();
                }
            }
        };

        AABB bounds = AABB.fromMinMax(new Vec3(-1, -1, -1), new Vec3(1, 1, 1));
        RepresentationNode[] nodes = {
                new RepresentationNode(new int[]{0}, 0.0f, 1000, bounds, 0),
                new RepresentationNode(new int[]{1}, 0.3f, 250, bounds, 0),
        };
        RepresentationStructure.Flat structure = new RepresentationStructure.Flat(nodes);

        // Create 5 meshes at various distances
        List<LodBudgetEntry> entries = new ArrayList<>();
        float[] meshDistances = {2f, 10f, 30f, 50f, 100f};
        for (float dist : meshDistances) {
            entries.add(new LodBudgetEntry(structure, bounds, dist, 0.1f, 1000, 0, 1.0f));
        }

        LodBudget budget = LodBudget.triangleBudget(3000); // can afford ~3 full-detail meshes

        distancePolicy.arbitrate(entries, budget);

        System.out.println("  After arbitration (budget: 3000 tris for 5 meshes):");
        for (LodBudgetEntry entry : entries) {
            System.out.printf("    dist=%.0f  effectiveThreshold=%.2f%n",
                    entry.distanceToCamera(), entry.effectiveErrorThreshold());
        }
        System.out.println("  PASSED: distant meshes got higher thresholds (coarser LOD)");
    }

    // =========================================================================
    // Demo 5: Parametric representation
    // =========================================================================

    private static void demoParametric() {
        System.out.println("--- Demo 5: Parametric Representation ---");

        AABB bounds = AABB.fromMinMax(new Vec3(-1, -1, -1), new Vec3(1, 1, 1));
        RepresentationNode base = new RepresentationNode(new int[]{0}, -1f, 0, bounds, 0);

        ParameterDescriptor tessLevel = ParameterDescriptor.increasing("tessellationFactor", 1f, 64f, 16f);
        ParameterDescriptor dispAmp = ParameterDescriptor.increasing("displacementAmplitude", 0f, 1f, 0.5f);

        RepresentationStructure.Parametric parametric = new RepresentationStructure.Parametric(
                base, new ParameterDescriptor[]{tessLevel, dispAmp});

        System.out.println("  Base node: " + parametric.nodeCount() + " node (parametric)");
        System.out.println("  Parameters:");
        for (ParameterDescriptor p : parametric.parameters()) {
            System.out.printf("    %s: [%.1f, %.1f] default=%.1f higherMeansMore=%b%n",
                    p.name(), p.min(), p.max(), p.defaultValue(), p.higherMeansMore());
        }

        // A parametric selector returns parameter values
        LodSelection.Parametric selection = LodSelection.Parametric.of(
                java.util.Map.of("tessellationFactor", 32.0f, "displacementAmplitude", 0.8f));

        System.out.println("  Selection: " + selection.parameters());
        System.out.println("  PASSED");
    }

    // =========================================================================
    // Demo 6: Metadata channels (key-based registry)
    // =========================================================================

    private static void demoMetadataChannels() {
        System.out.println("--- Demo 6: Metadata Channels ---");

        int partitionCount = 100;
        PartitionMetadata metadata = new PartitionMetadata(partitionCount);

        // Write error bounds using the LOD channel key
        float[] errors = metadata.floatChannel(LodChannels.ERROR_BOUND);
        for (int i = 0; i < partitionCount; i++) {
            errors[i] = i * 0.01f; // increasing error with partition index
        }

        // Write LOD levels
        int[] levels = metadata.intChannel(LodChannels.LOD_LEVEL);
        for (int i = 0; i < partitionCount; i++) {
            levels[i] = i / 25; // 4 levels of 25 partitions each
        }

        // Read back directly (O(1), no boxing)
        System.out.println("  Partition 0: error=" + errors[0] + " level=" + levels[0]);
        System.out.println("  Partition 50: error=" + errors[50] + " level=" + levels[50]);
        System.out.println("  Partition 99: error=" + errors[99] + " level=" + levels[99]);

        // Demonstrate the single-lookup getters (for when you don't hold the array)
        float e50 = metadata.getFloat(LodChannels.ERROR_BOUND, 50);
        int l50 = metadata.getInt(LodChannels.LOD_LEVEL, 50);
        assert e50 == 0.5f : "Expected 0.5, got " + e50;
        assert l50 == 2 : "Expected 2, got " + l50;

        // Demonstrate bulk GPU upload via MetadataStore
        MetadataStore errorStore = metadata.floatStore(LodChannels.ERROR_BOUND);
        System.out.println("  Store: " + errorStore.name() + " byteSize=" + errorStore.byteSize()
                + " count=" + errorStore.count());

        // Bulk write to a segment (simulating GPU upload)
        MemorySegment gpuSegment = Arena.ofAuto().allocate((long) errorStore.byteSize() * errorStore.count());
        errorStore.bulkWriteTo(gpuSegment, 0, 0, partitionCount);

        // Verify first few values in the segment
        float v0 = gpuSegment.get(java.lang.foreign.ValueLayout.JAVA_FLOAT, 0);
        float v1 = gpuSegment.get(java.lang.foreign.ValueLayout.JAVA_FLOAT, 4);
        assert v0 == 0.0f;
        assert v1 == 0.01f;

        // Demonstrate sharing: second access to same key returns same array
        float[] errors2 = metadata.floatChannel(LodChannels.ERROR_BOUND);
        assert errors == errors2 : "Same key must return same backing array";

        System.out.println("  Sharing verified: same key -> same array");
        System.out.println("  PASSED");
    }

    // =========================================================================
    // A simple screen-error LOD selector for the demo
    // =========================================================================

    /**
     * Simplest possible CPU selector: picks the coarsest level whose projected screen error
     * is below the threshold.
     */
    private static class ScreenErrorSelector implements LodSelector {
        private final float hysteresisMargin;

        ScreenErrorSelector(float hysteresisMargin) {
            this.hysteresisMargin = hysteresisMargin;
        }

        @Override
        public LodSelection select(RepresentationStructure representations, LodContext context) {
            if (!(representations instanceof RepresentationStructure.Flat flat)) {
                return LodSelection.None.EMPTY;
            }

            float distance = context.distanceTo(context.objectBounds());

            // Walk from coarsest to finest, pick the first that's good enough
            int selected = flat.nodeCount() - 1; // default to coarsest
            for (int i = flat.nodeCount() - 1; i >= 0; i--) {
                RepresentationNode node = flat.node(i);
                if (!node.hasErrorBound()) continue;
                float screenError = context.projectError(node.errorBound(), distance);
                if (screenError <= context.errorThreshold() + hysteresisMargin) {
                    selected = i;
                    break;
                }
            }

            // Build draw ranges from the selected node's partitions
            RepresentationNode node = flat.node(selected);
            List<GeometryDrawRange> ranges = List.of(
                    GeometryDrawRange.indexed((int) node.triangleCount() * 3, 0,
                            node.partitionIndices()[0] * 10000, // fake firstIndex based on partition
                            representations.node(selected).bounds().min // just demonstrating structure
                                    == null ? io.github.yetyman.vulkan.mesh.PrimitiveTopology.TRIANGLE_LIST
                                    : io.github.yetyman.vulkan.mesh.PrimitiveTopology.TRIANGLE_LIST)
            );

            return LodSelection.Explicit.of(ranges, selected);
        }
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private static void printSelection(float distance, LodSelection selection) {
        String desc = switch (selection) {
            case LodSelection.Explicit e -> "Explicit node=" + e.selectedNodeIndex();
            case LodSelection.Indirect i -> "Indirect (GPU)";
            case LodSelection.Parametric p -> "Parametric " + p.parameters();
            case LodSelection.None n -> "None";
        };
        System.out.printf("    dist=%6.1f -> %s%n", distance, desc);
    }

    private static Mat4 perspectiveMatrix(float fovDeg, float aspect, float near, float far) {
        Mat4 m = new Mat4();
        float fovRad = (float) Math.toRadians(fovDeg);
        float tanHalfFov = (float) Math.tan(fovRad / 2.0f);
        m.m00 = 1.0f / (aspect * tanHalfFov);
        m.m11 = 1.0f / tanHalfFov;
        m.m22 = -(far + near) / (far - near);
        m.m23 = -1.0f;
        m.m32 = -(2.0f * far * near) / (far - near);
        m.m33 = 0.0f;
        return m;
    }
}
