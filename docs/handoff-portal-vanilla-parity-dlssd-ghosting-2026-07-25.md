# Portal Vanilla-Parity and DLSSD Ghosting Handoff

Date: 2026-07-25  
Branch: `fix/portal-vanilla-parity`  
Base: `origin/alpha_weather` at `6956722`  
Implementation tip before this handoff: `aa2e522`  
Status: **runtime acceptance failed; do not merge**

## Purpose

Continue the portal vanilla-parity investigation without assuming that the current
Nether transport or DLSSD overlay design is correct. The latest build no longer
blackens the whole screen, but the Nether portal still ghosts while stationary and
moving, remains much pinker/brighter than vanilla, and visibly admits or reconstructs
scene content through the portal.

The previous session must not be treated as successful merely because its focused
contracts and production build passed.

## User-observed runtime facts

These observations came from testing the deployed Prism instance, not from source
inspection:

1. The End portal initially rendered as an extremely bright white/pastel field rather
   than the expected dark cyan/teal vanilla End portal texture.
2. The Nether portal rendered as intense pink/magenta rather than vanilla's deep,
   dark purple.
3. White/cyan ghosting appeared at portal edges and across the portal. It was visible
   while stationary and became more obvious with camera motion.
4. A hard disocclusion/silhouette mask did not remove the ghosting.
5. The first layered-transparency attempt made the normal view black while most debug
   views remained valid. Tonemapper comparison exposed only the portal texture.
6. `aa2e522` removed that full-screen black failure, but the original ghosting remained.
7. In the latest supplied captures, sky/scene silhouettes are visible through the
   portal and compete with the animated portal pattern. The user suspects an obsidian
   reflection or other scene/reflection contribution is passing through the portal.
   That ownership hypothesis is not yet proven.

## Branch commits beyond the remote tip

The remote branch was previously at `34d11a1`. These implementation attempts follow it:

### `c18ba95` — texture-faithful transport and temporal stability

- Restored LabPBR and heuristic emission-source classification.
- Removed active Nether-portal dielectric/glass handling.
- Added a dedicated Nether portal primitive/payload path.
- Added deterministic source-over transport and straight-through continuation.
- Added portal-aware shadow transmittance.
- Prevented animated portal texels from being submitted to SHaRC as miss radiance.
- Added portal RIS light handling and portal-specific temporal/disocclusion signals.
- Corrected End portal layer decoding and offline clock behavior.

### `cc6725d` — first DLSSD transparency separation

- Added color-before-transparency, transparency-layer, and opacity images.
- Shifted the fixed sky descriptor bindings to avoid overlap.
- Submitted all three resources to Streamline DLSS-RR.

This encoding was wrong. It wrote `float4(portalRgb, 1.0)` into the RGBA transparency
layer for every pixel, making non-portal pixels an opaque black overlay. It also kept
the already-composited portal in the scaling input. The live result was a black normal
view with the portal texture still visible in tonemapper comparison.

### `aa2e522` — valid single-texture RGBA overlay

- Changed the layer to premultiplied `float4(portalRgb * alpha, alpha)`.
- Removed the separate opacity image from the active submission.
- Uses the reconstructed scene-before-portal image as DLSSD scaling input.
- Relaxed the Java/native bridge's invented three-resource atomic requirement so
  Streamline's supported single RGBA overlay can be submitted.
- Reduced the world guide descriptor count from 20 to 19.

This fixed the full-screen black failure, but **did not fix the portal ghosting**.

## Current source behavior

### Nether surface sampling

`shaders/world/world.rchit.slang`:

- Samples the animated atlas sprite at a single LOD.
- Converts sampled RGB with `srgbToLinear`, applies tint, and converts BT.709 to BT.2020.
- Computes alpha as `portalTexel.a * pr.tint.a`.
- Exports `PAYLOAD_NETHER_PORTAL`, zero F0, and no dielectric behavior.

`src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtTerrainMesher.java` averages all
four vertex alpha values with `sa / 1020f`. This divisor is the correct four-vertex
average, but the resulting effective alpha and the atlas sample alpha still need to be
captured numerically in game.

### Nether source-over transport

`shaders/world/world.rgen.slang` composites the effective face as:

```slang
float3 contribution = throughput * payload.albedo * alpha;
L += contribution;
throughput *= 1.0 - alpha;
```

It then continues in the same direction. Therefore scene radiance behind the portal is
intentionally retained according to `1 - alpha`. The current failure may be in the
effective alpha, the retained scene content, reconstruction guides, or their interaction.

The paired-face rule is:

```slang
bool effectiveFace = payloadInterfaceEntering() || segment == 0;
```

This also needs runtime proof from both sides of X- and Z-axis portals.

### Suspicious visible-emission gate

The direct portal contribution is currently skipped when the portal is a published RIS
emitter:

```slang
bool gatePortalEmitter = risOn && payloadEmitterInList();
if (!gatePortalEmitter || showCelestial) {
    L += throughput * payload.albedo * alpha;
}
throughput *= 1.0 - alpha;
```

This is a high-priority audit target. Publishing an emitter for NEE should not normally
make its camera-visible source disappear. It also conflicts with the later algebraic
DLSSD separation, which always subtracts a premultiplied portal term even when the
visible term was gated out.

### Current DLSSD overlay extraction

At the end of `world.rgen.slang`:

```slang
float3 premultipliedPortal = gv_portalLayerColor * opacity;
float3 colorBefore = transmission > 1.0e-4
        ? max((frameRadiance - premultipliedPortal) / transmission, float3(0.0))
        : float3(0.0);
gColorBeforeTransparency[pix] = float4(
        opacity > 0.0 ? colorBefore : frameRadiance, 1.0);
gTransparencyLayer[pix] = float4(premultipliedPortal, opacity);
```

`RtComposite` submits `gColorBeforeTransparency` as `ScalingInputColor` and submits
`gTransparencyLayer` as Streamline buffer type 51.

The layer encoding now matches NVIDIA's supported single RGBA overlay contract.
However, the base is reconstructed algebraically from the final path result instead
of being captured before portal compositing. That result may already include:

- straight-through scene radiance;
- secondary/reflected radiance;
- RIS-gated or missing visible portal contribution;
- temporal or guide ownership associated with the portal surface rather than the
  transmitted scene.

The algebraic inverse is therefore not proven to represent a coherent noisy
scene-before-transparency sample.

## Most important open questions

1. What are the exact sampled portal texture alpha, vertex alpha, final alpha,
   premultiplied portal RGB, and retained throughput at failing pixels?
2. With RIS disabled, is the camera-visible portal term restored and does the hue or
   ghosting change?
3. With DLSSD disabled but identical path tracing, is the scene/reflection still visible
   through the portal? This separates transport failure from reconstruction failure.
4. Does forcing `alpha = 1` as a temporary diagnostic remove the background/reflection
   and all ghosting? Do not keep this as the parity fix.
5. Does removing/replacing the surrounding obsidian change the ghost silhouette?
6. Are the primary depth, normal, motion, disocclusion, and animated guides describing
   the portal plane while `ScalingInputColor` describes the transmitted destination?
7. Is the portal texture being decoded twice because the Vulkan atlas view already
   performs sRGB decoding, or is the view unorm and the explicit EOTF required?
8. Why does the texture land in bright magenta after exposure/tonemapping instead of
   vanilla's dark purple? Separate texture RGB truth from emitted-light spill and bloom.
9. Does each real portal block publish duplicate coplanar/paired light records or
   presentation faces?
10. Are End portal/gateway area lights actually present in the RIS hierarchy? This
    remained missing or unproven in the original review and was not runtime-certified.

## Recommended proof sequence

Do not begin with another temporal-mask tweak.

1. Reproduce one fixed camera view and capture four modes:
   - DLSSD off / RIS off;
   - DLSSD off / RIS on;
   - DLSSD on / RIS off;
   - DLSSD on / RIS on.
2. Capture or add a temporary debug view for:
   - final `frameRadiance`;
   - `gColorBeforeTransparency`;
   - `gTransparencyLayer.rgb`;
   - `gTransparencyLayer.a`;
   - depth, normal, motion, animated guide, disocclusion, and current-color bias.
3. Numerically inspect a stable portal-center pixel and a failing edge pixel.
4. Temporarily force alpha to one. If the competing scene vanishes before DLSSD, audit
   alpha/source-over ownership. If it exists only after DLSSD, audit guide/base coherence.
5. Disable the `gatePortalEmitter` camera-visible suppression while leaving RIS
   publication intact. Compare surface appearance and the reconstructed base.
6. Only after identifying the owning buffer, replace algebraic inversion with an
   explicitly captured pre-portal radiance path if required.
7. Re-run the same matrix while moving and stationary. A still-frame failure is not
   exclusively a temporal-history problem.

## Validation completed

The latest source passed:

```text
PortalRenderingContractTest
RtDlssRrResourceContractTest
DlssdDiffusePathGuideContractTest
OfflineGroundTruthContractTest
compileShaders -PwithoutSharc=true
compileShaders with SHARC 1.6.5
native Streamline bridge build and ABI test
jar
verifyProductionArtifact -x test
```

The broader branch test suite was not made fully green during this portal session.
Earlier runs had unrelated pre-existing settings/bootstrap/source-contract failures;
do not convert focused-test success into a full-suite claim.

## Latest deployed artifact

Prism instance:

```text
C:\Users\Administrator\AppData\Roaming\PrismLauncher\instances\26.2(2)\minecraft
```

Artifact:

```text
build\libs\caustica-0.1.0.jar
SHA-256 605C54AF8B820D9E24758203A39F393E02CA422A90774E06D18664C91B3760B1
```

The source and deployed hashes matched at deployment. Runtime testing of that artifact
produced the latest ghosting captures and failed acceptance.

## Acceptance remains

- End portal matches the active vanilla/resource-pack shader and is not white/pink.
- End gateway has correct faces and beam.
- Nether portal matches vanilla's deep dark purple animated appearance.
- No white/cyan scene or reflection ghost is visible through or around the portal,
  stationary or moving.
- Portal surface appearance is invariant under RIS on/off.
- DLSSD on/off changes reconstruction quality, not portal composition or hue.
- SHaRC never caches animated portal texels.
- Ordinary glass, panes, water, ice, particles, and generic translucency remain unchanged.
- Full builds/tests pass, followed by fresh in-game captures for every required backend.

