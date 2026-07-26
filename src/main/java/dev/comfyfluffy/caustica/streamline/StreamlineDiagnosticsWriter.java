package dev.comfyfluffy.caustica.streamline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coalescing off-thread diagnostics writer. The producer only publishes primitive telemetry. */
public final class StreamlineDiagnosticsWriter implements AutoCloseable {
    private final StreamlineTelemetry telemetry;
    private final Path output;
    private final AtomicBoolean requested = new AtomicBoolean();
    private final Thread worker;
    private volatile boolean running = true;
    private final StreamlineTelemetry.MutableSnapshot snapshot = new StreamlineTelemetry.MutableSnapshot();

    public StreamlineDiagnosticsWriter(StreamlineTelemetry telemetry, Path output) {
        this.telemetry = telemetry;
        this.output = output;
        worker = new Thread(this::run, "caustica-streamline-diagnostics");
        worker.setDaemon(true);
        worker.start();
    }

    public void requestWrite() {
        requested.set(true);
    }

    private void run() {
        while (running) {
            if (requested.getAndSet(false) && telemetry.copyInto(snapshot)) {
                try {
                    Files.createDirectories(output.getParent());
                    Files.writeString(output, "timestampNs=" + snapshot.timestampNs
                            + ",frameToken=" + snapshot.frameToken
                            + ",runtimeState=" + snapshot.runtimeState
                            + ",reason=" + snapshot.reason + "\n");
                } catch (IOException ignored) {
                    // Diagnostics are best-effort and never feed back into the frame path.
                }
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public void close() {
        running = false;
        worker.interrupt();
    }
}
