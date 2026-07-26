package dev.comfyfluffy.caustica.rt.pipeline;

/** Resolves one pacing owner at a normal frame boundary. */
public final class PacingPolicy {
    private long generation;
    private PacingOwner lastOwner = PacingOwner.NONE;
    private int lastTarget;

    public void resolveInto(PacingConfigSnapshot input, PacingDecision output) {
        output.reset();
        output.generation = ++generation;
        output.displayTargetFps = Math.max(0, input.outputTargetFps);
        output.effectiveGeneratedCount = Math.max(0, input.generatedFrames);
        output.sourceTargetFps = input.outputTargetFps > 0
                ? input.targetStrategy == PacingTargetStrategy.SOURCE_TARGET
                    ? Math.max(input.minimumSourceFps, Math.round(input.outputTargetFps
                            / (float) (output.effectiveGeneratedCount + 1)))
                    : input.outputTargetFps
                : 0;
        int target = input.targetStrategy == PacingTargetStrategy.SOURCE_TARGET
                ? output.sourceTargetFps : output.displayTargetFps;

        if (input.menuOrOutOfBand) {
            output.owner = lastOwner;
            output.minecraftLimiterEnabled = lastOwner == PacingOwner.MINECRAFT;
            output.reason = "out-of-band retains normal owner without a new sleep";
        } else if (input.reflexSupported && input.reflexPacingEnabled && target > 0) {
            output.owner = PacingOwner.REFLEX;
            output.reflexFrameLimitUs = Math.max(0, Math.round(1_000_000f / target));
            output.minecraftLimiterEnabled = false;
            output.reason = "Reflex owns the frame-boundary pacing decision";
        } else if (input.frameGenerationRequested && input.displayBackpressure) {
            output.owner = PacingOwner.DISPLAY_BACKPRESSURE;
            output.minecraftLimiterEnabled = input.userFramerateLimit > 0;
            output.reason = "display backpressure is pacing; no second Caustica sleeper";
        } else if (input.userFramerateLimit > 0) {
            output.owner = PacingOwner.MINECRAFT;
            output.minecraftLimiterEnabled = true;
            output.reason = "Minecraft owns the user-requested limiter";
        } else {
            output.owner = PacingOwner.NONE;
            output.reason = "uncapped";
        }
        lastOwner = output.owner;
        lastTarget = target;
    }

    public boolean minecraftLimiterAllowed(PacingDecision decision) {
        return decision.minecraftLimiterEnabled;
    }

    public PacingOwner owner() { return lastOwner; }
    public int lastTarget() { return lastTarget; }
    public long generation() { return generation; }
}
