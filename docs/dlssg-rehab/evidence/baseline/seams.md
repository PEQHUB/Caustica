# Phase 0 seam manifest

The frozen source provides exact source-level targets for the current Streamline
brackets. The actual Minecraft 26.2 limiter sleep and application queue-submit
owners are intentionally left unresolved until mappings/runtime tracing identifies
them; no guessed Mixin descriptor is used.

| Seam | Frozen source target | Status |
|---|---|---|
| frame begin | `Minecraft.runTick(Z)V` HEAD -> `RtDlssFg.beginSimulationFrame()` | source-verified |
| simulation end | `Minecraft.runTick(Z)V` before `Minecraft.renderFrame(Z)V` | source-verified |
| render begin | `GameRenderer.render(DeltaTracker, boolean)V` HEAD | source-verified |
| limiter lookup | `Minecraft.renderFrame()` invokes `FramerateLimitTracker.getFramerateLimit()I` | source-verified lookup; sleep owner unresolved |
| first application submit | Vulkan proxy bridge boundary | runtime/source mapping required |
| final application submit | Vulkan proxy bridge boundary | runtime/source mapping required |
| present | `slbridge_vk_queue_present` proxy path | source-verified |

Phase 2/3 must preserve the current behavior at unresolved seams and add the trace
at the closest stable wrapper rather than inventing an injection descriptor.
