package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class EntityEmfDirectCaptureContractTest {
    @Test
    void emfBackendIsPinnedExactAndFailClosed() throws Exception {
        String compatibility = source(
                "src/main/java/dev/comfyfluffy/caustica/rt/entity/RtEmfCompatibility.java");
        String emitter = source(
                "src/main/java/dev/comfyfluffy/caustica/rt/entity/RtCuboidEmitter.java");

        assertTrue(compatibility.contains("SUPPORTED_VERSION = \"3.2.6\""));
        assertTrue(compatibility.contains("EMFModelPartCustom$EMFCube"));
        assertTrue(compatibility.contains("!\"NORMAL\".equals(mode.name())"));
        assertTrue(compatibility.contains("EMF texture override requires generic material routing"));
        assertTrue(compatibility.contains("return PartDecision.skip(variant)"),
                "The EMF custom-part layer-phase subtree suppression must be preserved");
        assertTrue(compatibility.contains("oneTimeRunnable.invoke(root)"));
        assertTrue(compatibility.contains("animate.invoke(root)"),
                "Direct capture must execute EMF's render-time animation boundary before traversal");
        assertTrue(emitter.contains("if (template.emf && !emfDirectCaptureProven())"));
        assertTrue(emitter.contains("return false;"),
                "EMF direct capture must remain quarantined until same-invocation parity is possible");
    }

    @Test
    void firstUseCanaryKeepsGenericOutputAndQuarantinesMismatch() throws Exception {
        String collector = source(
                "src/main/java/dev/comfyfluffy/caustica/rt/entity/RtEntityCollector.java");
        String emitter = source(
                "src/main/java/dev/comfyfluffy/caustica/rt/entity/RtCuboidEmitter.java");

        int candidate = collector.indexOf("cuboidEmitter.emit(directTemplate, poseStack, parityCapture");
        int generic = collector.indexOf("model.renderToBuffer(poseStack, capture", candidate);
        int compare = collector.indexOf("capture.assertSubmissionBitwiseIdentical", generic);
        assertTrue(candidate >= 0 && generic > candidate && compare > generic,
                "A canary must write only to scratch while the generic renderer supplies visible output");
        assertTrue(collector.contains("cuboidEmitter.rejectCanary"));
        int rejection = collector.indexOf("cuboidEmitter.rejectCanary");
        int rejectionCatchEnd = collector.indexOf("}", rejection);
        assertTrue(!collector.substring(rejection, rejectionCatchEnd).contains("throw mismatch"),
                "A rejected probation backend must quarantine and retain generic output, not tear down RT");
        assertTrue(collector.indexOf("capture.assertSubmissionBitwiseIdentical", compare + 1) > compare,
                "The strict ongoing parity oracle must remain for approved direct submissions");
        assertTrue(emitter.contains("quarantinedSemanticKeys.contains"));
        assertTrue(emitter.contains("Set<String> verifiedSemanticKeys"),
                "Semantic states must use exact keys rather than collision-prone hash identity");
    }

    @Test
    void backendStatusIsExplicitAndFirstPersonRemainsGeneric() throws Exception {
        String collector = source(
                "src/main/java/dev/comfyfluffy/caustica/rt/entity/RtEntityCollector.java");
        String emitter = source(
                "src/main/java/dev/comfyfluffy/caustica/rt/entity/RtCuboidEmitter.java");

        assertTrue(collector.contains("captureMode == CaptureMode.FULL"));
        assertTrue(collector.contains("renderFilteredHumanoid"));
        assertTrue(collector.contains("entityCaptureBackendEmfDirect"));
        assertTrue(collector.contains("entityCaptureBackendVanillaDirect"));
        assertTrue(collector.contains("entityCaptureBackendGenericFallback"));
        assertTrue(emitter.contains("backend=GENERIC_FALLBACK"));
        assertTrue(emitter.contains("backend=EMF_DIRECT enabled"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
