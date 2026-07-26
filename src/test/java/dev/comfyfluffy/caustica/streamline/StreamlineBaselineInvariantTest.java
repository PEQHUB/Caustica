package dev.comfyfluffy.caustica.streamline;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StreamlineBaselineInvariantTest {
    @Test
    void bridgeSourceRetainsNextPresentOrdering() throws Exception {
        String source = Files.readString(Path.of("native", "streamline_bridge", "streamline_bridge.cpp"));
        assertTrue(source.contains("slbridge_set_dlssg_options"));
        assertTrue(source.contains("slbridge_vk_queue_present"));
        assertTrue(source.contains("slDLSSGSetOptions"));
    }
}
