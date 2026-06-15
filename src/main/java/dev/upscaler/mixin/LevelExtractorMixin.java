package dev.upscaler.mixin;

import dev.upscaler.rt.RtTerrain;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards vanilla's block-dirty signal to the RT renderer so edited sections re-extract. In 26.2
 * the dirty methods live on {@link LevelExtractor}; Sodium {@code @Overwrite}s them to drive its own
 * renderer, but a HEAD inject runs alongside that and is renderer-agnostic (it also works without
 * Sodium). {@code setBlocksDirty} is vanilla's block-change entry point (a single block change comes
 * through as the 3³ block area around it), so it covers the section plus the boundary neighbours whose
 * cull faces are affected. Lighting-only invalidations route through other methods we deliberately
 * skip — we ray-trace lighting, so they don't change our geometry.
 */
@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
    @Inject(method = "setBlocksDirty(IIIIII)V", at = @At("HEAD"))
    private void upscaler$rtBlocksDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
        RtTerrain.markBlocksDirty(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
