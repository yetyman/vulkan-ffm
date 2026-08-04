package io.github.yetyman.helpers.math.spatial;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.geometry.Frustum;
import io.github.yetyman.helpers.math.geometry.Ray;
import io.github.yetyman.helpers.math.geometry.Sphere;

import java.util.List;
import java.util.stream.Stream;

/**
 * Read-only spatial query interface shared by all spatial structures.
 * Provides allocating (List), streaming (Stream), and allocation-free (caller-provided List) variants.
 *
 * @param <T> the type of items stored in the spatial structure
 */
public interface SpatialQuery<T> {

    // --- List-returning queries ---

    /** Returns all items whose bounds overlap the given AABB. */
    List<T> query(AABB range);

    /** Returns all items whose bounds overlap the given sphere. */
    List<T> query(Sphere range);

    /** Returns all items whose bounds are intersected by the ray within maxDistance. */
    List<T> query(Ray ray, float maxDistance);

    /** Returns all items whose bounds are at least partially inside the frustum. */
    List<T> queryFrustum(Frustum frustum);

    // --- Stream-returning queries ---

    /** Returns a stream of items whose bounds overlap the given AABB. */
    Stream<T> queryStream(AABB range);

    /** Returns a stream of items whose bounds overlap the given sphere. */
    Stream<T> queryStream(Sphere range);

    /** Returns a stream of items whose bounds are intersected by the ray within maxDistance. */
    Stream<T> queryStream(Ray ray, float maxDistance);

    /** Returns a stream of items whose bounds are at least partially inside the frustum. */
    Stream<T> queryFrustumStream(Frustum frustum);

    // --- Allocation-free queries (append to caller-provided list) ---

    /** Queries by AABB, appending results to out. Returns the number of results added. */
    int query(AABB range, List<T> out);

    /** Queries by sphere, appending results to out. Returns the number of results added. */
    int query(Sphere range, List<T> out);

    /** Queries by frustum, appending results to out. Returns the number of results added. */
    int queryFrustum(Frustum frustum, List<T> out);

    // --- Point queries ---

    /** Returns true if any item's bounds contain the given point. */
    boolean contains(Vec3 point);

    /** Returns the item nearest to the given point, or null if empty. */
    T nearest(Vec3 point);

    // --- Count queries (no materialization) ---

    /** Returns the number of items whose bounds overlap the given AABB. */
    int count(AABB range);

    /** Returns the number of items whose bounds overlap the given sphere. */
    int count(Sphere range);

    /** Returns the number of items whose bounds are at least partially inside the frustum. */
    int countFrustum(Frustum frustum);
}
