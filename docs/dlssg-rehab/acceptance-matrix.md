# Acceptance matrix

This is the branch-local status ledger. `PASS` means source/build evidence exists
in this worktree. `AWAITING_RUNTIME_CAPTURE` is intentionally not a pass.
Release remains blocked until all required hardware and runtime cells have raw
evidence.

| Phase | Gate IDs | Current status |
|---:|---|---|
| 0 | SRC-001, SRC-002, TRACE-001, STALL-001, STALL-002 | PASS after source/unit/native gates; runtime stall/JFR proof pending |
| 0 | SRC-003 | NOT_STARTED: unresolved submit/limiter/input seams remain explicitly marked |
| 0 | BASE-001 | FAIL as a full frozen-source gate; 19 baseline failures are recorded |
| 0 | BASE-002, BASE-003, BASE-004 | AWAITING_RUNTIME_CAPTURE |
| 1 | HOT-001, HOT-002, HOT-003, HOT-004, HOT-006 | PASS after source/native/build gates |
| 1 | HOT-005, HOT-007, PERF-001, PERF-002, PERF-003, PERF-004 | AWAITING_RUNTIME_CAPTURE |
| 2 | LIFE-001, LIFE-002, PCL-001 | PASS after source/unit/native gates |
| 2 | PCL-002, PCL-003, INPUT-001, INPUT-002, SUBMIT-001, REFLEX-001 | AWAITING_RUNTIME_CAPTURE |
| 3 | PACE-001, PACE-002, PACE-003, PACE-007 | PASS after source/unit gates |
| 3 | PACE-004, PACE-005, PACE-006 | AWAITING_RUNTIME_CAPTURE |
| 4 | POOL-001, POOL-002, POOL-003, POOL-004, POOL-005, POOL-006 | PASS after source/unit/native gates |
| 4 | POOL-007 | AWAITING_RUNTIME_CAPTURE |
| 5 | REC-001, REC-002, REC-007 | PASS after source/unit gates |
| 5 | REC-003, REC-004, REC-005, REC-006 | AWAITING_RUNTIME_CAPTURE |
| 6 | PRES-001, CFG-001 | PASS after source/unit gates |
| 6 | PRES-002, PRES-003, CFG-002, UI-001, SUBMIT-002 | AWAITING_RUNTIME_CAPTURE |
| 7 | BUILD-001 | FAIL as a full-suite gate: six unrelated frozen-baseline contract failures remain documented in evidence/baseline/build.log; touched/new DLSS-G tests are separately run |
| 7 | BUILD-002, BUILD-003 | PASS: both shader variants, production JAR, native packaging and artifact verification passed |
| 7 | LAT-001, LIFE-003, SOAK-001, SOAK-002, VIS-001 | AWAITING_RUNTIME_CAPTURE |
| 7 | REL-001 | PASS: support matrix and known limitations are explicit; no tuple is falsely qualified |
| 7 | REL-002, REL-003 | NOT_STARTED: moving-branch review and final checksum manifest remain |

Hardware-dependent rows are not inferred from source or build success. Each
runtime capture must include the exact commit, config, driver, display mode,
refresh, queue policy, pacing policy, scene, timestamps, and raw tool output.

## Required runtime commands

Run from the built artifact with `-Dcaustica.streamline.trace=true`, then repeat
with development-only CPU/GPU stalls of 20, 35, 50, 100, and 250 ms. Capture
FrameView, Reflex Verification HUD/RTU when available, JFR allocation/file-I/O,
ETW/WPA contention, validation-layer output, lifecycle transitions, visual parity,
and primary/fault soak logs. Do not mark these rows PASS from screenshots alone.
