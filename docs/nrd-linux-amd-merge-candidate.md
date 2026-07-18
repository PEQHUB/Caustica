# NRD Linux/AMD merge candidate

Branch: `codex/nrd-linux-amd`

Base: `f7a1ff9` (`perf: restore bounded terrain build throughput`)

This candidate adds NVIDIA Real-time Denoisers (NRD) 4.17.3 as a vendor-neutral Vulkan reconstruction
backend. It does not deploy or modify a launcher instance.

## Runtime contract

`Auto` selects NRD on Linux, AMD, and Intel. Windows NVIDIA selects Streamline DLSS Ray Reconstruction
while it remains operational and falls back to NRD after a support/setup failure. Users can force NRD on
NVIDIA, force DLSS-RR, or turn reconstruction off globally.

NRD receives native Vulkan handles through a small FFM/C bridge and records NRI/NRD compute dispatches
into Caustica's existing graphics command buffer. The bridge enables neither NVAPI nor AMD AGS and links
no CUDA or NGX component. It uses the official NRD NRI integration layer with SPIR-V shaders.

The device bring-up no longer requires NV/EXT Shader Execution Reordering. AMD and other no-SER devices
select baseline live, offline, and SHaRC shader binaries built with standard Vulkan `TraceRay`; SER-capable
devices retain their specialized variants. The baseline binaries are disassembly-checked to contain no
invocation-reorder capability or extension.

Caustica supplies:

- packed noisy diffuse/combined path radiance plus hit distance;
- optional directional SH/SG coefficient data;
- world normal and linear roughness;
- linear view-space depth;
- render-pixel motion converted to UV motion in the bridge settings;
- current/previous non-jittered projection and view matrices; and
- explicit clear-and-restart history events.

NRD output is resolved back to Caustica's scene-linear RGB contract before exposure, bloom, tonemapping,
HDR mapping, or presentation. REBLUR YCoCg and SH output are decoded in `nrd_resolve.comp`; RELAX retains
linear RGB. A bridge failure decodes the current noisy input instead of presenting packed YCoCg.

## Live menu surface

The Reconstruction tab exposes backend, REBLUR/RELAX, directional SH/SG, anti-firefly, antilag,
main/fast history, history-fix frames/stride, prepass and blur radii, lobe/roughness/plane rejection,
disocclusion threshold, REBLUR hit-distance A/B/C, RELAX A-Trous iterations, and noisy/denoised split.

Backend, denoiser-family, and SH changes recreate correctly-sized images and reset history. All other NRD
settings are copied to `CommonSettings`, `ReblurSettings`, or `RelaxSettings` every frame and therefore
take effect live.

## Build and packaging

`native/nrd_bridge/CMakeLists.txt` pins NRD commit
`792eff196afdd350fd9c3f862119017ccb438a0e` (v4.17.3) and NRI v179 transitively. Gradle's
`buildNrdBridge` builds the host payload. `bundleNrdNatives` packages the host binary and accepts
`NRD_WINDOWS_X64` / `NRD_LINUX_X64` for cross-platform assembly. The production-candidate workflow builds
Linux first, downloads that exact `.so` into the Windows job, and creates one global review JAR.

The Windows Streamline bundle remains unchanged and licensed separately. NRD's license is copied from the
pinned fetched source into `META-INF/licenses/nvidia/NVIDIA-NRD.txt`.

## Proof boundary

Source, Windows native compilation, shader compilation, generated ABI records, Java compilation, and unit
contracts can be proven on the current Windows machine. An actual AMD/Linux render, image-quality tuning,
and Linux dynamic-loader proof require the requested review candidate to be run on that machine. Those are
runtime proof gates, not claims made by this document.

Caustica's existing renderer still requires the core KHR ray-tracing extensions, ray query, and
`VK_KHR_ray_tracing_position_fetch`; NRD removes the NVIDIA reconstruction/SER dependency but does not turn
non-ray-tracing Vulkan hardware into a supported renderer.
