package dev.comfyfluffy.caustica.rt.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.model.geom.ModelPart;

/** Narrow runtime bridge for the exact EMF implementation whose capture semantics we verify. */
final class RtEmfCompatibility {
    static final String MOD_ID = "entity_model_features";
    static final String SUPPORTED_VERSION = "3.2.6";
    static final String EMF_CUBE_CLASS =
            "traben.entity_model_features.models.parts.EMFModelPartCustom$EMFCube";
    private static final String EMF_PART_PREFIX = "traben.entity_model_features.models.parts.";

    private RtEmfCompatibility() {
    }

    static boolean isEmfCube(ModelPart.Cube cube) {
        return cube.getClass().getName().equals(EMF_CUBE_CLASS);
    }

    static boolean isEmfPart(ModelPart part) {
        return part.getClass().getName().startsWith(EMF_PART_PREFIX);
    }

    static Submission beginSubmission() {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (container.isEmpty()) {
            return Submission.unsupported("EMF mod metadata unavailable");
        }
        String version = container.get().getMetadata().getVersion().getFriendlyString();
        if (!SUPPORTED_VERSION.equals(version)) {
            return Submission.unsupported("unsupported EMF version " + version
                    + " (expected " + SUPPORTED_VERSION + ')');
        }
        Access access = AccessHolder.INSTANCE;
        if (access == null) {
            return Submission.unsupported("EMF 3.2.6 reflection contract unavailable");
        }
        try {
            Object handler = access.config.invoke(null);
            Object config = access.getConfig.invoke(handler);
            Object entity = access.getEmfEntity.invoke(null);
            Object renderMode = access.getRenderModeFor.invoke(config, entity);
            if (!(renderMode instanceof Enum<?> mode) || !"NORMAL".equals(mode.name())) {
                return Submission.unsupported("EMF render mode is not NORMAL");
            }
            boolean layerPhase = (boolean) access.isLayerPhase.invoke(null);
            return new Submission(access, layerPhase, null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return Submission.unsupported("EMF submission state unavailable: "
                    + exception.getClass().getSimpleName());
        }
    }

    enum Action {
        RENDER,
        SKIP_SUBTREE,
        UNSUPPORTED
    }

    record PartDecision(Action action, int variant, String reason) {
        static PartDecision render(int variant) {
            return new PartDecision(Action.RENDER, variant, null);
        }

        static PartDecision skip(int variant) {
            return new PartDecision(Action.SKIP_SUBTREE, variant, null);
        }

        static PartDecision unsupported(String reason) {
            return new PartDecision(Action.UNSUPPORTED, 0, reason);
        }
    }

    static final class Submission {
        private final Access access;
        private final boolean layerPhase;
        private final String unsupportedReason;

        private Submission(Access access, boolean layerPhase, String unsupportedReason) {
            this.access = access;
            this.layerPhase = layerPhase;
            this.unsupportedReason = unsupportedReason;
        }

        static Submission unsupported(String reason) {
            return new Submission(null, false, reason);
        }

        boolean supported() {
            return access != null;
        }

        String unsupportedReason() {
            return unsupportedReason;
        }

        String applyPreRenderAnimation(ModelPart part) {
            if (!access.emfPart.isInstance(part)) {
                return "EMF topology has no EMF model part";
            }
            try {
                Object root = access.getRoot.invoke(part);
                if (root == null) {
                    return "EMF model part has no root";
                }
                // EMFModelPartWithState.render performs these immediately before it renders geometry.
                // Its animation runnable is entity-render-count guarded, so the generic canary render that
                // follows observes the same pose without evaluating expressions a second time.
                access.oneTimeRunnable.invoke(root);
                access.animate.invoke(root);
                return null;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return "EMF pre-render animation unavailable: " + exception.getClass().getSimpleName();
            }
        }

        PartDecision inspect(ModelPart part) {
            if (!access.emfPart.isInstance(part)) {
                return PartDecision.render(0);
            }
            try {
                int variant = access.emfPartWithState.isInstance(part)
                        ? access.currentModelVariant.getInt(part) : 0;
                Object textureOverride = access.textureOverride.get(part);
                if (textureOverride == null) {
                    return PartDecision.render(variant);
                }
                if (access.emfCustomPart.isInstance(part) && layerPhase) {
                    Object root = access.getRoot.invoke(part);
                    if (root != null && access.isMainModel.getBoolean(root)) {
                        return PartDecision.skip(variant);
                    }
                }
                return PartDecision.unsupported("EMF texture override requires generic material routing");
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return PartDecision.unsupported("EMF part state unavailable: "
                        + exception.getClass().getSimpleName());
            }
        }
    }

    private static final class AccessHolder {
        static final Access INSTANCE = Access.create();
    }

    private record Access(Class<?> emfPart, Class<?> emfCustomPart, Class<?> emfPartWithState,
                          Field textureOverride, Field currentModelVariant, Field isMainModel,
                          Method getRoot, Method config, Method getConfig, Method getEmfEntity,
                          Method getRenderModeFor, Method isLayerPhase, Method oneTimeRunnable,
                          Method animate) {
        static Access create() {
            try {
                Class<?> emfPart = Class.forName(
                        "traben.entity_model_features.models.parts.EMFModelPart");
                Class<?> emfCustomPart = Class.forName(
                        "traben.entity_model_features.models.parts.EMFModelPartCustom");
                Class<?> emfPartWithState = Class.forName(
                        "traben.entity_model_features.models.parts.EMFModelPartWithState");
                Class<?> emfRoot = Class.forName(
                        "traben.entity_model_features.models.parts.EMFModelPartRoot");
                Class<?> emfConfig = Class.forName("traben.entity_model_features.config.EMFConfig");
                Class<?> emf = Class.forName("traben.entity_model_features.EMF");
                Class<?> context = Class.forName(
                        "traben.entity_model_features.models.animation.EMFAnimationEntityContext");
                Class<?> emfEntity = Class.forName("traben.entity_model_features.utils.EMFEntity");

                Method config = emf.getMethod("config");
                Method getConfig = config.getReturnType().getMethod("getConfig");
                Method getEmfEntity = context.getMethod("getEMFEntity");
                Method isLayerPhase = context.getMethod("isLayerPhase");
                Method getRenderModeFor = emfConfig.getMethod("getRenderModeFor", emfEntity);
                return new Access(emfPart, emfCustomPart, emfPartWithState,
                        emfPart.getField("textureOverride"),
                        emfPartWithState.getField("currentModelVariant"),
                        emfRoot.getField("isMainModel"),
                        emfPart.getMethod("getRoot"), config, getConfig, getEmfEntity,
                        getRenderModeFor, isLayerPhase,
                        emfRoot.getMethod("oneTimeRunnable"), emfRoot.getMethod("animate"));
            } catch (ReflectiveOperationException | LinkageError exception) {
                return null;
            }
        }
    }
}
