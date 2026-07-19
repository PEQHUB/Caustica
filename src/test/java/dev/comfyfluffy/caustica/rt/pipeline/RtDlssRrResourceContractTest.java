package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.comfyfluffy.caustica.streamline.StreamlineAbi;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

final class RtDlssRrResourceContractTest {
    @Test
    void diffusePathGuideExtendsTheCoreContractOnlyWhenSelected() {
        assertEquals(10, RtDlssRr.requiredResourceCount(false));
        assertEquals(11, RtDlssRr.requiredResourceCount(true));
        assertEquals(13, RtDlssRr.requiredResourceCount(false, true));
        assertEquals(14, RtDlssRr.requiredResourceCount(true, true));
        assertEquals(11, RtDlssRr.requiredResourceCount(false, false, true));
        assertEquals(12, RtDlssRr.requiredResourceCount(true, false, true));
        assertEquals(14, RtDlssRr.requiredResourceCount(false, true, true));
        assertEquals(15, RtDlssRr.requiredResourceCount(true, true, true));
    }

    @Test
    void layeredTransparencyUsesTheOfficialStreamline212Tags() {
        assertEquals(40, RtDlssRr.BUFFER_COLOR_BEFORE_TRANSPARENCY);
        assertEquals(51, RtDlssRr.BUFFER_TRANSPARENCY_LAYER);
        assertEquals(52, RtDlssRr.BUFFER_TRANSPARENCY_LAYER_OPACITY);
        assertEquals(26, RtDlssRr.BUFFER_PARTICLE_HINT);
        assertEquals(VK10.VK_FORMAT_R16G16B16A16_SFLOAT, RtDlssRr.TRANSPARENCY_LAYER_FORMAT);
        assertEquals(VK10.VK_FORMAT_R16G16B16A16_SFLOAT, RtDlssRr.TRANSPARENCY_LAYER_OPACITY_FORMAT);
        assertEquals(10, StreamlineAbi.VERSION);
    }

    @Test
    void vulkanTextureDescriptorUsesImageAndViewWithoutAllocatorMemory() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment resources = StreamlineAbi.allocate(arena, StreamlineAbi.RESOURCE_DESC_SIZE);
            RtDlssRr.writeResource(resources, 0, 0x1111L, 0x2222L, 126,
                    1280, 720, 14, 0x1f);
            ByteBuffer bytes = StreamlineAbi.bytes(resources);

            assertEquals(0x1111L, bytes.getLong(0));
            assertEquals(0x2222L, bytes.getLong(8));
            assertEquals(0L, bytes.getLong(16));
            assertEquals(1280, bytes.getInt(28));
            assertEquals(720, bytes.getInt(32));
            assertEquals(126, bytes.getInt(36));
            assertEquals(0x1f, bytes.getInt(52));
            assertEquals(14, bytes.getInt(56));
            assertEquals(2, bytes.getInt(60));
            assertEquals(1, bytes.get(64));
        }
    }

    @Test
    void acceptanceSchemaTracksReleaseTelemetryContract() {
        assertEquals(7, StreamlineAcceptanceReport.SCHEMA_VERSION);
    }
}
