package dev.comfyfluffy.caustica.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CaptureSessionPolicyTest {
    @AfterEach
    void clearScreenshotLease() {
        CaptureSession.discardScreenshotsForShutdown();
    }

    @Test
    void pausesOnlyAnUnsharedIntegratedServer() {
        assertTrue(CaptureSession.shouldPauseIntegratedServer(true, false));
        assertFalse(CaptureSession.shouldPauseIntegratedServer(true, true));
        assertFalse(CaptureSession.shouldPauseIntegratedServer(false, false));
    }

    @Test
    void screenshotLeaseBlocksOverlappingWritesUntilCompletion() {
        long f4 = CaptureSession.acquireScreenshot(true);
        assertNotEquals(0L, f4);
        assertTrue(CaptureSession.screenshotIsUltra(f4));
        assertTrue(CaptureSession.acquireScreenshot(false) == 0L);

        CaptureSession.releaseScreenshot(f4);
        assertFalse(CaptureSession.screenshotIsUltra(f4));
    }

    @Test
    void lateScreenshotCallbackCannotReleaseAReplacement() {
        long old = CaptureSession.acquireScreenshot(true);
        CaptureSession.discardScreenshotsForShutdown();
        long current = CaptureSession.acquireScreenshot(true);

        CaptureSession.releaseScreenshot(old);
        assertNotEquals(0L, current);
        assertTrue(CaptureSession.screenshotIsUltra(current));
    }
}
