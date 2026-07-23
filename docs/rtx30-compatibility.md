# RTX 30 compatibility

Caustica automatically selects a portable Vulkan ray-tracing path when
shader execution reordering is unavailable.

## Default policy

```toml
[compatibility]
as-lane-mode = "auto"
omm-mode = "auto"
```

Both settings are selected during Vulkan device and executor creation.
Changing either setting requires a complete game restart.

## Expected RTX 30 behavior

The default RTX 30 path should report:

* Hardware profile: `nvidia-portable-rt`
* Trace backend: `portable-TraceRay`
* SER backend: `none`
* AS lane mode: `serialized`
* OMM policy: `auto`
* OMM effective state: disabled
* OMM reason: `disabled-on-nvidia-portable-profile`

The renderer must use the packaged `_base` ray-generation shaders.

## AS lane modes

### `auto`

Resolves to `serialized`. This is the supported default.

### `serialized`

Orders compute-side acceleration-structure work against graphics-side
terrain/TLAS use with timeline-semaphore waits.

### `overlap`

Experimental. Omits the cross-lane serialization wait. Do not use this
mode for release qualification.

## OMM modes

### `auto`

Uses capability-driven policy. OMM is suppressed on the NVIDIA portable
profile where SER is unavailable.

### `off`

Always disables OMM.

### `on`

Requests OMM when the physical device reports support. This is an
experimental override and is not part of RTX 30 release qualification.

## Failure behavior

If OMM was requested but its Vulkan entry points are unavailable,
Caustica disables OMM and continues without it.

Missing mandatory ray-tracing pipeline, acceleration-structure, or
TraceRays entry points remain a bring-up failure.

## Reporting a compatibility result

Include:

* Caustica commit SHA
* Production artifact SHA-256
* GPU model
* Operating system
* NVIDIA driver version
* Full `RT compatibility:` log line
* Full `RT bring-up OK` line
* Vulkan validation messages
* Test scenarios completed
