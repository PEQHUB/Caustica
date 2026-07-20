package dev.comfyfluffy.caustica.client;

import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.rt.pipeline.RtDlssFg;
import dev.comfyfluffy.caustica.streamline.StreamlineRuntime;
import dev.comfyfluffy.caustica.streamline.StreamlineSwapchainCoordinator;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/** Detailed Streamline state kept one level below the normal player-facing controls. */
public final class RtFrameGenerationDiagnosticsScreen extends OptionsSubScreen {
    private Button statusWidget;
    private Button runtimeWidget;
    private Button detailsWidget;
    private Button vramWidget;

    public RtFrameGenerationDiagnosticsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, Component.translatable("caustica.options.rt.fg.diagnostics.title"));
    }

    @Override
    protected void addOptions() {
        statusWidget = diagnosticButton();
        runtimeWidget = diagnosticButton();
        detailsWidget = diagnosticButton();
        vramWidget = Button.builder(Component.empty(), button -> {
            RtDlssFg.INSTANCE.requestVramEstimate();
            refreshDiagnostics();
        }).width(Button.BIG_WIDTH).build();
        list.addBig(statusWidget);
        list.addBig(runtimeWidget);
        list.addBig(detailsWidget);
        list.addBig(vramWidget);
        refreshDiagnostics();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        refreshDiagnostics();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void refreshDiagnostics() {
        if (statusWidget == null) {
            return;
        }
        RtDlssFg fg = RtDlssFg.INSTANCE;
        Component availability = fg.isActive() ? Component.translatable("caustica.feature.active")
                : fg.isAvailable() && fg.hasGeneratedFrames()
                        ? Component.translatable("caustica.feature.verifiedSuspended")
                : fg.isAvailable() && RtDlssFg.requested() ? Component.literal(fg.submissionStatus())
                : fg.isAvailable() ? Component.translatable("caustica.feature.available")
                : fg.unavailableReason().isBlank() ? Component.translatable("caustica.feature.unavailable")
                        : Component.literal(fg.unavailableReason());
        statusWidget.setMessage(Component.translatable("caustica.options.rt.fg.diagnostics.status", availability));
        Component maximum = fg.multiFrameCountMax() > 0 ? Component.literal((fg.multiFrameCountMax() + 1) + "x")
                : Component.translatable("caustica.options.rt.fg.diagnostics.notProbed");
        runtimeWidget.setMessage(Component.translatable("caustica.options.rt.fg.diagnostics.runtime",
                RtDlssFg.statusDescription(fg.runtimeStatus()), maximum, fg.configuredMultiFrameCount(),
                fg.effectiveMultiFrameCount(), fg.nativeSubmittedMultiFrameCount(),
                fg.framesActuallyPresented(), fg.maxFramesActuallyPresented()));
        Component reflex = fg.isActive()
                ? CausticaConfig.Rt.Reflex.LOW_LATENCY_BOOST.value()
                        ? Component.translatable("caustica.options.rt.fg.reflex.boost")
                        : Component.translatable("caustica.options.rt.fg.diagnostics.reflexRequired")
                : CausticaConfig.Rt.Reflex.ENABLED.value()
                        ? CausticaConfig.Rt.Reflex.LOW_LATENCY_BOOST.value()
                                ? Component.translatable("caustica.options.rt.fg.reflex.boost")
                                : Component.translatable("caustica.options.rt.fg.reflex.on")
                        : Component.translatable("caustica.options.rt.fg.reflex.off");
        var overrides = CausticaConfig.activeOverrides();
        Component overrideStatus = overrides.isEmpty()
                ? Component.translatable("caustica.options.rt.fg.diagnostics.none")
                : Component.translatable("caustica.options.rt.fg.diagnostics.overrideSummary",
                        overrides.size(), overrides.getFirst().key());
        Component dispatch = Component.translatable(StreamlineSwapchainCoordinator.INSTANCE.nativeProxyDispatch()
                ? "caustica.options.rt.fg.diagnostics.streamlineProxy"
                : "caustica.options.rt.fg.diagnostics.nativeDispatch");
        Component queueFallback = fg.queueFallbackActive()
                ? Component.translatable("caustica.options.rt.fg.diagnostics.queueFallback", fg.queueFallbackReason())
                : Component.empty();
        Component vsyncSupport = Component.translatable(fg.vsyncSupportAvailable()
                ? "caustica.feature.available" : "caustica.feature.unsupported");
        detailsWidget.setMessage(Component.translatable("caustica.options.rt.fg.diagnostics.details",
                reflex, fg.outputTargetFps(),
                String.format(java.util.Locale.ROOT, "%.2f", fg.renderedTargetFps()),
                fg.totalFrameMultiplier(), fg.framesActuallyPresented(),
                String.format(java.util.Locale.ROOT, "%.2f%%", fg.generatedFrameDropPercent()),
                StreamlineSwapchainCoordinator.INSTANCE.requestedPresentMode(),
                StreamlineSwapchainCoordinator.INSTANCE.normalizedPresentMode(),
                StreamlineSwapchainCoordinator.INSTANCE.nativePresentMode(), dispatch,
                fg.effectiveQueueMode(), fg.queuePolicyReason(), queueFallback,
                StreamlineRuntime.flipMeteringState(), vsyncSupport,
                StreamlineSwapchainCoordinator.INSTANCE.applicationImageCount(),
                StreamlineSwapchainCoordinator.INSTANCE.proxyVisibleImageCount(), fg.reflexIntervalUs(),
                CausticaConfig.Rt.DlssRr.ENABLED.configuredValue(),
                CausticaConfig.Rt.DlssRr.ENABLED.value(), overrideStatus, fg.featureVersion()));
        Component vram = fg.estimatedVramUsage() > 0L
                ? Component.literal((fg.estimatedVramUsage() / (1024L * 1024L)) + " MiB")
                : Component.translatable("caustica.options.rt.fg.diagnostics.notEstimated");
        vramWidget.setMessage(Component.translatable("caustica.options.rt.fg.diagnostics.vram", vram));
    }

    private static Button diagnosticButton() {
        Button button = Button.builder(Component.empty(), ignored -> {
        }).width(Button.BIG_WIDTH).build();
        button.active = false;
        return button;
    }
}
