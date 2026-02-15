/**
 * Automatic buffer strategy selection for Vulkan.
 * 
 * <p>This package provides utilities to automatically select optimal buffer management
 * strategies based on access patterns and data characteristics. It implements a comprehensive
 * decision matrix covering common use cases:
 * 
 * <ul>
 *   <li>Uniform buffers for per-frame constants</li>
 *   <li>Dynamic vertex buffers for UI and debug rendering</li>
 *   <li>Static geometry with staging buffers</li>
 *   <li>GPU-owned compute data</li>
 *   <li>Readback buffers for query results</li>
 *   <li>Ring buffers for high-frequency updates</li>
 * </ul>
 * 
 * <p>The main entry point is {@link io.github.yetyman.vulkan.buffers.BufferStrategySelector#select},
 * which takes access frequency parameters and returns a recommended strategy.
 * 
 * @see io.github.yetyman.vulkan.buffers.BufferStrategySelector
 * @see io.github.yetyman.vulkan.buffers.BufferStrategySelection
 * @see io.github.yetyman.vulkan.buffers.MemoryStrategy
 * @see io.github.yetyman.vulkan.buffers.AccessFrequency
 * @see io.github.yetyman.vulkan.buffers.DataScale
 */
package io.github.yetyman.vulkan.buffers;
