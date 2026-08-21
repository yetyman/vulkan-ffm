# Development Guidelines

## Design Philosophy

- **Complete wrappers**: The core vulkan-core abstractions are expected to be complete API wrappers on Vulkan constructs or tight sets of functionality. If a wrapper class is missing functions that logically belong to it, add them — do not leave gaps.
- **Proactive completeness**: When working in vulkan-core, if a class lacks functionality that logically belongs to it, update it to add that functionality as part of the work. Don't leave a class half-wrapped.
- **Unbiased general-purpose library**: The goal of all vulkan-core work is to advance the library while keeping it viable for both beginners and experts in any application domain. Avoid baking in assumptions about rendering style, application structure, or use case. Prefer flexible, composable primitives over opinionated helpers.
- **Composition over inheritance for orthogonal concerns**: When a class combines multiple independent concerns (e.g. where memory lives vs. how data moves, or allocation strategy vs. IO strategy), prefer composition of strategy objects over inheritance. Inheritance forces a fixed combination of concerns and creates friction when one axis needs to vary independently. Use the Strategy Pattern — hold strategy interfaces as fields and compose them freely.
  - **Implied Sub System Separation**: To ensure that all major subsystems(Node-Tree, Frame-Graph, Pure Math libraries, etc) can all be composed and replaced, we should segregate them to well defined self contained modules. This will force their public API surface to expose every hook necessary for alternatives to be created and force a lower level to define interfaces for anything that must be shared.  
- **Fluent builders for all direct Vulkan wrappers**: Every class that directly wraps a Vulkan object or operation must expose a static `builder()` factory returning a fluent builder. Raw constructors are private. This applies to all `Vk*.java` wrappers and operation classes. The builder is the only public creation path.

---

## Code Organization

### Package Rules
- Never use wildcard imports from the project itself: `import io.github.yetyman.vulkan.*` is FORBIDDEN
- Always use single explicit imports, e.g. `import io.github.yetyman.vulkan.VkDevice;`
- Generated bindings live in `io.github.yetyman.vulkan.generated` — never edit these files
- Win32-specific generated types live in `io.github.yetyman.vulkan.generated.win32`
- shaderc bindings in `io.github.yetyman.shaderc.generated` / `io.github.yetyman.shaderc.enums`
- SPIRV-Reflect bindings in `io.github.yetyman.spirv.generated` / `io.github.yetyman.spirv.enums`
- Hand-written wrappers live in `io.github.yetyman.vulkan` (top-level) and sub-packages

### Standard Imports for Core Wrappers
```java
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
```
The `enums.*` and `generated.*` wildcards are acceptable since they are external packages, not the project's own top-level package.

---

## Wrapper Class Pattern (Vk*.java)

Every Vulkan object wrapper follows this exact structure:

### 1. Fields — private final, minimal
```java
private final MemorySegment handle;
private final VkDevice device;   // owner device, if applicable
private final long size;          // any extra state needed
```

### 2. Private constructor
```java
private VkFoo(MemorySegment handle, VkDevice device) {
    this.handle = handle;
    this.device = device;
}
```

### 3. Static factory methods
```java
/** @return a new builder for configuring foo creation */
public static Builder builder() { return new Builder(); }

/** Wraps an existing handle */
public static VkFoo wrap(MemorySegment handle) { return new VkFoo(handle, null); }
```

### 4. Accessors — single-line, no-prefix (not getX, just x())
```java
public MemorySegment handle() { return handle; }
public VkDevice device() { return device; }
```

### 5. AutoCloseable — always implemented
```java
@Override
public void close() {
    Vulkan.destroyFoo(device.handle(), handle);
}
```
For objects with device memory, free memory before destroying the object:
```java
@Override
public void close() {
    if (memory != null && !memory.equals(MemorySegment.NULL)) {
        Vulkan.freeMemory(device.handle(), memory);
    }
    Vulkan.destroyBuffer(device.handle(), handle);
}
```

### 6. Static inner Builder class
```java
public static class Builder {
    private VkDevice device;
    private int flags = 0;
    // ... other fields with sensible defaults

    private Builder() {}

    public Builder device(VkDevice device) { this.device = device; return this; }
    public Builder flagBit() { this.flags |= VkFooFlagBits.VK_FOO_BIT.value(); return this; }

    public VkFoo build(Arena arena) {
        if (device == null) throw new IllegalStateException("device not set");
        // allocate struct, set fields, call Vulkan.createFoo(...).check()
        // return new VkFoo(...)
    }
}
```

---

## Operation Wrapper Pattern (VkSubmit, VkPresent, VkFenceOps, etc.)

Stateless utility classes for common Vulkan operations that don't own a handle:

```java
public class VkSubmit {
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        // fluent setters accumulating arrays
        public Builder waitSemaphore(MemorySegment sem, int stage) { ... return this; }
        public Builder commandBuffer(VkCommandBuffer cmd) { ... return this; }
        public Builder signalSemaphore(MemorySegment sem) { ... return this; }

        // terminal method allocates struct and executes
        public VkResult submit(MemorySegment queue, MemorySegment fence, Arena arena) { ... }
    }
}
```

Key conventions:
- No instances — utility class with static `builder()` or static factory methods
- Builder accumulates parameters, terminal method takes `Arena` and executes
- `VkPushConstants` uses static factories: `floatValue(value, stageFlags, arena)`, `intValue(...)`, etc.
- `VkViewport`, `VkRect2D`, `VkClearValue` return `MemorySegment` from `build(Arena)`

---

## Barrier Pattern

```java
public abstract class VkBarrier {
    protected final MemorySegment handle;
    public abstract BarrierType getType();
    public void execute(MemorySegment commandBuffer, int srcStage, int dstStage) { ... }
}

// Concrete: VkMemoryBarrier, VkBufferBarrier, VkImageBarrier
VkBufferBarrier barrier = VkBufferBarrier.builder()
    .buffer(bufferHandle)
    .srcAccess(VK_ACCESS_TRANSFER_WRITE_BIT)
    .dstAccess(VK_ACCESS_SHADER_READ_BIT)
    .build(arena);
barrier.execute(commandBuffer, srcStage, dstStage);
```

---

## Shader System Patterns

### Compilation + Reflection
```java
// One-shot compile from classpath resource
CompiledShader compiled = ShaderLoader.compileShader("/shaders/model.vert");

// Builder with defines and optimization
CompiledShader compiled = ShaderLoader.builder("/shaders/model.frag")
    .define("ENABLE_SHADOWS", "1")
    .optimize()
    .compileShader();

// Inline source
CompiledShader compiled = ShaderLoader.fragment()
    .source(glslString)
    .compileShader();
```

### ShaderInstance — runtime binding
```java
try (ShaderInstance inst = compiled.createInstance(device)) {
    PushConstant<Float> time = inst.getPushConstant("time", Float.class);
    UniformBufferSlot camera = inst.getUniformBufferSlot("camera");
    StorageBufferSlot lights = inst.getStorageBufferSlot("lightBuf");
    TextureSlot albedo = inst.getTextureSlot("albedo");

    time.set(1.5f);           // marks dirty
    camera.set(cameraBuffer); // marks dirty
    inst.flush(commandBuffer); // writes dirty push constants + descriptor updates
}
```

### Specialization Constants
```java
// Via ShaderInstance builder
ShaderInstance inst = compiled.instanceBuilder(device)
    .specialize("enableFog", true)
    .specialize("fogDensity", 0.02f)
    .specialize("maxLights", 8)
    .build();
MemorySegment specInfo = inst.buildSpecializationInfo(arena); // for pipeline creation

// Via VkComputePipeline builder
VkComputePipeline pipeline = VkComputePipeline.builder()
    .device(device)
    .computeShader(spirvBytes)
    .specialize(0, 5)  // constant_id=0, value=5
    .build(arena);
```

### DescriptorGroup — one-shot descriptor setup
```java
// Manual layout
try (DescriptorGroup group = DescriptorGroup.builder()
        .device(device)
        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
        .storageBuffer(0, inputBuf)
        .storageBuffer(1, outputBuf)
        .build(arena)) {
    group.set().bind(commandBuffer, pipeline, 0, arena);
}

// Reflection-driven layout
try (DescriptorGroup group = compiled.descriptorGroup(device)
        .buffer(0, inputBuf)
        .buffer(1, outputBuf)
        .build(arena)) { ... }
```

### ShaderGenerator — code generation
```java
// CLI
ShaderGenerator /shaders/model.vert outputDir io.example.shaders
ShaderGenerator --dir shaders/ outputDir io.example.shaders

// API
ShaderGenerator.generate("/shaders/model.vert", outputDir, "io.example.shaders");
ShaderGenerator.generate(compiledShader, resourcePath, outputDir, "io.example.shaders");
```

Generated class shape:
```java
public class ModelVertShader implements AutoCloseable {
    public final ShaderInstance shader;
    public final PushConstant<Float> time;
    public final UniformBufferSlot camera;
    // ... spec constant fields, define fields, BufferWritable records, Descriptors helper
    public static Builder builder(VkDevice device) { ... }
}
```

---

## Buffer System Patterns

### ManagedBuffer via BufferFactory
```java
ManagedBuffer buf = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, size, BufferUsage.STORAGE, device, queue);
buf.write(byteBuffer, offset, queue);
buf.writeAsync(byteBuffer, offset, queue).onComplete(() -> slot.set(buf));
ByteBuffer data = buf.read(offset, length);
```

### TypedVkBuffer — typed array view
```java
public class MyVertex implements BufferWritable {
    public float[] position; // vec3
    public float[] color;    // vec4
    @Override public int byteSize() { return 28; }
    @Override public void writeTo(ByteBuffer buf) { ... }
    @Override public void readFrom(ByteBuffer buf) { ... }
}

TypedVkBuffer<MyVertex> vertices = new TypedVkBuffer<>(buffer, 28, 1000, true) {
    @Override protected MyVertex getInstance() { return new MyVertex(); }
};
vertices.write(0, vertex, queue);
MyVertex v = vertices.read(0); // zero-cost from mirror
```

### Primitive typed buffers
```java
FloatVkBuffer floats = new FloatVkBuffer(buffer, 1024);
floats.write(new float[]{1.0f, 2.0f, 3.0f}, 0, queue);
float[] data = floats.read(0, 3);
```

### Transfer batching
Transfers are automatically batched per-thread per-queue via `TransferBatchManager`:
```java
TransferCompletion tc = buffer.writeAsync(data, offset, queue); // recorded into batch
tc.flush(device, queue); // submits the batch
tc.await(); // waits for GPU completion
tc.onComplete(() -> { ... }); // callback on completion
```

---

## Builder Conventions

- Builder constructor is always `private Builder() {}`
- `build(Arena arena)` always takes an `Arena` parameter
- Validate required fields at the top of `build()` with `IllegalStateException`
- Flag-setting methods use `|=` to accumulate bits, named after the flag concept (e.g. `transientBit()`, `resetCommandBufferBit()`, `vertexBuffer()`, `hostVisible()`)
- Fluent setters always `return this`
- Javadoc on each setter: `/** Sets the ... */` or `/** Enables ... */`

---

## FFM / Native Memory Patterns

### Struct allocation
Always use the generated struct's static `allocate(arena)`:
```java
MemorySegment poolInfo = VkCommandPoolCreateInfo.allocate(arena);
VkCommandPoolCreateInfo.sType(poolInfo, VkStructureType.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO.value());
VkCommandPoolCreateInfo.pNext(poolInfo, MemorySegment.NULL);
```
Always set `sType` and `pNext` explicitly.

### Pointer-out pattern (handle retrieval)
```java
MemorySegment fooPtr = arena.allocate(ValueLayout.ADDRESS);
Vulkan.createFoo(device.handle(), createInfo, fooPtr).check();
MemorySegment foo = fooPtr.get(ValueLayout.ADDRESS, 0);
```

### Array allocation
```java
MemorySegment names = arena.allocate(ValueLayout.ADDRESS, count);
for (int i = 0; i < count; i++) {
    names.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(strings[i]));
}
```

### Null allocator convention
All Vulkan create/destroy calls pass `MemorySegment.NULL` as the allocator (pAllocator):
```java
VulkanFFM.vkCreateFoo(device, createInfo, MemorySegment.NULL, fooPtr);
VulkanFFM.vkDestroyFoo(device, foo, MemorySegment.NULL);
```

### Memory mapping
```java
MemorySegment mappedPtr = arena.allocate(ValueLayout.ADDRESS);
Vulkan.mapMemory(device.handle(), memory, 0, size, 0, mappedPtr).check();
MemorySegment mapped = mappedPtr.get(ValueLayout.ADDRESS, 0).reinterpret(size, arena, null);
// ... use mapped ...
Vulkan.unmapMemory(device.handle(), memory);
```

### Native calloc for oversized opaque structs
```java
// Used by ShaderReflection for SpvReflectShaderModule
MemorySegment moduleRaw = nativeCalloc(1, SpvReflectShaderModule.sizeof());
try {
    MemorySegment module = moduleRaw.reinterpret(SpvReflectShaderModule.sizeof(), arena, null);
    // ... use module ...
} finally {
    nativeFree(moduleRaw);
}
```

---

## Error Handling

- All Vulkan calls that return `int` are wrapped by `Vulkan.*` static methods returning `VkResult`
- Call `.check()` immediately when failure should throw:
  ```java
  Vulkan.createBuffer(device.handle(), bufferInfo, bufferPtr).check();
  ```
- `VkResult.check()` throws `VulkanException` (unchecked) if `value < 0`
- For optional/recoverable results, compare directly: `if (result == VkResult.VK_SUCCESS)`

---

## Vulkan Static Facade (Vulkan.java)

- `Vulkan` is a pure static utility class — no instances
- Static initializer loads the native library: `static { VulkanLibrary.load(); }`
- Every method wraps exactly one `VulkanFFM.*` call
- Methods that return void in Vulkan are void in Java
- Methods that return `VkResult` in Vulkan return `VkResult` in Java
- API version constants defined as: `public static final int VK_API_VERSION_1_X = makeVersion(1, X, 0);`

---

## Generated Bindings (Do Not Edit)

Generated files follow jextract conventions:
- Header comment: `// Generated by jextract`
- Typedef aliases extend their base class with a private no-arg constructor
- All imports are present even if unused (jextract output)
- Static imports: `import static java.lang.foreign.ValueLayout.*;` and `import static java.lang.foreign.MemoryLayout.PathElement.*;`

---

## Javadoc Style

- Class-level: one sentence describing what Vulkan object is wrapped
- Method-level: `/** @return ... */` for accessors, `/** Sets the ... */` for builder setters
- `@param` and `@return` tags used on non-trivial public methods
- No Javadoc on private methods or trivial one-liners
- C snippet shown in generated classes via `{@snippet lang=c : ... }`

---

## Naming Conventions

| Concept | Convention | Example |
|---|---|---|
| Wrapper classes | `Vk` prefix matching Vulkan name | `VkBuffer`, `VkCommandPool` |
| Handle accessor | `handle()` | `device.handle()` |
| Builder factory | `builder()` static method | `VkBuffer.builder()` |
| Flag-setting builder methods | Descriptive, no "set" prefix | `vertexBuffer()`, `hostVisible()`, `transientBit()` |
| Enum value access | `.value()` method | `VkStructureType.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO.value()` |
| Generated class names | Match C typedef exactly | `VkBufferCreateInfo`, `REGISTERWORD` |
| Operation wrappers | `Vk` prefix + noun | `VkSubmit`, `VkPresent`, `VkFenceOps` |
| Shader slot types | Descriptor type suffix | `StorageBufferSlot`, `UniformBufferSlot`, `TextureSlot` |
| Typed buffers | Type prefix + `VkBuffer` | `FloatVkBuffer`, `IntVkBuffer` |

---

## Resource Lifecycle

- Prefer `try-with-resources` with `Arena.ofConfined()` for scoped lifetimes
- `Arena.ofShared()` for cross-thread resources (TransferBatch, SparsePageAllocator)
- `Arena.global()` only for truly long-lived handles (e.g. queue handles, command pool registry)
- All wrapper classes implement `AutoCloseable` — always close in reverse creation order
- `VulkanContext.close()` calls `deviceWaitIdle` before destroying device
- `VkDevice.close()` calls `TransferBatchManager.destroyAll()` and `CommandPoolRegistry.destroyAll()`
- Builders do not own the Arena — callers manage Arena lifetime
- `CompiledShader` and `ShaderInstance` are AutoCloseable — close descriptor layouts/pools

---

## High-Level Framework Usage

```java
// Application lifecycle
public class MyApp extends VulkanApplication {
    public MyApp() { super("MyApp", 800, 600, new GLFWWindowSystem(), new GLFWInputSystem()); }
    @Override protected void initialize() { /* create renderer */ }
    @Override protected void render() { /* draw frame */ }
    @Override protected void onResize(int w, int h) { /* resize */ }
    @Override protected void shutdown() { /* cleanup */ }
}

// GraphicsRenderer subclass
public class MyRenderer extends GraphicsRenderer {
    public MyRenderer(Arena arena, VkDevice device, MemorySegment queue, MemorySegment surface, int w, int h) {
        super(arena, device, queue, surface, w, h, 2); // 2 frames in flight, auto-detects dynamic rendering
    }
    @Override
    protected void recordCommandBuffer(VkCommandBuffer cmd, int imageIndex, Arena frameArena) {
        VkRendering.builder()
            .renderArea(0, 0, width, height)
            .colorAttachment(swapchainImageViews[imageIndex].handle(),
                VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value(),
                VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value(), 0, 0, 0, 1)
            .begin(cmd.handle(), frameArena);
        // draw calls...
        VkRendering.end(cmd.handle());
    }
}

// GraphicsLoop
GraphicsLoop loop = GraphicsLoop.builder()
    .renderer(renderer)
    .driver(LoopDriver.uncapped())
    .shouldClose(() -> glfwWindowShouldClose(window))
    .pollEvents(() -> glfwPollEvents())
    .onResize(dims -> renderer.resize(dims[0], dims[1]))
    .onFpsUpdate(fps -> System.out.println("FPS: " + fps))
    .build();
loop.runOnCurrentThread();

// Compute pipeline
try (VkComputePipeline pipeline = VkComputePipeline.builder()
        .device(device)
        .computeShader(spirvBytes)
        .descriptorSetLayouts(group.layoutHandle())
        .pushConstantRange(VK_SHADER_STAGE_COMPUTE_BIT, 0, 4)
        .specialize(0, multiplier)
        .build(arena)) {
    pipeline.dispatchAndWait(queue, descriptorSet, groupCountX);
}

// TransientCommandBuffer
TransientCommandBuffer.execute(commandPool, queue, arena, cmd -> {
    cmd.copyBuffer(srcBuffer, dstBuffer, size);
});

// CommandManager (threaded)
CommandManager cmdMgr = CommandManager.builder()
    .context(vulkanContext)
    .queueFamilyIndex(graphicsFamily)
    .threaded(true)
    .build();
```

---

## Multi-Module Import Rules

- `vulkan-core` code imports from `io.github.yetyman.vulkan.generated.*`, `io.github.yetyman.shaderc.*`, `io.github.yetyman.spirv.*`
- `vulkan-ffm-graph` imports from `io.github.yetyman.vulkan.*` (core module)
- `vulkan-ffm-node-trees` imports from `io.github.yetyman.vulkan.*` (core module)
- `vulkan-ffm-mesh` imports from `io.github.yetyman.vulkan.*` (core module), `io.github.yetyman.helpers.*`
- `vulkan-ffm-mesh-processing` imports from `io.github.yetyman.vulkan.mesh.*`, `io.github.yetyman.vulkan.*` (core module)
- `vulkan-ffm-sample-ui-layers` imports from `io.github.yetyman.vulkan.nodetree.*`, `io.github.yetyman.vulkan.ui.*`, `io.github.yetyman.vulkan.mesh.*`, `io.github.yetyman.vulkan.*`
- `sample-app` imports from all of the above plus `io.github.yetyman.glfw.generated.*`
- Never import `sample-app` classes from any library module
- Never import `vulkan-core` internal implementation details beyond the public API
- `vulkan-ffm-mesh` must NOT depend on `vulkan-ffm-graph`, `vulkan-ffm-node-trees`, `vulkan-ffm-sample-ui-layers`, or `sample-app`
