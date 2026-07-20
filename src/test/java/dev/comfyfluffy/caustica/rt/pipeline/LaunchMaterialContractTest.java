package dev.comfyfluffy.caustica.rt.pipeline;

import dev.comfyfluffy.caustica.rt.material.RtMaterialRegistry;
import dev.comfyfluffy.caustica.rt.material.RtMaterials;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LaunchMaterialContractTest {
    @Test
    void javaAndShaderFamilyAbiMatchWithoutGrowingTheHeader() throws Exception {
        String common = read("shaders/world/world_common.slang");
        assertEquals(16, RtMaterialRegistry.MATERIAL_FAMILY_SHIFT);
        assertEquals(31, RtMaterialRegistry.MATERIAL_FAMILY_MASK);
        assertEquals(1, RtMaterials.Profile.FOLIAGE.ordinal());
        assertEquals(7, RtMaterials.Profile.WOOL.ordinal());
        assertTrue(common.contains("MATERIAL_FAMILY_SHIFT = 16u"));
        assertTrue(common.contains("MATERIAL_FAMILY_FOLIAGE = 1u"));
        assertTrue(common.contains("MATERIAL_FAMILY_WOOL = 7u"));
        assertTrue(common.contains("MATERIAL_FAMILY_IRON = 9u"));
        assertTrue(common.contains("MATERIAL_FAMILY_RAW_COPPER = 18u"));
    }

    @Test
    void foliageFallbackPreservesAuthoredZero() throws Exception {
        String hit = read("shaders/world/world.rchit.slang");
        assertTrue(hit.contains("(header.features & MATERIAL_FEATURE_SPEC) == 0u"));
        assertTrue(hit.contains("family == MATERIAL_FAMILY_FOLIAGE"));
        assertTrue(hit.contains("surface.sss = header.params.w"));
        assertTrue(hit.contains("surface.sss = allowSss ? surface0.a : 0.0"));
    }

    @Test
    void fiberUsesExistingContinuationAndStaysOutOfDirectionlessSharcValues() throws Exception {
        String raygen = read("shaders/world/world.rgen.slang");
        String function = between(raygen, "float3 evaluateFiberSheen", "float3 enchantedF0");
        assertTrue(function.contains("sqrt(max(1.0 - NoH * NoH"));
        assertTrue(function.contains("0.25 / max(NoI + NoO - NoI * NoO"));
        assertFalse(function.contains("TraceRay"));
        assertFalse(function.contains("Sampler"));
        assertFalse(function.contains("rnd("));
        assertTrue(raygen.contains("float3 l = sampleEon"));
        assertTrue(raygen.contains("fiberWeight * evaluateFiberSheen(l, v, n, diffAlb)"));
        assertTrue(raygen.contains("liveBrdf += fiberWeight * evaluateFiberSheen"));
        assertTrue(raygen.contains("cacheableDirectLighting += diffuseBrdf"));
        assertTrue(raygen.contains("liveDirectLighting += liveBrdf"));
    }

    @Test
    void enchantmentTagsBaseGeometryAndSuppressesCoplanarGlint() throws Exception {
        String collector = read("src/main/java/dev/comfyfluffy/caustica/rt/entity/RtEntityCollector.java");
        String hit = read("shaders/world/world.rchit.slang");
        String raygen = read("shaders/world/world.rgen.slang");
        assertTrue(collector.contains("RenderPipelines.GLINT"));
        assertTrue(collector.contains("FoilType.NONE"));
        assertTrue(collector.contains("orPrimitiveFlags(lastBasePrimStart, lastBasePrimEnd"));
        assertTrue(collector.contains("entityGlintUnmatched"));
        assertTrue(hit.contains("(pr.flags & PRIM_ENCHANTED)"));
        assertTrue(raygen.contains("F0 = enchantedF0(F0, dot(n, v))"));
        String glintBranch = between(collector, "if (isGlint(renderType))", "if (!capturesModel(model))");
        assertTrue(glintBranch.contains("return;"));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(from >= 0 && to > from, "missing source contract boundaries");
        return source.substring(from, to);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }
}
