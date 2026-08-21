# Buffer Cursor API — Draft Proposal

> **STATUS: EARLY IDEA — NOT YET DESIGNED**
>
> This document captures the motivation and rough direction for a cursor-based buffer
> IO API. The shape is NOT finalized. This needs significant discussion and iteration
> before any implementation work begins. Many open questions remain about how this
> interacts with existing strategies, dirty tracking, sparse/slab allocations, and
> the broader IBuffer contract.

---

## Problem Statement

The current `acquireWrite`/`acquireRead` scope pattern has fundamental performance
issues in hot paths:

1. **Object allocation per call** — Each `acquireWrite` creates a `BufferWriteScope`
   implementation (or reuses a pooled one with limitations) and an `asSlice`-derived
   `MemorySegment`. At hundreds of calls per frame this creates measurable GC pressure
   and FFM session-check overhead.

2. **Scope lifecycle is ceremony for the common case** — For coherent mapped memory,
   `close()` is a no-op. For deferred mirrored memory, `close()` just marks dirty.
   The try-with-resources block and AutoCloseable interface are pure overhead when the
   actual work is a memcpy + a dirty mark.

3. **Mixed concerns** — The same `acquireWrite` path must handle:
   - Coherent mapped (no-op close, direct write)
   - Non-coherent mapped (flush on close)
   - Staged device-local (allocate staging, record copy on close)
   - Deferred mirrored (write to mirror, mark dirty on close)
   - Sparse (validate pages committed before write)
   - Suballocated (offset translation within slab)

   The scope abstraction tries to unify all of these behind one interface, but pays
   the cost of the most complex case (staging) even in the simplest case (coherent).

---

## Rough Direction: Cursor-Style API

The idea: expose a **cursor** object that is configured once (or per-buffer) and
provides direct write/read methods without per-call allocation.

### Strawman (NOT final — needs discussion)

```java
// Acquired once, reused across frames. thread local maybe
//maybe an optional offset and size allowance parameter for zero indexed "scoped" writes for deeper return situations
BufferCursor cursor = buffer.cursor(offset, count);//optional, but for normal buffers 
BufferCursor cursor = buffer.cursor();//for suballocation buffers where the rest should not ever be visible. offset and count are already known and hidden in the suballocation buffer. same for sparse maybe?
//here the buffer really serves as a way to facilitate the scope restrictions of various options while still ultimately making calls on the real data with zero alloc?


// Hot-path writes — no allocation, no scope, no close. 
cursor.writeFloats(src, srcOffset, dstByteOffset, count);
cursor.writeBytes(src, srcOffset, dstByteOffset, count);
cursor.setFloat(byteOffset, value);
cursor.setInt(byteOffset, value);
cursor.write(offset, gpuLayout<T> item);//on a non generic managedbuffer. straight to bytes after all
cursor.write(offset, gpuCollection<T> items, srcOffset, count);//might be an alternative interface to gpuLayout targetted at items which would contain many individual gpuLayout items. allowing for bulk write optimizations?

// Dirty tracking happens internally per write call
// (or can be batched: cursor.beginBatch() ... cursor.endBatch())

// Hot-path reads — zero-copy into caller's array
cursor.readFloats(dst, dstOffset, srcByteOffset, count);
float v = cursor.getFloat(byteOffset);
```

### Key Properties (if we go this direction)

- **One cursor per buffer** — thread-local or explicitly acquired. Not per-call.
- **Zero allocation in the write/read path** — no segments, no scopes, no lambdas.
- **Dirty tracking built in** — each write marks the range dirty. Batch mode could
  defer marking to `endBatch()` for coalescing.
- **Strategy-aware internally** — the cursor knows whether it's writing to mapped
  memory, a mirror, or needs staging. Callers don't see this.
- **Absolute offsets** — no zero-indexed slices. You say where in the buffer you're
  writing and that's what happens.

---

## Open Questions (need discussion)

0. what does this gain us over direct methods on managedbuffer? reference passing, scope setting, sub/sparse/normal support in the same interface with correct scope based zero indexing to the hidden data?
1. **Staging/device-local path** — A cursor that writes to device-local memory can't
   just memcpy. Does it internally acquire staging memory? Does it batch into a
   transfer command? How does the caller know when the data is GPU-visible? The scope
   approach at least made the lifecycle explicit (close = commit). A cursor hides this.

2. **Thread safety** — Is one cursor per buffer per thread? Thread-local? Explicitly
   acquired with a thread assertion? The current scope approach is inherently scoped
   to a call site; cursors live longer and need a threading story.

3. **Sparse/suballocated buffers** — Sparse buffers need page validation before write.
   Suballocated buffers need offset translation (user offset != buffer offset). Does
   the cursor handle this transparently, or do those buffer types not support cursors?

4. **Relationship to IBuffer interface** — Is `cursor()` on IBuffer (forcing all
   implementations to support it) or only on ManagedBuffer? Custom IBuffer
   implementations (graph transient resources, externally-owned handles) may not be
   able to provide a cursor.

5. **Batch/flush semantics** — For mirrored buffers, when does the dirty state get
   flushed? With scopes it was "on close." With a cursor it's... when? Explicit
   `cursor.flush()`? Automatic on frame end? Does this couple the cursor to the
   frame lifecycle?

6. **Read consistency** — For mirrored buffers, reads come from the mirror which may
   be stale if the GPU wrote to the primary. Does the cursor expose a `refresh()`
   or `sync()` method? Does it transparently handle GPU->CPU dirty tracking?

7. **Coexistence** — Can `acquireWrite` and cursor writes coexist on the same buffer?
   Both mark dirty. Both touch the same memory. Is that fine, or do we deprecate
   scopes entirely?

8. **Bulk vs individual** — The strawman shows both `setFloat` (per-value) and
   `writeFloats` (bulk). Per-value still hits FFM VarHandle guards per call. Is bulk
   the only thing worth exposing, with callers accumulating into their own array first?
   Or is per-value access important for scattered writes?

---

## What This Replaces / Complements

- `acquireWrite` / `BufferWriteScope` — remains for cases needing explicit lifecycle
  (staging transfers, non-coherent flush). Cursor is the hot-path alternative.
- `IBuffer.write(ByteBuffer, offset, queue)` — the existing bulk-write-from-ByteBuffer
  path. Cursor would be an alternative that avoids the ByteBuffer intermediary.

---

## Next Steps

1. **Talk through the open questions** — especially staging, threading, and flush
2. **Decide scope** — cursor on ManagedBuffer only vs on IBuffer
3. **Prototype** — minimal cursor for mapped coherent + mirrored deferred (the two
   paths this demo exercises), validate it eliminates the allocation pressure
4. **Generalize** — extend to staging if the design supports it cleanly
