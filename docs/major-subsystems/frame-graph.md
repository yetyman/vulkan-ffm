# Unified Frame Graph

## Overview

A single unified graph that schedules graphics, compute, and transfer work across all available queues. Passes declare their queue preference and resource dependencies; the graph resolves execution order, barriers, memory aliasing, and cross-queue synchronization automatically.

Multiple graph instances can coexist (e.g. main render graph + background streaming graph), each aware of its available queues.

## Package Structure

```
io.github.yetyman.vulkan.graph/
    FrameGraph.java              - unified graph: build, compile, execute
    GraphPass.java               - base pass declaration (reads, writes, queue preference)
    GraphResource.java           - resource declaration (lifetime, format, size)
    ResourceLifetime.java        - enum: TRANSIENT, PERSISTENT, TEMPORAL
    TemporalResource.java        - double/triple buffered temporal resource
    PassMask.java                - which passes are active this submission
    GraphCompiler.java           - topology sort, barrier insertion, aliasing, cycle unrolling
    GraphExecutor.java           - records and submits command buffers per queue

io.github.yetyman.vulkan.graph.graphics/
    RenderPass.java              - graphics-specific pass (attachments, viewport, scissor)
    AttachmentDeclaration.java   - color/depth/stencil attachment description

io.github.yetyman.vulkan.graph.compute/
    ComputePass.java             - compute-specific pass (dispatch dimensions, push constants)
    IndirectDispatch.java        - indirect dispatch from buffer

io.github.yetyman.vulkan.graph.transfer/
    TransferPass.java            - copy/blit operations
    UploadPass.java              - CPU->GPU staging upload
    ReadbackPass.java            - GPU->CPU readback
```

## Capability Matrix: Shared vs Pass-Type-Specific

| Capability                          | Render | Compute | Transfer | Shared Infrastructure |
|-------------------------------------|--------|---------|----------|-----------------------|
| Dependency resolution (DAG topology)| Yes    | Yes     | Yes      | GraphCompiler         |
| Resource lifetime tracking          | Yes    | Yes     | Yes      | GraphResource         |
| Memory aliasing                     | Yes    | Yes     | Minimal  | GraphCompiler         |
| Barrier insertion                   | Yes    | Yes     | Minimal  | GraphCompiler         |
| Queue family ownership transfers    | Yes    | Yes     | Yes      | GraphExecutor         |
| Pass ordering/reordering            | Yes    | Yes     | Yes      | GraphCompiler         |
| Temporal resource management        | Yes    | Yes     | Rare     | TemporalResource      |
| Pass activation / multi-rate        | Yes    | Yes     | Yes      | PassMask              |
| Render target management            | Yes    | No      | No       | graphics/             |
| Attachment load/store ops           | Yes    | No      | No       | graphics/             |
| Pipeline binding                    | Yes    | Yes     | No       | (per pass type)       |
| Descriptor set management           | Yes    | Yes     | No       | (per pass type)       |
| Draw command recording              | Yes    | No      | No       | graphics/             |
| Dispatch recording                  | No     | Yes     | No       | compute/              |
| Copy/blit recording                 | Partial| Partial | Yes      | transfer/             |

Approximately 70% of graph infrastructure is shared. Pass-type-specific code is only the leaf recording logic and type-specific resource declarations (attachments for graphics, dispatch dims for compute).

## Design Principles

- Passes declare intent (reads, writes, queue preference); the graph decides scheduling
- No manual barriers ever - the graph inserts all synchronization
- Resource aliasing is automatic for transient resources with non-overlapping lifetimes
- Temporal resources (cycles) are first-class citizens, not external hacks
- Multi-rate rendering via pass activation predicates, not separate graph instances
- Compiled graphs are cached by pass mask for repeated patterns
- The graph does not own the loop - it is invoked per submission by application code

## Sub-documents
refer to the vulkan-ffm-graph module for more detailed documentation