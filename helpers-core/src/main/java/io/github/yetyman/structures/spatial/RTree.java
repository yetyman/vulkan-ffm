package io.github.yetyman.structures.spatial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A 3D R-tree for spatial queries over axis-aligned bounding boxes.
 * <p>
 * Each inserted entry is assigned a unique int id by the tree. Z is stored as
 * metadata on each entry and is used to sort results (descending = topmost first).
 * Ray queries return the closest hit by ray parameter T unless the full-set
 * overload is used.
 * <p>
 * Not thread-safe. Callers must synchronize externally if accessed from multiple threads.
 */
public class RTree {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final int DEFAULT_MAX_CHILDREN = 16;
    private static final int MIN_CHILDREN = 2;

    // -------------------------------------------------------------------------
    // Entry — leaf data
    // -------------------------------------------------------------------------

    private static final class Entry {
        int id;
        float minX, minY, minZ;
        float maxX, maxY, maxZ;
        float z; // sort key (depth / draw order)

        Entry(int id, float minX, float minY, float minZ,
                      float maxX, float maxY, float maxZ, float z) {
            this.id   = id;
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
            this.z    = z;
        }
    }

    // -------------------------------------------------------------------------
    // Node — internal or leaf
    // -------------------------------------------------------------------------

    private static final class Node {
        // MBR of this node
        float minX, minY, minZ;
        float maxX, maxY, maxZ;

        boolean isLeaf;
        // leaf: entries list populated; children null
        // internal: children list populated; entries null
        List<Entry> entries;
        List<Node>  children;
        Node parent;

        Node(boolean isLeaf) {
            this.isLeaf = isLeaf;
            if (isLeaf) entries  = new ArrayList<>();
            else        children = new ArrayList<>();
            resetMBR();
        }

        void resetMBR() {
            minX = minY = minZ = Float.POSITIVE_INFINITY;
            maxX = maxY = maxZ = Float.NEGATIVE_INFINITY;
        }

        void recomputeMBR() {
            resetMBR();
            if (isLeaf) {
                for (Entry e : entries) {
                    minX = Math.min(minX, e.minX); minY = Math.min(minY, e.minY); minZ = Math.min(minZ, e.minZ);
                    maxX = Math.max(maxX, e.maxX); maxY = Math.max(maxY, e.maxY); maxZ = Math.max(maxZ, e.maxZ);
                }
            } else {
                for (Node c : children) {
                    minX = Math.min(minX, c.minX); minY = Math.min(minY, c.minY); minZ = Math.min(minZ, c.minZ);
                    maxX = Math.max(maxX, c.maxX); maxY = Math.max(maxY, c.maxY); maxZ = Math.max(maxZ, c.maxZ);
                }
            }
        }

        void expandToEntry(Entry e) {
            minX = Math.min(minX, e.minX); minY = Math.min(minY, e.minY); minZ = Math.min(minZ, e.minZ);
            maxX = Math.max(maxX, e.maxX); maxY = Math.max(maxY, e.maxY); maxZ = Math.max(maxZ, e.maxZ);
        }

        void expandToNode(Node n) {
            minX = Math.min(minX, n.minX); minY = Math.min(minY, n.minY); minZ = Math.min(minZ, n.minZ);
            maxX = Math.max(maxX, n.maxX); maxY = Math.max(maxY, n.maxY); maxZ = Math.max(maxZ, n.maxZ);
        }

        int size() {
            return isLeaf ? entries.size() : children.size();
        }

        float volume() {
            float dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
            return (dx < 0 || dy < 0 || dz < 0) ? 0f : dx * dy * dz;
        }

        /** Volume of MBR enlarged to contain entry e */
        float enlargedVolume(Entry e) {
            float nx = Math.min(minX, e.minX), ny = Math.min(minY, e.minY), nz = Math.min(minZ, e.minZ);
            float xx = Math.max(maxX, e.maxX), xy = Math.max(maxY, e.maxY), xz = Math.max(maxZ, e.maxZ);
            return (xx - nx) * (xy - ny) * (xz - nz);
        }

        /** Volume of MBR enlarged to contain node n */
        float enlargedVolumeNode(Node n) {
            float nx = Math.min(minX, n.minX), ny = Math.min(minY, n.minY), nz = Math.min(minZ, n.minZ);
            float xx = Math.max(maxX, n.maxX), xy = Math.max(maxY, n.maxY), xz = Math.max(maxZ, n.maxZ);
            return (xx - nx) * (xy - ny) * (xz - nz);
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final int maxChildren;
    private Node root;
    private int nextId = 0;
    private float maxZ = Float.NEGATIVE_INFINITY;

    /** id → Entry, for O(1) lookup on remove/move/updateZ */
    private final Map<Integer, Entry> entryMap = new HashMap<>();
    /** id → leaf Node containing the entry */
    private final Map<Integer, Node>  leafMap  = new HashMap<>();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public RTree() {
        this(DEFAULT_MAX_CHILDREN);
    }

    public RTree(int maxChildren) {
        if (maxChildren < MIN_CHILDREN * 2)
            throw new IllegalArgumentException("maxChildren must be >= " + (MIN_CHILDREN * 2));
        this.maxChildren = maxChildren;
        this.root = new Node(true);
    }

    // -------------------------------------------------------------------------
    // Insert
    // -------------------------------------------------------------------------

    /**
     * Inserts an AABB with the given Z sort key.
     *
     * @return the id assigned to this entry by the tree
     */
    public int insert(float minX, float minY, float minZ,
                      float maxX, float maxY, float maxZ, float z) {
        int id = nextId++;
        Entry e = new Entry(id, minX, minY, minZ, maxX, maxY, maxZ, z);
        entryMap.put(id, e);
        if (z > maxZ) maxZ = z;
        insertEntry(e);
        return id;
    }

    private void insertEntry(Entry e) {
        Node leaf = chooseLeaf(root, e);
        leaf.entries.add(e);
        leaf.expandToEntry(e);
        leafMap.put(e.id, leaf);
        if (leaf.size() > maxChildren) {
            splitAndPropagate(leaf);
        } else {
            adjustMBRsUpward(leaf);
        }
    }

    // -------------------------------------------------------------------------
    // Choose leaf — descend minimising volume enlargement
    // -------------------------------------------------------------------------

    private Node chooseLeaf(Node node, Entry e) {
        if (node.isLeaf) return node;
        Node best = null;
        float bestEnlargement = Float.POSITIVE_INFINITY;
        float bestVolume      = Float.POSITIVE_INFINITY;
        for (Node child : node.children) {
            float enlarged   = child.enlargedVolumeNode(nodeFromEntry(e));
            float enlargement = enlarged - child.volume();
            if (enlargement < bestEnlargement ||
                (enlargement == bestEnlargement && child.volume() < bestVolume)) {
                best = child;
                bestEnlargement = enlargement;
                bestVolume = child.volume();
            }
        }
        return chooseLeaf(best, e);
    }

    /** Temporary single-entry node used for volume enlargement comparison */
    private Node nodeFromEntry(Entry e) {
        Node n = new Node(true);
        n.minX = e.minX; n.minY = e.minY; n.minZ = e.minZ;
        n.maxX = e.maxX; n.maxY = e.maxY; n.maxZ = e.maxZ;
        return n;
    }

    // -------------------------------------------------------------------------
    // Quadratic split
    // -------------------------------------------------------------------------

    private void splitAndPropagate(Node node) {
        Node sibling = split(node);
        if (node == root) {
            Node newRoot = new Node(false);
            newRoot.children.add(node);
            newRoot.children.add(sibling);
            node.parent   = newRoot;
            sibling.parent = newRoot;
            newRoot.recomputeMBR();
            root = newRoot;
        } else {
            Node parent = node.parent;
            sibling.parent = parent;
            parent.children.add(sibling);
            parent.expandToNode(sibling);
            if (parent.size() > maxChildren) {
                splitAndPropagate(parent);
            } else {
                adjustMBRsUpward(parent);
            }
        }
    }

    /**
     * Quadratic split: pick seeds by maximum waste, then assign remaining
     * entries/children to whichever group needs less enlargement.
     */
    private Node split(Node node) {
        Node sibling = new Node(node.isLeaf);
        sibling.parent = node.parent;

        if (node.isLeaf) {
            List<Entry> all = new ArrayList<>(node.entries);
            node.entries.clear();
            node.resetMBR();

            int[] seeds = pickSeedsEntries(all);
            Entry s1 = all.get(seeds[0]);
            Entry s2 = all.get(seeds[1]);
            node.entries.add(s1);    node.expandToEntry(s1); leafMap.put(s1.id, node);
            sibling.entries.add(s2); sibling.expandToEntry(s2); leafMap.put(s2.id, sibling);

            List<Entry> remaining = new ArrayList<>(all);
            remaining.remove(s1); remaining.remove(s2);

            for (Entry e : remaining) {
                Node target = pickTargetLeaf(node, sibling, e);
                target.entries.add(e);
                target.expandToEntry(e);
                leafMap.put(e.id, target);
            }
        } else {
            List<Node> all = new ArrayList<>(node.children);
            node.children.clear();
            node.resetMBR();

            int[] seeds = pickSeedsNodes(all);
            Node s1 = all.get(seeds[0]);
            Node s2 = all.get(seeds[1]);
            node.children.add(s1);    s1.parent = node;    node.expandToNode(s1);
            sibling.children.add(s2); s2.parent = sibling; sibling.expandToNode(s2);

            List<Node> remaining = new ArrayList<>(all);
            remaining.remove(s1); remaining.remove(s2);

            for (Node c : remaining) {
                Node target = pickTargetNode(node, sibling, c);
                target.children.add(c);
                c.parent = target;
                target.expandToNode(c);
            }
        }
        return sibling;
    }

    private int[] pickSeedsEntries(List<Entry> entries) {
        int i1 = 0, i2 = 1;
        float maxWaste = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                Entry a = entries.get(i), b = entries.get(j);
                float combinedVol = combinedVolumeEntries(a, b);
                float waste = combinedVol - entryVolume(a) - entryVolume(b);
                if (waste > maxWaste) { maxWaste = waste; i1 = i; i2 = j; }
            }
        }
        return new int[]{i1, i2};
    }

    private int[] pickSeedsNodes(List<Node> nodes) {
        int i1 = 0, i2 = 1;
        float maxWaste = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node a = nodes.get(i), b = nodes.get(j);
                float combinedVol = combinedVolumeNodes(a, b);
                float waste = combinedVol - a.volume() - b.volume();
                if (waste > maxWaste) { maxWaste = waste; i1 = i; i2 = j; }
            }
        }
        return new int[]{i1, i2};
    }

    private Node pickTargetLeaf(Node a, Node b, Entry e) {
        float enlA = a.enlargedVolume(e) - a.volume();
        float enlB = b.enlargedVolume(e) - b.volume();
        if (enlA < enlB) return a;
        if (enlB < enlA) return b;
        return a.size() <= b.size() ? a : b;
    }

    private Node pickTargetNode(Node a, Node b, Node c) {
        float enlA = a.enlargedVolumeNode(c) - a.volume();
        float enlB = b.enlargedVolumeNode(c) - b.volume();
        if (enlA < enlB) return a;
        if (enlB < enlA) return b;
        return a.size() <= b.size() ? a : b;
    }

    private static float entryVolume(Entry e) {
        return (e.maxX - e.minX) * (e.maxY - e.minY) * (e.maxZ - e.minZ);
    }

    private static float combinedVolumeEntries(Entry a, Entry b) {
        return (Math.max(a.maxX, b.maxX) - Math.min(a.minX, b.minX))
             * (Math.max(a.maxY, b.maxY) - Math.min(a.minY, b.minY))
             * (Math.max(a.maxZ, b.maxZ) - Math.min(a.minZ, b.minZ));
    }

    private static float combinedVolumeNodes(Node a, Node b) {
        return (Math.max(a.maxX, b.maxX) - Math.min(a.minX, b.minX))
             * (Math.max(a.maxY, b.maxY) - Math.min(a.minY, b.minY))
             * (Math.max(a.maxZ, b.maxZ) - Math.min(a.minZ, b.minZ));
    }

    // -------------------------------------------------------------------------
    // MBR propagation upward after insert (no split)
    // -------------------------------------------------------------------------

    private void adjustMBRsUpward(Node node) {
        Node cur = node.parent;
        while (cur != null) {
            cur.recomputeMBR();
            cur = cur.parent;
        }
    }

    // -------------------------------------------------------------------------
    // Remove
    // -------------------------------------------------------------------------

    /**
     * Removes the entry with the given id.
     *
     * @throws IllegalArgumentException if id is not present
     */
    public void remove(int id) {
        Entry e = entryMap.remove(id);
        if (e == null) throw new IllegalArgumentException("Unknown id: " + id);
        Node leaf = leafMap.remove(id);
        leaf.entries.remove(e);
        condenseTree(leaf);
        shrinkRootIfNeeded();
        if (e.z == maxZ) recomputeMaxZ();
    }

    /**
     * Condense tree after removal: collect underfull nodes, reinsert their entries.
     */
    private void condenseTree(Node node) {
        List<Entry> reinsert = new ArrayList<>();
        Node cur = node;
        while (cur != root) {
            Node parent = cur.parent;
            if (cur.size() < MIN_CHILDREN) {
                parent.children.remove(cur);
                if (cur.isLeaf) {
                    reinsert.addAll(cur.entries);
                } else {
                    collectAllEntries(cur, reinsert);
                }
            } else {
                cur.recomputeMBR();
            }
            cur = parent;
        }
        root.recomputeMBR();
        for (Entry e : reinsert) {
            insertEntry(e);
        }
    }

    private void collectAllEntries(Node node, List<Entry> out) {
        if (node.isLeaf) {
            out.addAll(node.entries);
        } else {
            for (Node c : node.children) collectAllEntries(c, out);
        }
    }

    private void shrinkRootIfNeeded() {
        while (!root.isLeaf && root.children.size() == 1) {
            root = root.children.get(0);
            root.parent = null;
        }
    }

    private void recomputeMaxZ() {
        maxZ = Float.NEGATIVE_INFINITY;
        for (Entry e : entryMap.values()) {
            if (e.z > maxZ) maxZ = e.z;
        }
    }

    // -------------------------------------------------------------------------
    // Move — spatial update, Z unchanged
    // -------------------------------------------------------------------------

    /**
     * Updates the AABB of an existing entry without changing its Z.
     *
     * @throws IllegalArgumentException if id is not present
     */
    public void move(int id, float minX, float minY, float minZ,
                              float maxX, float maxY, float maxZ) {
        Entry e = entryMap.get(id);
        if (e == null) throw new IllegalArgumentException("Unknown id: " + id);
        Node leaf = leafMap.get(id);
        leaf.entries.remove(e);
        e.minX = minX; e.minY = minY; e.minZ = minZ;
        e.maxX = maxX; e.maxY = maxY; e.maxZ = maxZ;
        // reinsert into best leaf (may differ after move)
        leafMap.remove(id);
        condenseTree(leaf);
        shrinkRootIfNeeded();
        insertEntry(e);
    }

    // -------------------------------------------------------------------------
    // UpdateZ — Z-only update
    // -------------------------------------------------------------------------

    /**
     * Updates the Z sort key of an existing entry without changing its AABB.
     *
     * @throws IllegalArgumentException if id is not present
     */
    public void updateZ(int id, float z) {
        Entry e = entryMap.get(id);
        if (e == null) throw new IllegalArgumentException("Unknown id: " + id);
        float old = e.z;
        e.z = z;
        if (z > maxZ) maxZ = z;
        else if (old == maxZ && z < old) recomputeMaxZ();
    }

    // -------------------------------------------------------------------------
    // BringToFront
    // -------------------------------------------------------------------------

    /**
     * Sets the Z of the given entry to maxZ + 1, making it sort above all others.
     *
     * @throws IllegalArgumentException if id is not present
     */
    public void bringToFront(int id) {
        Entry e = entryMap.get(id);
        if (e == null) throw new IllegalArgumentException("Unknown id: " + id);
        maxZ = (maxZ == Float.NEGATIVE_INFINITY) ? 0f : maxZ + 1f;
        e.z = maxZ;
    }

    // -------------------------------------------------------------------------
    // Bounds
    // -------------------------------------------------------------------------

    /**
     * @return the root MBR as [minX, minY, minZ, maxX, maxY, maxZ], or all-zero if empty
     */
    public float[] bounds() {
        if (entryMap.isEmpty()) return new float[6];
        return new float[]{ root.minX, root.minY, root.minZ,
                            root.maxX, root.maxY, root.maxZ };
    }

    /** @return the current maximum Z value across all entries */
    public float maxZ() { return maxZ; }

    /** @return the number of entries in the tree */
    public int size() { return entryMap.size(); }

    // -------------------------------------------------------------------------
    // Ray query
    // -------------------------------------------------------------------------

    /**
     * Returns the id of the entry whose AABB is intersected by the ray and has
     * the smallest T (closest to ray origin), or -1 if no intersection.
     * When multiple entries share the same T, the one with the highest Z wins.
     *
     * @param ox ray origin X
     * @param oy ray origin Y
     * @param oz ray origin Z
     * @param dx ray direction X (need not be normalised)
     * @param dy ray direction Y
     * @param dz ray direction Z
     */
    public int queryRay(float ox, float oy, float oz,
                        float dx, float dy, float dz) {
        float[] best = { Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY }; // [bestT, bestZ]
        int[] bestId = { -1 };
        queryRayNode(root, ox, oy, oz, dx, dy, dz, best, bestId, false, null);
        return bestId[0];
    }

    /**
     * Returns all ids whose AABBs are intersected by the ray, sorted by T ascending
     * (closest first). Ties in T are broken by Z descending.
     */
    public int[] queryRayAll(float ox, float oy, float oz,
                             float dx, float dy, float dz) {
        List<int[]> hits = new ArrayList<>(); // [id, Float.floatToIntBits(t), Float.floatToIntBits(z)]
        queryRayNode(root, ox, oy, oz, dx, dy, dz, null, null, true, hits);
        hits.sort((a, b) -> {
            float ta = Float.intBitsToFloat(a[1]), tb = Float.intBitsToFloat(b[1]);
            if (ta != tb) return Float.compare(ta, tb);
            return Float.compare(Float.intBitsToFloat(b[2]), Float.intBitsToFloat(a[2])); // Z desc
        });
        return hits.stream().mapToInt(h -> h[0]).toArray();
    }

    private void queryRayNode(Node node,
                              float ox, float oy, float oz,
                              float dx, float dy, float dz,
                              float[] best, int[] bestId,
                              boolean collectAll, List<int[]> hits) {
        if (!rayIntersectsAABB(ox, oy, oz, dx, dy, dz,
                node.minX, node.minY, node.minZ,
                node.maxX, node.maxY, node.maxZ)) return;

        if (node.isLeaf) {
            for (Entry e : node.entries) {
                float t = rayAABBIntersectT(ox, oy, oz, dx, dy, dz,
                        e.minX, e.minY, e.minZ, e.maxX, e.maxY, e.maxZ);
                if (t < 0) continue;
                if (collectAll) {
                    hits.add(new int[]{ e.id,
                            Float.floatToIntBits(t),
                            Float.floatToIntBits(e.z) });
                } else {
                    if (t < best[0] || (t == best[0] && e.z > best[1])) {
                        best[0] = t; best[1] = e.z; bestId[0] = e.id;
                    }
                }
            }
        } else {
            for (Node child : node.children) {
                queryRayNode(child, ox, oy, oz, dx, dy, dz, best, bestId, collectAll, hits);
            }
        }
    }

    /**
     * Slab method: returns the entry T of intersection, or -1 if no intersection.
     * T is the ray parameter at the near face of the AABB.
     */
    private static float rayAABBIntersectT(float ox, float oy, float oz,
                                           float dx, float dy, float dz,
                                           float minX, float minY, float minZ,
                                           float maxX, float maxY, float maxZ) {
        float tmin = Float.NEGATIVE_INFINITY;
        float tmax = Float.POSITIVE_INFINITY;

        if (dx != 0) {
            float invD = 1f / dx;
            float t1 = (minX - ox) * invD, t2 = (maxX - ox) * invD;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1); tmax = Math.min(tmax, t2);
        } else if (ox < minX || ox > maxX) return -1;

        if (dy != 0) {
            float invD = 1f / dy;
            float t1 = (minY - oy) * invD, t2 = (maxY - oy) * invD;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1); tmax = Math.min(tmax, t2);
        } else if (oy < minY || oy > maxY) return -1;

        if (dz != 0) {
            float invD = 1f / dz;
            float t1 = (minZ - oz) * invD, t2 = (maxZ - oz) * invD;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1); tmax = Math.min(tmax, t2);
        } else if (oz < minZ || oz > maxZ) return -1;

        if (tmax < 0 || tmin > tmax) return -1;
        return tmin >= 0 ? tmin : tmax;
    }

    private static boolean rayIntersectsAABB(float ox, float oy, float oz,
                                             float dx, float dy, float dz,
                                             float minX, float minY, float minZ,
                                             float maxX, float maxY, float maxZ) {
        return rayAABBIntersectT(ox, oy, oz, dx, dy, dz,
                minX, minY, minZ, maxX, maxY, maxZ) >= 0;
    }

    // -------------------------------------------------------------------------
    // Region query
    // -------------------------------------------------------------------------

    /**
     * Returns all ids whose AABBs overlap the given 3D region, sorted by Z descending.
     */
    public int[] queryRegion(float minX, float minY, float minZ,
                             float maxX, float maxY, float maxZ) {
        List<Entry> hits = new ArrayList<>();
        queryRegionNode(root, minX, minY, minZ, maxX, maxY, maxZ, hits);
        hits.sort((a, b) -> Float.compare(b.z, a.z));
        return hits.stream().mapToInt(e -> e.id).toArray();
    }

    private void queryRegionNode(Node node,
                                 float minX, float minY, float minZ,
                                 float maxX, float maxY, float maxZ,
                                 List<Entry> out) {
        if (!overlaps(node.minX, node.minY, node.minZ, node.maxX, node.maxY, node.maxZ,
                      minX, minY, minZ, maxX, maxY, maxZ)) return;
        if (node.isLeaf) {
            for (Entry e : node.entries) {
                if (overlaps(e.minX, e.minY, e.minZ, e.maxX, e.maxY, e.maxZ,
                             minX, minY, minZ, maxX, maxY, maxZ))
                    out.add(e);
            }
        } else {
            for (Node child : node.children) {
                queryRegionNode(child, minX, minY, minZ, maxX, maxY, maxZ, out);
            }
        }
    }

    private static boolean overlaps(float aMinX, float aMinY, float aMinZ,
                                    float aMaxX, float aMaxY, float aMaxZ,
                                    float bMinX, float bMinY, float bMinZ,
                                    float bMaxX, float bMaxY, float bMaxZ) {
        return aMaxX >= bMinX && aMinX <= bMaxX
            && aMaxY >= bMinY && aMinY <= bMaxY
            && aMaxZ >= bMinZ && aMinZ <= bMaxZ;
    }

    // -------------------------------------------------------------------------
    // 2D convenience
    // -------------------------------------------------------------------------

    /**
     * Convenience for 2D screen-space picking.
     * Equivalent to {@code queryRay(x, y, Float.MAX_VALUE, 0, 0, -1)} —
     * a ray fired straight down from above the scene along the Z axis.
     * Returns the id of the topmost (highest Z) entry whose XY AABB contains
     * the point, or -1 if none.
     */
    public int queryPoint2D(float x, float y) {
        return queryRay(x, y, Float.MAX_VALUE, 0f, 0f, -1f);
    }
}
