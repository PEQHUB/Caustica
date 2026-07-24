package dev.comfyfluffy.caustica.client.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.comfyfluffy.caustica.client.settings.SettingsCatalog.Control;
import dev.comfyfluffy.caustica.client.settings.SettingsCatalog.Page;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DimensionAtmosphereCatalogTest {
    private static final List<String> CONTROL_IDS = List.of(
            "sky.nether.horizonR",
            "sky.nether.horizonG",
            "sky.nether.horizonB",
            "sky.nether.zenithR",
            "sky.nether.zenithG",
            "sky.nether.zenithB",
            "sky.nether.brightness",
            "sky.nether.saturation",
            "sky.nether.gradientPower",

            "sky.end.horizonR",
            "sky.end.horizonG",
            "sky.end.horizonB",
            "sky.end.zenithR",
            "sky.end.zenithG",
            "sky.end.zenithB",
            "sky.end.brightness",
            "sky.end.saturation",
            "sky.end.gradientPower"
    );

    private static final List<String> SECTION_IDS = List.of(
            "sky.nether.colors",
            "sky.nether.shape",
            "sky.end.colors",
            "sky.end.shape"
    );

    @Test
    void registersEveryDimensionAtmosphereControl() {
        for (String id : CONTROL_IDS) {
            Control control = SettingsCatalog.byId(id);

            assertNotNull(control, "Missing control: " + id);
            assertEquals(
                    Page.SKY_ATMOSPHERE,
                    control.page(),
                    "Wrong page for: " + id
            );
        }
    }

    @Test
    void registersEveryDimensionAtmosphereSection() {
        for (String id : SECTION_IDS) {
            assertNotNull(
                    SettingsCatalog.section(
                            Page.SKY_ATMOSPHERE,
                            id
                    ),
                    "Missing section: " + id
            );
        }
    }
}
