## Purpose

Integrate the RTX30-COMPAT work into Alpha through a dedicated
validation branch.

## Included behavior

- Portable TraceRay fallback when SER is unavailable
- Capability-driven OMM policy
- Fail-closed OMM entry-point validation
- Serialized acceleration-structure lanes by default
- Bounded interactive terrain scheduling
- Schema-14 generated-default repair
- Scheduler and compatibility contract tests

## Required gates

- [ ] `Java, shaders, and source contracts` passes on the exact head SHA
- [ ] Production-candidate workflow passes on the exact head SHA
- [ ] RTX 30 default-policy validation passes
- [ ] Modern NVIDIA regression validation passes
- [ ] Fresh configuration validation passes
- [ ] Schema-14 migration validation passes
- [ ] Resource reload and dimension-change validation passes
- [ ] Shutdown/restart lifecycle validation passes
- [ ] No Vulkan validation errors or device loss
- [ ] RTX 30 validation report committed

## Merge policy

Do not merge while this PR is a draft.
Do not squash the RTX30-COMPAT history.
