package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.VulkanFFM;
import java.lang.foreign.*;

/**
 * Vulkan query command wrapper for query pools, timestamps, and occlusion queries.
 */
public record VkQuery(MemorySegment queryPool, int query, QueryType type, int flags, int pipelineStage) {
    
    public enum QueryType { BEGIN, END, RESET, WRITE_TIMESTAMP }
    
    public static void beginQuery(VkCommandBuffer cmd, MemorySegment queryPool, int query, int flags) {
        beginQuery(cmd.handle(), queryPool, query, flags);
    }
    
    public static void beginQuery(MemorySegment cmd, MemorySegment queryPool, int query, int flags) {
        VulkanFFM.vkCmdBeginQuery(cmd, queryPool, query, flags);
    }
    
    public static void beginQuery(VkCommandBuffer cmd, MemorySegment queryPool, int query) {
        beginQuery(cmd, queryPool, query, 0);
    }
    
    public static void beginQuery(MemorySegment cmd, MemorySegment queryPool, int query) {
        beginQuery(cmd, queryPool, query, 0);
    }
    
    public static void endQuery(VkCommandBuffer cmd, MemorySegment queryPool, int query) {
        endQuery(cmd.handle(), queryPool, query);
    }
    
    public static void endQuery(MemorySegment cmd, MemorySegment queryPool, int query) {
        VulkanFFM.vkCmdEndQuery(cmd, queryPool, query);
    }
    
    public static void resetQueryPool(VkCommandBuffer cmd, MemorySegment queryPool, int firstQuery, int queryCount) {
        resetQueryPool(cmd.handle(), queryPool, firstQuery, queryCount);
    }
    
    public static void resetQueryPool(MemorySegment cmd, MemorySegment queryPool, int firstQuery, int queryCount) {
        VulkanFFM.vkCmdResetQueryPool(cmd, queryPool, firstQuery, queryCount);
    }
    
    public static void writeTimestamp(VkCommandBuffer cmd, int pipelineStage, MemorySegment queryPool, int query) {
        writeTimestamp(cmd.handle(), pipelineStage, queryPool, query);
    }
    
    public static void writeTimestamp(MemorySegment cmd, int pipelineStage, MemorySegment queryPool, int query) {
        VulkanFFM.vkCmdWriteTimestamp(cmd, pipelineStage, queryPool, query);
    }
    
    // Reusable execution methods
    public void execute(VkCommandBuffer cmd) {
        execute(cmd.handle());
    }
    
    public void execute(MemorySegment cmd) {
        switch (type) {
            case BEGIN -> beginQuery(cmd, queryPool, query, flags);
            case END -> endQuery(cmd, queryPool, query);
            case WRITE_TIMESTAMP -> writeTimestamp(cmd, pipelineStage, queryPool, query);
            default -> throw new UnsupportedOperationException("Query type not implemented: " + type);
        }
    }
    
    // Builder for fluent construction
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private MemorySegment queryPool;
        private int query = 0;
        private QueryType type;
        private int flags = 0;
        private int pipelineStage = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value();
        
        private Builder() {}
        
        public Builder queryPool(MemorySegment queryPool) { this.queryPool = queryPool; return this; }
        public Builder query(int query) { this.query = query; return this; }
        public Builder beginQuery() { this.type = QueryType.BEGIN; return this; }
        public Builder endQuery() { this.type = QueryType.END; return this; }
        public Builder writeTimestamp() { this.type = QueryType.WRITE_TIMESTAMP; return this; }
        public Builder flags(int flags) { this.flags = flags; return this; }
        public Builder pipelineStage(int stage) { this.pipelineStage = stage; return this; }
        public Builder topOfPipe() { this.pipelineStage = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value(); return this; }
        public Builder bottomOfPipe() { this.pipelineStage = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT.value(); return this; }
        
        public VkQuery build() {
            return new VkQuery(queryPool, query, type, flags, pipelineStage);
        }
        
        public void query(VkCommandBuffer cmd) {
            build().execute(cmd);
        }
        
        public void query(MemorySegment cmd) {
            build().execute(cmd);
        }
    }
}