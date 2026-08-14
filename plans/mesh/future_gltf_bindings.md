# Future: glTF Bindings Module

Status: not started. Deferred from Phase 6.

---

## Intent

A separate `gltf-bindings` module (jextract over cgltf or tinygltf) plus a thin `GeometrySource`
adapter in `vulkan-ffm-mesh`.

The adapter must not add anything to `GeometrySource`. If it needs to, that means the source
interface is incomplete and the fix goes in the lower layer.

## Stress points

- Sparse accessors (sparse overrides on dense base data)
- Interleaved buffer views with unusual strides
- Draco-compressed primitives (need decompression before becoming a source)
- Morph targets as multiple accessors on the same primitive
- Multiple texture coordinate sets
- Joint/weight attributes for skinning

## Design constraint

The format adapter is purely a producer. It reads a file and presents its geometry as one or more
`GeometrySource` instances. It does not own allocation, upload, binding, or rendering decisions.
Those belong to the mesh system's upper layers.
