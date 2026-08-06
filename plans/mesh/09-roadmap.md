# Roadmap

Each phase states what is built, what it proves, and what would falsify the design. A phase that
cannot state what it proves is a phase that should be merged into another one.

The falsification criteria matter more than the deliverables. They are the checks that catch a wrong
interface while it is still cheap to change.

---

## Phase 0: Write the plan

Deliverable: this directory.

Proves: nothing technical. Its purpose is that the axes and non-goals are fixed before code exists, so
that scope creep is visible as a contradiction with a written document rather than as a gradual drift.

Status: complete with this commit.

---

## Phase 1: vulkan-core prerequisites

Deliverable: everything in `00-prerequisites.md`.

- Extract `GpuCompletion` interface.
- Fix the `TransferBatch` fence-reuse race.
- `GpuLayout` becomes `MemorySegment` plus explicit offset; delete `BufferWritable` in favor of
  `HasGpuLayout`; update `helpers-core` math and spatial.
- `IBuffer.acquireWrite` and `acquireRead` scopes, per transfer strategy.
- Bulk and strided primitive paths on the typed buffers.
- Delete `VulkanMesh` and `GLTFLoader`.

Proves: that the buffer system can hand out closest-to-final destination memory and that layouts can be
written in parallel. Both are preconditions for Invariants 3 and 4.

Falsified if: a transfer strategy cannot meaningfully implement `acquireWrite`. Sparse device-local is
the risky one, since it needs page commit before a destination exists. If that turns out to require a
different shape, better to learn it here than after the mesh module is built on it.

Verification: existing `BufferExample` continues to pass; add a copy-count assertion test per strategy;
run with validation layers to confirm the fence race is gone under concurrent `onComplete`.

Note: this phase touches existing working code and has no mesh deliverable. It is still the right first
phase, because every one of these changes is independently valuable and all of them are harder to make
once a mesh module depends on the current shapes.

---

## Phase 2: Layer 0 and Layer 1, CPU only

Deliverable: the `vulkan-ffm-meshes` module with `AttributeSemantic`, `AttributeFormat`, `MeshLayout`,
`AttributeStream`, `IndexStream`, `GeometrySource`, `SegmentGeometrySource`, procedural primitives, and
`MeshLayout.transcodeOps`. Convert `MeshOutput` in `helpers-core` to a `GeometrySource`.

No GPU work at all. Fully unit-testable without a device.

Proves: that the vocabulary can describe interleaved, planar, hybrid, and quantized layouts, and that
transcoding between arbitrary layouts is expressible as strided copies plus converters.

Falsified if: expressing marching cubes output as a `GeometrySource` is awkward. That is the designated
early validation case precisely because it is a real producer with an unusual shape (it generates
vertices and indices incrementally with no native layout).

Verification: unit tests transcoding between every pair of the four layout arrangements, asserting byte
output against hand-computed expectations. Parallel transcode test asserting identical output to
single-threaded.

---

## Phase 3: Layer 3 upload, dedicated allocator only

Deliverable: `GeometryAllocator` with `DedicatedAllocator` and `SlabAllocator`, the `UploadOp` /
`UploadPlan` / `UploadPlanner` / `UploadExecutor` decomposition, `TransferBatchExecutor`,
`ResidencyTracker` with an LRU `EvictionPolicy`.

Instrument heavily per `07-performance-invariants.md`: bytes, copy count, wall time, staging high-water
mark, identity-fast-path hit rate.

Proves: that upload is planned separately from executed, and that the identity-layout fast path
collapses to one flat copy.

Falsified if: `UploadPlanner` needs to know which executor will run the plan. That would mean the plan
is not a sufficient description and the seam in `08-integration-boundaries.md` does not hold.

Verification: assert copy counts per strategy. A `MAPPED` or `REBAR` destination with a matching layout
must show exactly one CPU copy. Assert that a mesh larger than the staging window uploads correctly in
windows.

---

## Phase 4: Layer 2 and Layer 4, first sample

Deliverable: `PrimitiveTopology`, `GeometryPartition`, `PartitionMetadata`, `PartitionSet`,
`NativePartitioning`, `SinglePartition`, `GeometryBinding`, `GeometryDrawRange`,
`IndirectDrawEncoder`, and the thin `Mesh` with its builder.

Sample: a basic mesh layer in `vulkan-ffm-sample-ui-layers` drawing a procedural mesh through the
vertex-input path.

Proves: the whole stack end to end, and that the convenience layer is implementable purely on the
public API.

Falsified if: `Mesh` or its builder needs privileged access to internals to be efficient. That means
the public API of Layers 0 through 3 is incomplete, and the fix goes in the lower layer.

Verification: the sample renders. Add a second sample using the vertex-pulling path with an
oct-encoded normal (a format with no `VkFormat`) to confirm shader-decoded attributes are genuinely
first-class rather than tolerated.

---

## Phase 5: Pool allocator, GeometryTable, GPU-driven sample

Deliverable: `PoolAllocator`, `SparsePoolAllocator`, `GeometryTable` with dirty tracking and attached
metadata channels, a meshlet partitioning interface with a naive reference builder.

Samples: a pooled layer with many meshes and one bind; a GPU-driven layer with compute culling and
`vkCmdDrawIndexedIndirectCount`.

Proves: Invariant 1. This is the phase the whole design is aimed at.

Falsified if: swapping `DedicatedAllocator` for `PoolAllocator` requires any change above the
`GeometryAllocator` interface. If it does, that interface is wrong, and this is the phase that reveals
it. This is the single most important falsification check in the roadmap.

Also falsified if: CPU command-recording time grows with scene size in the GPU-driven sample. That
would mean per-mesh CPU work survives somewhere.

Verification: measure CPU recording time at 100, 10 000, and 100 000 partitions. It must be flat.
Measure allocations per frame in the render path. Must be zero.

---

## Phase 6: Processing and formats

Deliverable: normal and tangent generation, vertex welding, bounds computation in
`vulkan-ffm-meshes`. Simplification and optimized meshlet building interfaces plus the
`vulkan-ffm-meshes-processing` sibling. One format module, most likely glTF, as a bindings module plus
a `GeometrySource` adapter.

Proves: that a real-world asset flows through the whole system, and that a format reader is genuinely
just an adapter.

Falsified if: the glTF adapter needs to add anything to `GeometrySource`. Sparse accessors, interleaved
buffer views with unusual strides, and Draco-compressed primitives are the likely stress points.

Native library policy: discuss before adding any. See `08-integration-boundaries.md`.

---

## Phase 7 and beyond: LOD

Not scheduled. See `06-lod.md`. Do not start until Phases 1 through 6 are complete and the samples
from Phases 4 and 5 are stable.

Before starting, run the five compatibility checks listed at the end of `06-lod.md`. Any that fail get
fixed in the lower layer first.

---

## Testing strategy

### What gets unit tests without a device

Everything in Layers 0 and 1. That is a deliberate design outcome, not an accident: the vocabulary and
transcoding layers touch no Vulkan, so they are testable in a plain JUnit run. Layout transcoding in
particular has exact expected byte output and should be tested exhaustively.

### What gets integration tests with a device

Layers 3 and 4, following the existing `BufferExample` and `ShaderExample` precedent in `vulkan-core` -
a runnable main class that exercises the full surface and reports failures, rather than only assertions.
A `MeshExample` in the same spirit.

### What gets sample apps

Each phase from 4 onward adds a sample layer, because the samples are what prove the abstractions did
not leak. A phase whose sample requires reaching around the public API has failed its own test.

### Instrumentation over guessing

Per the project's code style: when cycling to figure out a root cause, add instrumentation logs and
test rather than iterating on hypotheses. The metrics in `07-performance-invariants.md` should exist
from Phase 3 onward, not be added when something is slow.

---

## Documentation obligations

When each phase lands, the corresponding system-intent documentation goes in
`docs/major-subsystems/meshes/`, following the format of
`docs/major-subsystems/buffers/buffers.md` - overview, package structure, core composition, design
principles, and an explicit known-issues section.

These `plans/mesh/` documents are the plan. The `docs/` versions are the record of what was actually
built, including where it diverged from the plan and why. Divergences are expected and should be
written down rather than quietly absorbed.
