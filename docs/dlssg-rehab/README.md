# DLSS-G rehabilitation

This branch is an isolated Streamline/DLSS-G rehabilitation fork from frozen
source `42ca9b8ef5d9a9aa4d5fbd7f48479e4ab060b4ad`. It intentionally contains no
portal-rendering changes. The moving portal branch remains an integration input,
not a source of truth for this worktree.

The implementation is organized around persistent Java scratch storage, typed
FFM calls, cached native feature functions, fixed native tag storage, explicit
normal versus out-of-band frame lifecycles, one pacing owner, a bounded
nonblocking input pool, recoverable runtime state, and truthful Vulkan
presentation policy.

Build/source proof and hardware/runtime proof are separate. The source/build
gates can be green while RTX 5090 FrameView, Reflex HUD/RTU, JFR allocation,
ETW contention, visual parity, and soak captures remain pending.

See [acceptance-matrix.md](acceptance-matrix.md), [support-matrix.md](support-matrix.md),
and [known-limitations.md](known-limitations.md).
