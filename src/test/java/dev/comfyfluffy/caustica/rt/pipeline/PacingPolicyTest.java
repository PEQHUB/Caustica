package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PacingPolicyTest {
    @Test
    void reflexOwnsPacingWithoutMagicSubstitute() {
        PacingConfigSnapshot config = new PacingConfigSnapshot();
        config.reflexSupported = true;
        config.reflexPacingEnabled = true;
        config.outputTargetFps = 225;
        config.generatedFrames = 2;
        PacingDecision decision = new PacingDecision();
        new PacingPolicy().resolveInto(config, decision);
        assertEquals(PacingOwner.REFLEX, decision.owner);
        assertFalse(decision.minecraftLimiterEnabled);
        assertEquals(4_444, decision.reflexFrameLimitUs);
    }

    @Test
    void sourceTargetHasExplicitMinimumFloor() {
        PacingConfigSnapshot config = new PacingConfigSnapshot();
        config.reflexSupported = true;
        config.reflexPacingEnabled = true;
        config.outputTargetFps = 225;
        config.generatedFrames = 4;
        config.targetStrategy = PacingTargetStrategy.SOURCE_TARGET;
        config.minimumSourceFps = 90;
        PacingDecision decision = new PacingDecision();
        new PacingPolicy().resolveInto(config, decision);
        assertEquals(90, decision.sourceTargetFps);
        assertEquals(11_111, decision.reflexFrameLimitUs);
    }
}
