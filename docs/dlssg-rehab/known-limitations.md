# Known limitations and open proof

- The actual Minecraft limiter sleep owner, event-poll seam, input drain seam,
  camera-consume seam, and first/final application submit seams remain marked
  unresolved in [baseline/seams.md](evidence/baseline/seams.md). No guessed
  injection descriptor was added.
- No RTX 5090 runtime capture was available during this implementation pass.
  All hardware-dependent acceptance cells remain `AWAITING_RUNTIME_CAPTURE`.
- `FG_MAILBOX` selects a Vulkan present mode. It is not vendor-reported
  Streamline VSync support. The report and UI keep those facts separate.
- The nonblocking pool model and recovery state machine have source/unit proof,
  but validation-layer, FrameView, fault-injection, and two-hour soak proof are
  still required.
- Production deployment was not performed by this branch.
- C1 Java/build verification is blocked in this environment because the available `slangc` reports
  `2026.1-52-gc8ddf20bb`, while the repository requires pinned Slang `2026.13`.
- C2 still requires runtime ownership capture, hardware qualification, and the remaining acceptance
  ledger evidence. These are intentionally not claimed by C1.
