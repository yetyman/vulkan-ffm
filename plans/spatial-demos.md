# Spatial Structure Demo Apps

Visual demonstrations of every spatial structure, designed to be succinct example code
using the existing sample-app infrastructure (VulkanApplication, GraphicsLoop, Scene3DOverlayLayer, GPUDrivenTextLayer).

Each demo should be a single class with minimal boilerplate, navigable with orbit camera + WASD.

---

## Shared Infrastructure

All demos share:
- **OrbitCamera** — mouse drag to rotate, scroll to zoom, WASD to pan
- **Scene3DOverlayLayer** — wireframe rendering for AABB boxes, lines, points (already exists)
- **GPUDrivenTextLayer** — for labels and stats (already exists)
- **UIComposite** — layer stack with input dispatch

### Common patterns:
- Objects rendered as colored wireframe AABBs
- Query results highlighted in a different color
- Structure nodes/cells rendered as semi-transparent wireframes
- Interactive query shape (sphere/box/ray) that follows the cursor or can be dragged
- Real-time stats overlay (item count, node count, query time, tree depth)

---

## Demo 1: Octree Visualizer

**Shows:** Hierarchical spatial subdivision, split/merge behavior, frustum culling.

**Interaction:**
- Objects spawn randomly on click (or continuously rain down)
- Octree cells rendered as wireframe boxes, color-coded by depth
- Draggable query sphere highlights items within range
- Toggle: show/hide internal nodes vs leaf-only
- Slider: adjust split threshold live — watch tree restructure
- Frustum visualization: show camera frustum wireframe, highlight visible items green / culled items red

**Code shape:** ~100 lines. Insert items, render tree wireframes via overlay, run query, highlight results.

---

## Demo 2: Quadtree Top-Down View

**Shows:** 2D spatial partitioning viewed from above.

**Interaction:**
- Top-down orthographic camera (Z-up)
- Click to place objects on the XZ plane
- Quadtree cells drawn as colored rectangles (depth = hue)
- Draggable rectangle query region
- Object count per cell shown as text labels
- Live split/merge animation when thresholds crossed

---

## Demo 3: BVH Ray Caster

**Shows:** BVH traversal for ray intersection.

**Interaction:**
- Scene with ~1000 random AABBs
- Ray cast from camera into scene on click
- Highlight: nodes tested (orange wireframe), nodes rejected early (grey), leaf hit (green)
- Show traversal path as a sequence of expanding boxes
- Toggle: median split vs future SAH builder — compare tree shape
- Stats: nodes tested vs total nodes (efficiency metric)

---

## Demo 4: R-Tree Dynamic Insertions

**Shows:** Balanced tree behavior under continuous insert/remove/move.

**Interaction:**
- Particles moving randomly, each tracked in the R-tree
- Tree MBRs rendered as wireframes (overlapping allowed — that's the R-tree property)
- Pause to see tree structure, unpause to watch adaptation
- Click to add a burst of particles
- Right-click to delete nearest
- Overlap visualization: areas where multiple MBRs overlap colored red

---

## Demo 5: Sparse Grid Particle System

**Shows:** O(1) spatial lookup for broad-phase collision detection.

**Interaction:**
- 10k+ particles bouncing in a box
- Grid cells highlighted when occupied (intensity = particle count)
- Toggle: show grid lines
- Neighbor query visualization: click a particle, highlight all particles in adjacent cells
- Stats: particles per cell histogram

---

## Demo 6: Dense Grid Voxel Editor

**Shows:** Fixed-resolution grid with direct cell access.

**Interaction:**
- Small 3D grid (16x16x16) rendered as voxels
- Click to toggle cells on/off
- Paint mode: hold to draw voxels
- Layer slice view: show one Y-level at a time
- Sphere brush: toggle all cells within radius

---

## Demo 7: KD-Tree Nearest Neighbor

**Shows:** Nearest-neighbor search efficiency.

**Interaction:**
- Point cloud of 5000 random points
- Moving cursor sphere — nearest point highlighted in real-time
- K-nearest-neighbors mode: highlight K closest points
- Visualization of the splitting planes (alternating axis shown as colored planes fading with depth)
- Comparison: brute force search time vs KD-tree query time displayed

---

## Demo 8: Hex Grid Strategy Map

**Shows:** Hexagonal tessellation, neighbor traversal, pathfinding.

**Interaction:**
- Flat-top hex grid rendered as colored hexagons (terrain types: grass, water, mountain)
- Click to select hex — highlight neighbors
- Drag to draw path — show hex line from A to B
- Range query: select hex, show all hexes within N steps (ring highlight)
- Hover: show axial coordinates as text label
- Paint mode: click to change terrain type

---

## Demo 9: Geodesic Sphere

**Shows:** Icosphere tessellation, pentagon/hexagon cells, point-to-cell lookup.

**Interaction:**
- Rotating geodesic sphere rendered with cell outlines
- Pentagons colored differently from hexagons
- Click on sphere surface — highlight nearest cell and its neighbors
- Subdivision level slider (0-4): watch vertex count change
- Heat map mode: color cells by latitude or by arbitrary function
- Dual rendering: show triangulation AND cell boundaries simultaneously

---

## Demo 10: Marching Cubes Isosurface

**Shows:** Real-time isosurface extraction from animated scalar field.

**Interaction:**
- Animated scalar field (metaballs — N spheres whose fields sum)
- Drag metaballs to reshape the surface in real-time
- Resolution slider: coarse to fine
- Iso-level slider: watch surface inflate/deflate
- Wireframe toggle: solid mesh vs wireframe
- Stats: vertex count, triangle count, extraction time

---

## Demo 11: Marching Squares Contour Map

**Shows:** 2D contour extraction with topographic-style visualization.

**Interaction:**
- 2D scalar field (perlin noise terrain height)
- Multiple iso-levels drawn as nested contour lines (like a topographic map)
- Draggable noise parameters (frequency, octaves, amplitude)
- Animate the field to show contours moving
- Color contour lines by elevation

---

## Demo 12: Marching Hexagons Contour

**Shows:** Contour extraction on hex grid.

**Interaction:**
- Same as Demo 11 but on hexagonal sampling grid
- Show hex grid overlay + contour lines
- Compare visual quality vs square grid at same sample density
- Toggle between hex and square contours side by side

---

## Demo 13: Surface Nets Smooth Terrain

**Shows:** Smooth isosurface extraction vs marching cubes.

**Interaction:**
- Side-by-side comparison: Marching Cubes on left, Surface Nets on right
- Same scalar field, same resolution
- Visual difference: surface nets produces smoother mesh with fewer triangles
- Stats comparison: vertex/tri count, mesh quality metrics

---

## Demo 14: Dual Contouring Sharp Features

**Shows:** How dual contouring preserves sharp edges.

**Interaction:**
- Scalar field with sharp features (CSG: union/intersection of boxes and spheres)
- Compare Marching Cubes (rounded edges) vs Dual Contouring (sharp edges)
- Toggle between algorithms on same field
- Highlight QEF vertex positions vs edge-interpolated positions

---

## Demo 15: Marching Tetrahedra Ambiguity-Free

**Shows:** No ambiguous face configurations vs marching cubes.

**Interaction:**
- Construct a field that triggers MC ambiguity (thin features, tunnels)
- Side-by-side: MC shows holes/artifacts at ambiguous cases, MT does not
- Highlight the problematic cubes in MC
- Stats: MT produces more triangles but no topology errors

---

## Demo 16: Chunk Streaming (Octree + Dense Grid)

**Shows:** Minecraft-style world streaming using octree for LOD + dense grid per chunk.

**Interaction:**
- Infinite procedural terrain, chunks load/unload as camera moves
- Octree manages which chunks are loaded (LOD by distance)
- Each chunk is a 16x16x16 dense grid of voxels
- Marching cubes meshes generated per chunk on load
- Show chunk boundaries, loading state (loading/loaded/unloading)
- Fly camera (WASD + mouse) through infinite world

---

## Demo 17: Spatial Query Playground

**Shows:** All query types on one structure, interactive.

**Interaction:**
- Choose structure type from dropdown (octree, BVH, R-tree, KD-tree, sparse grid)
- Choose query type: AABB, Sphere, Ray, Frustum, Nearest, Contains
- Drag the query shape in 3D space
- Results highlighted in real-time
- Structure visualization toggleable
- Performance comparison: switch structure, same query, see timing difference

---

## Demo 18: Geodesic Planet Editor

**Shows:** Geodesic grid as a game-ready planet surface.

**Interaction:**
- Geodesic sphere at subdivision level 3-4
- Each cell has a terrain type (ocean, land, mountain, ice)
- Click to paint terrain
- Neighbor-based rules: smooth terrain transitions
- Rotate planet freely
- Show trade routes as great-circle arcs between cells

---

## Navigation / UI Pattern

All demos use a common navigation pattern:

```java
// In each demo's initialize():
UIComposite ui = new UIComposite();
ui.addLayer(new Scene3DOverlayLayer(context));  // wireframe rendering
ui.addLayer(new GPUDrivenTextLayer(context));   // stats/labels
ui.addLayer(new DemoControlLayer());            // sliders, toggles, dropdown

// Camera: orbit by default, WASD+mouse for free-fly demos
OrbitCamera camera = new OrbitCamera(center, distance, fov);
// or
FreeFlyCamera camera = new FreeFlyCamera(position, yaw, pitch);

// Input: left-drag rotates, right-drag pans, scroll zooms
// Middle-click spawns objects, etc.
```

---

## Implementation Priority

1. **Demo 17 (Playground)** — most reusable, tests everything
2. **Demo 10 (Marching Cubes)** — visually impressive, demonstrates mesh generation
3. **Demo 8 (Hex Grid)** — immediately useful for game prototyping
4. **Demo 1 (Octree)** — fundamental visualization of tree behavior
5. **Demo 9 (Geodesic)** — visually striking, unique
6. **Demo 16 (Chunk Streaming)** — demonstrates integration story
7. Remaining in any order
