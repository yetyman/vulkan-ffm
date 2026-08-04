package io.github.yetyman.vulkan.ui2d;

import io.github.yetyman.vulkan.nodetree.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Uniform grid spatial acceleration structure for 2D AABB hit-testing.
 *
 * Provides O(1) average point queries for axis-aligned UI rectangles. Rebuilds lazily
 * when elements move (dirty-flagged via PropertyNotifier bulk observer).
 *
 * Design assumptions:
 * - Elements are axis-aligned rectangles (no rotation for hit-test purposes)
 * - Elements move rarely relative to how often hit-tests are performed
 * - Typical element count: 100-10000
 * - Grid cell size is tuned to average element size for O(1) query
 *
 * The grid covers the full viewport area. Elements that extend beyond the grid bounds
 * are clamped to edge cells.
 *
 * Z-order (which element is "on top") is determined by tree depth — deeper nodes
 * are considered to be in front of shallower nodes. Among siblings at the same depth,
 * later children are in front.
 *
 * This is a tree-scoped component. It subscribes to position/size changes via the
 * BulkPropertyObserver mechanism.
 */
public class SpatialGrid implements TreeComponent, BulkPropertyObserver {

    private float gridWidth;
    private float gridHeight;
    private float cellSize;
    private int cols;
    private int rows;

    // Grid cells: each cell holds a list of element indices
    private List<int[]> cells; // cell index -> array of element indices
    private int[] cellCounts;  // how many elements per cell

    // Element data (parallel arrays for cache friendliness)
    private float[] elemX;      // left edge
    private float[] elemY;      // top edge
    private float[] elemW;      // width
    private float[] elemH;      // height
    private Node[] elemNodes;   // back-reference to node
    private int elemCount = 0;
    private int elemCapacity = 0;

    // Dirty state
    private boolean structureDirty = true; // full rebuild needed (adds/removes)
    private boolean positionDirty = true;  // positions changed, need re-cell

    // View reference
    private TraversalView<RectangleComponent> view;
    private Tree tree;

    /**
     * Creates a spatial grid covering the given viewport.
     *
     * @param width viewport width
     * @param height viewport height
     * @param cellSize size of each grid cell (tune to average element size)
     */
    public SpatialGrid(float width, float height, float cellSize) {
        resize(width, height, cellSize);
    }

    /**
     * Resizes the grid (e.g., on window resize).
     */
    public void resize(float width, float height, float cellSize) {
        this.gridWidth = width;
        this.gridHeight = height;
        this.cellSize = cellSize;
        this.cols = Math.max(1, (int) Math.ceil(width / cellSize));
        this.rows = Math.max(1, (int) Math.ceil(height / cellSize));
        this.cells = new ArrayList<>(cols * rows);
        this.cellCounts = new int[cols * rows];
        for (int i = 0; i < cols * rows; i++) {
            cells.add(null);
        }
        this.structureDirty = true;
    }

    // --- TreeComponent lifecycle ---

    @Override
    public void onInit(Tree tree) {
        this.tree = tree;
    }

    @Override
    public void afterResolve(Tree tree) {
        // Get or create a traversal view for rectangles
        this.view = tree.getOrCreateTraversalView("spatial-grid-rects",
                RectangleComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER);
    }

    @Override
    public void close(Tree tree) {
        if (view != null) {
            tree.releaseTraversalView("spatial-grid-rects");
        }
    }

    // --- BulkPropertyObserver ---

    @Override
    public void onPropertyChanged(Component source, int propertyOrdinal, int slotIndex) {
        // Update cached position/size from the source rect
        if (slotIndex >= 0 && slotIndex < elemCount && source instanceof RectangleComponent rect) {
            elemX[slotIndex] = rect.x();
            elemY[slotIndex] = rect.y();
            elemW[slotIndex] = rect.width();
            elemH[slotIndex] = rect.height();
        }
        positionDirty = true;
    }

    // --- Hit testing ---

    /**
     * Queries the grid for the topmost element at the given point.
     * Returns the node closest to the viewer (deepest in tree, latest among siblings).
     *
     * Triggers a rebuild if the grid is dirty.
     *
     * @param x the x coordinate to test
     * @param y the y coordinate to test
     * @return the hit node, or null if no element contains the point
     */
    public Node hitTest(float x, float y) {
        rebuildIfDirty();

        int col = (int) (x / cellSize);
        int row = (int) (y / cellSize);
        if (col < 0 || col >= cols || row < 0 || row >= rows) return null;

        int cellIdx = row * cols + col;
        int[] elemIndices = cells.get(cellIdx);
        int count = cellCounts[cellIdx];
        if (elemIndices == null || count == 0) return null;

        // Find the topmost (deepest/latest) element that actually contains the point
        Node best = null;
        int bestDepth = -1;
        int bestOrder = -1;

        for (int i = 0; i < count; i++) {
            int ei = elemIndices[i];
            if (ei >= elemCount) continue;

            float ex = elemX[ei], ey = elemY[ei];
            float ew = elemW[ei], eh = elemH[ei];

            if (x >= ex && x < ex + ew && y >= ey && y < ey + eh) {
                Node node = elemNodes[ei];
                int depth = node.depth();
                // Deeper = in front. Same depth = higher index (later in traversal) = in front
                if (depth > bestDepth || (depth == bestDepth && ei > bestOrder)) {
                    best = node;
                    bestDepth = depth;
                    bestOrder = ei;
                }
            }
        }

        return best;
    }

    /**
     * Queries all elements at the given point, sorted front-to-back.
     */
    public List<Node> hitTestAll(float x, float y) {
        rebuildIfDirty();

        int col = (int) (x / cellSize);
        int row = (int) (y / cellSize);
        if (col < 0 || col >= cols || row < 0 || row >= rows) return List.of();

        int cellIdx = row * cols + col;
        int[] elemIndices = cells.get(cellIdx);
        int count = cellCounts[cellIdx];
        if (elemIndices == null || count == 0) return List.of();

        List<Node> hits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int ei = elemIndices[i];
            if (ei >= elemCount) continue;

            float ex = elemX[ei], ey = elemY[ei];
            float ew = elemW[ei], eh = elemH[ei];

            if (x >= ex && x < ex + ew && y >= ey && y < ey + eh) {
                hits.add(elemNodes[ei]);
            }
        }

        // Sort by depth descending (front to back), then by traversal order descending
        hits.sort((a, b) -> {
            int da = a.depth(), db = b.depth();
            if (da != db) return Integer.compare(db, da);
            return 0; // stable sort preserves traversal order
        });

        return hits;
    }

    // --- Rebuild ---

    /**
     * Rebuilds the grid if any structural or positional changes have occurred.
     * Called automatically before hit-tests.
     */
    public void rebuildIfDirty() {
        if (structureDirty) {
            fullRebuild();
        } else if (positionDirty) {
            rebuildCells();
        }
    }

    /**
     * Forces a full rebuild (re-reads all elements from the traversal view).
     */
    private void fullRebuild() {
        // Count elements
        int count = view.liveCount();
        ensureCapacity(count);
        elemCount = 0;

        // Read all rectangle data
        view.forEach((node, rect) -> {
            int idx = elemCount;
            elemX[idx] = rect.x();
            elemY[idx] = rect.y();
            elemW[idx] = rect.width();
            elemH[idx] = rect.height();
            elemNodes[idx] = node;

            // Register bulk observer on this rect for future changes
            rect.properties().setBulkObserver(this, rect, idx);

            elemCount++;
        });

        rebuildCells();
        structureDirty = false;
    }

    /**
     * Rebuilds just the cell assignments (positions changed but element set is the same).
     */
    private void rebuildCells() {
        // Clear all cells
        for (int i = 0; i < cols * rows; i++) {
            cellCounts[i] = 0;
        }

        // Re-insert all elements into cells they overlap
        for (int ei = 0; ei < elemCount; ei++) {
            float ex = elemX[ei], ey = elemY[ei];
            float ew = elemW[ei], eh = elemH[ei];

            int minCol = Math.max(0, (int) (ex / cellSize));
            int maxCol = Math.min(cols - 1, (int) ((ex + ew) / cellSize));
            int minRow = Math.max(0, (int) (ey / cellSize));
            int maxRow = Math.min(rows - 1, (int) ((ey + eh) / cellSize));

            for (int row = minRow; row <= maxRow; row++) {
                for (int col = minCol; col <= maxCol; col++) {
                    int cellIdx = row * cols + col;
                    addToCell(cellIdx, ei);
                }
            }
        }

        positionDirty = false;
    }

    private void addToCell(int cellIdx, int elemIdx) {
        int[] arr = cells.get(cellIdx);
        int count = cellCounts[cellIdx];
        if (arr == null) {
            arr = new int[4];
            cells.set(cellIdx, arr);
        } else if (count >= arr.length) {
            int[] newArr = new int[arr.length * 2];
            System.arraycopy(arr, 0, newArr, 0, arr.length);
            arr = newArr;
            cells.set(cellIdx, arr);
        }
        arr[count] = elemIdx;
        cellCounts[cellIdx] = count + 1;
    }

    private void ensureCapacity(int needed) {
        if (needed > elemCapacity) {
            int newCap = Math.max(needed, elemCapacity * 2);
            elemX = growFloat(elemX, newCap);
            elemY = growFloat(elemY, newCap);
            elemW = growFloat(elemW, newCap);
            elemH = growFloat(elemH, newCap);
            elemNodes = growNodes(elemNodes, newCap);
            elemCapacity = newCap;
        }
    }

    private static float[] growFloat(float[] old, int newCap) {
        float[] arr = new float[newCap];
        if (old != null) System.arraycopy(old, 0, arr, 0, old.length);
        return arr;
    }

    private static Node[] growNodes(Node[] old, int newCap) {
        Node[] arr = new Node[newCap];
        if (old != null) System.arraycopy(old, 0, arr, 0, old.length);
        return arr;
    }

    /**
     * Marks the grid for a full structural rebuild (call when elements are added/removed).
     */
    public void markStructureDirty() {
        structureDirty = true;
    }

    /** @return the number of elements tracked. */
    public int elementCount() { return elemCount; }
}
