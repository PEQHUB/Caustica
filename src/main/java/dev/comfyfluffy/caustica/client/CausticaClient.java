package dev.comfyfluffy.caustica.client;

import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtDeviceBringup;
import dev.comfyfluffy.caustica.rt.RtComposite;
import dev.comfyfluffy.caustica.rt.RtFrameStats;
import dev.comfyfluffy.caustica.rt.RtUiOverlay;
import dev.comfyfluffy.caustica.rt.entity.RtEntities;
import dev.comfyfluffy.caustica.rt.entity.RtEntityTextures;
import dev.comfyfluffy.caustica.rt.material.RtBlockMaterials;
import dev.comfyfluffy.caustica.rt.material.RtMaterialRegistry;
import dev.comfyfluffy.caustica.rt.terrain.RtTerrain;
import dev.comfyfluffy.caustica.rt.terrain.RtWorkerPool;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class CausticaClient implements ClientModInitializer {
	private static boolean rtInitDone = false;

	@Override
	public void onInitializeClient() {
		CausticaMod.LOGGER.info("Caustica client initialized");
		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath(CausticaMod.MOD_ID, "offline-renderer"),
				OfflineRendererHud::extractRenderState);

		// The GpuDevice exists well before the first tick, so a one-shot at tick start
		// runs on the render thread with the device idle between frames.
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			CausticaDebugBridge.tick(client);
			while (CausticaKeyMappings.OPEN_MENU.consumeClick()) {
				client.setScreenAndShow(new CausticaOptionsScreen(null, client.options));
			}
			while (OfflineGroundTruth.KEY.consumeClick()) {
				OfflineGroundTruth.INSTANCE.handleHotkey(client);
			}
			OfflineGroundTruth.INSTANCE.tick(client);
			while (UltraScreenshot.KEY.consumeClick()) {
				UltraScreenshot.INSTANCE.toggle(client);
			}
			UltraScreenshot.INSTANCE.tick(client);
			dev.comfyfluffy.caustica.streamline.StreamlineSwapchainCoordinator.INSTANCE.synchronizeRequestedState();
			if (!VanillaRenderController.rtRuntimeWorkRequested()) {
				if (rtInitDone) {
					shutdownRt();
				}
				return;
			}

			// Bring up the RT device/context once; terrain residency + the composite follow below.
			if (!rtInitDone && RtDeviceBringup.rtRequested()) {
				RtContext ctx = RtContext.get();
				if (ctx != null) {
					rtInitDone = true;
				}
			}

			// P2: once RT is up, keep section residency synced to vanilla's loaded chunks around
			// the player — builds newly-in-range sections, frees out-of-range ones, per tick.
			if (rtInitDone) {
				RtContext ctx = RtContext.currentOrNull();
				if (ctx != null) {
					RtFrameStats.FRAME.beginIfInactive();
					// Bring the world pipeline + LabPBR atlases up before terrain tessellates, so per-prim
					// material flags resolve from the first section (PBR on join, no re-extract). No-op
					// until we're in a world with the block atlas loaded, or once already created.
					RtComposite.INSTANCE.ensureResourcesReady(ctx);
					if (!CapturePause.sceneFreezeRequested()) {
						RtTerrain.update(ctx);
					}
					// Log DLSS-FG availability once when frame generation is enabled (capability query only;
					// the present-loop integration that consumes it is built separately).
					if (dev.comfyfluffy.caustica.rt.pipeline.RtDlssFg.enabled()) {
						dev.comfyfluffy.caustica.rt.pipeline.RtDlssFg.INSTANCE.probeAvailabilityOnce();
					}
				}
			}
		});

		// Vanilla's full render-state invalidation (LevelExtractor.allChanged(): dimension change via
		// setLevel, render-distance change, F3+A) — drop RT terrain residency so it rebuilds for the new
		// world. Fixes stale geometry persisting across an End→Overworld switch (coords alone aren't
		// world-unique). Resource reloads do NOT fire this; that path is handled separately.
		InvalidateRenderStateCallback.EVENT.register(() -> {
			if (OfflineGroundTruth.INSTANCE.engaged()) {
				OfflineGroundTruth.INSTANCE.abort(
						Component.translatable("caustica.status.offline.worldStateChanged"));
			}
			RtTerrain.requestFullClear();
			RtComposite.INSTANCE.requestTemporalReset();
			if (VanillaRenderController.INSTANCE.shouldResetRtFailureLatchForInvalidation()) {
				RtComposite.INSTANCE.resetFailureLatch(); // F3+A doubles as manual RT recovery after a latched failure
			}
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			shutdownRt();
			dev.comfyfluffy.caustica.rt.pipeline.RtDlssFg.INSTANCE.destroy();
		});
	}

	private static void shutdownRt() {
		boolean initialized = rtInitDone;
		rtInitDone = false; // fail closed even if one cleanup owner throws
		RtContext ctx = RtContext.currentOrNull();
		CleanupFailures failures = new CleanupFailures();

		failures.run("world render scaler", WorldRenderScaler.INSTANCE::destroy);
		// GUI redirection is not gated by RT initialization; always release its texture target.
		failures.run("RT UI overlay", RtUiOverlay::destroy);
		if (initialized && ctx != null) {
			failures.run("device idle wait", () -> ctx.waitIdle("client shutdown"));
			// Terrain owns the terminal callback for every accepted worker task. Keep the pool alive
			// until those tasks have submitted their work or delivered cancellation.
			failures.run("terrain", () -> RtTerrain.shutdown(ctx));
			failures.run("entities", RtEntities.INSTANCE::shutdown);
		}
		failures.run("terrain worker pool", RtWorkerPool.INSTANCE::shutdown);
		failures.run("RT composite", RtComposite.INSTANCE::destroy);
		failures.run("entity textures", RtEntityTextures.INSTANCE::reset);
		// These buffers belong to RtContext's VMA allocator and must die before the context.
		failures.run("material registry", RtMaterialRegistry.INSTANCE::destroy);
		failures.run("block materials", RtBlockMaterials.INSTANCE::destroy);
		if (initialized && ctx != null) {
			failures.run("RT context", ctx::destroy);
		}
		failures.report();
	}

	@FunctionalInterface
	private interface CleanupStep {
		void run() throws Throwable;
	}

	private static final class CleanupFailures {
		private Throwable first;

		void run(String owner, CleanupStep step) {
			try {
				step.run();
			} catch (Throwable failure) {
				CausticaMod.LOGGER.error("Failed to release {} during RT shutdown", owner, failure);
				if (first == null) first = failure;
				else first.addSuppressed(failure);
			}
		}

		void report() {
			if (first != null) {
				CausticaMod.LOGGER.error("RT shutdown completed with cleanup failures", first);
			}
		}
	}
}
