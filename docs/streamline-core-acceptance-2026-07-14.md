# Streamline core acceptance — 2026-07-14

This document is the curated authority for the mergeable Streamline core. Raw
hook probes, event rings, crash logs, and historical handoffs remain on
`codex/archive-streamline-dlssg-20260714`.

## Corrected causes

- The development `sl.dlss_g.json` forced `numFramesToGenerate` to `1` while
  Prism supplied `-Dcaustica.streamline.dlssg.overrideMode=on`. That plugin-side
  override pinned execution to one generated frame (2x), even when Caustica sent
  a larger application value. Production now creates no behavior JSON, and
  development packaging is behavior-neutral unless a dedicated test constructs
  an override.
- `-Dcaustica.rt.dlssRr=false` replaced the configured TOML value, and the old
  setting implementation serialized that effective value back to disk. Runtime
  settings now keep configured and overridden values separate and never write a
  JVM override into TOML.
- Production initialization applied Streamline's secondary signature verifier
  to every NVIDIA DLL. `nvngx_dlssd.dll` has valid Authenticode but no Streamline
  secondary signature, so initialization failed before `slInit`. Gradle now
  Authenticode-verifies the complete DLL set; the native bridge applies the
  secondary check only to `sl.interposer.dll`.

## Supported behavior

The Vulkan backend supports fixed DLSS Frame Generation multipliers 2x through
6x. These correspond to `numFramesToGenerate` values 1 through 5. The adapter's
authoritative `numFramesToGenerateMax` is cached across swapchain recreation and
is cleared only with device teardown. A transient unqueried swapchain cannot
replace or persist the user's selected multiplier.

Dynamic Multi Frame Generation is not exposed on Vulkan. NVIDIA limits Dynamic
MFG to D3D12; an old dynamic selection is evaluated as fixed and migrates to
fixed on the next settings save. Auto remains Streamline's legacy fixed-count
fallback.

DLSS Ray Reconstruction retains the core eight-resource contract, explicit
option and evaluation results, reset handling, native call correlation, and
fallback reporting. Low-light/exposure tuning, hybrid shadows, optical media,
and PsychoV display changes are intentionally outside this branch.

## Schema 4 runtime proof

`run/caustica-streamline/dlssg-acceptance.json` and
`run/caustica-streamline/dlssd-acceptance.json` contain the same schema 4
snapshot. It records:

- production/development identity and all active launch overrides;
- configured, effective, native-submitted, and reported-maximum generated-frame
  counts;
- last and maximum `numFramesActuallyPresented`, capability validity, and
  Dynamic MFG availability;
- DLSSD configured/effective state, option/evaluation results, Java and native
  call counts, exact resource-contract state, and fallback reason.

Verify a fresh runtime snapshot with:

```powershell
.\tools\verify-streamline-acceptance.ps1 -RequireProduction
```

## Build and deployment proof

The production gate is:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot'
$env:STREAMLINE_SDK = 'C:\Users\Administrator\Documents\Caustica-deps'
$env:VULKAN_SDK = 'C:\VulkanSDK\1.4.341.1'
.\gradlew.bat clean check build --no-daemon
```

This runs Java tests, shader compilation/validation, native ABI tests, CTest,
production Authenticode checks, and packaged production initialization. After a
successful build, deploy the JAR to the configured Prism instance and compare
source and destination SHA-256 hashes.

Deployment proves only the bytes on disk. It does not prove that a live JVM
loaded those bytes.

## Prism repair and user gate

After the user manually closes both Prism and Minecraft, repair stale proof
arguments with:

```powershell
.\tools\repair-prism-dlss-config.ps1 `
  -InstanceRoot 'C:\Users\Administrator\AppData\Roaming\PrismLauncher\instances\26.2(2)'
```

The script never terminates either process and refuses to edit configuration if
one is active. It removes only the known DLSS proof overrides, sets DLSSD true,
and preserves the configured fixed multiplier unless `-GeneratedFrames 1..5`
is explicitly supplied.

Final runtime acceptance requires a fresh JVM and user observation. For each 2x
through 6x selection, schema 4 must show native-submitted counts 1 through 5 and
nonzero generated presentations. The 6x test specifically requires
`numFramesToGenerate=5` and at least one six-frame presentation. DLSSD must pass
option/evaluation, matching native calls, the eight-resource contract, and no
fallback. No source, build, or deployment result substitutes for this user gate.
