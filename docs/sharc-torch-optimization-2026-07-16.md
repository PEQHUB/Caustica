# SHaRC tuning and torch-noise audit (2026-07-16)

## Production SHaRC tuning

The measured live configuration was:

- cache exponent 25: 33,554,432 entries / 1.25 GiB
- scene scale 68.25
- radiance scale 10,000
- history 33 frames, stale lifetime 64 frames
- one update path per 2x2 tile (25% of render pixels)
- update depth 8, minimum segment 1.0 voxel
- glossy queries off, live secondary direct on

This is not a balanced configuration. A short diagnostic-counter sample reported about 169,000 occupied
entries, 0 insert failures, and very few collisions. That is about 0.50% occupancy; NVIDIA recommends
roughly 10-20% for a static-camera validation. The sample occurred during play, so its visual and timing
results are not a controlled benchmark, but movement should increase occupancy rather than explain such a
low value. The allocation is therefore safely classified as oversized.

The production candidate is:

| Setting | Value | Reason |
|---|---:|---|
| cache exponent | 20 (40 MiB) | 169k entries would be 16.1% of 2^20; the coarser scale and longer stale lifetime approximately offset each other |
| scene scale | 32 | near-camera voxels become 0.0625 blocks instead of 0.0293 at scale 68.25, increasing reuse without returning to the extremely coarse old default |
| radiance scale | 1,000 | NVIDIA's recommended starting point; 10,000 buys unnecessary quantization precision and reduces integer-overflow headroom by 10x |
| history | 256 frames | 256 / 25 = 10.24 nominal tile-update opportunities per history window, comparable to 33 / 4 = 8.25 in the old brute-force configuration |
| stale lifetime | 256 frames | retains roughly ten complete 5x5 update rotations while avoiding the 64-frame premature churn seen with sparse updates |
| update tile | 5x5 (4%) | NVIDIA's reference starting point; 82,944 update paths at 1920x1080 versus 518,400 for 2x2, a 6.25x reduction |
| update depth | 4 | matches `SHARC_PROPAGATION_DEPTH=4`; deeper update paths have sharply diminishing cache-propagation value |
| minimum segment | 0.25 voxel | reduces the dominant short-segment rejection while remaining above the ray-origin bias at the near level |
| glossy queries | off | avoids directionless-cache use for insufficiently broad specular lobes |
| live secondary direct | on | keeps view-dependent specular and SSS live |
| anti-firefly | on | uses the SDK confidence weighting |
| raw primary debug | off | the SDK explicitly excludes the primary hit from normal cache queries |

The cache capacity slider remains available through exponent 28 (10 GiB), but this is experimental. Resolve
dispatches over every entry every frame. Capacity therefore costs frame time even when almost every entry is
empty; it is not only a VRAM choice.

## What SHaRC can and cannot do for torches

The standards-compliant query occurs only after the primary camera hit. For a camera-visible wall lit by a
small torch, the difficult event is the wall's sampled diffuse continuation ray landing on the small emissive
surface. Secondary SHaRC can shorten and stabilize transport after a secondary hit, but it does not increase
the probability of that primary-wall-to-torch event. The raw primary debug mode hides this limitation by
replacing the primary result with one exact cached voxel, which is why it can look calmer and simultaneously
blocky.

The torch estimator is heavy-tailed: most 1-spp paths contribute no torch radiance, and a rare path contributes
a large value. DLSS Ray Reconstruction cannot infer the missing samples from geometry guides alone.

## DLSSD guide audit

The current production submission has ten resources: noisy color, depth, ordinary motion, diffuse albedo,
specular albedo, packed normal/roughness, specular motion, disocclusion mask, bias-current-color hint, and
output. DLSSD evaluation is active and accepts all ten resources.

There are real guide gaps, but they have different relevance:

1. `AnimatedTextureHint` is supported but not submitted. The current `gAnimatedGuide` is folded into
   `BiasCurrentColorHint`, and only animated water populates it. Terrain sprite animation metadata no longer
   reaches the ray payload. This is a real incomplete wiring issue, but it mainly affects visible animated
   surfaces, not static wall pixels receiving noisy torch illumination.
2. `ParticleHint` is supported but not submitted. Particle pixels have motion but no dedicated classification.
   This can improve flame/smoke stability, not indirect torch lighting.
3. `DiffuseRayDirectionHitDistance` (or the separate diffuse direction and distance resources) is supported but
   not generated. This is the most relevant missing first-bounce guide for noisy indirect diffuse transport and
   should be the first DLSSD guide A/B.
4. `ResponsivityMask` is supported but not submitted. It should describe genuinely changing pixels; marking rare
   bright Monte Carlo samples as responsive would reject history and make boiling worse.
5. A generic `Emissive` buffer type exists in Streamline's shared enum, but the bundled 2.12 DLSSD plugin does
   not retrieve or forward that tag during DLSSD evaluation. Creating that image alone would have no effect.
   The earlier low-light branch allocated an emissive image but did not submit it to DLSSD.

## Remaining rule-compliant torch options

Ranked by expected value:

1. Feed first-diffuse-ray direction plus hit distance to DLSSD and A/B it against the current ten-guide path.
   This improves the denoiser's knowledge without changing lighting.
2. Add a learned directional/path-guiding distribution built from ordinary emissive path hits. This increases
   the probability of sampling small emitters without an analytic torch light, torch list, or replacement mask.
3. Add temporal/spatial reuse of emissive contributions discovered by the existing path tracer. The reuse data
   must remain material-agnostic and must not become a separate torch-light list.
4. Add adaptive extra path samples only in empirically high-variance tiles. This is straightforward and robust,
   but it has a direct performance cost.
5. Add robust contribution clamping/winsorization before temporal reconstruction as a controlled biased mode.
   This reduces rare fireflies but cannot recover missing mean illumination and needs comparison to offline ground truth.

The best next experiment is option 1 because it is a contained guide-only change. If it does not materially
reduce the wall boil, the bottleneck is confirmed as sampling variance and option 2 or 3 is required.
