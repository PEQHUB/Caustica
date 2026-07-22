# Offline Benchmark Contract

Offline renderer measurements must separate scene snapshot work from steady-state
tracing. Use `tools/caustica-performance-loop.ps1` with the debug bridge enabled
and record the generated raw CSV alongside its JSON report.

## Required run identity

Every report must identify:

- `schemaVersion`
- build artifact SHA-256
- backend and RT readiness
- output and internal resolution
- benchmark label and kind
- warmup, capture interval, and sample duration
- GPU/driver identifiers when available
- the raw sample CSV path

The capture is invalid if the world, artifact, resolution, backend, or frame-stat
settings change while sampling. Minimized, unfocused, resized, or world-exited
runs must be discarded rather than mixed into a result.

## Offline fields

The bridge publishes configured batch metadata and the latest delayed GPU slot:

- `offlineConfiguredBatchLimit`
- `offlineMainPaths`
- `offlinePilotPaths`
- `offlineActiveTiles`
- `offlineTotalTiles`
- `offlineIndirectInvocations`
- `offlineIndirect`
- `offlineGpuFrameSerial`

These are workload metadata, not wall-clock throughput. Until device-local path
counters are enabled, `offlineMainPaths` is the submitted configured estimate and
must not be reported as an actual adaptive path count.

## Reporting rules

Do not average the offline snapshot frame into steady-state results. Report at
least five stable runs, P50/P95 GPU milliseconds, coefficient of variation, and
time to a fixed image-error target against a frozen reference. Keep the direct
full-screen path available for parity and rollback comparisons.
