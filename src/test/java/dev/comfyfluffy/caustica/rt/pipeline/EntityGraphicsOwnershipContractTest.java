package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EntityGraphicsOwnershipContractTest {
    private static final Path ENTITIES = Path.of(
            "src/main/java/dev/comfyfluffy/caustica/rt/entity/RtEntities.java");
    private static final Path COMPOSITE = Path.of(
            "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");

    @Test
    void successfulCommandEnqueuePublishesTheReservedGraphicsUse() throws Exception {
        String source = Files.readString(COMPOSITE);
        int reserve = source.indexOf("beginGraphicsTerrainUse(encoder)");
        int capture = source.indexOf("RtEntities.INSTANCE.beginFrame", reserve);
        int execute = source.indexOf("encoder.execute(cmd)", capture);
        int mark = source.indexOf("RtEntities.INSTANCE.markGraphicsUse(frameEntities, graphicsUse)", execute);
        assertTrue(reserve >= 0 && reserve < capture && capture < execute && execute < mark,
                "Entity owners must receive only the successfully enqueued frame's graphics token");
    }

    @Test
    void everyMutableOwnerWaitsBeforeReuseAndRigidReadsKeepTheirSlot() throws Exception {
        String source = Files.readString(ENTITIES);
        assertTrue(source.contains("awaitGraphicsUse(ctx, build, lists.lastGraphicsUse"));
        int waitLists = source.indexOf("awaitGraphicsUse(ctx, build, lists.lastGraphicsUse");
        int releaseLists = source.indexOf("lists.releaseDeferred()", waitLists);
        int resetLists = source.indexOf("lists.reset()", releaseLists);
        assertTrue(waitLists < releaseLists && releaseLists < resetLists);
        assertTrue(source.contains("awaitGraphicsUse(ctx, build, build.table.lastGraphicsUse"));
        assertTrue(source.contains("awaitGraphicsUse(ctx, build, slot.lastGraphicsUse"));
        assertTrue(source.contains("EntitySlot refSlot"));
        assertTrue(source.contains("ea.refSlot = slot"));
        assertTrue(source.contains("build.lists.usedEntitySlots.add(ea.refSlot)"));
        assertTrue(source.contains("ea.refSlot = null"));
        assertTrue(source.split("build\\.lists\\.usedBlockEntities\\.add\\(e\\)", -1).length - 1 >= 2,
                "Both new block-entity builds and cached emits must retain their owner");
    }

    @Test
    void motionAndGeometryTableWritesArePublishedOnceBeforeReturn() throws Exception {
        String source = Files.readString(ENTITIES);
        int flushStage = source.indexOf("stage(\"entity.uploadFlush\")");
        int motion = source.indexOf("build.motion.flushWrites()", flushStage);
        int table = source.indexOf("build.table.buffer.flush", motion);
        int result = source.indexOf("FrameEntities result", table);
        assertTrue(flushStage >= 0 && flushStage < motion && motion < table && table < result);
        assertFalse(source.contains("slice.flush()"),
                "Displacement allocations must be coalesced into the arena's pre-submit flush");
        assertFalse(source.contains("disp.flush()"),
                "Per-entity displacement flushes defeat page-level publication");
    }

    @Test
    void frameAgeNoLongerClaimsGpuCompletionForEntityResources() throws Exception {
        String source = Files.readString(ENTITIES);
        assertFalse(source.contains("record Deferred("));
        assertFalse(source.contains("frameCounter() + KEEP_FRAMES"));
        assertTrue(source.contains("long lastGraphicsUse"));
        assertTrue(source.contains("enqueueDestroyAfterGraphics(e.lastGraphicsUse"));
        assertTrue(source.contains("enqueueDestroyAfterGraphics(slot.lastGraphicsUse"));
        assertTrue(source.contains("enqueueDestroyAfterGraphics(old.lastGraphicsUse"));
    }

    @Test
    void offlineSnapshotCarriesAndRepublishesTheSameOwnerManifest() throws Exception {
        String entities = Files.readString(ENTITIES);
        String composite = Files.readString(COMPOSITE);
        assertTrue(entities.contains("if (offlineSession && offlineSnapshot != null)"));
        assertTrue(entities.contains("return offlineSnapshot;"));
        assertTrue(entities.contains("new FrameUse(build.lists, build.table)"));
        assertTrue(composite.contains("markGraphicsUse(frameEntities, graphicsUse)"),
                "Every repeated offline execute must raise the retained owners' last-use token");
    }
}
