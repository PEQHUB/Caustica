package dev.comfyfluffy.caustica.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.comfyfluffy.caustica.client.settings.SettingsCatalog;
import dev.comfyfluffy.caustica.client.settings.SettingsCatalog.Control;
import dev.comfyfluffy.caustica.rt.AtmosphereDimension;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AtmosphereDimensionSelectorContractTest {
    @Test
    void selectorAlwaysContainsAllProfilesInStableOrder() {
        assertEquals(
                List.of(
                        AtmosphereDimension.OVERWORLD,
                        AtmosphereDimension.NETHER,
                        AtmosphereDimension.END
                ),
                CausticaSettingsScreen.atmosphereSelectorDimensions()
        );
    }

    @Test
    void netherSearchResultSelectsNetherProfile() {
        Control control = SettingsCatalog.byId("sky.nether.horizonR");

        assertNotNull(control);
        assertEquals(
                AtmosphereDimension.NETHER,
                CausticaSettingsScreen.atmosphereDimensionForControl(control)
        );
    }

    @Test
    void endSearchResultSelectsEndProfile() {
        Control control = SettingsCatalog.byId("sky.end.horizonR");

        assertNotNull(control);
        assertEquals(
                AtmosphereDimension.END,
                CausticaSettingsScreen.atmosphereDimensionForControl(control)
        );
    }

    @Test
    void overworldSearchResultSelectsOverworldProfile() {
        Control control = SettingsCatalog.byId("sky.rayleigh");

        assertNotNull(control);
        assertEquals(
                AtmosphereDimension.OVERWORLD,
                CausticaSettingsScreen.atmosphereDimensionForControl(control)
        );
    }
}
