package dev.comfyfluffy.caustica.rt.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class RtToneMappingTest {
    @Test
    void sdrModeParsesCanonicalNames() {
        assertEquals(RtToneMapping.SdrMode.AGX, RtToneMapping.SdrMode.parse("agx"));
        assertEquals(RtToneMapping.SdrMode.PBR_NEUTRAL, RtToneMapping.SdrMode.parse("pbr-neutral"));
        assertEquals(RtToneMapping.SdrMode.REINHARD, RtToneMapping.SdrMode.parse("reinhard"));
        assertEquals(RtToneMapping.SdrMode.ACES, RtToneMapping.SdrMode.parse("aces"));
        assertEquals(RtToneMapping.SdrMode.LOTTES, RtToneMapping.SdrMode.parse("lottes"));
        assertEquals(RtToneMapping.SdrMode.FROSTBITE, RtToneMapping.SdrMode.parse("frostbite"));
        assertEquals(RtToneMapping.SdrMode.UNCHARTED_2, RtToneMapping.SdrMode.parse("uncharted2"));
        assertEquals(RtToneMapping.SdrMode.GT, RtToneMapping.SdrMode.parse("gt"));
        assertEquals(RtToneMapping.SdrMode.PSYCHOV11, RtToneMapping.SdrMode.parse("psychov11"));
        assertEquals(RtToneMapping.SdrMode.PSYCHOV23, RtToneMapping.SdrMode.parse("psychov23"));
        assertEquals(RtToneMapping.SdrMode.PSYCHOV24_EXPERIMENTAL, RtToneMapping.SdrMode.parse("psychov24-experimental"));
    }

    @Test
    void sdrModeParsesAliases() {
        assertEquals(RtToneMapping.SdrMode.UNCHARTED_2, RtToneMapping.SdrMode.parse("uncharted-2"));
        assertEquals(RtToneMapping.SdrMode.GT, RtToneMapping.SdrMode.parse("uchimura"));
        assertEquals(RtToneMapping.SdrMode.PSYCHOV11, RtToneMapping.SdrMode.parse("psychov"));
        assertEquals(RtToneMapping.SdrMode.PSYCHOV24_EXPERIMENTAL, RtToneMapping.SdrMode.parse("psychov24"));
    }

    @Test
    void sdrModeFallsBackToAgxOnUnknown() {
        assertEquals(RtToneMapping.SdrMode.AGX, RtToneMapping.SdrMode.parse("unknown"));
        assertEquals(RtToneMapping.SdrMode.AGX, RtToneMapping.SdrMode.parse(null));
        assertEquals(RtToneMapping.SdrMode.AGX, RtToneMapping.SdrMode.parse(""));
        assertEquals(RtToneMapping.SdrMode.AGX, RtToneMapping.SdrMode.parse("  "));
    }

    @Test
    void sdrModeParsesCaseInsensitive() {
        assertEquals(RtToneMapping.SdrMode.AGX, RtToneMapping.SdrMode.parse("AGX"));
        assertEquals(RtToneMapping.SdrMode.PBR_NEUTRAL, RtToneMapping.SdrMode.parse("PBR-NEUTRAL"));
        assertEquals(RtToneMapping.SdrMode.PSYCHOV11, RtToneMapping.SdrMode.parse("PSYCHOV11"));
    }

    @Test
    void sdrModeIdsMatchExpected() {
        assertEquals(0, RtToneMapping.SdrMode.AGX.id());
        assertEquals(1, RtToneMapping.SdrMode.PBR_NEUTRAL.id());
        assertEquals(2, RtToneMapping.SdrMode.REINHARD.id());
        assertEquals(3, RtToneMapping.SdrMode.ACES.id());
        assertEquals(4, RtToneMapping.SdrMode.LOTTES.id());
        assertEquals(5, RtToneMapping.SdrMode.FROSTBITE.id());
        assertEquals(6, RtToneMapping.SdrMode.UNCHARTED_2.id());
        assertEquals(7, RtToneMapping.SdrMode.GT.id());
        assertEquals(8, RtToneMapping.SdrMode.PSYCHOV11.id());
        assertEquals(9, RtToneMapping.SdrMode.PSYCHOV23.id());
        assertEquals(10, RtToneMapping.SdrMode.PSYCHOV24_EXPERIMENTAL.id());
    }

    @Test
    void hdrModeParsesCanonicalNames() {
        assertEquals(RtToneMapping.HdrMode.CAUSTICA, RtToneMapping.HdrMode.parse("caustica"));
        assertEquals(RtToneMapping.HdrMode.BT2390, RtToneMapping.HdrMode.parse("bt2390"));
        assertEquals(RtToneMapping.HdrMode.PSYCHOV11, RtToneMapping.HdrMode.parse("psychov11"));
        assertEquals(RtToneMapping.HdrMode.PSYCHOV23, RtToneMapping.HdrMode.parse("psychov23"));
        assertEquals(RtToneMapping.HdrMode.PSYCHOV24_EXPERIMENTAL, RtToneMapping.HdrMode.parse("psychov24-experimental"));
    }

    @Test
    void hdrModeParsesAliases() {
        assertEquals(RtToneMapping.HdrMode.BT2390, RtToneMapping.HdrMode.parse("bt-2390"));
        assertEquals(RtToneMapping.HdrMode.PSYCHOV11, RtToneMapping.HdrMode.parse("psychov"));
        assertEquals(RtToneMapping.HdrMode.PSYCHOV24_EXPERIMENTAL, RtToneMapping.HdrMode.parse("psychov24"));
    }

    @Test
    void hdrModeFallsBackToCausticaOnUnknown() {
        assertEquals(RtToneMapping.HdrMode.CAUSTICA, RtToneMapping.HdrMode.parse("unknown"));
        assertEquals(RtToneMapping.HdrMode.CAUSTICA, RtToneMapping.HdrMode.parse(null));
        assertEquals(RtToneMapping.HdrMode.CAUSTICA, RtToneMapping.HdrMode.parse(""));
    }

    @Test
    void hdrModeIdsMatchExpected() {
        assertEquals(0, RtToneMapping.HdrMode.CAUSTICA.id());
        assertEquals(1, RtToneMapping.HdrMode.BT2390.id());
        assertEquals(2, RtToneMapping.HdrMode.PSYCHOV11.id());
        assertEquals(3, RtToneMapping.HdrMode.PSYCHOV23.id());
        assertEquals(4, RtToneMapping.HdrMode.PSYCHOV24_EXPERIMENTAL.id());
    }

    @Test
    void sdrConfigNamesContainsAllModes() {
        var names = RtToneMapping.sdrConfigNames();
        assertNotNull(names);
        assertEquals(11, names.size());
        assertEquals("agx", names.get(0));
        assertEquals("pbr-neutral", names.get(1));
        assertEquals("psychov24-experimental", names.get(10));
    }

    @Test
    void hdrConfigNamesContainsAllModes() {
        var names = RtToneMapping.hdrConfigNames();
        assertNotNull(names);
        assertEquals(5, names.size());
        assertEquals("caustica", names.get(0));
        assertEquals("bt2390", names.get(1));
        assertEquals("psychov24-experimental", names.get(4));
    }
}
