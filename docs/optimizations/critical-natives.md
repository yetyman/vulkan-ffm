# Critical Natives

Niche FFM performance optimization: `Linker.Option.critical(false)` removes the JVM's
per-downcall safepoint-check overhead for `MethodHandle` invocations. For a hot render/compute
loop calling thousands of Vulkan functions per second, this overhead is measurable. This document
describes how VulkanFFM applies it.

## What critical(false) actually does

`Linker.Option.critical(boolean allowHeapAccess)` marks a downcall as a "critical" native call.
The JVM does not check for a safepoint request while the native call is executing, avoiding the
per-call safepoint-check cost that ordinary downcalls pay. The `allowHeapAccess` parameter
(always `false` in this codebase) controls whether the call may receive Java heap-array
arguments directly; `false` is both the safer and faster mode, and is a natural fit here since
every call in this codebase already marshals arguments into off-heap `MemorySegment`s via
`Arena`-backed allocation (see `BumpAllocator`, `VkArrayBuilder`) rather than passing raw Java
arrays into downcalls.

Two hard constraints apply regardless of GC:
- **No upcalls.** A critical native can never invoke a Java callback. Any function that fires a
  synchronous Java callback during the call (debug/validation callback registration) must never
  be made critical.
- **Blocking duration matters more under non-concurrent GCs.** A critical native prevents the
  calling thread from reaching a safepoint while it's blocked. Under ZGC (this project's
  recommended/default GC — see root `README.md`), safepoints are already sub-millisecond, so a
  microsecond-to-millisecond blocking critical native is not a practical concern. Under
  stop-the-world collectors (G1, Parallel GC), a long-blocking critical native can measurably
  delay a GC pause. This project does not attempt to detect or warn about GC choice; it is the
  embedding application's responsibility to pick an appropriate GC (ZGC recommended) if opting
  into the conditionally-critical call sites described below.

## Three-tier classification

Every Vulkan function falls into exactly one of three tiers. The tier is a property of the
function itself (does it fire callbacks; can it block for a caller-dependent duration), not a
runtime choice — with one exception, see "Conditionally critical" below.

### 1. Always critical (the default; no code visible for this)

The vast majority of Vulkan functions (~2900+, e.g. `vkCmdDraw`, `vkCmdBindPipeline`,
`vkCmdPushConstants`, `vkCmdSetViewport`, `vkCmdDispatch`) are neither callback-bearing nor
meaningfully blocking. `CriticalNativeInjector` (see below) makes these critical unconditionally,
once, at binding-generation time. There is no opt-out, no dual handle, and no extra indirection —
the call site cost is identical to a plain generated downcall.

### 2. Hard-excluded — never critical, no opt-in exists

Functions that register a debug/validation callback: `vkCreateDebugUtilsMessengerEXT`,
`vkDestroyDebugUtilsMessengerEXT`, `vkCreateDebugReportCallbackEXT`,
`vkDestroyDebugReportCallbackEXT`. These fire synchronous Java upcalls during Vulkan's internal
validation dispatch. Critical natives can never support upcalls under any circumstance, so no
opt-in critical variant exists or should ever be added for these. See
`CriticalNativeInjector.HARD_EXCLUDED`.

### 3. Conditionally excluded — opt-in critical variant available per call site

Functions that may block for a duration that depends on call-site arguments, driver behavior, or
OS state: CPU-blocking waits (`vkWaitForFences`, `vkWaitSemaphores`, `vkWaitSemaphoresKHR`,
`vkDeviceWaitIdle`, `vkQueueWaitIdle`), swapchain acquire (`vkAcquireNextImageKHR`,
`vkAcquireNextImage2KHR`), queue submit/present/bind (`vkQueueSubmit`, `vkQueueSubmit2`,
`vkQueueSubmit2KHR`, `vkQueuePresentKHR`, `vkQueueBindSparse`), allocation
(`vkAllocateMemory`), pipeline/shader-module creation (`vkCreateGraphicsPipelines`,
`vkCreateComputePipelines`, `vkCreateRayTracingPipelinesKHR`, `vkCreateRayTracingPipelinesNV`,
`vkCreateShaderModule`), `vkDeferredOperationJoinKHR`, `vkGetQueryPoolResults`,
`vkLatencySleepNV`, and memory mapping (`vkMapMemory`, `vkMapMemory2`, `vkMapMemory2KHR`). See
`CriticalNativeInjector.CONDITIONALLY_EXCLUDED`.

These are left as plain (non-critical) downcalls in the bulk generated `VulkanFFM_*.java` files —
existing callers of e.g. `Vulkan.waitForFences(...)` are completely unaffected. A parallel
opt-in critical variant exists for each in `io.github.yetyman.vulkan.generated.VulkanFFMCritical`
(e.g. `vkWaitForFencesCritical(...)`).

This tier is deliberately a **call-site decision, not a global one**. The actual blocking risk
for these functions depends heavily on the arguments passed and the surrounding context, e.g.:
- `vkAcquireNextImageKHR` with `timeout=0` (poll) vs `timeout=UINT64_MAX` (block indefinitely)
- `vkQueueSubmit` on a lightly-loaded queue (near-instant) vs a saturated one (can stall)
- `vkQueuePresentKHR` on `VK_PRESENT_MODE_FIFO_KHR` (can block a full vsync interval) vs
  `VK_PRESENT_MODE_MAILBOX_KHR`/`IMMEDIATE_KHR` (effectively non-blocking)
- `vkCreateGraphicsPipelines` compiling one trivial pipeline vs a large batch (can take seconds)

Because the same function can be safe or risky depending on how a specific call site uses it, the
library exposes both variants and lets the caller choose, rather than picking one blanket answer.
As a rule of thumb: prefer the critical variant only for calls with a short, bounded, or
near-instant expected blocking duration in that call site's actual usage; use the plain variant
for anything with unbounded or driver-dependent blocking risk (e.g. FIFO present, unbounded
fence waits, large pipeline batches).

## VulkanFFMCritical implementation notes

`VulkanFFMCritical` (`vulkan-bindings/src/main/java/io/github/yetyman/vulkan/generated/`) is
hand-written, not generated by jextract — it is not touched by binding regeneration.

For each of the ~23 conditionally-excluded functions:
- Two downcall handles are created once, at class-init: `HANDLE_NORMAL` (plain linkage) and
  `HANDLE_CRITICAL` (`Linker.Option.critical(false)`).
- A mutable, non-final `ACTIVE` field defaults to `HANDLE_CRITICAL`.
- The public `*Critical(...)` method reads `ACTIVE` directly and calls `invokeExact` on it — a
  single field read plus the call, identical call-site cost to a plain generated downcall. No
  branch, no `Supplier`, no synchronization on the hot path.
- Each nested per-function holder class independently resolves its own symbol and registers
  itself into a shared `CopyOnWriteArrayList` from its own static initializer, the first time
  that specific function is actually referenced. **Critically, `VulkanFFMCritical`'s own
  `<clinit>` does not reference any per-function holder class directly** — doing so would force
  that class to initialize immediately, and a resolution failure in any one holder's `<clinit>`
  (e.g. a symbol not resolvable via static `SymbolLookup` on some driver/platform — this has been
  observed for `vkWaitSemaphoresKHR`, which may require `vkGetInstanceProcAddr`-style dynamic
  lookup rather than static linking on some drivers) would otherwise poison the entire class
  (`NoClassDefFoundError` on every later reference), silently breaking every unrelated function in
  the file. Keeping the outer class free of such references means one bad symbol only breaks that
  one function.

### Global panic switch

`VulkanFFMCritical.disableCriticalOverrides(boolean disable)` iterates the registry and swaps
every registered holder's `ACTIVE` field between `HANDLE_NORMAL`/`HANDLE_CRITICAL`. This is a
cold-path operation (expected to be called rarely, e.g. once at startup or when toggling a debug
mode) and does not attempt to be fast; it exists purely as an operational escape hatch — e.g. if
an application opted into several `*Critical` call sites broadly and later discovers the
aggregate safepoint-delay risk under its chosen GC is intolerable in production, this flips
everything back to safe linkage without requiring call-site code changes.

Note the registry only contains holders that have actually been referenced at least once. A
holder for a function never called yet is not present in the registry and starts in the default
critical-active state whenever it does first initialize; `disableCriticalOverrides` does not
retroactively affect holders that initialize after the call.

## Performance characteristics

Verified during implementation:
- **Correctness**: `FunctionDescriptor` argument layouts must exactly match the real
  jextract-generated descriptors for the same function. A `long`-typed Java parameter backed by a
  64-bit Vulkan value (`uint64_t`/`VkDeviceSize`/`size_t`, e.g. `timeout`, `offset`, `size`,
  `dataSize`, `stride`) must use `VulkanFFM.C_LONG_LONG`, not `VulkanFFM.C_LONG` — on Windows
  (LLP64), C's `long` is 32-bit, so `C_LONG` produces a mismatched descriptor and throws
  `WrongMethodTypeException` at the first real call. Always cross-check against the actual
  generated descriptor for the same function rather than hand-deriving argument layouts from the
  Vulkan spec signature alone.
- **Hot-path cost with critical linkage active**: profiling in a real render-graph frame loop
  (`RenderGraphExecutor` → per-node command recording, `GraphicsFrame.drawFrame`) showed
  essentially unchanged frame time after swapping `vkWaitForFences`/`vkQueueSubmit`/
  `vkAcquireNextImageKHR` call sites to their critical variants, versus the plain variants.
  The dominant hot-path FFM cost observed is `VarHandleInts$FieldInstanceReadOnly.getVolatile` /
  `VarHandleGuards.guard_L_I` / `SharedSession.release0` — the FFM API's own per-call
  `MemorySegment`/`Arena` liveness check, paid on essentially every downcall regardless of
  critical linkage. Critical natives skip the JVM's own safepoint-check overhead; they do not
  skip or reduce this arena-liveness bookkeeping. See "Not addressed by this optimization" below.

## Call sites currently wired to critical variants

- `VkFenceOps.Builder.executeCritical(Arena)` → used by `GraphicsFrame`'s primary per-frame
  in-flight fence wait (bounded/near-instant in the common case).
- `VkSubmit.queueSubmitCritical(...)` → used by `MutexSubmitter.submit(...)` (the queue-submit
  path for locked/shared queues).
- `VkSwapchainOps.AcquireBuilder.executeCritical(Arena)` → used by `GraphicsFrame`'s per-frame
  swapchain image acquire.
- `VkPresent.Builder.presentCritical(MemorySegment, Arena)` → implemented and available, but
  **not** wired into `GraphicsFrame`'s default present call site, because `VkSwapchain`'s default
  present mode is `VK_PRESENT_MODE_FIFO_KHR` (vsync-gated, can block a full frame interval).
  Applications using mailbox/immediate present modes may safely opt into this at their own
  present call site.