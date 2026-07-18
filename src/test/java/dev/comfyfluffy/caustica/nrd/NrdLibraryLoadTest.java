package dev.comfyfluffy.caustica.nrd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

final class NrdLibraryLoadTest {
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void packagedBridgeLoadsWithoutASecondVendorDll() {
        Path bridge = Path.of("build", "native", "nrd_bridge", "release", "nrdbridge.dll")
                .toAbsolutePath();
        assertTrue(Files.isRegularFile(bridge), () -> "missing " + bridge);
        NrdLibrary library = NrdLibrary.load(bridge);
        assertEquals("NRD 4.17.3 / integration 22", library.version());
    }
}
