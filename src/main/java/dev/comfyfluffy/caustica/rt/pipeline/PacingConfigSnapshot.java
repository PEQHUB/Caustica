package dev.comfyfluffy.caustica.rt.pipeline;

/** Mutable frame-boundary configuration snapshot; callers reuse one instance. */
public final class PacingConfigSnapshot {
    public long generation;
    public long swapchainGeneration;
    public boolean frameGenerationRequested;
    public boolean frameGenerationAvailable;
    public boolean reflexSupported;
    public boolean reflexPacingEnabled;
    public boolean menuOrOutOfBand;
    public boolean displayBackpressure;
    public int userFramerateLimit;
    public int outputTargetFps;
    public int generatedFrames;
    public PacingTargetStrategy targetStrategy = PacingTargetStrategy.DISPLAYED_TARGET;
    public int minimumSourceFps = 90;

    public void copyFrom(PacingConfigSnapshot other) {
        generation = other.generation;
        swapchainGeneration = other.swapchainGeneration;
        frameGenerationRequested = other.frameGenerationRequested;
        frameGenerationAvailable = other.frameGenerationAvailable;
        reflexSupported = other.reflexSupported;
        reflexPacingEnabled = other.reflexPacingEnabled;
        menuOrOutOfBand = other.menuOrOutOfBand;
        displayBackpressure = other.displayBackpressure;
        userFramerateLimit = other.userFramerateLimit;
        outputTargetFps = other.outputTargetFps;
        generatedFrames = other.generatedFrames;
        targetStrategy = other.targetStrategy;
        minimumSourceFps = other.minimumSourceFps;
    }
}
