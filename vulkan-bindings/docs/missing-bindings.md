# Missing jextract bindings

Tracking note, not a fix. Some core Vulkan functions (declared plainly in `vulkan_core.h`, no
extension guard) are absent from both:

1. jextract's static output in `VulkanFFM.java`, and
2. the `DynFunctionGenerator` fallback that scans `vk.xml` for anything jextract missed and writes
   it into `VkDeviceFunctions.java` / `VkInstanceFunctions.java`.

Confirmed missing (verified against `C:\VulkanSDK\1.4.335.0\Include\vulkan\vulkan_core.h` on the
machine this was found on):

- `vkCmdDrawIndexedIndirectCount` (declared ~line 6918, core Vulkan 1.2)
- `vkCmdFillBuffer` (declared ~line 4868, core Vulkan 1.0)
- `vkCmdDrawIndexedIndirect` (declared ~line 5254, core Vulkan 1.0) - not yet needed, not yet added
- `vkCmdUpdateBuffer` (declared ~line 4861, core Vulkan 1.0) - not yet needed, not yet added

Not missing, for contrast (present in `VulkanFFM.java` as normal jextract output):

- `vkCmdDrawIndexed`, `vkCmdDraw`, `vkCmdDispatch`, `vkCmdCopyBuffer` - ordinary jextract output
- `vkCmdDrawMeshTasksEXT`, `vkCmdDrawMeshTasksIndirectEXT`, `vkCmdDrawMeshTasksIndirectCountEXT` -
  jextract bound the entire mesh-shader indirect-count family correctly, including the exact
  six-parameter shape (`buffer, offset, countBuffer, countBufferOffset, maxDrawCount, stride`) that
  `vkCmdDrawIndexedIndirectCount` also has

That last point is the strongest clue so far: jextract clearly can bind this parameter shape
correctly (it did, for the EXT mesh-shader variant), so the gap isn't jextract choking on the
signature shape itself. Something else about these specific four functions - core (non-suffixed)
naming, header ordering, or a jextract include/exclude filter behavior - is causing them to be
dropped. Not diagnosed further; whoever picks this up next should look at:

- Whether `--include-symbol` or similar jextract filtering flags are in play anywhere in
  `generate-vulkan-win32-bindings.bat` that might exclude by name pattern
- Whether these four functions share a `#ifdef` guard block in the header that differs from the
  guard around the functions that did get bound
- Whether jextract has an emitted warning/log during generation (not currently captured by the
  `.bat` scripts) that names skipped symbols
- Whether re-running jextract fresh (current `.bat` scripts, current SDK) reproduces the gap, or
  whether it was a stale/partial previous generation that never got re-run

## Current workaround

Four (well, two so far - see `vulkan-ffm-mesh` GeometryTable work) of these have been hand-added
directly to `VulkanFFM.java`, structurally copying the exact jextract-generated pattern of a sibling
function with the same parameter shape, with a comment on each explaining why and noting that a
future regeneration picking the symbol up natively will surface as a duplicate-symbol compile error
pointing straight at the hand-added block (intentional self-diagnosis, not a bug to suppress).

Search `VulkanFFM.java` for `Hand-supplemented, not jextract-generated` to find all such blocks.

Do not add more of these reactively without checking this file first - if the same four (or a fifth)
symbol is needed again, it is almost certainly the same underlying gap, not a new one.
