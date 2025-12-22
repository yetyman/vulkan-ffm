# Future Features

## CPU-Side Culling System
- **Frustum culling**: Remove objects outside camera view
- **Occlusion culling**: Remove objects behind others  
- **Distance culling**: Remove objects too far away
- **Requirements**: CPU mirrored positions and boundaries
- **Dependencies**: Improved model abstraction needed first

## Spatial Partitioning
- **Octree/BVH**: Hierarchical scene organization
- **Spatial queries**: Fast nearest neighbor, range queries
- **Dynamic updates**: Handle moving objects efficiently

## Advanced LOD System
- **Geometric LOD**: Multiple mesh resolutions per model
- **Texture LOD**: Mipmap selection based on distance
- **Shader LOD**: Simplified shaders for distant objects

## Multi-Queue Vulkan
- **Graphics Queue**: Primary rendering
- **Compute Queue**: Parallel compute shaders
- **Transfer Queue**: Async texture/buffer uploads
- **Requirements**: GPU with multiple queue families