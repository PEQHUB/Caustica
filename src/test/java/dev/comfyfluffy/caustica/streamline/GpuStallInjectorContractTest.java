package dev.comfyfluffy.caustica.streamline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GpuStallInjectorContractTest {
    @Test
    void disabledByDefaultInNormalTestProcess() {
        assertFalse(GpuStallInjector.enabled());
        assertFalse(GpuStallInjector.shouldInject(0));
    }
}
