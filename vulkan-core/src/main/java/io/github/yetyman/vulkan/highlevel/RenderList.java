package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Declarative render list for managing multi-pass rendering.
 * Graphics passes automatically handle begin/end using dynamic rendering when available,
 * falling back to render pass + framebuffer on older hardware. Callers never see the difference.
 *
 * <pre>{@code
 * RenderList list = RenderList.builder(device)
 *     .resource("sceneColor", ResourceDesc.color(width, height, VK_FORMAT_B8G8R8A8_SRGB))
 *     .resource("sceneDepth", ResourceDesc.depth(width, height, VK_FORMAT_D32_SFLOAT))
 *
 *     .graphicsPass("geometry")
 *         .write("sceneColor", COLOR_ATTACHMENT)
 *         .write("sceneDepth", DEPTH_ATTACHMENT)
 *         .execute((cmd, resources, arena) -> {
 *             Vulkan.cmdBindPipeline(cmd, GRAPHICS, pipeline.handle());
 *             mesh.draw(cmd);
 *         })
 *
 *     .build();
 *
 * // Each frame — inject the current swapchain image view for BACKBUFFER writes
 * list.execute(commandBuffer, swapchainImageView, frameArena);
 * }</pre>
 */
public class RenderList implements AutoCloseable {
    private final VkDevice device;
    private final Map<String, ResourceDesc> resourceDescs = new LinkedHashMap<>();
    private final List<Pass> passes = new ArrayList<>();

    // Allocated image + view per named resource (not backbuffer)
    private final Map<String, VkImage> allocatedImages = new HashMap<>();
    private final Map<String, VkImageView> allocatedViews = new HashMap<>();

    // Render pass path: lazily created per graphics pass, keyed by pass name
    private final Map<String, VkRenderPass> renderPasses = new HashMap<>();
    // Framebuffers keyed by "passName:swapchainImageViewAddress" — one per swapchain image
    private final Map<String, VkFramebuffer> framebuffers = new HashMap<>();

    private final Arena arena;
    private boolean built = false;

    private RenderList(VkDevice device) {
        this.device = device;
        this.arena = Arena.ofShared();
    }

    public static Builder builder(VkDevice device) {
        return new Builder(device);
    }

    /**
     * Executes all passes for one frame.
     *
     * @param commandBuffer      the primary command buffer, already begun
     * @param swapchainImageView the current frame's swapchain image view, used for BACKBUFFER writes
     * @param frameArena         arena for per-frame allocations
     */
    public void execute(MemorySegment commandBuffer, VkImageView swapchainImageView, Arena frameArena) {
        if (!built) throw new IllegalStateException("RenderList not built");
        for (Pass pass : passes) {
            if (pass.condition != null && !pass.condition.getAsBoolean()) continue;
            pass.execute(commandBuffer, swapchainImageView, frameArena);
        }
    }

    /**
     * Resolves the image view handle for a named resource, substituting the swapchain view for BACKBUFFER.
     */
    private MemorySegment resolveView(String name, VkImageView swapchainImageView) {
        if (BACKBUFFER.equals(name)) return swapchainImageView.handle();
        VkImageView view = allocatedViews.get(name);
        return view != null ? view.handle() : MemorySegment.NULL;
    }

    @Override
    public void close() {
        for (VkFramebuffer fb : framebuffers.values()) fb.close();
        for (VkRenderPass rp : renderPasses.values()) rp.close();
        for (VkImageView view : allocatedViews.values()) view.close();
        for (VkImage image : allocatedImages.values()) image.close();
        framebuffers.clear();
        renderPasses.clear();
        allocatedViews.clear();
        allocatedImages.clear();
        arena.close();
    }

    // ---- Builder ----

    public static class Builder {
        private final RenderList list;

        private Builder(VkDevice device) {
            this.list = new RenderList(device);
        }

        public Builder resource(String name, ResourceDesc desc) {
            list.resourceDescs.put(name, desc);
            return this;
        }

        public PassBuilder graphicsPass(String name) {
            return new PassBuilder(this, name, PassType.GRAPHICS);
        }

        public PassBuilder computePass(String name) {
            return new PassBuilder(this, name, PassType.COMPUTE);
        }

        public PassBuilder pass(String name) {
            return new PassBuilder(this, name, PassType.GENERIC);
        }

        public RenderList build() {
            allocateResources();
            list.built = true;
            return list;
        }

        private void allocateResources() {
            for (Map.Entry<String, ResourceDesc> entry : list.resourceDescs.entrySet()) {
                String name = entry.getKey();
                ResourceDesc desc = entry.getValue();
                if (BACKBUFFER.equals(name)) continue; // injected per-frame
                if (desc.type != ResourceType.IMAGE) continue; // buffers not allocated here

                if (desc.aliasOf != null) {
                    VkImageView aliased = list.allocatedViews.get(desc.aliasOf);
                    if (aliased != null) {
                        list.allocatedViews.put(name, aliased);
                        continue;
                    }
                }

                boolean isDepth = desc.usage == ResourceUsage.DEPTH_ATTACHMENT;
                int imageUsage = isDepth
                        ? VkImageUsageFlagBits.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT.value()
                        : VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT.value()
                          | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT.value();

                VkImage image = VkImage.builder()
                        .device(list.device)
                        .dimensions(desc.width, desc.height, 1)
                        .format(desc.format)
                        .usage(imageUsage)
                        .build(list.arena);

                int aspectMask = isDepth
                        ? VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT.value()
                        : VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value();

                VkImageView view = VkImageView.builder()
                        .device(list.device)
                        .image(image.handle())
                        .format(desc.format)
                        .aspectMask(aspectMask)
                        .build(list.arena);

                list.allocatedImages.put(name, image);
                list.allocatedViews.put(name, view);
            }
        }
    }

    // ---- PassBuilder ----

    public static class PassBuilder {
        private final Builder parent;
        private final Pass pass;

        PassBuilder(Builder parent, String name, PassType type) {
            this.parent = parent;
            this.pass = new Pass(name, type, parent.list);
        }

        public PassBuilder read(String resourceName, ResourceUsage usage) {
            pass.reads.put(resourceName, usage);
            return this;
        }

        public PassBuilder write(String resourceName, ResourceUsage usage) {
            pass.writes.put(resourceName, usage);
            return this;
        }

        public PassBuilder when(BooleanSupplier condition) {
            pass.condition = condition;
            return this;
        }

        /**
         * stub -- parallel execution is not implemented. Setting threadCount > 1 will
         * still execute sequentially on the calling thread. Secondary command buffer
         * recording on worker threads and vkCmdExecuteCommands are not wired up.
         */
        public PassBuilder parallel(int threadCount) {
            pass.threadCount = threadCount;
            return this;
        }

        public Builder execute(GraphicsExecutor executor) {
            pass.graphicsExecutor = executor;
            parent.list.passes.add(pass);
            return parent;
        }

        public Builder dispatch(ComputeExecutor executor) {
            pass.computeExecutor = executor;
            parent.list.passes.add(pass);
            return parent;
        }

        public Builder draw(DrawExecutor executor) {
            pass.drawExecutor = executor;
            parent.list.passes.add(pass);
            return parent;
        }

        public Builder execute(ParallelExecutor executor) {
            pass.parallelExecutor = executor;
            parent.list.passes.add(pass);
            return parent;
        }
    }

    // ---- Pass ----

    private static class Pass {
        final String name;
        final PassType type;
        final RenderList owner;
        final Map<String, ResourceUsage> reads = new LinkedHashMap<>();
        final Map<String, ResourceUsage> writes = new LinkedHashMap<>();
        BooleanSupplier condition;
        int threadCount = 1;
        GraphicsExecutor graphicsExecutor;
        ComputeExecutor computeExecutor;
        ParallelExecutor parallelExecutor;
        DrawExecutor drawExecutor;

        Pass(String name, PassType type, RenderList owner) {
            this.name = name;
            this.type = type;
            this.owner = owner;
        }

        void execute(MemorySegment commandBuffer, VkImageView swapchainImageView, Arena frameArena) {
            if (type == PassType.GRAPHICS) {
                beginGraphics(commandBuffer, swapchainImageView, frameArena);
            }

            if (threadCount == 1) {
                executeSingleThreaded(commandBuffer, swapchainImageView, frameArena);
            } else {
                executeParallel(commandBuffer, swapchainImageView, frameArena);
            }

            if (type == PassType.GRAPHICS) {
                endGraphics(commandBuffer);
            }
        }

        private void beginGraphics(MemorySegment commandBuffer, VkImageView swapchainImageView, Arena frameArena) {
            if (VulkanCapabilities.dynamicRendering) {
                beginDynamic(commandBuffer, swapchainImageView, frameArena);
            } else {
                beginRenderPass(commandBuffer, swapchainImageView, frameArena);
            }
        }

        private void endGraphics(MemorySegment commandBuffer) {
            if (VulkanCapabilities.dynamicRendering) {
                VkRendering.end(owner.device, commandBuffer);
            } else {
                Vulkan.cmdEndRenderPass(commandBuffer);
            }
        }

        private void beginDynamic(MemorySegment commandBuffer, VkImageView swapchainImageView, Arena frameArena) {
            VkRendering.Builder b = VkRendering.builder().device(owner.device);

            // Determine render area from first color attachment's resource desc
            int w = 0, h = 0;
            for (Map.Entry<String, ResourceUsage> entry : writes.entrySet()) {
                ResourceDesc desc = owner.resourceDescs.get(entry.getKey());
                if (desc != null && entry.getValue() == ResourceUsage.COLOR_ATTACHMENT) {
                    w = BACKBUFFER.equals(entry.getKey()) ? desc.width : desc.width;
                    h = BACKBUFFER.equals(entry.getKey()) ? desc.height : desc.height;
                    break;
                }
                if (desc != null) {
                    w = desc.width;
                    h = desc.height;
                }
            }
            b.renderArea(0, 0, w, h);

            for (Map.Entry<String, ResourceUsage> entry : writes.entrySet()) {
                String resName = entry.getKey();
                ResourceUsage usage = entry.getValue();
                ResourceDesc desc = owner.resourceDescs.get(resName);
                MemorySegment view = owner.resolveView(resName, swapchainImageView);

                if (usage == ResourceUsage.COLOR_ATTACHMENT) {
                    b.colorAttachment(view,
                            VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value(),
                            desc != null ? desc.loadOp : VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                            desc != null ? desc.storeOp : VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value(),
                            desc != null ? desc.clearR : 0f,
                            desc != null ? desc.clearG : 0f,
                            desc != null ? desc.clearB : 0f,
                            desc != null ? desc.clearA : 1f);
                } else if (usage == ResourceUsage.DEPTH_ATTACHMENT) {
                    b.depthAttachment(view,
                            VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL.value(),
                            desc != null ? desc.loadOp : VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                            desc != null ? desc.storeOp : VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value(),
                            desc != null ? desc.clearDepth : 1.0f);
                }
            }

            b.begin(commandBuffer, frameArena);
        }

        private void beginRenderPass(MemorySegment commandBuffer, VkImageView swapchainImageView, Arena frameArena) {
            VkRenderPass renderPass = owner.renderPasses.computeIfAbsent(name, k -> buildRenderPass());

            // Framebuffer is keyed by pass name + swapchain image view address so each swapchain image gets its own
            String fbKey = name + ":" + swapchainImageView.handle().address();
            VkFramebuffer framebuffer = owner.framebuffers.computeIfAbsent(fbKey, k ->
                    buildFramebuffer(renderPass, swapchainImageView));

            // Determine render area
            int w = 0, h = 0;
            for (Map.Entry<String, ResourceUsage> entry : writes.entrySet()) {
                ResourceDesc desc = owner.resourceDescs.get(entry.getKey());
                if (desc != null) {
                    w = desc.width;
                    h = desc.height;
                    break;
                }
            }

            VkCommandBuffer.RenderPassBuilder rpb = VkCommandBuffer.beginRenderPass(
                            commandBuffer, renderPass.handle(), framebuffer.handle())
                    .renderArea(0, 0, w, h);

            // Add clear values in write order
            for (Map.Entry<String, ResourceUsage> entry : writes.entrySet()) {
                ResourceDesc desc = owner.resourceDescs.get(entry.getKey());
                if (entry.getValue() == ResourceUsage.COLOR_ATTACHMENT) {
                    rpb.clearColor(
                            desc != null ? desc.clearR : 0f,
                            desc != null ? desc.clearG : 0f,
                            desc != null ? desc.clearB : 0f,
                            desc != null ? desc.clearA : 1f);
                } else if (entry.getValue() == ResourceUsage.DEPTH_ATTACHMENT) {
                    rpb.clearDepth(desc != null ? desc.clearDepth : 1.0f, 0);
                }
            }

            rpb.execute(frameArena);
        }

        private VkRenderPass buildRenderPass() {
            VkRenderPass.Builder b = VkRenderPass.builder().device(owner.device);
            boolean hasDepth = false;

            for (Map.Entry<String, ResourceUsage> entry : writes.entrySet()) {
                ResourceDesc desc = owner.resourceDescs.get(entry.getKey());
                int format = desc != null ? desc.format : VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value();
                int loadOp = desc != null ? desc.loadOp : VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value();
                int storeOp = desc != null ? desc.storeOp : VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value();

                if (entry.getValue() == ResourceUsage.COLOR_ATTACHMENT) {
                    b.colorAttachment(format, loadOp, storeOp);
                } else if (entry.getValue() == ResourceUsage.DEPTH_ATTACHMENT) {
                    b.depthAttachment(format, loadOp, storeOp);
                    hasDepth = true;
                }
            }

            b.subpassDependency(~0, 0,
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value()
                            | (hasDepth ? VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT.value() : 0),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value()
                            | (hasDepth ? VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT.value() : 0),
                    0,
                    VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value()
                            | (hasDepth ? VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value() : 0));

            return b.build(owner.arena);
        }

        private VkFramebuffer buildFramebuffer(VkRenderPass renderPass, VkImageView swapchainImageView) {
            VkFramebuffer.Builder b = VkFramebuffer.builder()
                    .device(owner.device)
                    .renderPass(renderPass.handle());

            int w = 0, h = 0;
            int attachmentIndex = 0;
            for (Map.Entry<String, ResourceUsage> entry : writes.entrySet()) {
                String resName = entry.getKey();
                ResourceDesc desc = owner.resourceDescs.get(resName);
                if (desc != null) {
                    w = desc.width;
                    h = desc.height;
                }

                MemorySegment viewHandle = owner.resolveView(resName, swapchainImageView);
                VkFramebufferAttachment.AttachmentType attType =
                        entry.getValue() == ResourceUsage.DEPTH_ATTACHMENT
                                ? VkFramebufferAttachment.AttachmentType.DEPTH
                                : VkFramebufferAttachment.AttachmentType.COLOR;

                b.attachment(viewHandle, attType);
                attachmentIndex++;
            }

            return b.dimensions(w, h).build(owner.arena);
        }

        private void executeSingleThreaded(MemorySegment commandBuffer, VkImageView swapchainImageView, Arena frameArena) {
            // Build a resolved resource map for the executor
            Map<String, MemorySegment> resolved = new HashMap<>();
            for (String name : reads.keySet()) {
                resolved.put(name, owner.resolveView(name, swapchainImageView));
            }
            for (String name : writes.keySet()) {
                resolved.put(name, owner.resolveView(name, swapchainImageView));
            }

            switch (type) {
                case GRAPHICS -> {
                    if (drawExecutor != null) {
                        drawExecutor.draw(resolved, frameArena).execute(commandBuffer);
                    } else if (graphicsExecutor != null) {
                        graphicsExecutor.execute(commandBuffer, resolved, frameArena);
                    }
                }
                case COMPUTE -> {
                    if (computeExecutor != null) {
                        computeExecutor.dispatch(commandBuffer, resolved, frameArena);
                    }
                }
                case GENERIC -> {
                    if (graphicsExecutor != null) {
                        graphicsExecutor.execute(commandBuffer, resolved, frameArena);
                    }
                }
            }
        }

        private void executeParallel(MemorySegment commandBuffer, VkImageView swapchainImageView, Arena frameArena) {
            // stub -- executes sequentially despite threadCount > 1. Real parallel execution
            // requires secondary command buffer allocation per thread, recording on worker threads,
            // and vkCmdExecuteCommands to merge them. Not implemented.
            if (parallelExecutor == null) return;
            Map<String, MemorySegment> resolved = new HashMap<>();
            for (String name : reads.keySet()) resolved.put(name, owner.resolveView(name, swapchainImageView));
            for (String name : writes.keySet()) resolved.put(name, owner.resolveView(name, swapchainImageView));
            for (int i = 0; i < threadCount; i++) {
                parallelExecutor.execute(commandBuffer, i, resolved, frameArena);
            }
        }
    }

    // ---- Types ----

    public enum PassType {GRAPHICS, COMPUTE, GENERIC}

    public enum ResourceUsage {
        COLOR_ATTACHMENT, DEPTH_ATTACHMENT, SHADER_RESOURCE,
        VERTEX_BUFFER, INDEX_BUFFER, UNIFORM_BUFFER, SHADER_STORAGE
    }

    public enum ResourceType {IMAGE, BUFFER}

    public static class ResourceDesc {
        final ResourceType type;
        final ResourceUsage usage;
        final int width, height;
        final int format;
        final String aliasOf;
        final int loadOp;
        final int storeOp;
        final float clearR, clearG, clearB, clearA;
        final float clearDepth;

        private ResourceDesc(ResourceType type, ResourceUsage usage, int width, int height,
                             int format, String aliasOf,
                             int loadOp, int storeOp,
                             float clearR, float clearG, float clearB, float clearA,
                             float clearDepth) {
            this.type = type;
            this.usage = usage;
            this.width = width;
            this.height = height;
            this.format = format;
            this.aliasOf = aliasOf;
            this.loadOp = loadOp;
            this.storeOp = storeOp;
            this.clearR = clearR;
            this.clearG = clearG;
            this.clearB = clearB;
            this.clearA = clearA;
            this.clearDepth = clearDepth;
        }

        public static ResourceDesc color(int width, int height, int format) {
            return new ResourceDesc(ResourceType.IMAGE, ResourceUsage.COLOR_ATTACHMENT,
                    width, height, format, null,
                    VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                    VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value(),
                    0f, 0f, 0f, 1f, 1.0f);
        }

        public static ResourceDesc color(int width, int height, int format, float r, float g, float b, float a) {
            return new ResourceDesc(ResourceType.IMAGE, ResourceUsage.COLOR_ATTACHMENT,
                    width, height, format, null,
                    VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                    VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value(),
                    r, g, b, a, 1.0f);
        }

        public static ResourceDesc depth(int width, int height, int format) {
            return new ResourceDesc(ResourceType.IMAGE, ResourceUsage.DEPTH_ATTACHMENT,
                    width, height, format, null,
                    VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                    VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value(),
                    0f, 0f, 0f, 0f, 1.0f);
        }

        public static ResourceDesc depth(int width, int height, int format, float clearDepth) {
            return new ResourceDesc(ResourceType.IMAGE, ResourceUsage.DEPTH_ATTACHMENT,
                    width, height, format, null,
                    VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                    VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value(),
                    0f, 0f, 0f, 0f, clearDepth);
        }

        public static ResourceDesc buffer(int size) {
            return new ResourceDesc(ResourceType.BUFFER, ResourceUsage.SHADER_STORAGE,
                    size, 0, 0, null,
                    0, 0, 0f, 0f, 0f, 0f, 0f);
        }

        public ResourceDesc alias(String resourceName) {
            return new ResourceDesc(type, usage, width, height, format, resourceName,
                    loadOp, storeOp, clearR, clearG, clearB, clearA, clearDepth);
        }

        /**
         * Returns a copy with LOAD instead of CLEAR — for passes that read existing content.
         */
        public ResourceDesc load() {
            return new ResourceDesc(type, usage, width, height, format, aliasOf,
                    VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_LOAD.value(), storeOp,
                    clearR, clearG, clearB, clearA, clearDepth);
        }
    }

    // ---- Executor interfaces ----

    @FunctionalInterface
    public interface GraphicsExecutor {
        void execute(MemorySegment commandBuffer, Map<String, MemorySegment> resources, Arena frameArena);
    }

    @FunctionalInterface
    public interface ComputeExecutor {
        void dispatch(MemorySegment commandBuffer, Map<String, MemorySegment> resources, Arena frameArena);
    }

    @FunctionalInterface
    public interface ParallelExecutor {
        void execute(MemorySegment commandBuffer, int threadId, Map<String, MemorySegment> resources, Arena frameArena);
    }

    @FunctionalInterface
    public interface DrawExecutor {
        DrawCommand draw(Map<String, MemorySegment> resources, Arena frameArena);
    }

    /**
     * Sentinel name for the current swapchain image — injected per-frame via execute().
     */
    public static final String BACKBUFFER = "__backbuffer__";
}
