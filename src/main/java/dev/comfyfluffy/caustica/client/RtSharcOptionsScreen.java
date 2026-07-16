package dev.comfyfluffy.caustica.client;

import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.rt.RtComposite;
import dev.comfyfluffy.caustica.rt.RtSharcSupport;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/** Complete, scrollable SHaRC configuration and live status surface. */
public final class RtSharcOptionsScreen extends OptionsSubScreen {
    private Button status;

    public RtSharcOptionsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, Component.translatable("caustica.options.rt.sharcSettings.title"));
    }

    @Override
    protected void addOptions() {
        status = Button.builder(Component.empty(), ignored -> {}).width(Button.BIG_WIDTH).build();
        status.active = false;
        list.addBig(status);
        for (var option : RtVideoOptions.sharcOptions()) list.addBig(option);
        Button reset = Button.builder(Component.translatable("caustica.options.rt.sharcReset"), ignored ->
                RtComposite.INSTANCE.requestSharcReset("manual menu reset")).width(Button.BIG_WIDTH).build();
        list.addBig(reset);
        refreshStatus();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        refreshStatus();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() {
        super.removed();
        CausticaConfig.save();
    }

    private void refreshStatus() {
        if (status == null) return;
        String value = !RtSharcSupport.available() ? RtSharcSupport.status()
                : RtComposite.INSTANCE.sharcActive() ? "Active"
                : CausticaConfig.Rt.Sharc.ENABLED.value() ? "Ready" : "Off";
        status.setMessage(Component.literal("NVIDIA SHaRC 1.6.5.0: " + value));
    }
}
