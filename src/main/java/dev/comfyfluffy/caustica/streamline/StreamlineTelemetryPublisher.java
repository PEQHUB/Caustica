package dev.comfyfluffy.caustica.streamline;

/** Shared publisher used by the render path; consumers sample without backpressure. */
public final class StreamlineTelemetryPublisher {
    public static final StreamlineTelemetryPublisher INSTANCE = new StreamlineTelemetryPublisher();
    private final StreamlineTelemetry telemetry = new StreamlineTelemetry();

    private StreamlineTelemetryPublisher() {
    }

    public StreamlineTelemetry telemetry() {
        return telemetry;
    }
}
