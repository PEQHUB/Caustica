package dev.comfyfluffy.caustica.rt.pipeline;

public final class PacingDecision {
    public PacingOwner owner = PacingOwner.NONE;
    public int reflexFrameLimitUs;
    public boolean minecraftLimiterEnabled;
    public int sourceTargetFps;
    public int displayTargetFps;
    public int effectiveGeneratedCount;
    public long generation;
    public String reason = "unresolved";

    public void reset() {
        owner = PacingOwner.NONE;
        reflexFrameLimitUs = 0;
        minecraftLimiterEnabled = false;
        sourceTargetFps = 0;
        displayTargetFps = 0;
        effectiveGeneratedCount = 0;
        generation = 0L;
        reason = "unresolved";
    }
}
