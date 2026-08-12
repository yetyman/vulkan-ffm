package io.github.yetyman.vulkan.mesh.processing;

import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.primitives.SphereSource;
import io.github.yetyman.vulkan.mesh.source.primitives.TorusSource;
import io.github.yetyman.vulkan.util.Logger;

import java.lang.foreign.Arena;

/**
 * Simple test of the QEM simplifier: creates a sphere and torus, simplifies them at several
 * ratios, and reports triangle counts and error. No GPU required.
 *
 * <p>Run with: {@code mvn exec:java -pl sample-app -Dexec.mainClass="io.github.yetyman.vulkan.mesh.processing.QemSimplifierTest"}</p>
 */
public class QemSimplifierTest {

    public static void main(String[] args) {
        try (Arena arena = Arena.ofConfined()) {
            QemSimplifier simplifier = new QemSimplifier();

            Logger.info("=== QEM Simplifier Test ===\n");

            // Test 1: Sphere (32x24 = high detail)
            SphereSource sphere = new SphereSource(arena, 1.0f, 24, 32);
            long sphereTris = sphere.indices().get().indexCount() / 3;
            Logger.info("Source: Sphere (" + sphere.elementCount() + " vertices, " + sphereTris + " triangles)");
            testSimplify(simplifier, sphere, "Sphere", arena, new float[]{0.5f, 0.25f, 0.1f, 0.05f});

            Logger.info("");

            // Test 2: Torus (32x24)
            TorusSource torus = new TorusSource(arena, 1.0f, 0.4f, 32, 24);
            long torusTris = torus.indices().get().indexCount() / 3;
            Logger.info("Source: Torus (" + torus.elementCount() + " vertices, " + torusTris + " triangles)");
            testSimplify(simplifier, torus, "Torus", arena, new float[]{0.5f, 0.25f, 0.1f, 0.05f});

            Logger.info("\n=== Test complete ===");
        }
    }

    private static void testSimplify(QemSimplifier simplifier, GeometrySource source,
                                     String name, Arena arena, float[] ratios) {
        long originalTris = source.indices().get().indexCount() / 3;

        for (float ratio : ratios) {
            long start = System.nanoTime();
            GeometrySource simplified = simplifier.simplify(source, ratio, arena);
            long elapsed = System.nanoTime() - start;

            long newTris = simplified.indices().get().indexCount() / 3;
            long newVerts = simplified.elementCount();
            float actualRatio = (float) newTris / originalTris;
            float error = simplifier.lastError();

            Logger.info(String.format("  %s @ %.0f%%: %d tris, %d verts (actual %.1f%%), error=%.6f, %.1f ms",
                    name, ratio * 100, newTris, newVerts, actualRatio * 100, error, elapsed / 1_000_000.0));
        }
    }
}
