# Frame Graph Plans

## Documents

- [UNFULFILLED_FEATURES.md](./UNFULFILLED_FEATURES.md) - Comprehensive plan for features described in design docs but not yet fully implemented. Includes priority ordering, implementation steps, and target APIs.

## Relationship to Design Docs

The `docs/` folder in this module contains the system's design documentation (moved from `docs/major-subsystems/frame-graph/`). Those documents describe the full target state of the system.

This `plans/` folder tracks what remains to be built to reach that target state, with actionable implementation guidance.

## Current Implementation Status

The frame graph is a working system with substantial real functionality:
- Multi-queue execution with inter-queue timeline semaphores
- Parallel secondary command buffer recording
- Full automatic barrier synthesis (resource, temporal, optional, bindless, cross-queue)
- Temporal resources with physical slot rotation and staleness tracking
- GPU timestamp profiling and debug labels
- Compiled graph caching by PassMask
- Degradation strategy integration
- Readback copies (GPU-to-CPU)
- Imported resources with layout transitions
- Iterative passes with ping-pong automation
- Auto-rendering (dynamic rendering begin/end around graphics nodes)

See UNFULFILLED_FEATURES.md for what's left.
