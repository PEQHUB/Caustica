# Ordered implementation commits

The implementation is intentionally split into rollbackable invariants. The
subjects below are the plan order; each commit must remain free of portal files.

1. `test(streamline): add baseline trace and deterministic stall harness`
2. `refactor(streamline): make Java bridge calls typed and allocation-free`
3. `refactor(streamline-native): remove hot lookup allocation and locking`
4. `fix(streamline): remove reporting and parsing from frame-critical paths`
5. `refactor(reflex): make normal and out-of-band frame lifecycles explicit`
6. `feat(streamline): add PCL ABI markers and latency ping plumbing`
7. `refactor(streamline): make pacing ownership explicit`
8. `refactor(streamline): add persistent nonblocking DLSS-G input pool`
9. `feat(streamline): add hitch discontinuity recovery state machine`
10. `feat(streamline): add truthful Vulkan presentation policy`
11. `feat(settings): expose frame-generation policy controls`
12. `test(streamline): add deterministic pool and recovery model coverage`
13. `docs(streamline): record build and runtime qualification evidence`
14. `build(streamline): verify production artifact and shader variants`
15. `test(streamline): run final source and native contract gates`
16. `docs(streamline): finalize acceptance matrix and moving-branch handoff`

The current working tree is being validated before these commits are created.
No commit is allowed to claim hardware/runtime qualification without raw capture.
