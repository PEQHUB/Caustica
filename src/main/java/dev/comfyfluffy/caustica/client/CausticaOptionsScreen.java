package dev.comfyfluffy.caustica.client;

import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;

/** Stable entry point for Caustica's complete settings workstation. */
public final class CausticaOptionsScreen extends CausticaSettingsScreen {
    public CausticaOptionsScreen(Screen previous, Options options) {
        super(previous, options);
    }

    public CausticaOptionsScreen(Screen previous, Options options, String initialCategory) {
        super(previous, options, initialCategory);
    }
}
